"""Analyze synthetic DEV receipts without copying inputs, completions or secrets."""

import argparse
from pathlib import Path

from _bootstrap import write_new
from local_error_analysis import analyze_report, read_report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    report, digest = read_report(args.report)
    result = analyze_report(report)
    result["sourceReportDigest"] = digest
    write_new(args.output, result)
    print("PASS complete DEV diagnostics; quality NOT_EVALUATED; release NO_GO")


if __name__ == "__main__":
    main()
