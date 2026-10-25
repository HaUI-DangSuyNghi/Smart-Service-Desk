"""An ungrounded decision vote is separate from a literal-evidence proposal."""

from dataclasses import dataclass
import math

from .protocols import ClassificationRequest, require


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
