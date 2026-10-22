from pathlib import Path

import pytest

from hdpi.domain.errors import EngineConfigurationError
from hdpi.evaluation.candidates import DenseCandidate, TfidfCandidate
from hdpi.evaluation.errors import EvaluationError
from servicedesk.intelligence.evaluation.retrieval import evaluate
from hdpi.retrieval.models import MAX_SOURCE_BYTES

ROOT = Path(__file__).resolve().parents[3]
DATA = ROOT / 'fixtures/intelligence/service-desk'


def test_candidate_uses_same_chunks_and_preserves_query_identity(provider):
    sources = [('a', '1', 'checksum', 'Alpha beta\n\ngamma')]
    candidate = DenseCandidate(provider)
    assert candidate.build(sources).chunks == TfidfCandidate().build(sources).chunks
    assert candidate.system['queryStrategyVersion'] == TfidfCandidate().system['queryStrategyVersion']


@pytest.mark.parametrize('key', ['engineConfigVersion', 'normalizationVersion', 'chunkPolicyVersion',
                                 'queryStrategyVersion', 'modelVersion', 'modelArtifactDigest'])
def test_mutated_candidate_metadata_fails_closed(provider, key):
    candidate = DenseCandidate(provider)
    candidate.system[key] = 'changed'
    with pytest.raises(EngineConfigurationError):
        candidate.build(())


def test_degraded_candidate_quality_and_unknown_source_gate(provider):
    candidate = DenseCandidate(provider)
    candidate.rank = lambda query, index: ()
    report = evaluate(ROOT, DATA, baseline=DATA / 'evaluation/baseline-v0.1.json', candidate=candidate)
    assert report['result'] == 'FAIL'
    candidate.rank = lambda query, index: ('foreign-source',)
    with pytest.raises(EvaluationError, match='unknown artifact'):
        evaluate(ROOT, DATA, candidate=candidate)


def test_authoring_source_bounds(provider):
    candidate = DenseCandidate(provider)
    source = ('a', '1', 'checksum', 'alpha')
    with pytest.raises(EngineConfigurationError):
        candidate.build([source, source])
    with pytest.raises(EngineConfigurationError):
        candidate.build([('a', '1', 'checksum', 'a' * (MAX_SOURCE_BYTES + 1))])
