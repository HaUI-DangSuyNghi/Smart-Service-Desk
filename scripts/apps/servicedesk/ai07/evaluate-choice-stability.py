"""Compare paired DEV Tev policies without treating rule predictions as gold."""

import argparse
from collections import Counter, defaultdict
from hashlib import sha256
from pathlib import Path

from _bootstrap import write_new
from experiment_policies import POLICIES
from local_error_analysis import EXPERIMENTS, checked_attempts, read_report, wilson


def compare_reports(reports, *, control="tev1"):
    if not reports:
        raise ValueError("At least one frozen DEV report is required")

    grouped = defaultdict(dict)
    dataset = reports[0]["datasetDigest"]
    identities = {}
    serving_fields = (
        "bridgeRevision",
        "ollamaImage",
        "ollamaVersion",
        "contextTokens",
        "concurrency",
        "inferenceParameters",
    )
    reference_runtime = reports[0]["runtimeIdentity"]

    for report in reports:
        checked_attempts(report)
        if (
            report["datasetDigest"] != dataset
            or report["caseIds"] != reports[0]["caseIds"]
        ):
            raise ValueError("Policy comparison requires the same paired DEV cohort")
        if any(
            report["runtimeIdentity"].get(field) != reference_runtime.get(field)
            for field in serving_fields
        ):
            raise ValueError("Policy study must use the same frozen serving identity")

        for name in report["summaries"]:
            if not name.startswith("tev1"):
                continue
            if name in grouped:
                raise ValueError(
                    "Duplicate candidate run; select one frozen run per policy"
                )
            candidate = report["runtimeIdentity"]["candidates"][name]
            filename, adapter = POLICIES[name]
            if (
                candidate["policyFile"] != filename
                or candidate["adapter"] != adapter
                or candidate["policyDigest"]
                != sha256((EXPERIMENTS / filename).read_bytes()).hexdigest()
            ):
                raise ValueError("Candidate policy binding differs")
            identities[name] = candidate

            for item in report["attempts"]:
                if item["candidate"] == name:
                    grouped[name][(item["caseId"], item["repeat"])] = item

    if control not in grouped or len(grouped) < 2:
        raise ValueError("A control and at least one study policy are required")

    comparisons = {}
    baseline = grouped[control]
    for name, attempts in sorted(grouped.items()):
        if name == control:
            continue
        if set(attempts) != set(baseline):
            raise ValueError("Paired repeat denominator differs")
        for field in ("modelArtifactDigest", "tokenizerDigest", "apiDigest"):
            if identities[name][field] != identities[control][field]:
                raise ValueError("Policy study must use the same frozen model")

        changed_cases = set()
        changed = 0
        for key, item in attempts.items():
            original = baseline[key]
            if item["inputDigest"] != original["inputDigest"]:
                raise ValueError("Paired input digest differs")
            if (item["state"], item.get("classId")) != (
                original["state"],
                original.get("classId"),
            ):
                changed += 1
                changed_cases.add(key[0])

        unique_cases = len({key[0] for key in attempts})
        comparisons[name] = {
            "pairedAttempts": len(attempts),
            "uniqueCases": unique_cases,
            "changedAttempts": changed,
            "changedCases": len(changed_cases),
            "changedCaseWilson95": wilson(len(changed_cases), unique_cases),
            "states": dict(
                sorted(Counter(item["state"] for item in attempts.values()).items())
            ),
            "policyDigest": identities[name]["policyDigest"],
        }

    return {
        "schemaVersion": "ai07-choice-stability@0.1.0",
        "datasetDigest": dataset,
        "control": control,
        "comparisons": comparisons,
        "quality": {"status": "NOT_EVALUATED", "macroF1": None},
        "release": "NO_GO",
        "operationalAuthority": False,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reports", nargs="+", type=Path, required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    loaded = [read_report(path) for path in args.reports]
    result = compare_reports([report for report, _ in loaded])
    result["sourceReportDigests"] = [digest for _, digest in loaded]
    write_new(args.output, result)
    print(
        "PASS paired choice stability; no calibration or classification-quality claim"
    )


if __name__ == "__main__":
    main()
