"""Recompute current bytes, HEAD and outcomes; reject forged/stale DEV evidence."""

from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))

from _cli import main

if __name__ == "__main__":
    raise SystemExit(main("verify"))
