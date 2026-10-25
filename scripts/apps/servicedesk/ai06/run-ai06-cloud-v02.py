"""Explicitly approved per-provider smoke/full DEV; no network by default."""

from pathlib import Path
import subprocess
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))

from _cli import parser_for
from _contracts import ROOT, default_inputs, read_json, write_json
from _validation import default_bundle_paths


def main():
    parser = parser_for("cloud evaluation")
    parser.add_argument("--provider", choices=("gemini", "groq"), required=True)
    parser.add_argument("--scope", choices=("smoke", "full"), default="smoke")
    parser.add_argument("--allow-external-egress", action="store_true")
    parser.add_argument("--data-class", default="disabled")
    parser.add_argument("--approval-file", type=Path)
    parser.add_argument("--approval-sha256", default="")
    parser.add_argument("--max-requests", type=int, default=0)
    parser.add_argument("--max-input-tokens", type=int, default=0)
    parser.add_argument("--max-output-tokens", type=int, default=0)
    parser.add_argument("--max-cost", default="0")
    args = parser.parse_args()
    report = {
        "result": "BLOCKED",
        "realModelValidated": False,
        "officialDecision": "BLOCKED",
        "provider": args.provider,
        "scope": args.scope,
        "reasonCode": "EGRESS_KEYS_AND_EXTERNAL_SCOPE_APPROVAL_REQUIRED",
        "sourceRevision": subprocess.check_output(
            ["git", "rev-parse", "HEAD"], cwd=ROOT, text=True
        ).strip(),
        "executedCases": 0,
        "providerAttemptSlots": 0,
    }

    try:
        if args.mode == "official" or not args.allow_external_egress:
            return finish(args, report, 2)

        if not args.installed:
            sys.path.insert(0, str(ROOT / "intelligence/src"))

        from _cloud import run_cloud

        bundle = {key: read_json(getattr(args, key)) for key in default_bundle_paths()}
        inputs = {key: getattr(args, key) for key in default_inputs()}
        report = run_cloud(args, bundle, inputs)
        return finish(args, report, 0 if report["realModelValidated"] else 1)
    except Exception:
        return finish(args, report, 2)


def finish(args, report, code):
    if args.output:
        write_json(args.output, report)

    print(str(report["result"]) + " [OFFICIAL_QUALITY_BLOCKED]")
    return code


if __name__ == "__main__":
    raise SystemExit(main())
