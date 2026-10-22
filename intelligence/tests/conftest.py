import json
from pathlib import Path

import pytest
import sys

from hdpi.domain.models import TrustedContext
from hdpi.services.triage import TriageService

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'intelligence/src/apps'))


@pytest.fixture(scope='session')
def fixtures():
    path = ROOT / 'fixtures/intelligence/service-desk/tickets/tickets-v0.1.jsonl'
    return [json.loads(line) for line in path.read_text(encoding='utf-8').splitlines()]


@pytest.fixture
def service():
    return TriageService()


@pytest.fixture
def trusted():
    return TrustedContext(workspace_id='lab-a', subject='test-subject')


@pytest.fixture(autouse=True)
def configured_app_profiles(monkeypatch):
    monkeypatch.setenv('HDPI_PROFILE_ROOT', str(ROOT / 'intelligence/src/apps/servicedesk/intelligence/profiles'))
