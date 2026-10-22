import json
from pathlib import Path

import pytest

from hdpi.evaluation.conformance import check_case

PATH = Path(__file__).resolve().parents[3] / 'fixtures/intelligence/service-desk/tickets/tickets-v0.1.jsonl'
CASES = [json.loads(line) for line in PATH.read_text(encoding='utf-8').splitlines()]


@pytest.mark.parametrize('fixture', CASES, ids=lambda f: f['id'])
def test_authored_conformance(fixture, service):
    check_case(fixture, service, profile_version='0.3.0', expected_runtime={'engine': 'rules', 'profileVersion': 'service-desk@0.3.0', 'rulesVersion': '0.3.0'}, normalization_version='text-normalization@0.2.0')
