from dataclasses import replace

import pytest

from hdpi.classification.decision_observation import (
    CONTRACT,
    DecisionObservation,
    validate_observation,
)
from hdpi.classification.protocols import ClassificationError, ClassificationRequest
from hdpi.classification.validation import quotes_to_proposal


@pytest.fixture
def classification_request():
    return ClassificationRequest(
        fields=(("input", "café rocket"),),
        allowed_classes=("A", "B"),
        policy_digest="a" * 64,
    )


def observation():
    return DecisionObservation(
        contract_version=CONTRACT,
        decision="PROPOSE",
        class_id="A",
        option_order=("A", "B", "UNKNOWN"),
        model_scores=(("A", 0.7), ("B", 0.2), ("UNKNOWN", 0.1)),
    )


def test_decision_has_no_spans_or_operational_authority(classification_request):
    value = validate_observation(
        observation(), classification_request, abstain_id="UNKNOWN"
    )
    assert value.input_spans == ()
    assert value.evidence_status == "UNVERIFIED_BY_MODEL"
    assert value.operational_authority is False
    missing = replace(value, model_scores=None, score_semantics="NOT_AVAILABLE")
    assert (
        validate_observation(
            missing, classification_request, abstain_id="UNKNOWN"
        ).model_scores
        is None
    )


@pytest.mark.parametrize(
    "change",
    [
        {"operational_authority": True},
        {"input_spans": ("fake",)},
        {"class_id": "C"},
        {"option_order": ("B", "A", "UNKNOWN")},
        {"model_scores": (("A", 1.0), ("B", float("nan")), ("UNKNOWN", 0.0))},
        {"model_scores": (("A", 0.0), ("B", 0.0), ("UNKNOWN", 0.0))},
        {"model_scores": (("A", 1.0), ("A", 0.0), ("UNKNOWN", 0.0))},
        {"concentration": float("inf")},
        {"score_semantics": "CALIBRATED"},
        {"decision": "ABSTAIN", "class_id": "A"},
    ],
)
def test_invalid_observations_fail_closed(classification_request, change):
    with pytest.raises(ClassificationError):
        validate_observation(
            replace(observation(), **change),
            classification_request,
            abstain_id="UNKNOWN",
        )


def test_unicode_quote_mapping_and_unambiguous_offsets(classification_request):
    value = quotes_to_proposal(
        {"decision": "PROPOSE", "classId": "A", "evidenceQuotes": ["café"]},
        classification_request,
    )
    assert (value.spans[0].start, value.spans[0].end) == (0, 4)


@pytest.mark.parametrize(
    "fields,quotes",
    [
        ((("input", "café rocket"),), ["cafe\u0301"]),
        ((("input", "abc abc"),), ["abc"]),
        ((("left", "abc"), ("right", "abc")), ["abc"]),
        ((("input", "aaaa"),), ["aaa"]),
        ((("input", "abc"),), ["abc", "abc"]),
        ((("input", "abc"),), []),
        ((("input", "abc"),), ["invented"]),
    ],
)
def test_quotes_cannot_be_normalized_guessed_or_assigned_first_occurrence(
    classification_request, fields, quotes
):
    classification_request = replace(classification_request, fields=fields)

    with pytest.raises(ClassificationError):
        quotes_to_proposal(
            {"decision": "PROPOSE", "classId": "A", "evidenceQuotes": quotes},
            classification_request,
        )
