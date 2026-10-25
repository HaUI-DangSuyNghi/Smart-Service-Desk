"""Bounded rejection codes only. Never serialize exception text or model content."""

import math

from hdpi.classification.protocols import ClassificationError, require
from local_classification.ollama_transport import strict_json


REASON_CODES = frozenset(
    {
        "INVALID_JSON",
        "INVALID_STATE_PAIR",
        "INVALID_QUOTE_MISSING",
        "INVALID_QUOTE_REPEATED",
        "INVALID_QUOTE_OVERLAP",
        "INVALID_CLASS",
        "SCHEMA_MISMATCH",
        "TRUNCATED",
        "RUNTIME_IDENTITY",
        "TRANSPORT",
        "TIMEOUT",
        "UNAVAILABLE",
    }
)


class ObservationRejected(ClassificationError):
    def __init__(self, reason_code):
        require(type(reason_code) is str and reason_code in REASON_CODES)
        self.reason_code = reason_code
        self.telemetry = {}
        super().__init__("Local observation rejected")


def safe_reason(value, default="SCHEMA_MISMATCH"):
    return value if type(value) is str and value in REASON_CODES else default


def safe_telemetry(value):
    result = {}
    if type(value) is not dict:
        return result

    elapsed = value.get("nativeElapsedMs")
    if (
        type(elapsed) in (int, float)
        and math.isfinite(elapsed)
        and 0 <= elapsed <= 180000
    ):
        result["nativeElapsedMs"] = elapsed
    if type(value.get("loadedBefore")) is bool:
        result["loadedBefore"] = value["loadedBefore"]

    for key in ("nativeInputTokens", "nativeOutputTokens", "nativeCachedInputTokens"):
        count = value.get(key)
        if type(count) is int and 0 <= count <= 2048:
            result[key] = count
    if value.get("stopReason") in ("stop", "length"):
        result["stopReason"] = value["stopReason"]

    return result


def native_telemetry(value):
    if type(value) is not dict:
        return {}

    usage = value.get("usage")
    if type(usage) is not dict:
        usage = {}
    return safe_telemetry(
        {
            "nativeInputTokens": value.get(
                "prompt_eval_count", usage.get("input_tokens")
            ),
            "nativeOutputTokens": value.get("eval_count", usage.get("output_tokens")),
            "nativeCachedInputTokens": value.get("prompt_eval_cached_count"),
            "stopReason": value.get("done_reason"),
        }
    )


def chat_content(value, *, model, context_tokens=2048):
    """Validate the native envelope independently of the model output contract."""
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

    if type(value) is not dict or not required <= set(value) <= required | optional:
        raise ObservationRejected("SCHEMA_MISMATCH")
    if value["model"] != model:
        raise ObservationRejected("RUNTIME_IDENTITY")
    if value["done_reason"] == "length":
        raise ObservationRejected("TRUNCATED")
    if value["done"] is not True or value["done_reason"] != "stop":
        raise ObservationRejected("SCHEMA_MISMATCH")

    message = value["message"]
    if (
        type(message) is not dict
        or set(message) != {"role", "content"}
        or message["role"] != "assistant"
        or type(message["content"]) is not str
    ):
        raise ObservationRejected("SCHEMA_MISMATCH")

    for key in ("total_duration", "load_duration", "prompt_eval_count", "eval_count"):
        if type(value[key]) is not int or value[key] < 0:
            raise ObservationRejected("SCHEMA_MISMATCH")

    if (
        value["prompt_eval_count"] <= 0
        or value["prompt_eval_count"] + value["eval_count"] > context_tokens
        or value["eval_count"] > 256
    ):
        raise ObservationRejected("TRUNCATED")

    return message["content"].encode("utf-8")


def decision_rejection(raw, request, *, quotes=False):
    """Called only after rejection; this function never accepts or repairs output."""
    try:
        value = strict_json(raw, 8192)
    except ClassificationError:
        return "INVALID_JSON"

    keys = (
        {"decision", "classId", "evidenceQuotes"} if quotes else {"decision", "classId"}
    )
    if type(value) is not dict or set(value) != keys:
        return "SCHEMA_MISMATCH"

    decision, class_id = value["decision"], value["classId"]
    if type(decision) is not str or decision not in ("PROPOSE", "ABSTAIN"):
        return "SCHEMA_MISMATCH"
    if decision == "ABSTAIN" and (
        class_id is not None or quotes and value["evidenceQuotes"] != []
    ):
        return "INVALID_STATE_PAIR"
    if decision == "PROPOSE" and class_id is None:
        return "INVALID_STATE_PAIR"
    if decision == "PROPOSE" and (
        type(class_id) is not str or class_id not in request.allowed_classes
    ):
        return "INVALID_CLASS"
    if not quotes:
        return "SCHEMA_MISMATCH"

    evidence = value["evidenceQuotes"]
    if (
        type(evidence) is not list
        or len(evidence) > 4
        or any(type(item) is not str or not 0 < len(item) <= 200 for item in evidence)
    ):
        return "SCHEMA_MISMATCH"
    if decision == "PROPOSE" and not evidence:
        return "INVALID_QUOTE_MISSING"
    if len(set(evidence)) != len(evidence):
        return "INVALID_QUOTE_REPEATED"

    for quote in evidence:
        matches = 0

        for _, original in request.fields:
            offset = original.find(quote)

            while offset != -1:
                matches += 1
                offset = original.find(quote, offset + 1)

        if matches == 0:
            return "INVALID_QUOTE_MISSING"
        if matches > 1:
            return "INVALID_QUOTE_REPEATED"

    return "SCHEMA_MISMATCH"


def chat_rejection(value, request, *, model):
    try:
        raw = chat_content(value, model=model)
    except ObservationRejected as error:
        return error.reason_code

    return decision_rejection(raw, request, quotes=True)
