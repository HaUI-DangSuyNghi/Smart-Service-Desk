from copy import deepcopy
from dataclasses import replace
from pathlib import Path
import tempfile

import pytest

from cloud_providers.contracts import CloudFailure, decode_json, encode_json
from cloud_providers.credentials import ServerCredential
from cloud_providers.http_client import public_address, retry_after
from cloud_providers.runtime import CloudAssistanceRuntime
from hdpi.generation.errors import GenerationConfigurationError

from .conftest import KEYS, ROOT, FakeClient, completion, payload, write_policy


@pytest.mark.parametrize("route_id", ["gemini-primary", "groq-secondary"])
@pytest.mark.parametrize("decision", ["SELECT", "REFUSE"])
def test_native_cloud_selection_preserves_exact_grounding_and_minimizes_payload(
    registry, route_id, decision
):
    vendor = registry.routes[route_id].vendor
    client = FakeClient(
        completion(
            vendor,
            {
                "decision": decision,
                "citation_ids": ["C1"] if decision == "SELECT" else [],
            },
        )
    )
    runtime = CloudAssistanceRuntime(registry, client=client, environment=KEYS)
    request = payload(registry, route_id)
    response = runtime.assist(request)

    assert response["status"] == (
        "SUCCESS" if decision == "SELECT" else "INSUFFICIENT_EVIDENCE"
    )
    assert response["answer"] == (
        "Verify the device connection. [C1]" if decision == "SELECT" else None
    )
    assert response["routeBinding"] == request["routeBinding"]
    assert response["generationRuntime"] == request["routeBinding"]["expectedRuntime"]
    assert response["routeFeedback"]["providerCalls"] == 1
    sent = client.calls[0][1].decode()
    for forbidden in [
        request["workspaceId"],
        request["documents"][0]["metadataId"],
        request["requestId"],
        "Bearer",
        "sourceChecksum",
        "synthetic-actor",
    ]:
        assert forbidden not in sent
    for secret in KEYS.values():
        assert secret not in sent and secret not in repr(response)


@pytest.mark.parametrize(
    "mutation", ["question", "source", "route", "identity", "digest", "class"]
)
def test_unapproved_data_or_route_is_rejected_before_vendor(registry, mutation):
    client = FakeClient()
    runtime = CloudAssistanceRuntime(registry, client=client, environment=KEYS)
    request = payload(registry)
    if mutation == "question":
        request["question"] = "Real customer private question"
    elif mutation == "source":
        request["documents"][0]["checksum"] = "a" * 64
    elif mutation == "route":
        request["routeBinding"]["routeId"] = "unapproved-route"
    elif mutation == "identity":
        request["routeBinding"]["expectedRuntime"]["model"] = "different"
    elif mutation == "class":
        request["routeBinding"]["dataClass"] = "private-customer-data"
    else:
        request["routeBinding"]["routeDigest"] = "0" * 64

    with pytest.raises(Exception):
        runtime.assist(request)
    assert not client.calls


@pytest.mark.parametrize("vendor", ["gemini", "groq"])
@pytest.mark.parametrize(
    "mutation", ["prose", "alias", "duplicate", "model", "finish", "tools"]
)
def test_untrusted_native_completion_never_reaches_public_answer(
    registry, vendor, mutation
):
    value = completion(vendor)
    if vendor == "gemini":
        message = value["candidates"][0]["content"]["parts"][0]
        if mutation == "finish":
            value["candidates"][0]["finishReason"] = "MAX_TOKENS"
        elif mutation == "model":
            value["modelVersion"] = "other-model"
        elif mutation == "tools":
            message["functionCall"] = {"name": "execute"}
        else:
            message["text"] = {
                "prose": "answer prose",
                "alias": '{"decision":"SELECT","citation_ids":["C99"]}',
                "duplicate": '{"decision":"REFUSE","decision":"SELECT","citation_ids":["C1"]}',
            }[mutation]
    else:
        message = value["choices"][0]["message"]
        if mutation == "finish":
            value["choices"][0]["finish_reason"] = "length"
        elif mutation == "model":
            value["model"] = "other-model"
        elif mutation == "tools":
            message["tool_calls"] = []
        else:
            message["content"] = {
                "prose": "answer prose",
                "alias": '{"decision":"SELECT","citation_ids":["C99"]}',
                "duplicate": '{"decision":"REFUSE","decision":"SELECT","citation_ids":["C1"]}',
            }[mutation]

    runtime = CloudAssistanceRuntime(
        registry, client=FakeClient(value), environment=KEYS
    )
    response = runtime.assist(
        payload(registry, "gemini-primary" if vendor == "gemini" else "groq-secondary")
    )
    assert response["status"] == "ERROR"
    assert response["answer"] is None and response["citations"] == []
    assert response["routeFeedback"]["state"] == "DISABLED"


