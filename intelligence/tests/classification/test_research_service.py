"""Research cannot create approved artifacts or domain-specific authority."""

from hashlib import sha256
import json

import pytest

from hdpi.classification.decision_observation import CONTRACT, DecisionObservation
from hdpi.classification.protocols import ClassificationRequest
from hdpi.classification.research import SilverResearchClassifier, WeakExample
from hdpi.classification.service import ClassificationInferenceService


def test_weak_research_is_deterministic_and_refuses_family_leakage():
    examples = (
        WeakExample("1", "a" * 64, "Room empty available", "AVAILABLE"),
        WeakExample("2", "b" * 64, "Room reserved occupied", "OCCUPIED"),
        WeakExample("3", "c" * 64, "Available open room", "AVAILABLE"),
        WeakExample("4", "d" * 64, "Occupied closed room", "OCCUPIED"),
    )

    def candidate():
        return SilverResearchClassifier(
            allowed_classes=("AVAILABLE", "OCCUPIED"),
            dataset_digest="a" * 64,
            policy_digest="b" * 64,
            enabled=True,
        )

    with pytest.raises(ValueError):
        candidate().fit(examples, evaluation_families={"a" * 64})
    first = candidate().fit(examples, evaluation_families={"f" * 64})
    second = candidate().fit(examples, evaluation_families={"f" * 64})
    assert first.manifest == second.manifest
    assert first.manifest["role"] == "NEVER_PROMOTABLE"
    assert first.manifest["officialQualityClaim"] is False
    request = ClassificationRequest(
        fields=(("input", "empty available room"),),
        allowed_classes=("AVAILABLE", "OCCUPIED"),
        policy_digest="b" * 64,
    )
    observation = first.observe(request, abstain_id="UNKNOWN")
    assert (
        observation.operational_authority is False and observation.model_scores is None
    )
    assert first.observe(request, abstain_id="UNKNOWN") == second.observe(
        request, abstain_id="UNKNOWN"
    )


@pytest.mark.parametrize(
    "mutation", [None, "model", "policy", "input", "classes", "extra"]
)
def test_service_pins_request_and_never_exposes_scores(mutation):
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
    fields = {"room": "café 🚀"}
    payload = {
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
    if mutation == "model":
        payload["modelDigest"] = "c" * 64
    elif mutation == "policy":
        payload["policyDigest"] = "c" * 64
    elif mutation == "input":
        payload["input"]["room"] = "changed"
    elif mutation == "classes":
        payload["allowedClasses"] = ["OTHER", "AVAILABLE"]
    elif mutation == "extra":
        payload["priority"] = "URGENT"
    if mutation:
        with pytest.raises(ValueError):
            service.infer(payload)
        assert not seen
    else:
        result = service.infer(payload)
        assert result["observation"]["classId"] == "AVAILABLE"
        assert result["observation"]["score"] is None
        assert result["operationalAuthority"] is False
