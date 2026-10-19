"""Expose only external application tooling, even during installed-wheel tests."""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / 'intelligence/src/apps'))
