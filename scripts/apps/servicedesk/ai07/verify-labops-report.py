"""Recompute full engineering denominators and proxy metrics; production always refuses."""

import argparse
import json
from pathlib import Path

import _bootstrap  # noqa: F401

from servicedesk.intelligence.triage_ai07.dataset_profiles import (
    bounded_json,
    validate_dataset,
)
from servicedesk.intelligence.triage_ai07.labops_evaluation import verify_report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset-root", required=True)
    parser.add_argument("--reports", nargs="+", type=Path, required=True)
    parser.add_argument("--require-production", action="store_true")
    parser.add_argument("--runtime-config", type=Path)
    args = parser.parse_args()
    dataset = validate_dataset(args.dataset_root)
    runtime_raw = args.runtime_config.read_bytes() if args.runtime_config else None
    results = [
        verify_report(dataset, bounded_json(path.read_bytes()), runtime_raw=runtime_raw)
        for path in args.reports
    ]
    print(json.dumps(results, sort_keys=True))
    if args.require_production:
        raise SystemExit(2)


if __name__ == "__main__":
    main()
