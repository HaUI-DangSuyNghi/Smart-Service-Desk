"""Validate pinned private LabOps v4 bytes and unreviewed engineering partitions."""

import argparse
import json

from _bootstrap import write_new
from servicedesk.intelligence.triage_ai07.dataset_profiles import validate_dataset


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset-root", required=True)
    parser.add_argument("--output")
    args = parser.parse_args()
    summary = validate_dataset(args.dataset_root).summary
    if args.output:
        write_new(args.output, summary)
    print(json.dumps(summary, sort_keys=True))


if __name__ == "__main__":
    main()
