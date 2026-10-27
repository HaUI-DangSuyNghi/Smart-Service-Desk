"""Tổng hợp bằng chứng synthetic từ kiểm thử engine Java, không triển khai lại policy."""

import argparse
from collections import Counter
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[4]
DATASET = ROOT / "backend/src/test/resources/apps/servicedesk/context-evaluation-v0.1.json"


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def summarize(report_path, suites_path):
    report = json.loads(report_path.read_text(encoding="utf-8"))
    dataset = json.loads(DATASET.read_text(encoding="utf-8"))
    expected = {item["caseId"]: item for item in dataset["cases"]}
    observed = {item["caseId"]: item for item in report["results"]}

    if (
        report["datasetKind"] != "SYNTHETIC_ONLY"
        or report["datasetDigest"] != digest(DATASET.read_bytes())
        or report["reviewedGold"] is not False
        or report["productionDecision"] != "NO_GO"
        or report["selectedModel"] is not None
        or report["extraModelInvocations"] != 0
        or len(observed) != len(report["results"])
        or set(observed) != set(expected)
    ):
        raise ValueError("Báo cáo sai dataset hoặc tự nâng mức bằng chứng")

    for case_id, row in observed.items():
        fixture = expected[case_id]
        if row["familyId"] != fixture["familyId"] or row["decision"] != fixture["pair"]["expectedDecision"]:
            raise ValueError("Quyết định không khớp fixture synthetic")

    for name in ("slotChecks", "literalEvidenceChecks", "missingSlotChecks", "pairChecks"):
        metric = report[name]
        if (type(metric["total"]) is not int or type(metric["passed"]) is not int
                or metric["total"] <= 0 or metric["passed"] != metric["total"]):
            raise ValueError("Kiểm tra chất lượng chưa đạt: " + name)
    denominators = {
        "slotChecks": sum(len(item["expected"]) for item in expected.values()),
        "missingSlotChecks": sum(len(item["missingSlots"]) for item in expected.values()),
        "pairChecks": len(expected),
    }
    if any(report[name]["total"] != total for name, total in denominators.items()):
        raise ValueError("Mẫu số không khớp cohort synthetic đã đóng băng")

    policy_root = ROOT / "backend/src/main/resources/apps/servicedesk/intelligence"
    for name, expected_digest in report["policyDigests"].items():
        if Path(name).name != name or digest((policy_root / name).read_bytes()) != expected_digest:
            raise ValueError("Policy đã thay đổi sau khi chạy test")

    suites = []
    for path in sorted(suites_path.glob("TEST-*.xml")):
        node = ET.parse(path).getroot()
        if ".intelligence.context." not in node.get("name", "") and ".intelligence.duplicate." not in node.get("name", ""):
            continue
        counts = {key: int(node.get(key, "0")) for key in ("tests", "failures", "errors", "skipped")}
        if counts["failures"] or counts["errors"] or counts["skipped"]:
            raise ValueError("Suite chưa chạy hoặc chưa đạt: " + node.get("name", ""))
        suites.append({"name": node.get("name"), **counts, "reportDigest": digest(path.read_bytes())})
    required = {"ContextIntelligenceIntegrationTest", "ContextProcessingGateTest", "ContextIntelligenceModeTest",
                "ContextQualityEvaluationTest", "ContextSlotExtractorTest", "DuplicateDecisionEngineTest"}
    if not required <= {suite["name"].rsplit(".", 1)[-1] for suite in suites}:
        raise ValueError("Thiếu suite bắt buộc, gồm tích hợp PostgreSQL/Flowable")

    support = Counter(row["decision"] for row in report["results"])
    return {
        "project": "SERVICE_DESK_CONTEXT_INTELLIGENCE",
        "createdAt": datetime.now(timezone.utc).isoformat(),
        "gitHead": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
        "worktreeDirty": bool(subprocess.check_output(["git", "status", "--porcelain"], cwd=ROOT, text=True).strip()),
        "datasetKind": "SYNTHETIC_ONLY",
        "evaluationMode": "OFFLINE_SYNTHETIC",
        "deploymentModeDefault": "DISABLED",
        "familyCount": len({item["familyId"] for item in expected.values()}),
        "reportDigest": digest(report_path.read_bytes()),
        "suiteResults": suites,
        "decisionSupport": dict(sorted(support.items())),
        "hardNegativeFalsePositives": 0,
        "goldPrecision": None,
        "goldRecallAt20": None,
        "hostedCi": "UNVERIFIED",
        "productionDecision": "NO_GO",
        "evaluation": report,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, default=ROOT / "backend/target/context-quality-evaluation.json")
    parser.add_argument("--suites", type=Path, default=ROOT / "backend/target/surefire-reports")
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    receipt = summarize(args.report, args.suites)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("x", encoding="utf-8", newline="\n") as stream:
        json.dump(receipt, stream, ensure_ascii=False, indent=2)
        stream.write("\n")
    print(f"PASS synthetic: {receipt['evaluation']['pairChecks']['total']} pairs; "
          f"{receipt['familyCount']} families; production=NO_GO; receipt={args.output}")


if __name__ == "__main__":
    main()
