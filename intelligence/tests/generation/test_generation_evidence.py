import importlib.util
import json
from pathlib import Path

import pytest


SCRIPT = Path(__file__).resolve().parents[3] / 'scripts/intelligence/generation-evidence.py'
SPEC = importlib.util.spec_from_file_location('generation_evidence', SCRIPT)
evidence = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(evidence)


def test_foundation_evidence_is_repeatable_and_excludes_content():
    report = evidence.build_report()

    assert report == evidence.build_report()
    assert report['benchmarkClaim'] is False
    assert len(report['cases']) == 5
    assert report['result'] == 'PASS'

    serialized = json.dumps(report)

    for private in (
        'Synthetic instruction', 'Synthetic question', 'Synthetic private failure',
        '11111111-1111-4111-8111-111111111111', 'synthetic-evidence-workspace',
    ):
        assert private not in serialized


def test_equivalence_rejects_runtime_or_result_drift(tmp_path):
    report = evidence.build_report()
    first = tmp_path / 'source.json'
    second = tmp_path / 'wheel.json'
    first.write_text(json.dumps(report), encoding='utf-8')
    second.write_text(json.dumps(report), encoding='utf-8')

    evidence.verify(first, second)

    report['runtime']['config_version'] = 'changed@2'
    second.write_text(json.dumps(report), encoding='utf-8')

    with pytest.raises(ValueError, match='differs'):
        evidence.verify(first, second)

    report['result'] = 'FAIL'
    second.write_text(json.dumps(report), encoding='utf-8')

    with pytest.raises(ValueError, match='PASS'):
        evidence.verify(first, second)
