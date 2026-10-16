import json
from pathlib import Path

import pytest

from smart_service_intelligence.evaluation.conformance import check_case

PATH = Path(__file__).resolve().parents[3] / 'fixtures/intelligence/service-desk/tickets/tickets-v0.1.jsonl'
CASES = [json.loads(line) for line in PATH.read_text(encoding='utf-8').splitlines()]


@pytest.mark.parametrize('fixture', CASES, ids=lambda f: f['id'])
def test_authored_conformance(fixture, service):
    check_case(fixture, service)
