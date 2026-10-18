from pathlib import Path
from email.message import Message
from io import BytesIO
import json
import runpy

import pytest

CHECKS = runpy.run_path(str(Path(__file__).resolve().parents[3] / 'scripts/intelligence/summarize-integration.py'))
HTTP = runpy.run_path(str(Path(__file__).resolve().parents[3] / 'scripts/acceptance/triage-gateway.py'))['http']


@pytest.mark.parametrize(('tests', 'failures', 'errors', 'skipped', 'expected'), [
    (1, 0, 0, 0, True), (0, 0, 0, 0, False), (1, 1, 0, 0, False),
    (1, 0, 1, 0, False), (1, 0, 0, 1, False),
])
def test_integration_report_rejects_empty_failed_or_skipped_suites(tmp_path, tests, failures, errors, skipped, expected):
    report = tmp_path / 'tests.xml'
    report.write_text(f'<testsuites><testsuite tests="{tests}" failures="{failures}" errors="{errors}" skipped="{skipped}"/></testsuites>')
    assert CHECKS['junit_pass'](report) is expected


def test_missing_integration_report_is_not_a_pass(tmp_path):
    assert CHECKS['junit_pass'](tmp_path / 'missing.xml') is False


@pytest.mark.parametrize(('status', 'code', 'valid'), [
    (503, 'INTELLIGENCE_UNAVAILABLE', True), (504, 'INTELLIGENCE_TIMEOUT', True),
    (503, 'INTELLIGENCE_TIMEOUT', False), (504, 'INTELLIGENCE_UNAVAILABLE', False),
    (200, 'INTELLIGENCE_UNAVAILABLE', False), (502, 'INTELLIGENCE_TIMEOUT', False),
])
def test_gateway_outage_requires_exact_http_problem_mapping(monkeypatch, status, code, valid):
    response = BytesIO(json.dumps({'code': code}).encode())
    response.status = status
    response.headers = Message()
    response.headers['Content-Type'] = 'application/problem+json'
    monkeypatch.setitem(HTTP.__globals__, 'urlopen', lambda *args, **kwargs: response)
    if valid:
        assert HTTP('POST', 'http://127.0.0.1/synthetic', expected=(503, 504)) == {'code': code}
    else:
        with pytest.raises(AssertionError):
            HTTP('POST', 'http://127.0.0.1/synthetic', expected=(503, 504))
