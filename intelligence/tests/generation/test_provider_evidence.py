from copy import deepcopy
import importlib.util
import json
from pathlib import Path

import pytest


ROOT = Path(__file__).resolve().parents[3]
SCRIPT = ROOT / 'scripts/intelligence/verify-ai06-provider-evidence.py'
SPEC = importlib.util.spec_from_file_location('ai06_provider_equivalence_tests', SCRIPT)
equivalence = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(equivalence)


def test_forty_case_pack_is_repeatable_content_free_and_explicitly_synthetic():
    report = equivalence.evidence.build_report()

    assert report == equivalence.evidence.build_report()
    assert report['caseCount'] == 40
    assert report['categoryCounts'] == {
        'direct': 8,
        'indirect': 8,
        'schema': 6,
        'delimiter': 6,
        'privacy': 4,
        'resource': 4,
        'benign': 4,
    }
    assert sum(report['outcomeCounts'].values()) == 40
    assert report['invariantViolations'] == 0
    assert report['realModelValidation'] == 'REAL_MODEL_VALIDATION_BLOCKED'
    assert report['benchmarkClaim'] is False
    assert sum(case['unsafeSourceQuoted'] for case in report['cases']) == 8

    serialized = json.dumps(report)

    for private in (
        'DUMMY_CANARY',
        'Synthetic approved evidence',
        '169.254.169.254',
        'policy_instructions',
    ):
        assert private not in serialized


@pytest.mark.parametrize(
    'mutation', ['model', 'config', 'code', 'dataset', 'denominator', 'promotion', 'counter-type']
)
def test_comparison_rejects_two_matching_stale_or_counterfeit_reports(tmp_path, mutation):
    report = deepcopy(equivalence.evidence.build_report())

    if mutation == 'model':
        report['manifestDigest'] = 'sha256:' + 'a' * 64
    elif mutation == 'config':
        report['runtime']['config_version'] = 'stale@0.0.0'
    elif mutation == 'code':
        report['implementationDigests']['generation/transport.py'] = 'sha256:' + 'b' * 64
    elif mutation == 'dataset':
        report['testSetDigest'] = 'sha256:' + 'c' * 64
    elif mutation == 'denominator':
        report['cases'].pop()
    elif mutation == 'counter-type':
        report['cases'][0]['claimCount'] = True
    else:
        report['runtimePromotion'] = 'AUTHORIZED'

    first = tmp_path / 'first.json'
    second = tmp_path / 'second.json'

    for path in (first, second):
        path.write_text(json.dumps(report), 'utf-8')

    with pytest.raises(ValueError, match='current identities'):
        equivalence.verify(first, second)


def test_evidence_comparison_accepts_crlf_semantic_equivalence(tmp_path):
    report = equivalence.evidence.build_report()
    first = tmp_path / 'source.json'
    second = tmp_path / 'wheel.json'
    first.write_bytes(json.dumps(report, indent=2).encode())
    second.write_bytes(json.dumps(report, indent=4).replace('\n', '\r\n').encode())

    equivalence.verify(first, second)
