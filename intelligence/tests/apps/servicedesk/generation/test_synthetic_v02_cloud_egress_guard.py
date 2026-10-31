from dataclasses import asdict
from datetime import datetime, timedelta, timezone
import json
from types import SimpleNamespace

import pytest

from hdpi.assistance_http import reference_runtime
from hdpi.generation.models import GenerationRuntimeIdentity
from hdpi.profiles.registry import ProfileRegistry

from _cloud import authorize_dataset, run_cloud
from _contracts import DatasetError, digest, write_json
from _evaluation import CONFIG, PROFILES


def arguments(path):
    return SimpleNamespace(
        allow_external_egress=True,
        data_class="SYNTHETIC_DEV_ONLY",
        provider="gemini",
        scope="smoke",
        approval_file=path,
        approval_sha256="",
        installed=True,
        max_requests=30,
        max_input_tokens=1000000,
        max_output_tokens=10000,
        max_cost="0",
    )


def receipt_for(bundle):
    return {
        "schemaVersion": "synthetic-dataset-egress@0.2.0",
        "status": "APPROVED",
        "owner": "TEST_ONLY_NOT_A_REAL_APPROVAL",
        "expiresAt": (datetime.now(timezone.utc) + timedelta(hours=1)).isoformat(),
        "dataClass": "SYNTHETIC_DEV_ONLY",
        "datasetDigest": digest(bundle["dataset"]),
        "manifestDigest": digest(bundle["manifest"]),
        "scope": "smoke",
        "provider": "gemini",
        "model": "fixture-model",
        "providerCatalogDigest": "a" * 64,
        "maxRequests": 400,
        "maxInputTokens": 10000000,
        "maxOutputTokens": 200000,
        "maxCostMicros": 0,
        "freeOnly": True,
    }


@pytest.mark.parametrize("interval", [-1, 61, False])
def test_invalid_pacing_cannot_create_a_provider(
    pristine_bundle, approved_scope, interval
):
    args, _, environment = approved_scope
    args.minimum_interval_seconds = interval
    created = []

    with pytest.raises(DatasetError, match="INVALID_REQUEST_INTERVAL"):
        run_cloud(
            args,
            pristine_bundle,
            runtime_factory=lambda *a, **k: created.append(True),
            environment=environment,
        )

    assert not created


def test_approval_is_rechecked_after_pacing_before_the_next_provider_call(
    pristine_bundle, approved_scope, monkeypatch
):
    args, _, environment = approved_scope
    args.minimum_interval_seconds = 60
    runtime = FakeRuntime()
    waits = []

    def revoke_during_wait(seconds):
        waits.append(seconds)
        args.approval_file.write_text("{}", encoding="utf-8")

    monkeypatch.setattr("_cloud.sleep", revoke_during_wait)
    report = run_cloud(
        args,
        pristine_bundle,
        runtime_factory=lambda *a, **k: (runtime, runtime.route),
        environment=environment,
    )

    assert len(waits) == 1
    assert len(runtime.calls) == 1
    assert report["result"] == "PARTIAL_AUTHORIZATION_REVOKED"
    assert report["notExecutedCases"] == 29


@pytest.fixture
def approved_scope(pristine_bundle, tmp_path):
    path = tmp_path / "dataset-approval.json"
    args = arguments(path)
    receipt = receipt_for(pristine_bundle)
    write_json(path, receipt)
    args.approval_sha256 = digest(path.read_bytes())
    return (
        args,
        receipt,
        {
            "CLOUD_ENVIRONMENT": "isolated-synthetic",
            "PROVIDER_CATALOG_SHA256": "a" * 64,
        },
    )


@pytest.mark.parametrize(
    "mutation",
    [
        "flag",
        "data-class",
        "environment",
        "missing-file",
        "wrong-digest",
        "pending",
        "expiry",
        "dataset",
        "manifest",
        "provider",
        "scope",
        "catalog",
        "request-budget",
        "paid",
    ],
)
def test_cloud_guard_rejects_missing_or_drifted_scope_before_runtime_creation(
    pristine_bundle, approved_scope, mutation
):
    args, receipt, environment = approved_scope

    if mutation == "flag":
        args.allow_external_egress = False
    elif mutation == "data-class":
        args.data_class = "unapproved"
    elif mutation == "environment":
        environment["CLOUD_ENVIRONMENT"] = "production"
    elif mutation == "missing-file":
        args.approval_file = None
    elif mutation == "wrong-digest":
        args.approval_sha256 = "0" * 64
    else:
        if mutation == "pending":
            receipt["status"] = "PENDING"
        elif mutation == "expiry":
            receipt["expiresAt"] = "2020-01-01T00:00:00Z"
        elif mutation == "dataset":
            receipt["datasetDigest"] = "0" * 64
        elif mutation == "manifest":
            receipt["manifestDigest"] = "0" * 64
        elif mutation == "provider":
            receipt["provider"] = "groq"
        elif mutation == "scope":
            receipt["scope"] = "full"
        elif mutation == "catalog":
            receipt["providerCatalogDigest"] = "0" * 64
        elif mutation == "request-budget":
            receipt["maxRequests"] = 1
        elif mutation == "paid":
            receipt["freeOnly"] = False

        write_json(args.approval_file, receipt)
        args.approval_sha256 = digest(args.approval_file.read_bytes())

    with pytest.raises(DatasetError):
        authorize_dataset(args, pristine_bundle, environment=environment)


