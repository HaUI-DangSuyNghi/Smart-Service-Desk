from dataclasses import asdict

import pytest

from _contracts import DATA, ROOT, read_json
from hdpi.generation.models import GenerationDraft, GenerationRuntimeIdentity
from hdpi.generation.policy import GenerationPolicy
from hdpi.profiles.registry import ProfileRegistry
from hdpi.services.assistance_runtime import AssistanceRuntime


class RefusingProbe:
    name = "offline-candidate-probe"
    version = "1"
    config_version = "1"

    def __init__(self, identity):
        self.requests = []
        self.runtime_identity = GenerationRuntimeIdentity(
            provider=self.name,
            provider_version=self.version,
            model="none",
            model_revision="none",
            config_version=self.config_version,
            prompt_envelope_version="probe@1",
            app_prompt_version=identity,
            output_schema_version="grounded-claims@0.1.0",
        )

    def generate(self, request):
        self.requests.append(request)
        return GenerationDraft(claims=())


@pytest.mark.parametrize(
    "case_id",
    [
        "GEN-SD-0025",
        "GEN-SD-0026",
        "GEN-SD-0027",
        "GEN-SD-0028",
        "GEN-SD-0358",
        "GEN-SD-0361",
    ],
)
def test_existing_false_refusals_offer_original_evidence_to_semantic_selector(case_id):
    case = next(
        c for c in read_json(DATA / "dev-v0.2.json")["cases"] if c["caseId"] == case_id
    )
    policy = GenerationPolicy(
        **read_json(
            ROOT
            / "intelligence/src/apps/servicedesk/intelligence/assistance-cloud-policy-v0.2.json"
        )
    )
    probe = RefusingProbe(policy.identity)
    runtime = AssistanceRuntime(
        provider=probe,
        policy=policy,
        profile_id="service-desk",
        profile_version="0.3.0",
        profiles=ProfileRegistry(
            ROOT / "intelligence/src/apps/servicedesk/intelligence/profiles"
        ),
    )
    response = runtime.assist(
        {
            "contractVersion": "0.1.0",
            "requestId": "11111111-1111-4111-8111-111111111111",
            "workspaceId": "synthetic-candidate-probe",
            "profileId": "service-desk",
            "profileVersion": "0.3.0",
            "policyIdentity": policy.identity,
            "question": case["question"],
            "documents": case["documents"],
        }
    )

    assert len(probe.requests) == 1
    assert probe.requests[0].evidence
    for evidence in probe.requests[0].evidence:
        assert any(
            evidence.excerpt in source["content"] for source in case["documents"]
        )
    assert "provisionalExpectedStatus" not in asdict(probe.requests[0])
    assert response["retrievalRuntime"]["engine"] == "snapshot"
    # A probe refusal is not a model-quality pass. This control checks recall only.
    assert response["status"] == "INSUFFICIENT_EVIDENCE"


def test_new_policy_withholds_all_declared_unsafe_sources_without_model_calls():
    cases = read_json(DATA / "dev-v0.2.json")["cases"]
    policy = GenerationPolicy(
        **read_json(
            ROOT
            / "intelligence/src/apps/servicedesk/intelligence/assistance-cloud-policy-v0.3.json"
        )
    )
    probe = RefusingProbe(policy.identity)
    runtime = AssistanceRuntime(
        provider=probe,
        policy=policy,
        profile_id="service-desk",
        profile_version="0.3.0",
        profiles=ProfileRegistry(
            ROOT / "intelligence/src/apps/servicedesk/intelligence/profiles"
        ),
    )
    unsafe = [case for case in cases if case["unsafeSourceIds"]]
    assert len(unsafe) == 64

    for case in unsafe:
        response = runtime.assist(
            {
                "contractVersion": "0.1.0",
                "requestId": "11111111-1111-4111-8111-111111111111",
                "workspaceId": "synthetic-candidate-probe",
                "profileId": "service-desk",
                "profileVersion": "0.3.0",
                "policyIdentity": policy.identity,
                "question": case["question"],
                "documents": case["documents"],
            }
        )
        assert response["status"] == "INSUFFICIENT_EVIDENCE"
        assert not response["citations"]

    assert not probe.requests

    # Coverage of the current fixture does not prove a complete injection detector.
    for case in cases:
        if not case["unsafeSourceIds"]:
            assert not any(
                policy.blocks_source(doc["content"]) for doc in case["documents"]
            )
