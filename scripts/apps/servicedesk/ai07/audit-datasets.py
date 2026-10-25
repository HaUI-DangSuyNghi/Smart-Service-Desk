"""Inventory immutable SSD fixtures; no external import or rights approval implied."""

import argparse

from _bootstrap import ROOT, write_new
from servicedesk.intelligence.triage_ai07.datasets import inventory


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sources", choices=("ssd",), default="ssd")
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    write_new(args.output, inventory(ROOT))
    print("PASS immutable SSD inventory; labels/rights pending; LabOps not imported")


if __name__ == "__main__":
    main()
