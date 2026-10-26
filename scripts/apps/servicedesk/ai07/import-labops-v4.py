"""Import a pinned private engineering package without publishing or approving it."""

import argparse
import json
from pathlib import Path

from _bootstrap import ROOT, write_new
from servicedesk.intelligence.triage_ai07.dataset_profiles import import_dataset


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--destination", type=Path, required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    destination = args.destination.resolve()
    if not destination.is_relative_to((ROOT / ".local/ai07/datasets").resolve()):
        raise ValueError("Private dataset imports must stay under .local/ai07/datasets")
    summary = import_dataset(args.source, destination)
    write_new(args.output, summary)
    print(json.dumps(summary, sort_keys=True))


if __name__ == "__main__":
    main()
