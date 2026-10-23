from pathlib import Path

import pytest

from hdpi.domain.errors import EngineConfigurationError
from hdpi.evaluation.candidates import Bm25Candidate, TfidfCandidate
from hdpi.evaluation.errors import EvaluationError
from servicedesk.intelligence.evaluation.retrieval import evaluate
from hdpi.evaluation.retrieval import stable_report
from hdpi.retrieval.models import MAX_SOURCE_BYTES

ROOT = Path(__file__).resolve().parents[3]
DATA = ROOT / 'fixtures/intelligence/service-desk'
BASELINE = DATA / 'evaluation/baseline-v0.1.json'


def test_same_dataset_representation_and_metadata():
    sources = [('a', '1', 'checksum', 'Alpha beta\n\ngamma')]
    assert Bm25Candidate().build(sources).chunks == TfidfCandidate().build(sources).chunks
    bm25 = stable_report(evaluate(ROOT, DATA, baseline=BASELINE, candidate=Bm25Candidate()))
    tfidf = stable_report(evaluate(ROOT, DATA, baseline=BASELINE))
    assert bm25['dataset'] == tfidf['dataset']
    assert bm25['components'] == tfidf['components']
    assert bm25['metricVersion'] == tfidf['metricVersion']
    assert bm25['caseCount'] == tfidf['caseCount'] == 12
    for key in ('normalizationVersion', 'chunkPolicyVersion', 'queryStrategyVersion'):
        assert bm25['system'][key] == tfidf['system'][key]
    assert bm25['system']['engine'] == 'bm25'
    assert bm25['benchmarkClaim'] is False and bm25['runtimeCitations'] is False
    assert bm25['fingerprint'] != tfidf['fingerprint']


@pytest.mark.parametrize('key', ['engineConfigVersion', 'normalizationVersion', 'chunkPolicyVersion', 'queryStrategyVersion'])
def test_identity_mutations_fail_before_evaluation(key):
    candidate = Bm25Candidate()
    candidate.system[key] = 'changed-without-version-bump'
    with pytest.raises((EngineConfigurationError, EvaluationError)):
        evaluate(ROOT, DATA, candidate=candidate)


def test_unknown_source_rejected_by_inherited_evaluator():
    candidate = Bm25Candidate()
    candidate.rank = lambda query, index: ('foreign-artifact',)
    with pytest.raises(EvaluationError, match='unknown artifact'):
        evaluate(ROOT, DATA, candidate=candidate)


def test_degraded_bm25_cannot_lower_frozen_baseline_gate():
    candidate = Bm25Candidate()
    candidate.rank = lambda query, index: ()
    report = evaluate(ROOT, DATA, candidate=candidate, baseline=BASELINE, gates={'hitAt1': 0, 'hitAt3': 0})
    assert report['result'] == 'FAIL'
    assert len(report['gates']['violations']) == 2


def test_duplicate_source_and_resource_bounds():
    candidate = Bm25Candidate()
    source = ('a', '1', 'checksum', 'alpha')
    with pytest.raises(EngineConfigurationError, match='source identity'):
        candidate.build([source, source])
    with pytest.raises(EngineConfigurationError, match='resource limits'):
        candidate.build([('a', '1', 'checksum', 'a' * (MAX_SOURCE_BYTES + 1))])
