"""Native structured chat. Exact quotes are syntax provenance, not entailment."""

import json

from hdpi.classification.protocols import require
from hdpi.classification.validation import quotes_to_proposal
from local_classification.ollama_transport import strict_json


VERSION = "gemma-chat-schema@0.1.0"
OPTIONS = (("num_ctx", 2048), ("num_predict", 256), ("temperature", 0), ("seed", 0))


def request_payload(request, *, model, policy, keep_alive=0):
    require(set(policy) == {"instructions", "outputSchema"})
    require(
        type(policy["instructions"]) is str and 0 < len(policy["instructions"]) <= 4096
    )
    schema = policy["outputSchema"]
    require(type(schema) is dict and schema.get("additionalProperties") is False)
    require(schema.get("required") == ["decision", "classId", "evidenceQuotes"])
    require(
        schema["properties"]["classId"]["anyOf"][0]["enum"]
        == list(request.allowed_classes)
    )

    return {
        "model": model,
        "messages": [
            {"role": "system", "content": policy["instructions"]},
            {
                "role": "user",
                "content": json.dumps(dict(request.fields), ensure_ascii=True),
            },
        ],
        "format": schema,
        "stream": False,
        "keep_alive": keep_alive,
        "truncate": False,
        "shift": False,
        "options": dict(OPTIONS),
    }


def decode_response(value, request, *, model, context_tokens=2048):
    required = {
        "model",
        "created_at",
        "message",
        "done",
        "done_reason",
        "total_duration",
        "load_duration",
        "prompt_eval_count",
        "eval_count",
    }
    optional = {"prompt_eval_duration", "eval_duration", "prompt_eval_cached_count"}
    require(type(value) is dict and required <= set(value) <= required | optional)
    require(
        value["model"] == model
        and value["done"] is True
        and value["done_reason"] == "stop"
    )
    message = value["message"]
    require(type(message) is dict and set(message) == {"role", "content"})
    require(message["role"] == "assistant" and type(message["content"]) is str)

    for key in ("total_duration", "load_duration", "prompt_eval_count", "eval_count"):
        require(type(value[key]) is int and value[key] >= 0)

    require(0 < value["prompt_eval_count"])
    require(value["prompt_eval_count"] + value["eval_count"] <= context_tokens)
    require(value["eval_count"] <= 256)
    proposal = quotes_to_proposal(
        strict_json(message["content"].encode("utf-8"), 8192), request
    )
    usage = {
        "input_tokens": value["prompt_eval_count"],
        "output_tokens": value["eval_count"],
    }
    return proposal, usage
