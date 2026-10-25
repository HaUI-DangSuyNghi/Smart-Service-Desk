from dataclasses import asdict
from hashlib import sha256
from types import SimpleNamespace
from uuid import UUID

import pytest

from hdpi.generation.errors import GenerationContractError
from hdpi.generation.models import (
    CitationRef,
    GenerationDraft,
    GenerationRuntimeIdentity,
    GroundedClaim,
)
from hdpi.generation.policy import GenerationPolicy
from hdpi.services.assistance_runtime import AssistanceRuntime


class Profiles:
    def get(self, *_):
        return SimpleNamespace()


class Selector:
    name = "probe-selector"
    version = "1"
    config_version = "1"

    def __init__(self):
        self.requests = []
        self.runtime_identity = GenerationRuntimeIdentity(
            provider="probe-selector",
            provider_version="1",
            model="probe",
            model_revision="1",
            config_version="1",
            prompt_envelope_version="1",
            app_prompt_version="probe@1",
            output_schema_version="grounded-claims@0.1.0",
        )

    def generate(self, request):
        self.requests.append(request)
        first = request.evidence[0]
        return GenerationDraft(
            claims=(
                GroundedClaim(
                    text=first.excerpt,
                    citations=(CitationRef(first.citation_id),),
                ),
            )
        )


def runtime(markers=()):
    selector = Selector()
    service = AssistanceRuntime(
        provider=selector,
        policy=GenerationPolicy(
            identity="probe@1",
            instructions="Use only supplied evidence.",
            max_claims=3,
            max_evidence=10,
            blocked_source_markers=markers,
            evidence_mode="source-balanced@0.1.0",
        ),
        profile_id="probe",
        profile_version="1",
        profiles=Profiles(),
    )
    return service, selector


def payload(texts, question="zzq unmatched characters"):
    return {
        "contractVersion": "0.1.0",
        "requestId": str(UUID(int=999)),
        "workspaceId": "probe-workspace",
        "profileId": "probe",
        "profileVersion": "1",
        "policyIdentity": "probe@1",
        "question": question,
        "documents": [
            {
                "metadataId": str(UUID(int=index + 1)),
                "version": "1",
                "checksum": sha256(text.encode("utf-8")).hexdigest(),
                "content": text,
                "mediaType": "text/plain",
            }
            for index, text in enumerate(texts)
        ],
    }


def test_no_keyword_overlap_still_reaches_selector_with_original_source_bytes():
    service, selector = runtime()
    text = "The blue sample has a round outline."
    response = service.assist(payload([text]))

    assert len(selector.requests) == 1
    assert selector.requests[0].evidence[0].excerpt == text
    assert response["status"] == "SUCCESS"
    assert response["answer"] == text + " [C1]"
    assert response["retrievalRuntime"]["engine"] == "snapshot"
    assert response["retrievalRuntime"]["config_version"] == "source-balanced@0.1.0"


def test_round_robin_represents_each_source_before_taking_additional_chunks():
    service, selector = runtime()
    request = payload([(f"probe{index} " * 700) for index in range(3)])
    response = service.assist(request)
    evidence = selector.requests[0].evidence

    assert len(evidence) == 10
    assert sum(len(item.excerpt) for item in evidence) <= 16000
    assert all(len(item.excerpt) <= 900 for item in evidence)
    assert response["generationRuntime"] == asdict(selector.runtime_identity)

    # The aliases expose excerpts only; source provenance stays in the trusted response.
    assert all(not hasattr(item, "metadata_id") for item in evidence)
    for document in request["documents"]:
        assert any(item.excerpt in document["content"] for item in evidence)


def test_empty_snapshot_refuses_without_calling_provider():
    service, selector = runtime()
    response = service.assist(payload([]))

    assert response["status"] == "INSUFFICIENT_EVIDENCE"
    assert not selector.requests


def test_stale_snapshot_cannot_be_offered_to_selector(monkeypatch):
    service, selector = runtime()
    monkeypatch.setattr(
        "hdpi.services.assistance_runtime.CoreCitationVerifier.verify",
        lambda *_: None,
    )

    with pytest.raises(GenerationContractError):
        service.assist(payload(["The blue sample has a round outline."]))

    assert not selector.requests


@pytest.mark.parametrize("mode", [False, "unknown", None])
def test_evidence_mode_is_explicit_and_validated(mode):
    with pytest.raises(GenerationContractError):
        GenerationPolicy(identity="probe@1", instructions="Probe.", evidence_mode=mode)


def test_ranked_policy_remains_the_default():
    assert (
        GenerationPolicy(identity="probe@1", instructions="Probe.").evidence_mode
        == "ranked"
    )


def test_more_input_evidence_does_not_raise_output_claim_limit():
    service, selector = runtime()

    def select_all(request):
        return GenerationDraft(
            claims=tuple(
                GroundedClaim(
                    text=item.excerpt, citations=(CitationRef(item.citation_id),)
                )
                for item in request.evidence
            )
        )

    selector.generate = select_all

    with pytest.raises(GenerationContractError):
        service.assist(payload([f"Probe source {index}." for index in range(5)]))


def test_source_policy_withholds_all_candidates_before_provider_even_beyond_chunk_budget():
    service, selector = runtime(markers=["administrative override"])
    request = payload(
        [
            ("ordinary probe words " * 500) + "ADMINISTRATIVE   OVERRIDE",
            "Other plain source.",
        ]
    )
    response = service.assist(request)

    assert not selector.requests
    assert response["status"] == "INSUFFICIENT_EVIDENCE"
    assert not response["citations"]


def test_source_policy_applies_unicode_normalization_but_preserves_source_bytes():
    service, selector = runtime(markers=["administrative override"])
    response = service.assist(payload(["ＡＤＭＩＮＩＳＴＲＡＴＩＶＥ override"]))

    assert not selector.requests
    assert response["answer"] is None


@pytest.mark.parametrize("limit", [False, 0, 11, "10"])
def test_evidence_input_limit_is_strictly_bounded(limit):
    with pytest.raises(GenerationContractError):
        GenerationPolicy(identity="probe@1", instructions="Probe.", max_evidence=limit)
