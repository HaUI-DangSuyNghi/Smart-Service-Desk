import importlib.util
import json
from pathlib import Path

import pytest

SCRIPT = Path(__file__).resolve().parents[3] / 'scripts/intelligence/verify-evaluation-equivalence.py'
spec = importlib.util.spec_from_file_location('evaluation_equivalence', SCRIPT)
equivalence = importlib.util.module_from_spec(spec)
spec.loader.exec_module(equivalence)


@pytest.mark.parametrize('mutation', [None, 'ranking', 'fingerprint', 'gate', 'missing'])
def test_cross_environment_proof_rejects_mismatch_or_missing_evidence(tmp_path, mutation):
    first, second = tmp_path / 'first', tmp_path / 'second'
    first.mkdir()
    second.mkdir()
    for name in equivalence.REPORTS:
        data = dict(schemaVersion='1.0', result='PASS', fingerprint='sha256:' + '0'*64,
                    cases=[{'rankedLocalArtifacts': ['a', 'b']}])
        (first / name).write_text(json.dumps(data), 'utf-8')
        if mutation == 'missing' and name == 'retrieval-report.json':
            continue
        if name == 'retrieval-report.json':
            if mutation == 'ranking':
                data['cases'][0]['rankedLocalArtifacts'].reverse()
            elif mutation == 'fingerprint':
                data['fingerprint'] = 'sha256:' + '1'*64
            elif mutation == 'gate':
                data['result'] = 'FAIL'
        (second / name).write_text(json.dumps(data), 'utf-8')
    if mutation:
        with pytest.raises((ValueError, OSError)):
            equivalence.verify(first, second)
    else:
        equivalence.verify(first, second)
