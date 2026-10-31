"""Small app-owned entrypoints; errors and reports never echo raw case content."""

import argparse
from pathlib import Path
import sys
import tempfile

from _contracts import (
    DATA,
    SEED,
    DatasetError,
    canonical,
    default_inputs,
    digest,
    read_json,
    require,
    write_json,
)
from _dataset import generate_bundle, sample_smoke
from _validation import default_bundle_paths, inspect_bundle, validate_bundle


def parser_for(command):
    parser = argparse.ArgumentParser(
        description="Service Desk synthetic DEV " + command
    )

    for key, default in default_inputs().items():
        parser.add_argument("--" + key, type=Path, default=default)

    for key, default in default_bundle_paths().items():
        parser.add_argument("--" + key, type=Path, default=default)

    parser.add_argument("--seed", type=int, default=SEED)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--installed", action="store_true")
    parser.add_argument(
        "--mode", choices=("synthetic", "official"), default="synthetic"
    )
    parser.add_argument("--compare-source-wheel", nargs=2, type=Path)
    parser.add_argument("reports", nargs="*", type=Path)
    return parser


def execute(command, args):
    inputs = {key: getattr(args, key) for key in default_inputs()}
    outputs = {key: getattr(args, key) for key in default_bundle_paths()}

    if command == "prepare":
        if args.output:
            outputs["dataset"] = args.output

        protected = {path.resolve() for path in inputs.values()}
        destinations = {path.resolve() for path in outputs.values()}
        require(
            len(destinations) == 4 and not protected.intersection(destinations),
            "PROTECTED_OUTPUT_PATH",
        )
        bundle = generate_bundle(inputs, args.seed)
        validation = inspect_bundle(bundle, inputs)

        for key, destination in outputs.items():
            write_json(destination, bundle[key])

        return {
            "result": "GENERATED",
            "caseCount": validation["caseCount"],
            "datasetDigest": validation["datasetDigest"],
            "officialDecision": "BLOCKED",
        }

    bundle = {key: read_json(path) for key, path in outputs.items()}

    if command == "validate":
        return validate_bundle(bundle, inputs)

    if command == "select":
        validate_bundle(bundle, inputs)
        selected = sample_smoke(bundle["dataset"], bundle["curation"])
        write_json(args.output or outputs["smoke"], selected)
        return {
            "result": "SAMPLED",
            "caseCount": len(selected["caseIds"]),
            "datasetDigest": selected["datasetDigest"],
            "officialDecision": "BLOCKED",
        }

    if command == "compare":
        validate_bundle(bundle, inputs)

        with tempfile.TemporaryDirectory(
            prefix="synthetic-repeatability-"
        ) as directory:
            snapshots = []

            for trial in ("first", "second"):
                generated = generate_bundle(inputs, args.seed)
                hashes = {}

                for key, value in generated.items():
                    destination = Path(directory) / trial / (key + ".json")
                    write_json(destination, value)
                    hashes[key] = digest(destination.read_bytes())
                    require(
                        destination.read_bytes() == outputs[key].read_bytes(),
                        "COMMITTED_BYTE_DRIFT",
                    )

                snapshots.append(hashes)

            require(snapshots[0] == snapshots[1], "NON_REPRODUCIBLE")

        return {
            "result": "PASS",
            "artifactDigests": snapshots[0],
            "repeatedRuns": 2,
            "officialDecision": "BLOCKED",
        }

    if not args.installed:
        from _contracts import ROOT

        sys.path.insert(0, str(ROOT / "intelligence/src"))

    from _evaluation import build_report, verify_reports

    if command == "run":
        return build_report(bundle, inputs)

    require(command == "verify", "UNKNOWN_COMMAND")
    reports = args.compare_source_wheel or args.reports
    require(bool(reports), "REPORT_REQUIRED")
    verify_reports(reports, bundle, inputs)
    return {
        "result": "PASS",
        "verifiedReports": len(reports),
        "officialDecision": "BLOCKED",
    }


def main(command):
    args = parser_for(command).parse_args()

    if args.mode == "official":
        report = {
            "result": "BLOCKED",
            "officialDecision": "BLOCKED",
            "reasons": [
                "HUMAN_REVIEW_PENDING",
                "RIGHTS_PENDING",
                "HOLDOUT_NOT_LOCKED",
                "THRESHOLDS_NOT_APPROVED",
            ],
        }

        if args.output:
            write_json(args.output, report)

        print("BLOCKED [OFFICIAL_QUALITY_NOT_AUTHORIZED]")
        return 2

    try:
        report = execute(command, args)

        if args.output and command not in {"prepare", "select"}:
            write_json(args.output, report)

        if command == "run":
            print(
                "DEV_EVALUATED cases="
                + str(report["caseCount"])
                + " technicalFailures="
                + str(report["technicalFailures"])
                + " unsafeSelections="
                + str(report["unsafeExactSelections"])
            )
            return int(report["technicalFailures"] != 0)

        print(str(report["result"]) + " [OFFICIAL_QUALITY_BLOCKED]")
        return 0
    except Exception as error:
        code = (
            str(error)
            if isinstance(error, DatasetError)
            else "INVALID_OR_UNAVAILABLE_SYNTHETIC_INPUT"
        )
        print("FAIL [" + code + "]")
        return 1
