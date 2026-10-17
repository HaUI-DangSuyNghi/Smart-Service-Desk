import json
from pathlib import Path

import pytest

from smart_service_intelligence.domain.models import TrustedContext
from smart_service_intelligence.services.triage import TriageService

ROOT = Path(__file__).resolve().parents[2]


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
