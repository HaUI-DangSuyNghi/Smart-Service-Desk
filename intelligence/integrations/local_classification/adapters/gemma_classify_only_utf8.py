"""Versioned G1 input serialization experiment; the strict decoder stays unchanged."""

import json

from local_classification.adapters import gemma_classify_only


VERSION = "gemma-classify-only@0.3.0"
OPTIONS = gemma_classify_only.OPTIONS


def request_payload(request, *, model, policy, keep_alive=0):
    payload = gemma_classify_only.request_payload(
        request, model=model, policy=policy, keep_alive=keep_alive
    )
    payload["messages"][1]["content"] = json.dumps(
        dict(request.fields), ensure_ascii=False
    )
    return payload


decode_response = gemma_classify_only.decode_response
