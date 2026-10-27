"""Kiểm tra receipt lifecycle từ suite Java thật; không tự nâng synthetic thành Gold."""

import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[4]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def verify(report_path, suites_path):
    report = json.loads(report_path.read_text(encoding="utf-8"))
    fixture_path = ROOT / "backend/src/test/resources/apps/servicedesk/readiness-cases-v0.1.json"
    fixture = json.loads(fixture_path.read_text(encoding="utf-8"))
    expected = {case["caseId"]: case for case in fixture["cases"]}
    if (report["datasetKind"] != "SYNTHETIC_ONLY" or report["datasetDigest"] != digest(fixture_path)
            or report["productionDecision"] != "NO_GO" or report["workflowGateApplied"] is not False
            or report["includesDatabaseOrModel"] is not False or report["wrongGlobalBlocks"] != 0
            or len(report["cases"]) != len(expected)):
        raise ValueError("Sai provenance, mẫu số hoặc tự nâng mức bằng chứng")
    observed = {case["caseId"]: case for case in report["cases"]}
    if set(observed) != set(expected):
        raise ValueError("Cohort có case thiếu/lặp/không được phép")
    for case_id, case in observed.items():
        original = expected[case_id]
        if case["familyId"] != original["familyId"] or case["observed"] != original["expected"]:
            raise ValueError("Readiness sai label synthetic")
    safety_count = sum(case["safety"] for case in expected.values())
    unblocked_count = sum(case["expected"] in {"READINESS_OK", "ENRICHMENT_OPTIONAL"} for case in expected.values())
    for key, total in (("safetyBypass", safety_count), ("unblockedAssessment", unblocked_count)):
        if report[key] != {"passed": total, "total": total}:
            raise ValueError("Sai mẫu số hoặc safety/unblocked gate")
    policy_path = ROOT / "backend/src/main/resources/apps/servicedesk/intelligence/readiness-policy-v0.1.json"
    if report["policyDigest"] != digest(policy_path):
        raise ValueError("Policy drift sau test")
    suites = []
    for path in sorted(suites_path.glob("TEST-*.xml")):
        node = ET.parse(path).getroot()
        name = node.get("name", "")
        if ".apps.servicedesk.intelligence." not in name:
            continue
        counts = {key: int(node.get(key, "0")) for key in ("tests", "failures", "errors", "skipped")}
        if counts["failures"] or counts["errors"] or counts["skipped"]:
            raise ValueError("Suite chưa PASS đầy đủ: " + name)
        suites.append({"name": name, **counts, "digest": digest(path)})
    required = {"ContextReadinessPolicyTest", "ContextEvaluationGuardTest", "ContextProcessingGateTest",
                "EnrichmentLifecycleIntegrationTest", "EnrichmentReadinessEvaluationTest",
                "TicketContextualAssessmentIntegrationTest", "TicketAssessmentInputAssemblerTest"}
    if not required <= {suite["name"].rsplit(".", 1)[-1] for suite in suites}:
        raise ValueError("Thiếu suite lifecycle/contextual/ACL/DB thực thi")
    return {
        "project": "SERVICE_DESK_ENRICHMENT_LIFECYCLE",
        "createdAt": datetime.now(timezone.utc).isoformat(),
        "headSha": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
        "workingTreeClean": not bool(subprocess.check_output(["git", "status", "--porcelain"], cwd=ROOT, text=True).strip()),
        "datasetKind": "SYNTHETIC_ONLY", "reportDigest": digest(report_path), "suites": suites,
        "readiness": report, "contextualModelEvidence": "SYNTHETIC_TRANSPORT_ONLY",
        "goldStatus": "PENDING", "hostedCI": "UNVERIFIED", "productionDecision": "NO_GO",
        "workflowGateApplied": False,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, default=ROOT / "backend/target/enrichment-readiness-evaluation.json")
    parser.add_argument("--suites", type=Path, default=ROOT / "backend/target/surefire-reports")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    receipt = verify(args.report, args.suites)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("x", encoding="utf-8", newline="\n") as stream:
        json.dump(receipt, stream, ensure_ascii=False, indent=2)
        stream.write("\n")
    print(f"PASS lifecycle synthetic: {len(receipt['readiness']['cases'])} cases; production=NO_GO; receipt={args.output}")


if __name__ == "__main__":
    main()
