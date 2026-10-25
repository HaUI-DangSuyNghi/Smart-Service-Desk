from copy import deepcopy
from pathlib import Path
import sys

import pytest


ROOT = Path(__file__).resolve().parents[5]
sys.path.insert(0, str(ROOT / "scripts/apps/servicedesk/ai06"))

if not sys.flags.isolated:
    sys.path.insert(0, str(ROOT / "intelligence/integrations"))

from _contracts import digest
from _dataset import generate_bundle


class SafeBundle(dict):
    def __repr__(self):
        return "<synthetic v0.2 bundle; raw content omitted>"


@pytest.fixture(scope="session")
def pristine_bundle():
    return SafeBundle(generate_bundle())


@pytest.fixture
def synthetic_bundle(pristine_bundle):
    return deepcopy(pristine_bundle)
