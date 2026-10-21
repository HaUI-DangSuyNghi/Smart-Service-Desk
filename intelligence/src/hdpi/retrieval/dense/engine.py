import math

from ...domain.errors import EngineConfigurationError, EngineUnavailable
from ..models import MAX_CHUNKS, MAX_QUERY_CHARS, RankedHit
from .config import DenseConfig, provider_identity
from .index import DenseIndex, normalize_vector


class DenseSearchEngine:
    name = 'dense'
    version = '0.1.0'
    config_version = 'dense-e5-cpu-config@0.1.0'

    def __init__(self, provider, config=None):
        self.config = config or DenseConfig()
        self.config.validate()
        self.provider = provider
        provider_identity(provider)

    def build(self, chunks):
        self.config.validate()
        identity = provider_identity(self.provider)
        chunks = tuple(sorted(chunks, key=lambda c: (c.source_id, c.ordinal, c.chunk_id)))
        if len(chunks) > MAX_CHUNKS or len({c.chunk_id for c in chunks}) != len(chunks):
            raise EngineConfigurationError('Duplicate chunk identity or corpus chunk limit exceeded')
        if not chunks:
            return DenseIndex((), (), self.config, identity)
        try:
            encoded = tuple(self.provider.encode_documents(tuple(c.normalized_text for c in chunks)))
        except (EngineUnavailable, EngineConfigurationError):
            raise
        except Exception:
            raise EngineUnavailable('Document embedding failed') from None
        if len(encoded) != len(chunks):
            raise EngineConfigurationError('Embedding count differs from chunk count')
        vectors = tuple(normalize_vector(vector, self.config.dimensions) for vector in encoded)
        return DenseIndex(chunks, vectors, self.config, identity)

    def search(self, query, index, top_k):
        if type(top_k) is not int or not 1 <= top_k <= 10:
            raise EngineConfigurationError('top_k outside supported bounds')
        return self.rank(query, index)[:top_k]

    def rank(self, query, index):
        self.config.validate()
        identity = provider_identity(self.provider)
        if not isinstance(index, DenseIndex) or index.config != self.config or index.provider_identity != identity:
            raise EngineConfigurationError('Dense index configuration/model mismatch')
        if not isinstance(query, str) or len(query) > MAX_QUERY_CHARS:
            raise EngineConfigurationError('Invalid query or internal resource limit exceeded')
        if not index.chunks or not any(char.isalnum() for char in query):
            return ()
        try:
            encoded = self.provider.encode_query(query)
        except (EngineUnavailable, EngineConfigurationError):
            raise
        except Exception:
            raise EngineUnavailable('Query embedding failed') from None
        vector = normalize_vector(encoded, self.config.dimensions)
        hits = []
        for chunk, document in zip(index.chunks, index.vectors):
            score = math.fsum(a * b for a, b in zip(vector, document))
            if not math.isfinite(score) or not -1 - 1e-12 <= score <= 1 + 1e-12:
                raise EngineConfigurationError('Invalid dense similarity score')
            if score > 0:
                hits.append(RankedHit(chunk, score))
        hits.sort(key=lambda hit: (-hit.score, hit.chunk.source_id, hit.chunk.ordinal, hit.chunk.chunk_id))
        return tuple(hits)
