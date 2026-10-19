"""Expose only external application tooling, even during installed-wheel tests."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / 'intelligence/src/apps'))
