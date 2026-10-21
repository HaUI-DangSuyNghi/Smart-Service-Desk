from dataclasses import dataclass, field
from fractions import Fraction
import math

from ...domain.errors import EngineConfigurationError, EngineUnavailable
from ..bm25.config import Bm25Config
from ..bm25.engine import Bm25Index, Bm25SearchEngine, fit_chunks
from ..dense.config import DenseConfig, provider_identity
from ..dense.index import DenseIndex
from ..models import Chunk, MAX_CHUNKS, MAX_QUERY_CHARS, RankedHit
from .config import HybridConfig


@dataclass(frozen=True)
class HybridIndex:
    chunks: tuple[Chunk, ...] = field(repr=False)
    lexical: Bm25Index = field(repr=False)
    dense: DenseIndex = field(repr=False)
    config: HybridConfig


def document_ranking(hits, chunks, depth):
    """Validate the full child ranking, then deduplicate documents before depth."""
    known = {chunk.chunk_id: chunk for chunk in chunks}
    seen_chunks, seen_sources, documents, previous = set(), set(), [], None
    for hit in hits:
        if (not isinstance(hit, RankedHit) or hit.chunk.chunk_id not in known
                or known[hit.chunk.chunk_id] != hit.chunk or hit.chunk.chunk_id in seen_chunks
                or not math.isfinite(hit.score) or hit.score <= 0):
            raise EngineConfigurationError('Invalid, duplicate or foreign child candidate')
        key = (-hit.score, hit.chunk.source_id, hit.chunk.ordinal, hit.chunk.chunk_id)
        if previous is not None and key < previous:
            raise EngineConfigurationError('Child ranking is not in deterministic score order')
        previous = key
        seen_chunks.add(hit.chunk.chunk_id)
        if hit.chunk.source_id not in seen_sources:
            seen_sources.add(hit.chunk.source_id)
            if len(documents) < depth:
                documents.append(hit.chunk)
    return tuple(documents)


def fuse(lexical_hits, dense_hits, chunks, config):
    config.validate()
    if type(chunks) is not tuple or len(chunks) > MAX_CHUNKS or len({c.chunk_id for c in chunks}) != len(chunks):
        raise EngineConfigurationError('Invalid hybrid chunk association')
    scores, representatives = {}, {}
    for hits in (lexical_hits, dense_hits):
        documents = document_ranking(hits, chunks, config.retrieval_depth)
        for rank, chunk in enumerate(documents, 1):
            source = chunk.source_id
            scores[source] = scores.get(source, Fraction()) + Fraction(1, config.constant + rank)
            prior = representatives.get(source, chunk)
            representatives[source] = min((prior, chunk), key=lambda c: (c.ordinal, c.chunk_id))
    ordered = sorted(scores, key=lambda source: (-scores[source], source,
                     representatives[source].ordinal, representatives[source].chunk_id))
    return tuple(RankedHit(representatives[source], float(scores[source])) for source in ordered)


class HybridSearchEngine:
    name = 'hybrid'
    version = '0.1.0'
    config_version = 'hybrid-rrf-config@0.1.0'

    def __init__(self, dense_engine, *, lexical_engine=None, config=None):
        self.config = config or HybridConfig()
        self.lexical = lexical_engine if lexical_engine is not None else Bm25SearchEngine()
        self.dense = dense_engine
        self._validate()

    def _validate(self):
        self.config.validate()
        for engine, expected, config in (
            (self.lexical, ('bm25', '0.1.0', self.config.lexical_config), Bm25Config()),
            (self.dense, ('dense', '0.1.0', self.config.dense_config), DenseConfig()),
        ):
            if ((engine.name, engine.version, engine.config_version) != expected or engine.config != config):
                raise EngineConfigurationError('Hybrid child engine/config identity mismatch')
        provider_identity(self.dense.provider)

    def build(self, chunks, *, dense_index=None):
        self._validate()
        lexical = fit_chunks(tuple(chunks), self.lexical.config)
        dense = dense_index if dense_index is not None else self.dense.build(lexical.chunks)
        index = HybridIndex(lexical.chunks, lexical, dense, self.config)
        self._validate_index(index)
        return index

    def _validate_index(self, index):
        if (not isinstance(index, HybridIndex) or index.config != self.config
                or not isinstance(index.lexical, Bm25Index) or not isinstance(index.dense, DenseIndex)
                or index.lexical.config != self.lexical.config or index.dense.config != self.dense.config
                or index.dense.provider_identity != provider_identity(self.dense.provider)
                or index.chunks != index.lexical.chunks or index.chunks != index.dense.chunks):
            raise EngineConfigurationError('Hybrid index/configuration/child corpus mismatch')

    def search(self, query, index, top_k=3):
        if type(top_k) is not int or not 1 <= top_k <= 10:
            raise EngineConfigurationError('top_k outside supported bounds')
        return self.rank(query, index)[:top_k]

    def rank(self, query, index):
        self._validate()
        self._validate_index(index)
        if not isinstance(query, str) or len(query) > MAX_QUERY_CHARS:
            raise EngineConfigurationError('Invalid query or internal resource limit exceeded')
        try:
            lexical = self.lexical.rank(query, index.lexical)
            dense = self.dense.rank(query, index.dense)
            self._validate()
            return fuse(lexical, dense, index.chunks, self.config)
        except (EngineUnavailable, EngineConfigurationError):
            raise
        except Exception:
            raise EngineUnavailable('Hybrid child retrieval failed; no fallback is permitted') from None
