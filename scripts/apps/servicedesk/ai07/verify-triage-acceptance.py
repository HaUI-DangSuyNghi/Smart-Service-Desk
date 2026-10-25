"""Recompute every DEV result; never promote a model from synthetic controls."""

import argparse
import json
from pathlib import Path

from _bootstrap import ROOT
from servicedesk.intelligence.triage_ai07.evaluation import verify


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", required=True)
    parser.add_argument("--require-promotion", action="store_true")
    args = parser.parse_args()
    report = json.loads(Path(args.report).read_text("utf-8"))
    result = verify(ROOT, report, require_promotion=args.require_promotion)
    print(json.dumps(result, sort_keys=True))


if __name__ == "__main__":
    main()
