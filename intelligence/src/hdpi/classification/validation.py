"""Strict wire decoding and literal input provenance, without domain defaults."""

import json
import math

from .protocols import (
    ClassificationError,
    ClassificationProposal,
    ClassificationRequest,
    InputSpan,
    require,
)


def _object(pairs):
    value = {}

    for key, item in pairs:
        require(key not in value)
        value[key] = item

    return value


def validate_proposal(value, request):
    require(type(request) is ClassificationRequest)
    require(type(value) is ClassificationProposal and value.contract_version == "0.1.0")
    require(type(value.spans) is tuple and len(value.spans) <= 8)

    if value.decision == "ABSTAIN":
        require(value.class_id is None and not value.spans)
        require(value.score is None and value.score_semantics is None)
        return value

    require(
        value.decision == "PROPOSE"
        and type(value.class_id) is str
        and value.class_id in request.allowed_classes
    )
    fields = dict(request.fields)

    for span in value.spans:
        require(
            type(span) is InputSpan and type(span.field) is str and span.field in fields
        )
        require(type(span.start) is int and type(span.end) is int)
        require(0 <= span.start < span.end <= len(fields[span.field]))
        require(
            type(span.text) is str
            and fields[span.field][span.start : span.end] == span.text
        )

    if value.score is None:
        require(value.score_semantics is None)
    else:
        require(type(value.score) is float and math.isfinite(value.score))
        require(
            0 <= value.score <= 1
            and value.score_semantics == "UNCALIBRATED_PROBABILITY"
        )

    return value


def decode_proposal(raw, request):
    """Wire SLM output requires exact copied spans; no free-form rationale or tools."""
    try:
        require(type(raw) is bytes and 0 < len(raw) <= 8192)
        value = json.loads(raw.decode("utf-8"), object_pairs_hook=_object)
        require(type(value) is dict)
        require(set(value) == {"contractVersion", "decision", "classId", "spans"})
        require(type(value["spans"]) is list and len(value["spans"]) <= 8)
        spans = []

        for item in value["spans"]:
            require(
                type(item) is dict and set(item) == {"field", "start", "end", "text"}
            )
            spans.append(InputSpan(**item))

        proposal = ClassificationProposal(
            contract_version=value["contractVersion"],
            decision=value["decision"],
            class_id=value["classId"],
            spans=tuple(spans),
        )
        validate_proposal(proposal, request)
        require(proposal.decision == "ABSTAIN" or bool(proposal.spans))
        return proposal
    except (ValueError, TypeError, KeyError, RecursionError):
        raise ClassificationError("Classification response rejected") from None
