from dataclasses import replace
import math

import pytest

from hdpi.domain.errors import EngineConfigurationError, EngineUnavailable
from hdpi.retrieval.chunking import chunk_text
from hdpi.retrieval.dense.engine import DenseSearchEngine
from hdpi.retrieval.dense.index import normalize_vector
from hdpi.retrieval.models import MAX_CHUNKS, MAX_QUERY_CHARS


def chunks():
    return tuple(chunk_text('alpha', source_id=source, source_state=('1',))[0] for source in ('a', 'b', 'c'))


def vec(*values):
    return tuple(values) + (0,) * (384 - len(values))


def test_hand_computed_cosine_and_nonpositive_filter(provider):
    provider.documents = (vec(3, 4), vec(1, 0), vec(-1, 0))
    engine = DenseSearchEngine(provider)
    index = engine.build(chunks())
    hits = engine.rank('alpha', index)
    assert [hit.chunk.source_id for hit in hits] == ['b', 'a']
    assert [hit.score for hit in hits] == pytest.approx([1, .6])
    provider.query = vec(0, 1)
    assert [hit.score for hit in engine.rank('alpha', index)] == pytest.approx([.8])
    assert 'vectors' not in repr(index) and 'alpha' not in repr(index)


def test_canonical_build_and_all_tie_break_fields(provider):
    original = chunks()[0]
    expected = tuple(replace(original, source_id=source, ordinal=ordinal, chunk_id=identity)
                     for source, ordinal, identity in [('a', 0, 'a'), ('a', 0, 'b'), ('a', 1, 'c'), ('b', 0, 'd')])
    engine = DenseSearchEngine(provider)
    first, second = engine.build(expected), engine.build(reversed(expected))
    assert first == second
    assert tuple(hit.chunk for hit in engine.rank('alpha', first)) == expected


@pytest.mark.parametrize('values', [(), (1,), (0,) * 384, vec(float('nan')), vec(float('inf')),
                                    vec(float('-inf')), vec(True), vec('bad'), None])
def test_invalid_vectors_rejected(values):
    with pytest.raises(EngineConfigurationError):
        normalize_vector(values, 384)


def test_large_finite_vector_normalizes_without_square_overflow():
    assert math.hypot(*normalize_vector(vec(1e300, 1e300), 384)) == pytest.approx(1)


@pytest.mark.parametrize('top_k', [0, 11, -1, True, 1.5, '3', None])
def test_invalid_topk_before_empty_index(provider, top_k):
    engine = DenseSearchEngine(provider)
    with pytest.raises(EngineConfigurationError):
        engine.search('', engine.build(()), top_k)


def test_empty_corpus_and_query_skip_inference(provider):
    engine = DenseSearchEngine(provider)
    assert engine.rank('alpha', engine.build(())) == ()
    assert provider.document_calls == provider.query_calls == 0
    index = engine.build(chunks())
    for query in ('', '   ', '!!!'):
        assert engine.rank(query, index) == ()
    assert provider.query_calls == 0
    for query in (None, 'a' * (MAX_QUERY_CHARS + 1)):
        with pytest.raises(EngineConfigurationError):
            engine.rank(query, engine.build(()))


def test_index_and_model_mismatch_duplicate_chunks_and_count(provider):
    engine = DenseSearchEngine(provider)
    index = engine.build(chunks())
    for foreign in (object(), replace(index, provider_identity=('foreign',))):
        with pytest.raises(EngineConfigurationError):
            engine.rank('alpha', foreign)
    with pytest.raises(EngineConfigurationError):
        engine.build((chunks()[0],) * 2)
    with pytest.raises(EngineConfigurationError):
        engine.build((chunks()[0],) * (MAX_CHUNKS + 1))
    provider.documents = ()
    with pytest.raises(EngineConfigurationError):
        engine.build(chunks())


def test_query_and_document_encoding_errors_do_not_fallback(provider):
    engine = DenseSearchEngine(provider)
    index = engine.build(chunks())
    def fail(*args):
        raise RuntimeError('synthetic protected input must not appear in diagnostics')
    provider.encode_query = fail
    with pytest.raises(EngineUnavailable, match='Query embedding failed') as error:
        engine.rank('alpha', index)
    assert error.value.__suppress_context__
    provider.encode_documents = fail
    with pytest.raises(EngineUnavailable, match='Document embedding failed'):
        engine.build(chunks())


@pytest.mark.parametrize('query_vector', [vec(0), vec(float('nan')), (1,)])
def test_query_vector_failures_do_not_return_scores(provider, query_vector):
    engine = DenseSearchEngine(provider)
    index = engine.build(chunks())
    provider.query = query_vector
    with pytest.raises(EngineConfigurationError):
        engine.rank('alpha', index)
