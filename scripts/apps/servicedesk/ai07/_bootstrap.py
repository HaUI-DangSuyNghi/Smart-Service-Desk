"""CLI bootstrapping for Tier 3 tooling; not packaged into the generic wheel."""

from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "intelligence/src"))
sys.path.insert(0, str(ROOT / "intelligence/src/apps"))


def write_new(path, value):
    import json

    target = Path(path).resolve()
    allowed = (ROOT / ".local/ai07").resolve()

    if not target.is_relative_to(allowed):
        raise ValueError("Evidence output must stay inside .local/ai07")

    target.parent.mkdir(parents=True, exist_ok=True)

    # Never overwrite original fixtures, prior attempts or review receipts.
    with target.open("x", encoding="utf-8", newline="\n") as stream:
        json.dump(value, stream, indent=2, ensure_ascii=True)
        stream.write("\n")
