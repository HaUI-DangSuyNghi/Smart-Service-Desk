"""Content-free model-primary source/wheel controls; no private data or model execution."""

import argparse
from hashlib import sha256
import json
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))

from _bootstrap import write_new  # noqa: E402
from hdpi.classification.decision_observation import CONTRACT, DecisionObservation  # noqa: E402
from hdpi.classification.protocols import ClassificationRequest  # noqa: E402
from servicedesk.intelligence.triage_ai07.dataset_profiles import PROFILE  # noqa: E402
from servicedesk.intelligence.triage_ai07.guarded_decision import (
    POLICY,
    POLICY_DIGEST,
    guard_observation,
)  # noqa: E402


def evidence():
    request = ClassificationRequest(
        fields=(("title", "Màn hình sau cập nhật driver"), ("description", "")),
        allowed_classes=tuple(POLICY["allowedClasses"]),
        policy_digest=POLICY_DIGEST,
    )
    observation = DecisionObservation(
        contract_version=CONTRACT,
        decision="PROPOSE",
        class_id="SOFTWARE",
        option_order=(*request.allowed_classes, "UNDETERMINED"),
        model_scores=None,
        score_semantics="NOT_AVAILABLE",
    )
    visible = guard_observation(observation, request)
    shadow = guard_observation(observation, request, mode="SHADOW")
    if (
        visible["classification"]["category"] != "SOFTWARE"
        or shadow["classification"] is not None
    ):
        raise ValueError("Model-primary or shadow contract changed")
    if (
        visible["triage"]["priority"] is not None
        or visible["operationalAuthority"] is not False
    ):
        raise ValueError("Classification acquired operational authority")
    return {
        "schemaVersion": "ai07-model-primary-controls@0.1.0",
        "scope": "OWNED_SYNTHETIC_CONTRACT_ONLY_NO_MODEL_INFERENCE",
        "guardDigest": POLICY_DIGEST,
        "datasetProfileDigest": sha256(PROFILE.read_bytes()).hexdigest(),
        "missingUrgencyPreservesModelCategory": True,
        "shadowConcealsModel": True,
        "noAutomaticRouting": True,
        "needsReview": True,
        "officialQualityClaim": False,
        "promotion": "NO_GO",
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output")
    parser.add_argument("--compare", nargs=2, type=Path)
    args = parser.parse_args()
    expected = evidence()
    if args.compare:
        if any(json.loads(path.read_bytes()) != expected for path in args.compare):
            raise ValueError("Source/wheel model-primary controls differ")
    elif args.output:
        write_new(args.output, expected)
    else:
        parser.error("--output or --compare is required")
    print("PASS model-primary guard/source-wheel controls; production NO_GO")


if __name__ == "__main__":
    main()
