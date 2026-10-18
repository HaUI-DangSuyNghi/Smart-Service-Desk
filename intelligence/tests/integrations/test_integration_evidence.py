from pathlib import Path
import runpy

import pytest

CHECKS = runpy.run_path(str(Path(__file__).resolve().parents[3] / 'scripts/intelligence/summarize-integration.py'))


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
