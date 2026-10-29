from contextlib import contextmanager
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from threading import Thread
from uuid import uuid4
import urllib.error
import urllib.request

import pytest

from hdpi.advisory_api import ApiAssistanceProvider
from hdpi.classification.api_provider import ApiServer
from hdpi.classification.protocols import ClassificationError
from hdpi.generation.errors import GenerationUnavailable


@contextmanager
def upstream(
    vendor,
    *,
    status=200,
    text="Kiểm tra cáp nguồn bên ngoài.",
    finish="stop",
    tool=False,
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
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            if vendor == "gemini":
                parts = (
                    [{"functionCall": {"name": "invalid"}}]
                    if tool
                    else [{"text": text}]
                )
                value = {
                    "candidates": [
                        {
                            "finishReason": (
                                "STOP" if finish == "stop" else "MAX_TOKENS"
                            ),
                            "content": {"parts": parts},
                        }
                    ]
                }
            else:
                message = {"content": text}
                if tool:
                    message["tool_calls"] = [{"function": {"name": "invalid"}}]
                value = {"choices": [{"finish_reason": finish, "message": message}]}
            self.wfile.write(json.dumps(value).encode())

    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    thread = Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        yield f"http://127.0.0.1:{server.server_port}/v1", captured
    finally:
        server.shutdown()
        server.server_close()
        thread.join()


def request():
    return {
        "contractVersion": "advisory@0.1.0",
        "requestId": str(uuid4()),
        "question": "Tôi cần làm gì?",
        "context": {
            "title": "Máy không lên nguồn",
            "description": "Cần thi sau 15 phút",
        },
    }


def provider(tmp_path, vendor, base):
    key = tmp_path / "key"
    key.write_text("SYNTHETIC_NOT_A_REAL_KEY")
    return ApiAssistanceProvider(
        vendor=vendor,
        base_url=base,
        model="synthetic-model",
        credential_file=key,
        policy={"instructions": "Answer in Vietnamese; no fabricated citations."},
        allow_http=True,
    )


@pytest.mark.parametrize("vendor", ["gemini", "openai", "groq"])
def test_questions_use_text_generation_and_authorized_ticket_context(tmp_path, vendor):
    with upstream(vendor) as (base, captured):
        advice = provider(tmp_path, vendor, base)
        req = request()
        result = advice.answer(req)
    assert result == {
        "contractVersion": "advisory@0.1.0",
        "requestId": req["requestId"],
        "answer": "Kiểm tra cáp nguồn bên ngoài.",
    }
    path, headers, body = captured[0]
    assert "SYNTHETIC_NOT_A_REAL_KEY" not in path
    if vendor == "gemini":
        assert "responseFormat" not in body["generationConfig"]
        user = json.loads(body["contents"][0]["parts"][0]["text"])
    else:
        assert "response_format" not in body and "tools" not in body
        user = json.loads(body["messages"][1]["content"])
    assert user == {"question": req["question"], "ticket": req["context"]}


@pytest.mark.parametrize("vendor", ["gemini", "groq"])
@pytest.mark.parametrize(
    "kwargs",
    [
        {"text": " "},
        {"text": "x" * 16001},
        {"text": "bad\x00text"},
        {"finish": "length"},
        {"tool": True},
    ],
)
def test_unsafe_or_incomplete_provider_envelopes_are_rejected(tmp_path, vendor, kwargs):
    with upstream(vendor, **kwargs) as (base, _):
        with pytest.raises(ClassificationError):
            provider(tmp_path, vendor, base).answer(request())


@pytest.mark.parametrize("status", [401, 429, 503])
def test_provider_errors_do_not_leak_request_or_credentials(tmp_path, status, caplog):
    with upstream("openai", status=status) as (base, _):
        with pytest.raises(GenerationUnavailable):
            provider(tmp_path, "openai", base).answer(request())
    assert (
        "SYNTHETIC_NOT_A_REAL_KEY" not in caplog.text and "Tôi cần" not in caplog.text
    )


def test_question_limit_and_unknown_context_never_call_provider(tmp_path):
    with upstream("groq") as (base, captured):
        advice = provider(tmp_path, "groq", base)
        req = request()
        req["question"] = "x" * 2001
        with pytest.raises(ClassificationError):
            advice.answer(req)
        req = request()
        req["context"]["password"] = "unwanted"
        with pytest.raises(ClassificationError):
            advice.answer(req)
        assert not captured


def test_authenticated_http_assistance_route(tmp_path):
    with upstream("groq") as (base, _):
        advice = provider(tmp_path, "groq", base)
        server = ApiServer(
            ("127.0.0.1", 0), service=None, token="t" * 64, assistance=advice
        )
        thread = Thread(target=server.serve_forever, daemon=True)
        thread.start()
        url = (
            f"http://127.0.0.1:{server.server_port}/internal/intelligence/v1/assistance"
        )
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        try:
            for auth in [False, True]:
                headers = {"Content-Type": "application/json"}
                if auth:
                    headers["Authorization"] = "Bearer " + "t" * 64
                req = urllib.request.Request(
                    url, data=json.dumps(request()).encode(), headers=headers
                )
                if auth:
                    with opener.open(req, timeout=3) as response:
                        assert json.load(response)["answer"]
                else:
                    with pytest.raises(urllib.error.HTTPError) as error:
                        opener.open(req, timeout=3)
                    assert error.value.code == 403
        finally:
            server.shutdown()
            server.server_close()
            thread.join()
