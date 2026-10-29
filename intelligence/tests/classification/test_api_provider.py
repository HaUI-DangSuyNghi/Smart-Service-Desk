from contextlib import contextmanager
from hashlib import sha256
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import urllib.error
import urllib.request
from threading import Thread

import pytest

from hdpi.classification.api_provider import ApiProvider, ApiServer
from hdpi.classification.protocols import ClassificationError, ClassificationRequest
from hdpi.classification.service import ClassificationInferenceService
from hdpi.generation.errors import GenerationUnavailable


POLICY = {
    "allowedClasses": ["HARDWARE", "NETWORK"],
    "abstainId": "UNDETERMINED",
    "instructions": "Classify input data only; output JSON.",
}
FAKE_KEY = "SYNTHETIC_API_KEY_NOT_A_REAL_SECRET"


@contextmanager
def upstream(
    vendor,
    *,
    text='{"decision":"PROPOSE","classId":"HARDWARE"}',
    status=200,
    content_type="application/json"
):
    captured = []

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *_):
            pass

        def do_POST(self):
            captured.append(
                (
                    self.path,
                    dict(self.headers),
                    json.loads(self.rfile.read(int(self.headers["Content-Length"]))),
                )
            )
            self.send_response(status)
            self.send_header("Content-Type", content_type)
            if status == 307:
                self.send_header("Location", "https://must-not-follow.invalid/")
            self.end_headers()
            result = (
                {
                    "candidates": [
                        {"finishReason": "STOP", "content": {"parts": [{"text": text}]}}
                    ]
                }
                if vendor == "gemini"
                else {
                    "choices": [{"finish_reason": "stop", "message": {"content": text}}]
                }
            )
            self.wfile.write(json.dumps(result).encode())

    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    thread = Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        yield "http://127.0.0.1:" + str(server.server_port) + "/v1", captured
    finally:
        server.shutdown()
        server.server_close()
        thread.join()


def provider(tmp_path, vendor, base, **kwargs):
    key = tmp_path / "credential"
    key.write_text(FAKE_KEY, encoding="ascii")
    return ApiProvider(
        vendor=vendor,
        base_url=base,
        model="synthetic-model",
        credential_file=key,
        policy=POLICY,
        allow_http=True,
        **kwargs
    )


def request():
    return ClassificationRequest(
        fields=(("title", "Synthetic physical-power failure"),),
        allowed_classes=tuple(POLICY["allowedClasses"]),
        policy_digest="b" * 64,
    )


@pytest.mark.parametrize("vendor", ["gemini", "openai", "groq"])
def test_vendor_requests_return_only_neutral_unverified_observations(tmp_path, vendor):
    with upstream(vendor) as (base, captured):
        client = provider(tmp_path, vendor, base)
        observation = client.observe(request())
    assert observation.class_id == "HARDWARE"
    assert observation.model_scores is None
    assert observation.operational_authority is False
    path, headers, body = captured[0]
    headers = {name.lower(): value for name, value in headers.items()}
    if vendor == "gemini":
        assert path.endswith("/models/synthetic-model:generateContent")
        assert headers["x-goog-api-key"] == FAKE_KEY
        assert (
            body["generationConfig"]["responseFormat"]["text"]["mimeType"]
            == "APPLICATION_JSON"
        )
    else:
        assert path.endswith("/chat/completions")
        assert headers["authorization"] == "Bearer " + FAKE_KEY
        assert body["response_format"]["type"] == "json_object"
    assert FAKE_KEY not in json.dumps(body)


def test_structured_outputs_are_explicit_and_local_validation_remains_final(tmp_path):
    with upstream("openai") as (base, captured):
        client = provider(tmp_path, "openai", base, response_format="json_schema")
        client.observe(request())
    schema = captured[0][2]["response_format"]["json_schema"]
    assert schema["strict"] is True
    assert schema["schema"]["additionalProperties"] is False


@pytest.mark.parametrize(
    "text",
    [
        '{"decision":"PROPOSE","classId":"ROOT"}',
        '{"decision":"PROPOSE","classId":"HARDWARE","priority":"CRITICAL"}',
        '{"decision":"PROPOSE","decision":"ABSTAIN","classId":null}',
        '```json\n{"decision":"ABSTAIN","classId":null}\n```',
    ],
)
def test_provider_cannot_add_authority_priority_or_unapproved_classes(tmp_path, text):
    with upstream("openai", text=text) as (base, _):
        with pytest.raises(ClassificationError):
            provider(tmp_path, "openai", base).observe(request())


@pytest.mark.parametrize("status", [401, 429, 503, 307])
def test_rejections_and_redirects_do_not_leak_keys_or_raw_vendor_responses(
    tmp_path, status
):
    with upstream("openai", status=status) as (base, _):
        with pytest.raises(GenerationUnavailable) as error:
            provider(tmp_path, "openai", base).observe(request())
    assert FAKE_KEY not in str(error.value)


def test_https_is_required_and_credentials_are_not_allowed_in_urls(tmp_path):
    for base in (
        "http://example.com/v1",
        "https://user:password@example.com/v1",
        "https://example.com/v1?key=secret",
    ):
        with pytest.raises(ClassificationError):
            provider(tmp_path, "openai", base)


def test_existing_classification_envelope_is_used_end_to_end(tmp_path):
    with upstream("gemini") as (base, _):
        client = provider(tmp_path, "gemini", base)
        service = ClassificationInferenceService(
            model_digest=client.model_digest,
            policy_digest="b" * 64,
            allowed_classes=POLICY["allowedClasses"],
            abstain_id=POLICY["abstainId"],
            observe=client.observe,
        )
        fields = dict(request().fields)
        payload = dict(
            contractVersion="classification-inference@0.1.0",
            requestId="a" * 8
            + "-"
            + "a" * 4
            + "-"
            + "a" * 4
            + "-"
            + "a" * 4
            + "-"
            + "a" * 12,
            inputDigest=sha256(
                json.dumps(
                    fields, ensure_ascii=False, sort_keys=True, separators=(",", ":")
                ).encode()
            ).hexdigest(),
            modelDigest=client.model_digest,
            policyDigest="b" * 64,
            allowedClasses=POLICY["allowedClasses"],
            input=fields,
        )
        result = service.infer(payload)
    assert result["observation"]["classId"] == "HARDWARE"
    assert result["observation"]["score"] is None
    assert result["operationalAuthority"] is False


def test_gateway_health_and_auth_do_not_send_upstream_requests(tmp_path):
    with upstream("openai") as (base, captured):
        client = provider(tmp_path, "openai", base)
        service = ClassificationInferenceService(
            model_digest=client.model_digest,
            policy_digest="b" * 64,
            allowed_classes=POLICY["allowedClasses"],
            abstain_id=POLICY["abstainId"],
            observe=client.observe,
        )
        server = ApiServer(("127.0.0.1", 0), service=service, token="a" * 64)
        thread = Thread(target=server.serve_forever, daemon=True)
        thread.start()
        url = "http://127.0.0.1:" + str(server.server_port)
        try:
            with pytest.raises(urllib.error.HTTPError) as rejected:
                urllib.request.urlopen(url + "/health/ready", timeout=2)
            assert rejected.value.code == 403
            req = urllib.request.Request(
                url + "/health/ready", headers={"Authorization": "Bearer " + "a" * 64}
            )
            with urllib.request.urlopen(req, timeout=2) as response:
                health = json.load(response)
            assert health == {"ready": True, "upstreamVerified": False}
            assert captured == []
        finally:
            server.shutdown()
            server.server_close()
            thread.join()