class FakeRuntime:
    def __init__(self, *, failure=None):
        self.failure = failure
        self.calls = []
        self.observer = None
        self.engine = reference_runtime(
            CONFIG, digest(CONFIG.read_bytes()), profiles=ProfileRegistry(PROFILES)
        )
        self.route = SimpleNamespace(
            id="fixture-route",
            vendor="gemini",
            model="fixture-model",
            catalog_digest="a" * 64,
            digest="b" * 64,
            max_input_bytes=16000,
            max_output_tokens=256,
            runtime=GenerationRuntimeIdentity(
                provider="gemini-selection",
                provider_version="0.1.0",
                model="fixture-model",
                model_revision="vendor-unpublished",
                config_version="fixture@1",
                prompt_envelope_version="fixture@1",
                app_prompt_version=self.engine.policy.identity,
                output_schema_version="grounded-claims@0.1.0",
            ),
        )
        self.registry = SimpleNamespace(
            catalog={
                "profileId": self.engine.profile_id,
                "profileVersion": self.engine.profile_version,
                "policyId": "synthetic-assistance",
            },
            policy=self.engine.policy,
            catalog_digest="a" * 64,
            approval_digest="c" * 64,
        )

    def assist(self, payload):
        self.calls.append(payload)
        request = {
            key: value for key, value in payload.items() if key != "routeBinding"
        }
        request["contractVersion"] = "0.1.0"
        response = self.engine.assist(request)
        response["contractVersion"] = "0.2.0"
        response["generationRuntime"] = asdict(self.route.runtime)
        response["routeBinding"] = payload["routeBinding"]
        response["routeFeedback"] = {
            "state": "READY",
            "code": "OK",
            "providerCalls": int(bool(payload["documents"])),
            "cooldownSeconds": 0,
            "latencyBucket": "LT_100MS",
        }

        if self.failure:
            response.update(
                status="DEGRADED", answer=None, citations=[], reasonCode=self.failure
            )
            response["routeFeedback"].update(
                state=(
                    "RATE_LIMITED"
                    if self.failure == "PROVIDER_RATE_LIMITED"
                    else "UNAVAILABLE"
                ),
                code=self.failure,
            )

        self.observer({"observedModelDigest": "d" * 64, "provider": "gemini"})
        return response


def test_no_egress_flag_blocks_before_instantiating_any_provider(
    pristine_bundle, approved_scope
):
    args, _, environment = approved_scope
    args.allow_external_egress = False
    calls = []

    def factory(*unused, **kwargs):
        calls.append(True)
        raise AssertionError("Provider must not be initialized")

    with pytest.raises(DatasetError):
        run_cloud(
            args, pristine_bundle, runtime_factory=factory, environment=environment
        )

    assert calls == []


@pytest.mark.parametrize("failure", ["PROVIDER_RATE_LIMITED", "GENERATION_TIMEOUT"])
def test_rate_limit_timeout_stop_without_switching_and_keep_full_denominator(
    pristine_bundle, approved_scope, failure
):
    args, _, environment = approved_scope
    runtime = FakeRuntime(failure=failure)
    report = run_cloud(
        args,
        pristine_bundle,
        runtime_factory=lambda *args, **kwargs: (runtime, runtime.route),
        environment=environment,
    )
    assert len(runtime.calls) == 1
    assert report["totalPlannedCases"] == report["caseCount"] == 30
    assert report["executedCases"] == 1
    assert report["notExecutedCases"] == 29
    assert report["realModelValidated"] is False
    assert report["result"].startswith("PARTIAL_")
    assert report["provider"] == "gemini"


def test_conservative_budget_stops_and_fake_run_never_becomes_real_cloud_evidence(
    pristine_bundle, approved_scope
):
    args, _, environment = approved_scope
    args.max_requests = 1
    runtime = FakeRuntime()
    report = run_cloud(
        args,
        pristine_bundle,
        runtime_factory=lambda *args, **kwargs: (runtime, runtime.route),
        environment=environment,
    )
    assert len(runtime.calls) == 1
    assert report["requestSlotsReserved"] == 1
    assert report["notExecutedCases"] == 29
    assert report["result"] == "PARTIAL_LOCAL_BUDGET_EXHAUSTED"
    assert report["officialDecision"] == "BLOCKED"


