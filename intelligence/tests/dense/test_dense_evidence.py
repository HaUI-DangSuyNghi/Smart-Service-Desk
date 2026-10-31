import importlib.util
import json
from pathlib import Path

import pytest

SCRIPT = Path(__file__).resolve().parents[3] / 'scripts/intelligence/verify-knowledge-evidence.py'
spec = importlib.util.spec_from_file_location('dense_evidence', SCRIPT)
evidence = importlib.util.module_from_spec(spec)
spec.loader.exec_module(evidence)


@pytest.mark.parametrize('mutation', [None, 'ranking', 'fingerprint', 'missing', 'promotion', 'holdout'])
@pytest.mark.parametrize('quality', ['PASS', 'FAIL'])
def test_dense_equivalence_detects_drift_and_preserves_quality_rejection(tmp_path, mutation, quality):
    first, second = tmp_path / 'first', tmp_path / 'second'
    first.mkdir()
    second.mkdir()
    for name in evidence.DENSE:
        value = {'benchmarkClaim': False, 'fingerprint': 'synthetic-fingerprint',
                 'cases': [{'rankedCuratedArtifacts': ['a', 'b']}], 'result': quality}
        if name.endswith('summary.json'):
            value.update(engineeringResult='PASS', qualityResult=quality,
                         holdout='NOT_EVALUATED', runtimePromotion='NOT_AUTHORIZED',
                         sameA2ProjectionAndJudgmentBasis=True)
        (first / name).write_text(json.dumps(value), encoding='utf-8')
        if name == 'b2-dense-report.json':
            if mutation == 'missing':
                continue
            if mutation == 'ranking':
                value['cases'][0]['rankedCuratedArtifacts'].reverse()
            if mutation == 'fingerprint':
                value['fingerprint'] = 'changed'
        if name.endswith('summary.json'):
            if mutation == 'promotion':
                value['runtimePromotion'] = 'AUTHORIZED'
            if mutation == 'holdout':
                value['holdout'] = 'EVALUATED'
        (second / name).write_text(json.dumps(value), encoding='utf-8')
    if mutation:
        with pytest.raises((ValueError, OSError)):
            evidence.verify(first, second, dense=True)
    else:
        evidence.verify(first, second, dense=True)
