"""Private snapshot composition controls, not authoritative Core acceptance."""

from copy import deepcopy
from hashlib import sha256
import json
from pathlib import Path
from unittest.mock import Mock
from uuid import uuid4

import pytest

from hdpi.assistance_http import reference_runtime
from hdpi.domain.errors import CitationVerificationError, ContractValidationError
from hdpi.generation.errors import (
    GenerationConfigurationError,
    GenerationContractError,
    GenerationTimeout,
    GenerationUnavailable,
)


ROOT = Path(__file__).resolve().parents[3]
CONFIG = (
    ROOT / "intelligence/src/apps/servicedesk/intelligence/assistance-reference.json"
)


@pytest.fixture
def runtime():
    return reference_runtime(CONFIG, sha256(CONFIG.read_bytes()).hexdigest())


def request(documents=None, question="device connection"):
    content = "Verify the device connection."

    return {
        "contractVersion": "0.1.0",
        "requestId": str(uuid4()),
        "workspaceId": "synthetic-workspace",
        "profileId": "service-desk",
        "profileVersion": "0.3.0",
        "policyIdentity": "servicedesk-assistance@0.1.0",
        "question": question,
        "documents": (
            documents
            if documents is not None
            else [
                {
                    "metadataId": "11111111-1111-4111-8111-111111111111",
                    "version": "synthetic-v1",
                    "checksum": sha256(content.encode()).hexdigest(),
                    "content": content,
                    "mediaType": "text/plain",
                }
            ]
        ),
    }


def test_snapshot_retrieval_exact_quotes_and_no_provider_identity_leak(runtime):
    provider = runtime.generation.provider
    spy = Mock(wraps=provider.generate)
    object.__setattr__(provider, "generate", spy)
    payload = request()
    response = runtime.assist(payload)

    assert response["status"] == "SUCCESS"
    assert response["answer"] == "Verify the device connection. [C1]"
    assert response["requestId"] == payload["requestId"]
    assert (
        response["citations"][0]["sourceChecksum"]
        == payload["documents"][0]["checksum"]
    )
    assert response["retrievalRuntime"]["engine"] == "tfidf"
    assert response["needsReview"] is True

    view = repr(spy.call_args.args[0])
    assert payload["workspaceId"] not in view
    assert payload["documents"][0]["metadataId"] not in view
    assert payload["requestId"] not in view


@pytest.mark.parametrize("documents,question", [([], "device"), (None, "zzzzunknown")])
def test_no_evidence_refuses_without_inference(runtime, documents, question):
    spy = Mock(side_effect=AssertionError("Provider must not be called"))
    object.__setattr__(runtime.generation.provider, "generate", spy)

    response = runtime.assist(request(documents, question))

    assert response["status"] == "INSUFFICIENT_EVIDENCE"
    assert response["answer"] is None
    assert response["citations"] == []
    spy.assert_not_called()


@pytest.mark.parametrize(
    "field,value",
    [
        ("subject", "forged-actor"),
        ("model", "forged-model"),
        ("question", " " * 20),
        ("question", "x" * 2001),
        ("workspaceId", "foreign/../workspace"),
        ("documents", [{}]),
    ],
)
def test_invalid_snapshot_rejected_before_generation(runtime, field, value):
    payload = request()
    payload[field] = value

    with pytest.raises(ContractValidationError):
        runtime.assist(payload)


@pytest.mark.parametrize("field", ["profileId", "profileVersion", "policyIdentity"])
def test_identity_override_fails_closed(runtime, field):
    payload = request()
    payload[field] = "different"

    with pytest.raises(GenerationConfigurationError):
        runtime.assist(payload)


def test_changed_checksum_duplicate_sources_and_request_isolation(runtime):
    payload = request()
    poisoned = deepcopy(payload)
    poisoned["documents"][0]["content"] = "Changed source"

    with pytest.raises(CitationVerificationError):
        runtime.assist(poisoned)

    duplicated = deepcopy(payload)
    duplicated["documents"] *= 2

    with pytest.raises(GenerationContractError):
        runtime.assist(duplicated)

    assert runtime.assist(payload)["status"] == "SUCCESS"
    assert runtime.assist(request([], "device"))["citations"] == []


@pytest.mark.parametrize(
    "error,code",
    [
        (GenerationTimeout, "GENERATION_TIMEOUT"),
        (GenerationUnavailable, "GENERATION_UNAVAILABLE"),
    ],
)
def test_operational_errors_never_return_answer(runtime, error, code):
    object.__setattr__(
        runtime.generation.provider,
        "generate",
        Mock(side_effect=error("private details")),
    )
    response = runtime.assist(request())

    assert response["status"] == "DEGRADED"
    assert response["reasonCode"] == code
    assert response["answer"] is None
    assert response["citations"] == []
    assert "private details" not in json.dumps(response)


def test_config_pin_and_no_implicit_real_model_fallback(tmp_path):
    with pytest.raises(ValueError):
        reference_runtime(CONFIG, "0" * 64)

    config = json.loads(CONFIG.read_text("utf-8"))
    config["mode"] = "real-model"
    path = tmp_path / "config.json"
    path.write_text(json.dumps(config), encoding="utf-8")

    with pytest.raises(ValueError):
        reference_runtime(path, sha256(path.read_bytes()).hexdigest())


def test_same_runtime_serves_second_domain_with_only_injected_assets():
    from hdpi.generation.deterministic import CitationBoundTemplateProvider
    from hdpi.generation.policy import GenerationPolicy
    from hdpi.profiles.registry import ProfileRegistry
    from hdpi.services.assistance_runtime import AssistanceRuntime

    policy = GenerationPolicy(
        identity="external-policy@1", instructions="Quote evidence.", max_claims=3
    )
    runtime = AssistanceRuntime(
        provider=CitationBoundTemplateProvider(app_prompt_version=policy.identity),
        policy=policy,
        profile_id="fake-domain",
        profile_version="1.0",
        profiles=ProfileRegistry(
            ROOT / "intelligence/tests/generic/fixtures/fake-domain"
        ),
    )
    payload = request()
    payload.update(
        profileId="fake-domain", profileVersion="1.0", policyIdentity=policy.identity
    )

    response = runtime.assist(payload)

    assert response["status"] == "SUCCESS"
    assert response["generationRuntime"]["app_prompt_version"] == "external-policy@1"
