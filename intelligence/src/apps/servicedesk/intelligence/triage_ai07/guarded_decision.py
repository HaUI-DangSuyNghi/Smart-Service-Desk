"""Model-first category suggestions; application guards never rewrite the class."""

from hashlib import sha256
import json
from pathlib import Path
import re

from hdpi.classification.decision_observation import validate_observation
from hdpi.classification.protocols import require


POLICY_BYTES = Path(__file__).with_name("guard-policy-v0.1.json").read_bytes()
POLICY_DIGEST = sha256(POLICY_BYTES).hexdigest()
POLICY = json.loads(POLICY_BYTES)


def guard_observation(observation, request, *, mode="ASSISTED"):
    require(mode in ("SHADOW", "ASSISTED"))
    validate_observation(observation, request, abstain_id=POLICY["abstainId"])
    require(set(request.allowed_classes) == set(POLICY["allowedClasses"]))
    matches, reasons = [], []
    for rule in POLICY["guards"]:
        for field, text in request.fields:
            for match in re.finditer(rule["pattern"], text, re.IGNORECASE):
                matches.append(
                    {
                        "ruleId": rule["id"],
                        "kind": rule["kind"],
                        "field": field,
                        "start": match.start(),
                        "end": match.end(),
                        "text": match.group(),
                    }
                )
                if rule["kind"] != "SOFT_SIGNAL":
                    reasons.append(rule["kind"])

    if mode == "SHADOW":
        reasons.append("SHADOW_OBSERVATION_NOT_PUBLISHED")
    else:
        if observation.decision == "ABSTAIN":
            reasons.append("MODEL_ABSTAINED")
        reasons.append("UNVERIFIED_MODEL_SUGGESTION")
    classification = {
        "decision": observation.decision,
        "category": observation.class_id,
        "score": None,
        "scoreSemantics": "NOT_AVAILABLE",
        "evidenceStatus": "UNVERIFIED_BY_MODEL",
    }
    if mode == "SHADOW":
        classification = None
    return {
        "contractVersion": "model-primary-assessment@0.1.0",
        "mode": mode,
        "classification": classification,
        "policyDisposition": {
            "status": "REVIEW_REQUIRED",
            "reasonCodes": sorted(set(reasons)),
            "ruleMatches": matches,
            "autoRoutingAllowed": False,
        },
        "triage": {"impact": None, "urgency": None, "priority": None},
        "needsReview": True,
        "operationalAuthority": False,
        "policyDigest": POLICY_DIGEST,
    }
