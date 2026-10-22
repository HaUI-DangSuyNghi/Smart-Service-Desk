"""Deterministic, in-process BM25 candidate over normalized authoring chunks."""
from collections import Counter
from dataclasses import dataclass
import math
import re

from ...domain.errors import EngineConfigurationError
from ..models import Chunk, MAX_CHUNKS, MAX_QUERY_CHARS, RankedHit
from .config import Bm25Config


def analyze(text: str, config: Bm25Config) -> tuple[str, ...]:
    # Same lexical features as TF-IDF: no extra normalization or stop words.
    words = re.findall(config.token_pattern, text)
    return tuple(words) + tuple(left + ' ' + right for left, right in zip(words, words[1:]))


@dataclass(frozen=True)
class Bm25Index:
    """Immutable derived state; confers no authorization or citation authority."""
    chunks: tuple[Chunk, ...]
    term_frequencies: tuple[tuple[tuple[str, int], ...], ...]
    document_frequencies: tuple[tuple[str, int], ...]
    lengths: tuple[int, ...]
    average_length: float
    config: Bm25Config


def fit_chunks(chunks: tuple[Chunk, ...], config: Bm25Config | None = None) -> Bm25Index:
    config = config or Bm25Config()
    config.validate()
    chunks = tuple(sorted(chunks, key=lambda c: (c.source_id, c.ordinal, c.chunk_id)))
    if len(chunks) > MAX_CHUNKS:
        raise EngineConfigurationError('Corpus exceeds chunk limit')
    if len({c.chunk_id for c in chunks}) != len(chunks):
        raise EngineConfigurationError('Duplicate chunk identity')
    frequencies, lengths, df = [], [], Counter()
    for chunk in chunks:
        counts = Counter(analyze(chunk.normalized_text, config))
        frequencies.append(tuple(sorted(counts.items())))
        lengths.append(sum(counts.values()))
        df.update(counts.keys())
    average = sum(lengths) / len(chunks) if chunks else 0.0
    return Bm25Index(chunks, tuple(frequencies), tuple(sorted(df.items())), tuple(lengths), average, config)


def term_score(tf: int, df: int, count: int, length: int, average: float, config: Bm25Config) -> float:
    idf = math.log1p((count - df + 0.5) / (df + 0.5))
    return idf * tf * (config.k1 + 1) / (tf + config.k1 * (1 - config.b + config.b * length / average))


class Bm25SearchEngine:
    name = 'bm25'
    version = '0.1.0'
    config_version = 'bm25-config@0.2.0'

    def __init__(self, config: Bm25Config | None = None):
        self.config = config or Bm25Config()
        self.config.validate()

    def search(self, query: str, index: Bm25Index, top_k: int) -> tuple[RankedHit, ...]:
        if type(top_k) is not int or not 1 <= top_k <= 10:
            raise EngineConfigurationError('top_k outside supported bounds')
        return self.rank(query, index)[:top_k]

    def rank(self, query: str, index: Bm25Index) -> tuple[RankedHit, ...]:
        self.config.validate()
        if not isinstance(index, Bm25Index) or index.config != self.config:
            raise EngineConfigurationError('BM25 index configuration mismatch')
        if not isinstance(query, str) or len(query) > MAX_QUERY_CHARS:
            raise EngineConfigurationError('Invalid query or internal resource limit exceeded')
        if not index.average_length:
            return ()
        query_terms = set(analyze(query, self.config))
        df = dict(index.document_frequencies)
        hits = []
        for chunk, frequencies, length in zip(index.chunks, index.term_frequencies, index.lengths):
            score = math.fsum(term_score(tf, df[term], len(index.chunks), length, index.average_length, self.config)
                              for term, tf in frequencies if term in query_terms)
            if not math.isfinite(score) or score < 0:
                raise EngineConfigurationError('Invalid BM25 ranking score')
            if score > 0:
                hits.append(RankedHit(chunk, score))
        hits.sort(key=lambda hit: (-hit.score, hit.chunk.source_id, hit.chunk.ordinal, hit.chunk.chunk_id))
        return tuple(hits)
