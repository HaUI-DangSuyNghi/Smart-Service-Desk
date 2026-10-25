"""G1 classification-only chat. G0 exact-quote behavior remains frozen."""

import json

from hdpi.classification.decision_observation import decode_decision
from hdpi.classification.protocols import ClassificationError, require
from local_classification.adapters.gemma_chat_json import OPTIONS
from local_classification.diagnostics import (
    ObservationRejected,
    chat_content,
    decision_rejection,
)


VERSION = "gemma-classify-only@0.2.0"


def output_schema(allowed_classes):
    return {
        "type": "object",
        "additionalProperties": False,
        "required": ["decision", "classId"],
        "properties": {
            "decision": {"type": "string", "enum": ["PROPOSE", "ABSTAIN"]},
            "classId": {
                "anyOf": [
                    {"type": "string", "enum": list(allowed_classes)},
                    {"type": "null"},
                ]
            },
        },
    }


def request_payload(request, *, model, policy, keep_alive=0):
    require(
        type(policy) is dict
        and set(policy) == {"instructions", "outputSchema", "abstainId"}
    )
    require(
        type(policy["instructions"]) is str and 0 < len(policy["instructions"]) <= 4096
    )
    require(
        type(policy["abstainId"]) is str
        and policy["abstainId"] not in request.allowed_classes
    )
    require(policy["outputSchema"] == output_schema(request.allowed_classes))

    return {
        "model": model,
        "messages": [
            {"role": "system", "content": policy["instructions"]},
            {
                "role": "user",
                "content": json.dumps(dict(request.fields), ensure_ascii=True),
            },
        ],
        "format": policy["outputSchema"],
        "stream": False,
        "keep_alive": keep_alive,
        "truncate": False,
        "shift": False,
        "options": dict(OPTIONS),
    }


def decode_response(value, request, *, model, abstain_id, context_tokens=2048):
    raw = chat_content(value, model=model, context_tokens=context_tokens)

    try:
        observation = decode_decision(raw, request, abstain_id=abstain_id)
    except ClassificationError:
        raise ObservationRejected(decision_rejection(raw, request)) from None

    return observation, {
        "input_tokens": value["prompt_eval_count"],
        "output_tokens": value["eval_count"],
    }
