from dataclasses import replace
from fractions import Fraction

import pytest

from hdpi.domain.errors import EngineConfigurationError, EngineUnavailable
from hdpi.evaluation.candidates import HybridCandidate
from hdpi.retrieval.chunking import chunk_text
from hdpi.retrieval.dense.engine import DenseSearchEngine
from hdpi.retrieval.hybrid.config import HybridConfig
from hdpi.retrieval.hybrid.engine import HybridSearchEngine, fuse
from hdpi.retrieval.models import MAX_QUERY_CHARS, RankedHit


def chunks(count=3):
    return tuple(chunk_text('alpha', source_id=f'{index:03}', source_state=('1',))[0]
                 for index in range(count))


def ranked(items):
    return tuple(RankedHit(chunk, len(items) - rank) for rank, chunk in enumerate(items))


@pytest.mark.parametrize('change', [
    {'version': 'changed'}, {'constant': 1}, {'constant': 60.0}, {'retrieval_depth': 3},
    {'default_top_k': 10}, {'algorithm': 'raw-score-sum'}, {'deduplication': 'chunk'},
    {'representative': 'highest-score'}, {'tie_break': 'unstable'},
    {'lexical_config': 'changed'}, {'dense_config': 'changed'},
])
def test_hybrid_config_changes_rejected(change):
    with pytest.raises(EngineConfigurationError):
        replace(HybridConfig(), **change).validate()


def test_hand_computed_rrf_and_exact_tie():
    a, b, c = chunks()
    hits = fuse(ranked([a, b]), ranked([b, a, c]), (a, b, c), HybridConfig())
    assert [hit.chunk.source_id for hit in hits] == [a.source_id, b.source_id, c.source_id]
    assert hits[0].score == hits[1].score == float(Fraction(1, 61) + Fraction(1, 62))
    assert hits[2].score == float(Fraction(1, 63))


def test_empty_children_and_score_scales_are_irrelevant():
    a, b, c = chunks()
    lexical, dense = ranked([a, b]), ranked([b, c])
    reference = fuse(lexical, dense, (a, b, c), HybridConfig())
    scaled = tuple(RankedHit(hit.chunk, hit.score * 1000000) for hit in lexical)
    assert fuse(scaled, dense, (a, b, c), HybridConfig()) == reference
    assert fuse((), (), (a, b, c), HybridConfig()) == ()
    assert len(fuse(lexical, (), (a, b, c), HybridConfig())) == 2


def test_document_dedup_precedes_depth_and_uses_known_representative():
    original = chunks(1)[0]
    repeats = tuple(replace(original, ordinal=index, chunk_id=str(index)) for index in range(25))
    other = chunks(2)[1]
    hits = fuse(ranked([*repeats, other]), ranked([repeats[1]]), (*repeats, other), HybridConfig())
    assert len(hits) == 2
    assert hits[0].chunk == repeats[0]
    assert hits[1].score == float(Fraction(1, 62))


def test_depth_is_twenty_documents_per_child_before_final_topk():
    corpus = chunks(30)
    result = fuse(ranked(corpus), (), corpus, HybridConfig())
    assert len(result) == 20
    assert result[-1].chunk == corpus[19]


@pytest.mark.parametrize('kind', ['duplicate', 'foreign', 'changed-bytes', 'unstable', 'unknown-object'])
def test_invalid_child_candidates_fail_closed(kind):
    a, b, c = chunks()
    hits = ranked([a, b])
    if kind == 'duplicate':
        hits = ranked([a, a])
    elif kind == 'foreign':
        hits = ranked([replace(a, chunk_id='unknown')])
    elif kind == 'changed-bytes':
        hits = ranked([replace(a, original_text='foreign bytes')])
    elif kind == 'unstable':
        hits = tuple(reversed(hits))
    else:
        hits = [object()]
    with pytest.raises(EngineConfigurationError):
        fuse(hits, (), (a, b, c), HybridConfig())


def test_child_failure_and_identity_mismatch_never_fallback(provider, monkeypatch):
    engine = HybridSearchEngine(DenseSearchEngine(provider))
    index = engine.build(chunks())
    def fail(*args):
        raise EngineUnavailable('synthetic child unavailable')
    monkeypatch.setattr(engine.dense, 'rank', fail)
    with pytest.raises(EngineUnavailable):
        engine.rank('alpha', index)
    monkeypatch.setattr(engine.lexical, 'version', 'foreign')
    with pytest.raises(EngineConfigurationError):
        engine.rank('alpha', index)


def test_index_mutation_empty_bounds_and_candidate_metadata(provider):
    candidate = HybridCandidate(provider)
    index = candidate.build([('a', '1', 'checksum', 'alpha')])
    assert len(candidate.engine.search('alpha', index)) == 1
    with pytest.raises(EngineConfigurationError):
        candidate.engine.rank('alpha', replace(index, config=replace(index.config, retrieval_depth=3)))
    with pytest.raises(EngineConfigurationError):
        candidate.engine.rank('alpha', replace(index, chunks=()))
    with pytest.raises(EngineConfigurationError):
        candidate.engine.search('alpha', index, True)
    with pytest.raises(EngineConfigurationError):
        candidate.engine.rank('a' * (MAX_QUERY_CHARS + 1), candidate.build(()))
    assert candidate.rank('alpha', candidate.build(())) == ()
    candidate.system['denseEngineConfigVersion'] = 'changed'
    with pytest.raises(EngineConfigurationError):
        candidate.build(())


@pytest.mark.dense_model
def test_real_hybrid_repeatable_and_shared_representation(real_provider):
    from hdpi.evaluation.candidates import DenseCandidate

    sources = [('b', '1', 'checksum', 'alpha beta'), ('a', '1', 'checksum', 'alpha gamma')]
    candidate = HybridCandidate(real_provider)
    first = candidate.build(sources)
    second = candidate.build(reversed(sources))
    assert first.chunks == DenseCandidate(real_provider).build(sources).chunks
    assert candidate.rank('alpha beta', first) == candidate.rank('alpha beta', second)