@pytest.mark.parametrize(
    "code,state",
    [
        ("PROVIDER_RATE_LIMITED", "RATE_LIMITED"),
        ("GENERATION_UNAVAILABLE", "UNAVAILABLE"),
        ("GENERATION_TIMEOUT", "UNAVAILABLE"),
        ("PROVIDER_CONFIGURATION_ERROR", "DISABLED"),
    ],
)
def test_failure_cools_only_selected_route_and_never_replays(registry, code, state):
    client = FakeClient(failure=CloudFailure(code, state, 30))
    runtime = CloudAssistanceRuntime(registry, client=client, environment=KEYS)
    response = runtime.assist(payload(registry))
    assert response["answer"] is None
    assert response["reasonCode"] == code
    assert len(client.calls) == 1 and client.calls[0][0] == "gemini"
    runtime.assist(payload(registry))
    assert len(client.calls) == 1  # governor blocks before another vendor socket


def test_missing_secret_pending_receipt_changed_policy_and_bad_environment_block(
    registry,
):
    with pytest.raises(CloudFailure):
        CloudAssistanceRuntime(registry, client=FakeClient(), environment={})
    with tempfile.TemporaryDirectory() as directory:
        with pytest.raises(GenerationConfigurationError):
            write_policy(Path(directory), status="PENDING")
    registry.approval_path.write_bytes(b"{}")
    with pytest.raises(GenerationConfigurationError):
        registry.validate_current()


@pytest.mark.parametrize(
    "raw",
    [b'{"x":1,"x":2}', b'{"x":NaN}', b'{"x":1e999}', b"\xff", b"{} prose", b"[" * 100],
)
def test_outer_json_is_strict_without_rejecting_valid_usage_floats(raw):
    with pytest.raises(Exception):
        decode_json(raw)
    assert decode_json(b'{"usage":{"time":0.1}}')["usage"]["time"] == 0.1


def test_credential_source_ambiguity_does_not_echo_values():
    secret = "synthetic-value-that-must-not-appear"
    with pytest.raises(CloudFailure) as error:
        ServerCredential(
            "gemini", {"GEMINI_API_KEY": secret, "GEMINI_API_KEY_FILE": "missing"}
        ).read()
    assert secret not in str(error.value)


def test_safe_metrics_are_per_request_and_never_contain_content_or_keys(registry):
    events = []
    client = FakeClient(completion("gemini"))
    runtime = CloudAssistanceRuntime(
        registry, client=client, environment=KEYS, observer=events.append
    )
    request = payload(registry)
    runtime.assist(request)

    assert len(events) == 1
    assert events[0]["provider"] == "gemini"
    assert events[0]["attemptSlotsReserved"] == 1
    assert len(events[0]["observedModelDigest"]) == 64
    assert events[0]["inputTokens"] is None
    assert events[0]["outputTokens"] is None
    assert events[0]["usageCost"] == "NOT_VERIFIED"
    serialized = encode_json(events).decode()

    for private in (
        request["question"],
        request["documents"][0]["content"],
        request["workspaceId"],
        *KEYS.values(),
    ):
        assert private not in serialized


def test_dns_private_addresses_and_retry_header_bounds(monkeypatch):
    monkeypatch.setattr(
        "socket.getaddrinfo", lambda *a, **k: [(2, 1, 6, "", ("127.0.0.1", 443))]
    )
    with pytest.raises(CloudFailure) as error:
        public_address("api.groq.com", 1)
    assert error.value.code == "EGRESS_NOT_AUTHORIZED"
    assert retry_after("999999") == 3600
    assert retry_after("garbage private body") == 60
