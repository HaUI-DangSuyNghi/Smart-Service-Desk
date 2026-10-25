"""Compare operator receipts; hosted outputs need not match bytes."""

import argparse
from pathlib import Path
import subprocess
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))

from _contracts import ROOT, digest, read_json, require, write_json
from _validation import default_bundle_paths, validate_bundle


def compare_cloud_reports(reports, bundle):
    validate_bundle(bundle)
    revision = subprocess.check_output(
        ["git", "rev-parse", "HEAD"], cwd=ROOT, text=True
    ).strip()
    rows = {}
    selection = None

    for report in reports:
        require(
            report["provider"] in {"gemini", "groq"} and report["provider"] not in rows,
            "PROVIDER_COMPARISON_IDENTITY",
        )
        require(
            report["sourceRevision"] == revision
            and report["datasetDigest"] == digest(bundle["dataset"])
            and report["manifestDigest"] == digest(bundle["manifest"]),
            "STALE_CLOUD_RECEIPT",
        )
        identities = [row["caseId"] for row in report["results"]]
        require(
            selection is None or selection == identities, "CLOUD_SELECTION_MISMATCH"
        )
        selection = identities

        for field in (
            "totalPlannedCases",
            "executedCases",
            "notExecutedCases",
            "technicalFailures",
            "unsafeExactSelections",
            "providerAttemptSlots",
        ):
            require(
                type(report[field]) is int and 0 <= report[field] <= len(identities),
                "CLOUD_RECEIPT_COUNTER",
            )

        require(
            report["executedCases"] + report["notExecutedCases"]
            == report["totalPlannedCases"]
            == len(identities),
            "CLOUD_RECEIPT_DENOMINATOR",
        )
        require(
            report["unsafeExactSelections"]
            == sum(row["unsafeExactSelection"] is True for row in report["results"]),
            "CLOUD_SAFETY_COUNTER",
        )
        rows[report["provider"]] = {
            key: report[key]
            for key in (
                "model",
                "scope",
                "result",
                "totalPlannedCases",
                "executedCases",
                "notExecutedCases",
                "technicalFailures",
                "unsafeExactSelections",
                "providerAttemptSlots",
                "realModelValidated",
                "configurationDigest",
                "providerImplementationDigest",
                "approvalDigest",
                "caseRuntimeLatencyMs",
                "reportedTokenUsage",
            )
        }

    return {
        "schemaVersion": "0.2.0",
        "sourceRevision": revision,
        "datasetDigest": digest(bundle["dataset"]),
        "manifestDigest": digest(bundle["manifest"]),
        "providers": rows,
        "humanReview": "PENDING",
        "officialDecision": "BLOCKED",
        "comparisonClass": "UNATTESTED_OPERATOR_RECEIPT_CONSISTENCY_NOT_REMOTE_REPLAY",
        "outputEquivalenceRequired": False,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("reports", nargs="+", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    try:
        bundle = {key: read_json(path) for key, path in default_bundle_paths().items()}
        result = compare_cloud_reports(
            [read_json(path) for path in args.reports], bundle
        )
        write_json(args.output, result)
        print("PASS [RECEIPT_CONSISTENCY_ONLY_OFFICIAL_QUALITY_BLOCKED]")
        return 0
    except Exception:
        print("FAIL [INVALID_OR_INCOMPARABLE_CLOUD_RECEIPTS]")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
