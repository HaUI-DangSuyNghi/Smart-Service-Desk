"""Audit source ownership and optionally the actual generic distribution."""

import argparse
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))

from _contracts import write_json
from _ownership import audit_repository, audit_wheel


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--wheel", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()

    try:
        report = audit_repository()

        if args.wheel:
            report["wheel"] = audit_wheel(args.wheel)

        failed = (
            report["result"] != "PASS"
            or report.get("wheel", {}).get("result", "PASS") != "PASS"
        )

        if args.output:
            write_json(args.output, report)

        print(
            "FAIL [SEMANTIC_OWNERSHIP]"
            if failed
            else "PASS [SEMANTIC_OWNERSHIP_DECLARED_SCOPE]"
        )
        return int(failed)
    except Exception:
        print("FAIL [SEMANTIC_OWNERSHIP_INPUT_UNAVAILABLE]")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