def test_full_scope_requires_its_own_approval_and_retains_all_400_cases(
    pristine_bundle, approved_scope
):
    args, receipt, environment = approved_scope
    args.scope = "full"

    with pytest.raises(DatasetError, match="APPROVAL_SCOPE"):
        authorize_dataset(args, pristine_bundle, environment=environment)

    receipt["scope"] = "full"
    write_json(args.approval_file, receipt)
    args.approval_sha256 = digest(args.approval_file.read_bytes())
    args.max_requests = 1
    runtime = FakeRuntime()
    report = run_cloud(
        args,
        pristine_bundle,
        runtime_factory=lambda *args, **kwargs: (runtime, runtime.route),
        environment=environment,
    )
    assert report["totalPlannedCases"] == report["caseCount"] == 400
    assert report["notExecutedCases"] == 399
    assert report["realModelValidated"] is False


def test_complete_fake_smoke_is_never_real_provider_verification(
    pristine_bundle, approved_scope
):
    args, _, environment = approved_scope
    runtime = FakeRuntime()
    report = run_cloud(
        args,
        pristine_bundle,
        runtime_factory=lambda *args, **kwargs: (runtime, runtime.route),
        environment=environment,
    )
    assert report["executedCases"] == 30
    assert report["technicalFailures"] == 0
    assert report["result"] == "OFFLINE_CLOUD_CONTRACT_VERIFIED"
    assert report["realModelValidated"] is False
    assert report["officialDecision"] == "BLOCKED"


def test_test_receipt_cannot_construct_real_network_runtime(
    pristine_bundle, approved_scope
):
    args, _, environment = approved_scope

    with pytest.raises(DatasetError, match="TEST_RECEIPT_CANNOT_ENABLE_NETWORK"):
        run_cloud(args, pristine_bundle, environment=environment)


def test_receipt_revocation_stops_following_cases(pristine_bundle, approved_scope):
    args, _, environment = approved_scope
    runtime = FakeRuntime()
    original = runtime.assist

    def revoke(payload):
        response = original(payload)
        args.approval_file.write_bytes(b"{}")
        return response

    runtime.assist = revoke
    report = run_cloud(
        args,
        pristine_bundle,
        runtime_factory=lambda *args, **kwargs: (runtime, runtime.route),
        environment=environment,
    )
    assert len(runtime.calls) == 1
    assert report["result"] == "PARTIAL_AUTHORIZATION_REVOKED"
    assert report["notExecutedCases"] == 29


def test_approved_digest_inventory_accepts_full_scope_and_remains_bounded(
    pristine_bundle,
):
    from cloud_providers.registry import checksums
    from hdpi.generation.errors import GenerationConfigurationError

    questions = sorted(
        {
            digest(case["question"].encode())
            for case in pristine_bundle["dataset"]["cases"]
        }
    )
    assert len(questions) > 256
    checksums(questions)

    with pytest.raises(GenerationConfigurationError):
        checksums([digest(str(index).encode()) for index in range(1025)])


def test_native_outbound_body_contains_only_question_alias_evidence_and_injected_policy(
    pristine_bundle,
):
    from cloud_providers.contracts import Route, encode_json
    from cloud_providers.credentials import ServerCredential
    from cloud_providers.providers import GeminiSelectionProvider
    from hdpi.generation.models import EvidenceExcerpt, GenerationRequest

    case = pristine_bundle["dataset"]["cases"][52]
    metadata = pristine_bundle["curation"]["cases"][52]
    calls = []

    class Client:
        def post(self, route, body, credential):
            calls.append(json.loads(body))
            return encode_json(
                {
                    "modelVersion": "fixture-model",
                    "candidates": [
                        {
                            "finishReason": "STOP",
                            "content": {
                                "role": "model",
                                "parts": [
                                    {
                                        "text": '{"decision":"SELECT","citation_ids":["C1"]}'
                                    }
                                ],
                            },
                        }
                    ],
                }
            )

    route = Route(
        "fixture-route",
        "gemini",
        "fixture-model",
        ("fixture-model",),
        1,
        16000,
        256,
        3,
        100,
        100,
        100000,
        100000,
        "a" * 64,
        "synthetic-policy@1",
    )
    key = "synthetic-offline-credential-only"
    provider = GeminiSelectionProvider(
        route,
        client=Client(),
        credential=ServerCredential("gemini", {"GEMINI_API_KEY": key}),
    )
    request = GenerationRequest(
        question=case["question"],
        policy_identity="synthetic-policy@1",
        policy_instructions="Select only supplied synthetic aliases or refuse.",
        output_schema_version="grounded-claims@0.1.0",
        evidence=(
            EvidenceExcerpt(citation_id="C1", excerpt=case["documents"][0]["content"]),
        ),
    )
    draft = provider.generate(request)
    assert len(draft.claims) == 1
    body = json.dumps(calls[0])

    for forbidden in (
        "workspaceId",
        "metadataId",
        "familyId",
        "templateId",
        "factId",
        "access_token",
        "sourceUrl",
        key,
        case["documents"][0]["metadataId"],
        metadata["factId"],
        metadata["templateId"],
    ):
        assert forbidden not in body
