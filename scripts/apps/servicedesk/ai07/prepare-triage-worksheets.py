"""Prepare blank metadata-only DEV label worksheets, never gold or holdout exports."""

import argparse

from _bootstrap import ROOT, write_new
from servicedesk.intelligence.triage_ai07.datasets import worksheets


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset", choices=("ssd-v0.2",), required=True)
    parser.add_argument("--split", choices=("DEV",), required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    write_new(args.output, worksheets(ROOT))
    print("PASS blank DEV worksheets; DRAFT, no provisional label copied")


if __name__ == "__main__":
    main()
