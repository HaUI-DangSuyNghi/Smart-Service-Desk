"""Execute rules on DEV; unapproved candidates stay unavailable, not simulated."""

import argparse

from _bootstrap import ROOT, write_new
from servicedesk.intelligence.triage_ai07.evaluation import evaluate


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", choices=("shadow",), required=True)
    parser.add_argument("--split", choices=("DEV",), required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    write_new(args.output, evaluate(ROOT))
    print("PASS DEV engineering controls; CPU/Gemma quality NOT_EVALUATED")


if __name__ == "__main__":
    main()
