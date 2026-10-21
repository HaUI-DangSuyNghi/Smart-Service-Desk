"""Real pinned-model controls; never use pending or AI-assisted qrels as truth."""
from pathlib import Path
import sys

import pytest

from hdpi.evaluation.candidates import DenseCandidate
from hdpi.evaluation.evidence import serialize_report
from hdpi.normalization.v1 import NormalizerV1

ROOT = Path(__file__).resolve().parents[3]
DATA = ROOT / 'fixtures/intelligence/service-desk'
sys.path.insert(0, str(ROOT / 'intelligence/src/apps'))

from servicedesk.intelligence.knowledge.dataset import load_dataset
from servicedesk.intelligence.knowledge.experiments import compare_a2_engines
from servicedesk.intelligence.knowledge.projection import project


pytestmark = pytest.mark.dense_model


def test_real_semantic_paraphrase_ranks_related_document(real_provider):
    candidate = DenseCandidate(real_provider)
    sources = [
        ('credentials', '1', 'authored', 'A guide to resetting a forgotten password and restoring account access.'),
        ('bicycle', '1', 'authored', 'Instructions for repairing a punctured bicycle tyre and inflating it.'),
    ]
    index = candidate.build(sources)
    query = 'I cannot remember my login credentials'
    assert candidate.rank(query, index)[0] == 'credentials'


def test_dense_a2_comparison_freezes_representation_and_is_repeatable(real_provider):
    first = compare_a2_engines(ROOT, DATA, dense_provider=real_provider)
    second = compare_a2_engines(ROOT, DATA, dense_provider=real_provider)
    assert set(first) == {'B0', 'B1', 'B2'}
    for key in first:
        assert serialize_report(first[key]) == serialize_report(second[key])
    b0, b2 = first['B0'], first['B2']
    for key in ('dataset', 'components', 'metricVersion', 'caseCount'):
        assert b0[key] == b2[key]
    assert b2['system']['engine'] == 'dense'
    assert b2['comparison']['v02DocumentQrels'] is False
    assert b2['benchmarkClaim'] is False and b2['runtimeCitations'] is False
    assert 'embedding' not in serialize_report(b2).lower()


def test_all_72_draft_queries_smoke_without_relevance_metrics(real_provider):
    dataset = load_dataset(ROOT, DATA)
    sources, _ = project(ROOT, DATA, enriched=True, allow_draft=True)
    candidate = DenseCandidate(real_provider)
    index = candidate.build(sources)
    known = {source[0] for source in sources}
    normalizer = NormalizerV1()
    assert len(dataset.queries) == 72
    for query in dataset.queries:
        text = normalizer.normalize(query['title'] + '\n' + query['description']).value
        ranked = candidate.rank(text, index)
        assert set(ranked) <= known
        assert ranked == candidate.rank(text, index)
