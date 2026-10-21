"""Exercise draft queries without using pending or AI-assisted relevance labels."""
from pathlib import Path

from hdpi.evaluation.candidates import Bm25Candidate, TfidfCandidate
from hdpi.normalization.v1 import NormalizerV1
from servicedesk.intelligence.knowledge.dataset import load_dataset
from servicedesk.intelligence.knowledge.projection import project

ROOT = Path(__file__).resolve().parents[3]
DATA = ROOT / 'fixtures/intelligence/service-desk'


def test_all_72_draft_queries_have_deterministic_valid_bm25_rankings():
    dataset = load_dataset(ROOT, DATA)
    sources, _ = project(ROOT, DATA, enriched=True, allow_draft=True)
    candidate = Bm25Candidate()
    index = candidate.build(sources)
    assert index.chunks == tuple(sorted(TfidfCandidate().build(sources).chunks,
                                        key=lambda c: (c.source_id, c.ordinal, c.chunk_id)))
    known = {source[0] for source in sources}
    normalizer = NormalizerV1()
    assert len(dataset.queries) == 72
    for query in dataset.queries:
        text = normalizer.normalize(query['title'] + '\n' + query['description']).value
        ranking = candidate.rank(text, index)
        assert set(ranking) <= known
        assert ranking == candidate.rank(text, index)
        # Deliberately no qrels, label comparison, slice tuning or quality metrics.
