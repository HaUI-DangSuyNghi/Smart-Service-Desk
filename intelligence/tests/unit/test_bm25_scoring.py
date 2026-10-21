import math

import pytest

from hdpi.retrieval.bm25.engine import Bm25SearchEngine, fit_chunks
from hdpi.retrieval.chunking import chunk_text


def make_index(*texts):
    return fit_chunks(tuple(chunk for i, text in enumerate(texts)
                            for chunk in chunk_text(text, source_id=str(i), source_state=('1',))))


def test_hand_computed_df_idf_and_tf_saturation():
    # Features: [alpha], [alpha, alpha, alpha alpha], [beta].
    # N=3, df(alpha)=2, avgdl=5/3. The bigram is a distinct feature.
    index = make_index('alpha', 'alpha alpha', 'beta')
    assert dict(index.document_frequencies) == {'alpha': 2, 'alpha alpha': 1, 'beta': 1}
    assert index.lengths == (1, 3, 1)
    assert index.average_length == 5 / 3
    scores = {hit.chunk.source_id: hit.score for hit in Bm25SearchEngine().rank('alpha', index)}
    idf = math.log(1 + 1.5 / 2.5)
    assert scores['0'] == pytest.approx(idf * 2.2 / (1 + 1.2 * (.25 + .75 * 1 / (5 / 3))))
    assert scores['1'] == pytest.approx(idf * 2 * 2.2 / (2 + 1.2 * (.25 + .75 * 3 / (5 / 3))))


def test_tf_saturation_at_equal_document_lengths():
    index = make_index('alpha beta gamma', 'alpha alpha gamma')
    assert index.lengths == (5, 5)
    scores = {hit.chunk.source_id: hit.score for hit in Bm25SearchEngine().rank('alpha', index)}
    assert scores['1'] / scores['0'] == pytest.approx(2 * 2.2 / 3.2)


def test_length_normalization_prefers_shorter_equal_tf_document():
    hits = Bm25SearchEngine().rank('alpha', make_index('alpha', 'alpha beta gamma'))
    assert [hit.chunk.source_id for hit in hits] == ['0', '1']
    assert hits[0].score > hits[1].score > 0


def test_repeated_query_terms_have_presence_semantics_and_bigrams_are_explicit():
    engine, index = Bm25SearchEngine(), make_index('alpha', 'alpha alpha', 'beta')
    assert engine.rank('alpha alpha', index) == engine.rank('alpha alpha alpha', index)
    assert engine.rank('alpha', index) != engine.rank('alpha alpha', index)


def test_idf_stays_positive_when_every_chunk_matches():
    hits = Bm25SearchEngine().rank('alpha', make_index('alpha', 'alpha'))
    assert len(hits) == 2
    assert hits[0].score == pytest.approx(math.log(1 + .5 / 2.5))
