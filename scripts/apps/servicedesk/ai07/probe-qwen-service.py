"""Probe the real private Qwen HTTP service and existing app guards with synthetic text."""

import argparse
from hashlib import sha256
import json
from pathlib import Path
import sys
import subprocess
from uuid import uuid4

sys.path.insert(0, str(Path(__file__).resolve().parent))
from _bootstrap import ROOT, write_new  # noqa: E402

if not sys.flags.isolated:
    sys.path.insert(0, str(ROOT / "intelligence/integrations"))

from hdpi.classification.decision_observation import CONTRACT, DecisionObservation  # noqa: E402
from hdpi.classification.protocols import ClassificationRequest, require  # noqa: E402
from local_classification.ollama_transport import strict_json  # noqa: E402
from servicedesk.intelligence.triage_ai07.guarded_decision import guard_observation  # noqa: E402
from servicedesk.intelligence.triage_ai07.qwen_policy import CLASSES, POLICY_DIGEST  # noqa: E402


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--package", type=Path, required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--container", default="ai07-qwen-team-ai07-qwen-1")
    args = parser.parse_args()
    package = json.loads((args.package / "package.json").read_bytes())
    require(package["policyDigest"] == POLICY_DIGEST)
    fields = {
        "title": "🚀 e\u0301 Màn hình đổi độ phân giải sau cập nhật driver",
        "description": "Mạng vẫn hoạt động. Đây là dữ liệu kiểm thử tổng hợp.",
    }
    request_id = str(uuid4())
    input_digest = sha256(
        json.dumps(
            fields, ensure_ascii=False, sort_keys=True, separators=(",", ":")
        ).encode()
    ).hexdigest()
    payload = {
        "contractVersion": "classification-inference@0.1.0",
        "requestId": request_id,
        "inputDigest": input_digest,
        "modelDigest": package["modelDigest"],
        "policyDigest": POLICY_DIGEST,
        "allowedClasses": list(CLASSES),
        "input": fields,
    }
    require(args.container and not args.container.startswith("-"))
    result = subprocess.run(
        [
            "docker",
            "exec",
            "-i",
            args.container,
            "python",
            "/run/classify-client.py",
            "--stdin",
        ],
        input=json.dumps(payload, ensure_ascii=False).encode(),
        capture_output=True,
        timeout=8,
    )
    report = strict_json(result.stdout, 32768)
    status, elapsed = report["httpStatus"], report["wallMs"]
    receipt = {
        "schemaVersion": "ai07-qwen-native-http-probe@0.1.0",
        "scope": "OWNED_SYNTHETIC_NOT_BACKEND_ACTIVATION",
        "modelDigest": package["modelDigest"],
        "policyDigest": POLICY_DIGEST,
        "httpStatus": status,
        "wallMs": elapsed,
        "operationalAuthority": False,
        "officialQualityClaim": False,
        "release": "NO_GO",
    }
    if status != 200:
        receipt["result"] = "DEGRADED_NO_PROPOSAL"
        write_new(args.output, receipt)
        raise SystemExit(2)
    value = report["response"]
    require(
        all(
            value[key] == payload[key]
            for key in (
                "contractVersion",
                "requestId",
                "inputDigest",
                "modelDigest",
                "policyDigest",
            )
        )
    )
    vote = value["observation"]
    require(
        value["operationalAuthority"] is False and vote["operationalAuthority"] is False
    )
    require(
        vote["score"] is None
        and vote["scoreSemantics"] == "NOT_AVAILABLE"
        and vote["evidenceStatus"] == "UNVERIFIED_BY_MODEL"
    )
    observation = DecisionObservation(
        contract_version=CONTRACT,
        decision=vote["decision"],
        class_id=vote["classId"],
        option_order=(*CLASSES, "UNDETERMINED"),
        model_scores=None,
        score_semantics="NOT_AVAILABLE",
    )
    request = ClassificationRequest(
        fields=tuple(fields.items()),
        allowed_classes=CLASSES,
        policy_digest=POLICY_DIGEST,
    )
    assisted = guard_observation(observation, request)
    shadow = guard_observation(observation, request, mode="SHADOW")
    require(
        assisted["classification"]["category"] == vote["classId"]
        and assisted["triage"]["priority"] is None
    )
    require(assisted["needsReview"] and shadow["classification"] is None)
    receipt.update(
        result="PASS",
        decision=vote["decision"],
        classId=vote["classId"],
        modelCategoryPreserved=True,
        priority=None,
        needsReview=True,
        shadowConcealed=True,
    )
    write_new(args.output, receipt)
    print("PASS native Qwen HTTP -> neutral contract -> app guard; production NO_GO")


if __name__ == "__main__":
    main()
