"""Content-free diagnosis; this does not attest or replay model evidence."""

import argparse
from collections import Counter, defaultdict
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))

from _contracts import DATA, DatasetError, digest, read_json, require, write_json


def analyze(report, dataset):
    require(report["datasetDigest"] == digest(dataset), "DATASET_PIN_MISMATCH")
    cases = {case["caseId"]: case for case in dataset["cases"]}
    rows = report["results"]
    ids = [row["caseId"] for row in rows]
    require(len(cases) == len(dataset["cases"]), "DUPLICATE_DATASET_ID")
    require(
        len(ids) == len(set(ids)) and set(ids) == set(cases), "REPORT_CASE_COVERAGE"
    )
    require(type(report["technicalFailures"]) is int, "INVALID_COUNTER")
    require(type(report["provisionalBehaviorMismatches"]) is int, "INVALID_COUNTER")
    categories = defaultdict(list)
    scopes = Counter()
    confusion = Counter()
    technical = 0

    for row in rows:
        case = cases[row["caseId"]]
        require(type(row["technicalPass"]) is bool, "INVALID_TECHNICAL_FLAG")
        require(row["category"] == case["category"], "CATEGORY_DRIFT")
        technical += int(not row["technicalPass"])

        if not row["technicalPass"]:
            continue

        expected, actual = case["provisionalExpectedStatus"], row["status"]
        require(type(row["provisionalBehaviorMatch"]) is bool, "INVALID_BEHAVIOR_FLAG")
        require(
            row["provisionalBehaviorMatch"] == (expected == actual),
            "BEHAVIOR_FLAG_DRIFT",
        )

        if expected == actual:
            continue

        scope = (
            "CANDIDATE_RECALL_REVIEW"
            if actual == "INSUFFICIENT_EVIDENCE"
            else "SEMANTIC_SELECTION_REVIEW"
        )
        scopes[scope] += 1
        confusion[(expected, actual)] += 1
        categories[case["category"]].append(case["caseId"])

    require(technical == report["technicalFailures"], "TECHNICAL_COUNT_DRIFT")
    # The generic evaluator counts technical failures as provisional mismatches too.
    require(
        sum(scopes.values()) + technical == report["provisionalBehaviorMismatches"],
        "MISMATCH_COUNT_DRIFT",
    )

    return {
        "evidenceClass": "UNVERIFIED_INPUT_TRIAGE_NOT_RUNTIME_REPLAY",
        "sourceRevisionInInput": report["sourceRevision"],
        "inputReportDigest": digest(report),
        "datasetDigest": digest(dataset),
        "caseCount": len(rows),
        "technicalFailures": technical,
        "provisionalBehaviorMismatches": sum(scopes.values()) + technical,
        "fixScopes": dict(sorted(scopes.items())),
        "confusion": [
            {"expected": key[0], "observed": key[1], "count": count}
            for key, count in sorted(confusion.items())
        ],
        "categories": {
            key: {"count": len(ids), "caseIds": ids}
            for key, ids in sorted(categories.items())
        },
        "officialDecision": "BLOCKED",
        "humanReview": "PENDING",
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--dataset", type=Path, default=DATA / "dev-v0.2.json")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--examples", type=int, default=3)
    args = parser.parse_args()

    try:
        result = analyze(read_json(args.report), read_json(args.dataset))

        if args.output:
            write_json(args.output, result)

        print("Cases:", result["caseCount"])
        print("Technical failures:", result["technicalFailures"])
        print(
            "Provisional behavior mismatches:", result["provisionalBehaviorMismatches"]
        )
        print("Fix scopes:", result["fixScopes"])

        for name, row in result["categories"].items():
            print(
                name, row["count"], ", ".join(row["caseIds"][: max(0, args.examples)])
            )

        print("TRIAGE_ONLY [OFFICIAL_QUALITY_BLOCKED]")
        return 0
    except Exception as error:
        print(
            "FAIL ["
            + (
                str(error)
                if isinstance(error, DatasetError)
                else "INVALID_TRIAGE_INPUT"
            )
            + "]"
        )
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
