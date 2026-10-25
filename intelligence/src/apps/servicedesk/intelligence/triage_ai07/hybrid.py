"""Offline shadow comparison. Candidates never enter authoritative response fields."""

from copy import deepcopy
from dataclasses import dataclass
from hashlib import sha256
import json
from pathlib import Path
from time import perf_counter

from hdpi.classification.protocols import ClassificationRequest
from hdpi.classification.validation import validate_proposal


POLICY_BYTES = Path(__file__).with_name("policy-v0.1.json").read_bytes()
POLICY = json.loads(POLICY_BYTES)
POLICY_DIGEST = sha256(POLICY_BYTES).hexdigest()


@dataclass(frozen=True)
class ShadowObservation:
    state: str
    agreement: bool | None
    proposed_class: str | None
    elapsed_ms: float


class HybridTriagePolicy:
    """Explicit caller-owned offline seam; no jobs, production calls or cloud routes."""

    def __init__(self, *, candidate=None, enabled=False):
        self.candidate = candidate
        self.enabled = enabled is True

    def compare(self, authoritative, *, title, description):
        snapshot = deepcopy(authoritative)

        if not self.enabled or self.candidate is None:
            return snapshot, ShadowObservation("DISABLED", None, None, 0.0)

        request = ClassificationRequest(
            fields=(("title", title), ("description", description)),
            allowed_classes=tuple(POLICY["allowedClasses"]),
            policy_digest=POLICY_DIGEST,
        )
        started = perf_counter()

        try:
            proposal = validate_proposal(self.candidate.predict(request), request)
            category = (
                snapshot.get("result", {}).get("category")
                if snapshot.get("result")
                else None
            )
            state = "ABSTAIN" if proposal.decision == "ABSTAIN" else "OBSERVED"
            agreement = (
                proposal.class_id == category
                if proposal.class_id and category
                else None
            )
            observation = ShadowObservation(
                state,
                agreement,
                proposal.class_id,
                round((perf_counter() - started) * 1000, 3),
            )
        except Exception:
            # Raw model text, input, exceptions and scores never reach telemetry or rule output.
            observation = ShadowObservation(
                "DEGRADED",
                None,
                None,
                round((perf_counter() - started) * 1000, 3),
            )

        return snapshot, observation
