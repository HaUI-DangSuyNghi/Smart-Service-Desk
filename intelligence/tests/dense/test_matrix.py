from dataclasses import replace
import importlib.util
import json
from pathlib import Path
import sys

import pytest

from hdpi.evaluation.errors import EvaluationError
from hdpi.evaluation.evidence import write_report
from hdpi.retrieval.models import digest

ROOT = Path(__file__).resolve().parents[3]
DATA = ROOT / 'fixtures/intelligence/service-desk'
sys.path.insert(0, str(ROOT / 'intelligence/src/apps'))
from servicedesk.intelligence.knowledge.candidate_benchmark import candidates, evaluate_matrix, KEYS
from servicedesk.intelligence.knowledge.selection import claim_holdout, finish_holdout, readiness, validate_holdout_freeze
from servicedesk.intelligence.knowledge.working_dataset import load_working_dataset

SCRIPT = ROOT / 'scripts/intelligence/verify-candidate-matrix.py'
spec = importlib.util.spec_from_file_location('matrix_equivalence', SCRIPT)
equivalence = importlib.util.module_from_spec(spec)
spec.loader.exec_module(equivalence)


@pytest.fixture(scope='module')
def working():
    return load_working_dataset(ROOT, DATA)


def test_provisional_matrix_runs_new_grades_on_dev_with_paired_and_slice_metrics(provider, working):
    reports, summary = evaluate_matrix(working, candidates(provider))
    assert tuple(reports) == KEYS
    assert summary['caseCount'] == 48 and summary['holdout'] == 'NOT_EVALUATED'
    assert summary['judgmentBasis'] == 'AI_ASSISTED_PROVISIONAL' and summary['humanReviewed'] is False
    assert summary['runtimePromotion'] == 'NOT_AUTHORIZED'
    assert summary['freezeDigest'] == 'sha256:' + digest(summary['freeze'])
    dev_ids = {query['id'] for query in working.queries if query['split'] == 'DEV'}
    for name, report in reports.items():
        assert {case['queryId'] for case in report['cases']} == dev_ids
        assert report['comparison']['humanReviewed'] is False and report['benchmarkClaim'] is False
        assert report['comparison']['slices']['scenario:NETWORK']['caseCount'] == 8
        assert report['comparison']['slices']['coverage:root-cause-conflict']['caseCount'] > 0
        assert all(sum(counts.values()) == 48 for counts in summary['pairedComparisons'][name]['counts'].values())


def test_provisional_cannot_unlock_holdout_and_unknown_results_fail(provider, working):
    engines = candidates(provider)
    with pytest.raises(EvaluationError, match='Provisional'):
        evaluate_matrix(working, engines, split='HOLDOUT')
    engines['B3_HYBRID'].rank = lambda *args: ('foreign-artifact',)
    with pytest.raises(EvaluationError, match='unjudged'):
        evaluate_matrix(working, engines)


def test_data_readiness_records_the_usable_new_qrels_and_actual_review_state():
    report = readiness(ROOT, DATA)
    assert report['provisionalEvaluationAvailable'] is True and report['workingJudgmentCount'] == 720
    assert report['pendingHumanJudgmentCount'] == 720 and report['reviewedBenchmarkReady'] is False
    assert report['runtimePromotion'] == 'NOT_AUTHORIZED'


def test_reviewed_freeze_and_journal_associations(provider, working, tmp_path):
    # Synthetic state-machine test only; no source fixture or human provenance is changed.
    reviewed = replace(working, judgment_basis='HUMAN_REVIEWED')
    engines = candidates(provider)
    _, summary = evaluate_matrix(reviewed, engines)
    freeze = validate_holdout_freeze(reviewed, engines, summary)
    journal = claim_holdout(tmp_path, reviewed.identity, freeze)
    with pytest.raises(EvaluationError, match='already recorded'):
        claim_holdout(tmp_path, reviewed.identity, 'other-freeze-cannot-reset-the-attempt')
    finish_holdout(journal, summary)
    assert json.loads(journal.read_text('utf-8'))['state'] == 'COMPLETED'
    summary['judgmentBasis'] = 'AI_ASSISTED_PROVISIONAL'
    with pytest.raises(EvaluationError):
        validate_holdout_freeze(reviewed, engines, summary)


@pytest.mark.parametrize('mutation', [None, 'ranking', 'freeze', 'review', 'promotion'])
def test_matrix_equivalence_detects_drift(provider, working, tmp_path, mutation):
    reports, summary = evaluate_matrix(working, candidates(provider))
    left, right = tmp_path / 'left', tmp_path / 'right'
    for directory in (left, right):
        directory.mkdir()
        for name, report in reports.items():
            write_report(report, directory / (name.lower().replace('_', '-') + '-report.json'))
        (directory / 'v02-comparison-summary.json').write_text(json.dumps(summary), encoding='utf-8')
    if mutation == 'ranking':
        path = right / 'b3-hybrid-report.json'
        value = json.loads(path.read_text('utf-8'))
        value['cases'][0]['rankedArtifacts'].reverse()
        path.write_text(json.dumps(value), encoding='utf-8')
    elif mutation:
        summary['freezeDigest' if mutation == 'freeze' else 'humanReviewed' if mutation == 'review' else
                'runtimePromotion'] = 'changed'
        (right / 'v02-comparison-summary.json').write_text(json.dumps(summary), encoding='utf-8')
    if mutation:
        with pytest.raises(ValueError):
            equivalence.verify(left, right)
    else:
        equivalence.verify(left, right)
