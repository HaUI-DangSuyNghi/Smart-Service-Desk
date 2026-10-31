"""The additive inference service is authenticated, neutral and bounded."""

from hashlib import sha256
import http.client
import json
from pathlib import Path
import sys
from threading import Thread

import pytest

from hdpi.classification.decision_observation import CONTRACT, DecisionObservation
from hdpi.classification.service import ClassificationInferenceService

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "integrations"))

from local_classification.classification_api import ClassificationServer  # noqa: E402


@pytest.fixture
def server():
    seen = []

    def observe(request):
        seen.append(request)
        return DecisionObservation(
            contract_version=CONTRACT,
            decision="PROPOSE",
            class_id="AVAILABLE",
            option_order=("AVAILABLE", "OCCUPIED", "UNKNOWN"),
            model_scores=None,
            score_semantics="NOT_AVAILABLE",
        )

    service = ClassificationInferenceService(
        model_digest="a" * 64,
        policy_digest="b" * 64,
        allowed_classes=("AVAILABLE", "OCCUPIED"),
        abstain_id="UNKNOWN",
        observe=observe,
    )
    httpd = ClassificationServer(("127.0.0.1", 0), service=service, token="x" * 40)
    thread = Thread(target=httpd.serve_forever, daemon=True)
    thread.start()
    try:
        yield httpd, seen
    finally:
        httpd.shutdown()
        httpd.server_close()
        thread.join(timeout=2)


def call(server, raw, *, token="x" * 40):
    connection = http.client.HTTPConnection(*server[0].server_address, timeout=3)
    connection.request(
        "POST",
        "/internal/intelligence/v1/classify",
        raw,
        {"Content-Type": "application/json", "Authorization": "Bearer " + token},
    )
    response = connection.getresponse()
    status, value, cache = (
        response.status,
        json.loads(response.read()),
        response.getheader("Cache-Control"),
    )
    connection.close()
    return status, value, cache


def test_authenticated_second_domain_contract_end_to_end(server):
    fields = {"room": "Café 🚀 available"}
    raw = json.dumps(
        {
            "contractVersion": "classification-inference@0.1.0",
            "requestId": "00000000-0000-0000-0000-000000000000",
            "input": fields,
            "inputDigest": sha256(
                json.dumps(
                    fields, ensure_ascii=False, sort_keys=True, separators=(",", ":")
                ).encode()
            ).hexdigest(),
            "modelDigest": "a" * 64,
            "policyDigest": "b" * 64,
            "allowedClasses": ["AVAILABLE", "OCCUPIED"],
        }
    ).encode()
    status, value, cache = call(server, raw)
    assert status == 200 and cache == "no-store"
    assert (
        value["observation"]["classId"] == "AVAILABLE"
        and value["observation"]["score"] is None
    )
    assert value["operationalAuthority"] is False
    assert len(server[1]) == 1


@pytest.mark.parametrize(
    "raw,token",
    [(b"{}", "wrong"), (b'{"x":1,"x":2}', "x" * 40), (b"x" * 16385, "x" * 40)],
)
def test_auth_and_malformed_bodies_never_call_provider(server, raw, token):
    status, value, _ = call(server, raw, token=token)
    assert status in (400, 403, 502)
    assert server[1] == []
    assert value == {"code": "CLASSIFICATION_REJECTED"}
