"""Decode the SFT alphabet; the injected application owns its interpretation."""

import re

from hdpi.classification.decision_observation import CONTRACT, DecisionObservation
from hdpi.classification.protocols import require


VERSION = "qwen35-lora-letter@0.1.0"


def decode_letter(raw, request, *, letters, abstain_id):
    require(type(raw) is str and 0 < len(raw) <= 64)
    letter = raw.strip()
    require(re.fullmatch("[A-F]", letter) is not None)
    require(tuple(letters) == tuple("ABCDEF") and letters["F"] is None)
    require(tuple(letters[key] for key in "ABCDE") == request.allowed_classes)
    category = letters[letter]
    return DecisionObservation(
        contract_version=CONTRACT,
        decision="ABSTAIN" if category is None else "PROPOSE",
        class_id=category,
        option_order=(*request.allowed_classes, abstain_id),
        model_scores=None,
        score_semantics="NOT_AVAILABLE",
    )
