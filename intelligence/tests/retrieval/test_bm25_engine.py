from dataclasses import replace

import pytest

from hdpi.domain.errors import EngineConfigurationError
from hdpi.retrieval.bm25 import engine as module
from hdpi.retrieval.bm25.engine import Bm25SearchEngine, fit_chunks
from hdpi.retrieval.chunking import chunk_text
from hdpi.retrieval.models import MAX_CHUNKS, MAX_QUERY_CHARS


def chunks(text='alpha'):
    return chunk_text(text, source_id='a', source_state=('1',))


@pytest.mark.parametrize('text', ['', '!!!', 'a'])
def test_empty_vocabulary_and_corpus(text):
    engine = Bm25SearchEngine()
    assert engine.search('alpha', fit_chunks(chunks(text)), 10) == ()
    assert engine.search('alpha', fit_chunks(()), 10) == ()


@pytest.mark.parametrize('query', ['', '!!!', 'unmatched'])
def test_no_zero_score_fillers(query):
    assert Bm25SearchEngine().search(query, fit_chunks(chunks()), 10) == ()


@pytest.mark.parametrize('top_k', [0, -1, 11, True, 1.5, '3', None])
def test_invalid_topk_fails_even_with_empty_index(top_k):
    with pytest.raises(EngineConfigurationError):
        Bm25SearchEngine().search('', fit_chunks(()), top_k)


@pytest.mark.parametrize('query', ['a' * (MAX_QUERY_CHARS + 1), None, 1])
def test_query_bounds_apply_before_empty_index(query):
    with pytest.raises(EngineConfigurationError):
        Bm25SearchEngine().rank(query, fit_chunks(()))


def test_duplicate_and_excess_chunks_rejected():
    chunk = chunks()[0]
    with pytest.raises(EngineConfigurationError, match='Duplicate'):
        fit_chunks((chunk, chunk))
    with pytest.raises(EngineConfigurationError, match='chunk limit'):
        fit_chunks((chunk,) * (MAX_CHUNKS + 1))


@pytest.mark.parametrize('score', [float('nan'), float('inf'), float('-inf'), -1])
def test_invalid_scores_fail_closed(monkeypatch, score):
    monkeypatch.setattr(module, 'term_score', lambda *args: score)
    with pytest.raises(EngineConfigurationError):
        Bm25SearchEngine().rank('alpha', fit_chunks(chunks()))


def test_zero_score_is_omitted(monkeypatch):
    monkeypatch.setattr(module, 'term_score', lambda *args: 0)
    assert Bm25SearchEngine().rank('alpha', fit_chunks(chunks())) == ()


def test_foreign_index_and_config_rejected():
    engine, index = Bm25SearchEngine(), fit_chunks(chunks())
    with pytest.raises(EngineConfigurationError):
        engine.rank('alpha', object())
    with pytest.raises(EngineConfigurationError):
        engine.rank('alpha', replace(index, config=replace(index.config, b=0)))


def test_all_tie_break_fields_and_topk():
    original = chunks()[0]
    ordered = tuple(replace(original, source_id=source, ordinal=ordinal, chunk_id=identity)
                    for source, ordinal, identity in [('a', 0, 'a'), ('a', 0, 'b'), ('a', 1, 'c'), ('b', 0, 'd')])
    engine, index = Bm25SearchEngine(), fit_chunks(tuple(reversed(ordered)))
    assert tuple(hit.chunk for hit in engine.rank('alpha', index)) == ordered
    assert len(engine.search('alpha', index, 1)) == 1
