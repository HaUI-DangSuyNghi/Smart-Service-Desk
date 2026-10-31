from dataclasses import dataclass

import numpy as np
from scipy.sparse import csr_matrix
from sklearn.feature_extraction.text import TfidfVectorizer

from ...domain.errors import EngineConfigurationError
from ..models import Chunk, ENGINE, ENGINE_CONFIG, ENGINE_VERSION, MAX_QUERY_CHARS, RankedHit
from .config import check_numerical_runtime, vectorizer_options


@dataclass(frozen=True)
class LexicalIndex:
    """Pure derived state, also used by authoring evaluation; no citation authority."""
    chunks: tuple[Chunk, ...]
    vectorizer: TfidfVectorizer | None
    matrix: csr_matrix


def fit_chunks(chunks: tuple[Chunk, ...]) -> LexicalIndex:
    check_numerical_runtime()
    if len({c.chunk_id for c in chunks}) != len(chunks):
        raise EngineConfigurationError('Duplicate chunk identity')
    vectorizer = TfidfVectorizer(**vectorizer_options())
    # Empty/punctuation-only sources are legitimate, with no lexical vectors.
    analyzer = vectorizer.build_analyzer()
    if not any(analyzer(c.normalized_text) for c in chunks):
        return LexicalIndex(chunks, None, csr_matrix((len(chunks), 0), dtype=np.float64))
    matrix = vectorizer.fit_transform(c.normalized_text for c in chunks).tocsr()
    if not np.isfinite(matrix.data).all():
        raise EngineConfigurationError('Non-finite TF-IDF matrix')
    return LexicalIndex(chunks, vectorizer, matrix)


class TfidfSearchEngine:
    name = ENGINE
    version = ENGINE_VERSION
    config_version = ENGINE_CONFIG

    def search(self, query: str, index, top_k: int) -> tuple[RankedHit, ...]:
        if type(top_k) is not int or not 1 <= top_k <= 10:
            raise EngineConfigurationError('top_k outside supported bounds')
        return self.rank(query, index)[:top_k]

    def rank(self, query: str, index) -> tuple[RankedHit, ...]:
        """Internal complete ranking for document-level evaluation, no wire output."""
        if len(query) > MAX_QUERY_CHARS:
            raise EngineConfigurationError('Query exceeds internal resource limit')
        lexical = getattr(index, 'lexical', index)
        if lexical.vectorizer is None:
            return ()
        query_vector = lexical.vectorizer.transform([query])
        scores = (lexical.matrix @ query_vector.T).toarray().ravel()
        if not np.isfinite(scores).all():
            raise EngineConfigurationError('Non-finite ranking score')
        hits = [RankedHit(chunk, float(score)) for chunk, score in zip(lexical.chunks, scores) if score > 0]
        hits.sort(key=lambda h: (-h.score, h.chunk.source_id, h.chunk.ordinal, h.chunk.chunk_id))
        return tuple(hits)
