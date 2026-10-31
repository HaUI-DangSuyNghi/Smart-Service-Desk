"""A pinned inference binding; caller authorization remains outside model authority."""

from hashlib import sha256
import json
import re

from .decision_observation import validate_observation
from .protocols import ClassificationRequest, digest, require


class ClassificationInferenceService:
    def __init__(
        self, *, model_digest, policy_digest, allowed_classes, abstain_id, observe
    ):
        require(digest(model_digest) and digest(policy_digest))
        self.model_digest = model_digest
        self.policy_digest = policy_digest
        self.allowed_classes = tuple(allowed_classes)
        self.abstain_id = abstain_id
        self.observe = observe

    def infer(self, payload):
        require(
            type(payload) is dict
            and set(payload)
            == {
                "contractVersion",
                "requestId",
                "inputDigest",
                "modelDigest",
                "policyDigest",
                "allowedClasses",
                "input",
            }
        )
        require(payload["contractVersion"] == "classification-inference@0.1.0")
        require(
            type(payload["requestId"]) is str
            and re.fullmatch(r"[0-9a-f-]{36}", payload["requestId"])
        )
        require(
            payload["modelDigest"] == self.model_digest
            and payload["policyDigest"] == self.policy_digest
        )
        require(payload["allowedClasses"] == list(self.allowed_classes))
        require(type(payload["input"]) is dict)
        request = ClassificationRequest(
            fields=tuple(payload["input"].items()),
            allowed_classes=self.allowed_classes,
            policy_digest=self.policy_digest,
        )
        input_digest = sha256(
            json.dumps(
                payload["input"],
                ensure_ascii=False,
                sort_keys=True,
                separators=(",", ":"),
            ).encode()
        ).hexdigest()
        require(payload["inputDigest"] == input_digest)
        observation = validate_observation(
            self.observe(request), request, abstain_id=self.abstain_id
        )
        return {
            "contractVersion": "classification-inference@0.1.0",
            "requestId": payload["requestId"],
            "inputDigest": input_digest,
            "modelDigest": self.model_digest,
            "policyDigest": self.policy_digest,
            "observation": {
                "decision": observation.decision,
                "classId": observation.class_id,
                "evidenceStatus": "UNVERIFIED_BY_MODEL",
                "score": None,
                "scoreSemantics": "NOT_AVAILABLE",
                "operationalAuthority": False,
            },
            "operationalAuthority": False,
        }
