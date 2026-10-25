"""An ungrounded decision vote is separate from a literal-evidence proposal."""

from dataclasses import dataclass
import json
import math

from .protocols import ClassificationError, ClassificationRequest, require


CONTRACT = "decision-observation@0.1.0"


@dataclass(frozen=True, kw_only=True)
class DecisionObservation:
    contract_version: str
    decision: str
    class_id: str | None
    option_order: tuple[str, ...]
    model_scores: tuple[tuple[str, float], ...] | None
    concentration: float | None = None
    score_semantics: str = "UNVALIDATED_CHOICE_DISTRIBUTION"
    evidence_status: str = "UNVERIFIED_BY_MODEL"
    input_spans: tuple = ()
    operational_authority: bool = False


def validate_observation(value, request, *, abstain_id):
    require(type(request) is ClassificationRequest)
    require(type(value) is DecisionObservation and value.contract_version == CONTRACT)
    require(type(abstain_id) is str and abstain_id not in request.allowed_classes)
    require(value.operational_authority is False)
    require(value.evidence_status == "UNVERIFIED_BY_MODEL" and value.input_spans == ())
    require(type(value.option_order) is tuple)
    require(value.option_order == (*request.allowed_classes, abstain_id))

    if value.decision == "ABSTAIN":
        require(value.class_id is None)
    else:
        require(
            value.decision == "PROPOSE" and value.class_id in request.allowed_classes
        )

    if value.model_scores is None:
        require(value.score_semantics == "NOT_AVAILABLE")
    else:
        require(value.score_semantics == "UNVALIDATED_CHOICE_DISTRIBUTION")
        require(type(value.model_scores) is tuple)
        require(
            all(type(pair) is tuple and len(pair) == 2 for pair in value.model_scores)
        )
        require(tuple(key for key, _ in value.model_scores) == value.option_order)
        probabilities = tuple(score for _, score in value.model_scores)
        require(
            all(
                type(score) is float and math.isfinite(score) and 0 <= score <= 1
                for score in probabilities
            )
        )
        require(abs(sum(probabilities) - 1.0) <= 0.001)

    if value.concentration is not None:
        require(
            type(value.concentration) is float and math.isfinite(value.concentration)
        )
        require(0 <= value.concentration <= 1)

    return value


def decode_decision(raw, request, *, abstain_id):
    """Decode a classification-only vote without inventing evidence or scores."""

    def unique_object(pairs):
        result = {}

        for key, value in pairs:
            require(key not in result)
            result[key] = value

        return result

    def reject_constant(_value):
        raise ClassificationError("Non-finite decision rejected")

    try:
        require(type(raw) is bytes and 0 < len(raw) <= 8192)
        value = json.loads(
            raw.decode("utf-8"),
            object_pairs_hook=unique_object,
            parse_constant=reject_constant,
        )
        require(type(value) is dict and set(value) == {"decision", "classId"})
        require(type(value["decision"]) is str)
        require(value["classId"] is None or type(value["classId"]) is str)
        observation = DecisionObservation(
            contract_version=CONTRACT,
            decision=value["decision"],
            class_id=value["classId"],
            option_order=(*request.allowed_classes, abstain_id),
            model_scores=None,
            score_semantics="NOT_AVAILABLE",
        )
        return validate_observation(observation, request, abstain_id=abstain_id)
    except (ValueError, TypeError, KeyError, RecursionError):
        raise ClassificationError("Decision response rejected") from None
