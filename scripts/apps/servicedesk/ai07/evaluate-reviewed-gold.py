"""Score a genuinely reviewed DEV freeze. No CLI boolean can create Gold or promotion."""

import argparse
import os
from pathlib import Path

from _bootstrap import write_new
from servicedesk.intelligence.triage_ai07.reviewed_gold import evaluate_reviewed


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--review-manifest", type=Path, required=True)
    parser.add_argument("--reviewed-rows", type=Path, required=True)
    parser.add_argument("--predictions", type=Path, required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    key_path = os.environ.get("AI07_REVIEW_TRUST_KEY_FILE")
    if not key_path:
        raise ValueError(
            "Independent owner/reviewer trust key is not configured; quality remains PENDING"
        )
    key = Path(key_path).read_bytes()
    result = evaluate_reviewed(
        args.review_manifest.read_bytes(),
        args.reviewed_rows.read_bytes(),
        args.predictions.read_bytes(),
        trusted_key=key,
    )
    write_new(args.output, result)
    print(
        "PASS authenticated reviewed DEV evaluation; production approval remains separate"
    )


if __name__ == "__main__":
    main()
