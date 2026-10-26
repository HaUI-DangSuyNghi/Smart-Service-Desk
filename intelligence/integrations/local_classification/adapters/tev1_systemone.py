"""System One choice votes carry no literal evidence or operational authority."""

from hdpi.classification.decision_observation import (
    CONTRACT,
    DecisionObservation,
    validate_observation,
)
from hdpi.classification.protocols import require


VERSION = "tev1-systemone@0.1.0"


def request_payload(request, *, model, policy, keep_alive=0):
    require(set(policy) == {"instructions", "criteria", "abstainId"})
    criteria = policy["criteria"]
    require(type(criteria) is dict)
    require(tuple(criteria) == (*request.allowed_classes, policy["abstainId"]))
    require(policy["abstainId"] not in request.allowed_classes)
    require(
        type(policy["instructions"]) is str and 0 < len(policy["instructions"]) <= 4096
    )
    require(
        all(type(value) is str and 0 < len(value) <= 500 for value in criteria.values())
    )

    return {
        "model": model,
        "state": dict(request.fields),
        "questions": {
            "decision": {
                "type": "choice",
                "instructions": policy["instructions"],
                "criteria": criteria,
            }
        },
        "keep_alive": keep_alive,
    }


def decode_response(value, request, *, model, abstain_id, context_tokens=2048):
    require(type(value) is dict)
    require(
        set(value)
        in (
            {"model", "answers", "usage"},
            {"model", "answers", "usage", "prompt_eval_cached_count"},
        )
    )
    require(value["model"] == model)
    require(type(value["answers"]) is dict and set(value["answers"]) == {"decision"})
    answer = value["answers"]["decision"]
    require(
        type(answer) is dict
        and {"type", "choice"} <= set(answer)
        and set(answer) <= {"type", "choice", "probabilities", "confidence"}
    )
    require(answer["type"] == "choice")
    options = (*request.allowed_classes, abstain_id)
    require(type(answer["choice"]) is str and answer["choice"] in options)
    usage = value["usage"]
    require(type(usage) is dict and set(usage) == {"input_tokens", "output_tokens"})
    require(all(type(number) is int and number >= 0 for number in usage.values()))
    require(0 < usage["input_tokens"] and sum(usage.values()) <= context_tokens)
    scores = None

    if "probabilities" in answer:
        probabilities = answer["probabilities"]
        require(type(probabilities) is dict and set(probabilities) == set(options))
        require(
            all(
                type(number) in (int, float) and type(number) is not bool
                for number in probabilities.values()
            )
        )
        scores = tuple((option, float(probabilities[option])) for option in options)

    concentration = answer.get("confidence")
    require(concentration is None or type(concentration) in (int, float))
    observation = DecisionObservation(
        contract_version=CONTRACT,
        decision="ABSTAIN" if answer["choice"] == abstain_id else "PROPOSE",
        class_id=None if answer["choice"] == abstain_id else answer["choice"],
        option_order=options,
        model_scores=scores,
        concentration=None if concentration is None else float(concentration),
        score_semantics="NOT_AVAILABLE"
        if scores is None
        else "UNVALIDATED_CHOICE_DISTRIBUTION",
    )
    return validate_observation(observation, request, abstain_id=abstain_id), usage
