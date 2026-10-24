"""Recompute AI06-03 evidence against current bytes; reject stale or edited reports."""

import argparse
import importlib.util
import json
from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[2]


def unique_object(pairs):
    result = {}

    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate evidence field")
        result[key] = value

    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("reports", type=Path, nargs="+")
    parser.add_argument("--installed", action="store_true")
    parser.add_argument(
        "--compare",
        action="store_true",
        help="Cross-OS parity after each job's execution checks",
    )
    args = parser.parse_args()

    if not args.installed:
        sys.path.insert(0, str(ROOT / "intelligence/src"))

    path = Path(__file__).with_name("run-generation-evaluation.py")
    spec = importlib.util.spec_from_file_location("generation_evidence_runner", path)
    runner = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(runner)
    dataset = ROOT / "fixtures/intelligence/service-desk/generation/dev-v0.1.json"
    config = (
        ROOT
        / "intelligence/src/apps/servicedesk/intelligence/assistance-reference.json"
    )
    identity = runner.current_identity(dataset, config, ROOT / "intelligence/src/hdpi")

    try:
        reports = [
            json.loads(path.read_text("utf-8"), object_pairs_hook=unique_object)
            for path in args.reports
        ]
    except (ValueError, OSError):
        parser.exit(1, "Invalid generation evidence file\n")

    if args.compare and len(reports) < 2:
        parser.error("Cross-OS comparison requires at least two reports")

    expected = reports[0] if args.compare else runner.build_report(dataset, config)

    for observed in reports:
        if (
            not isinstance(observed, dict)
            or any(observed.get(key) != value for key, value in identity.items())
            or type(observed.get("technicalFailures")) is not int
            or observed["technicalFailures"] != 0
            or observed.get("officialDecision") != "BLOCKED"
            or observed.get("realModelValidated") is not False
        ):
            parser.exit(1, "Invalid or stale generation identity\n")

        # Canonical serialized comparison also distinguishes true from 1.
        if json.dumps(observed, sort_keys=True) != json.dumps(expected, sort_keys=True):
            parser.exit(
                1,
                "Generation evidence does not match current identities and execution\n",
            )

    print("PASS current generation evidence; official quality remains BLOCKED")


if __name__ == "__main__":
    main()
