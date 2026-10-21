"""Minimal candidate protocol; engine-specific construction stays in its adapter."""
from typing import Protocol

from ..retrieval.chunking import chunk_text
from ..retrieval.models import CHUNK_POLICY, ENGINE_CONFIG, NORMALIZATION, NUMERICAL_RUNTIME
from ..retrieval.tfidf.engine import fit_chunks, TfidfSearchEngine
from ..domain.errors import EngineConfigurationError
from ..retrieval.bm25.config import Bm25Config
from ..retrieval.bm25.engine import Bm25SearchEngine, fit_chunks as fit_bm25
from ..retrieval.models import MAX_CORPUS_BYTES, MAX_DOCUMENTS, MAX_SOURCE_BYTES


class RetrievalCandidate(Protocol):
    system: dict

    def build(self, sources): ...

    def rank(self, query, index): ...


class TfidfCandidate:
    def __init__(self):
        self.engine = TfidfSearchEngine()
        self.system = {'engine': self.engine.name, 'engineVersion': self.engine.version,
                       'engineConfigVersion': ENGINE_CONFIG, 'normalizationVersion': NORMALIZATION,
                       'chunkPolicyVersion': CHUNK_POLICY, 'queryStrategyVersion': 'title+newline+description@0.1.0',
                       'numericalRuntime': NUMERICAL_RUNTIME}

    def build(self, sources):
        chunks = []
        for identifier, version, checksum, text in sources:
            chunks.extend(chunk_text(text, source_id=identifier, source_state=(version, checksum)))
        return fit_chunks(tuple(chunks))

    def rank(self, query, index):
        return tuple(hit.chunk.source_id for hit in self.engine.rank(query, index))


class Bm25Candidate:
    """Authoring-only challenger; production SearchService remains TF-IDF."""

    def __init__(self, config=None):
        self.config = config or Bm25Config()
        self.engine = Bm25SearchEngine(self.config)
        self.system = self.config.system()

    def _validate(self):
        if self.system != self.config.system() or self.engine.config != self.config:
            raise EngineConfigurationError('BM25 candidate identity differs from pinned configuration')
        if (self.engine.name, self.engine.version, self.engine.config_version) != (
                self.system['engine'], self.system['engineVersion'], self.system['engineConfigVersion']):
            raise EngineConfigurationError('BM25 engine identity mismatch')
        if (CHUNK_POLICY, NORMALIZATION) != (self.config.chunk_policy, self.config.normalization):
            raise EngineConfigurationError('BM25 preprocessing identity mismatch')

    def build(self, sources):
        self._validate()
        chunks, known, total = [], set(), 0
        for identifier, version, checksum, text in sources:
            if not isinstance(identifier, str) or not identifier.strip() or identifier in known:
                raise EngineConfigurationError('Duplicate or invalid authoring source identity')
            known.add(identifier)
            size = len(text.encode('utf-8'))
            total += size
            if len(known) > MAX_DOCUMENTS or size > MAX_SOURCE_BYTES or total > MAX_CORPUS_BYTES:
                raise EngineConfigurationError('Corpus exceeds internal resource limits')
            chunks.extend(chunk_text(text, source_id=identifier, source_state=(version, checksum)))
        return fit_bm25(tuple(chunks), self.config)

    def rank(self, query, index):
        self._validate()
        return tuple(hit.chunk.source_id for hit in self.engine.rank(query, index))


class DenseCandidate:
    """Local semantic experiment; no runtime registry or citation capability."""

    def __init__(self, provider=None, *, model_directory=None, config=None):
        from ..retrieval.dense.config import DenseConfig
        from ..retrieval.dense.engine import DenseSearchEngine
        from ..retrieval.dense.provider import OnnxEmbeddingProvider

        self.config = config or DenseConfig()
        self.config.validate()
        self.engine = DenseSearchEngine(provider if provider is not None else
                                       OnnxEmbeddingProvider(model_directory, self.config), self.config)
        self.system = self.config.system()

    def _validate(self):
        if (self.system != self.config.system() or self.engine.config != self.config
                or (self.engine.name, self.engine.version, self.engine.config_version) !=
                (self.system['engine'], self.system['engineVersion'], self.system['engineConfigVersion'])
                or (CHUNK_POLICY, NORMALIZATION) != (self.config.chunk_policy, self.config.normalization)):
            raise EngineConfigurationError('Dense candidate or preprocessing identity mismatch')

    def build(self, sources):
        self._validate()
        chunks, known, total = [], set(), 0
        for identifier, version, checksum, text in sources:
            if not isinstance(identifier, str) or not identifier.strip() or identifier in known:
                raise EngineConfigurationError('Duplicate or invalid authoring source identity')
            known.add(identifier)
            size = len(text.encode('utf-8'))
            total += size
            if len(known) > MAX_DOCUMENTS or size > MAX_SOURCE_BYTES or total > MAX_CORPUS_BYTES:
                raise EngineConfigurationError('Corpus exceeds internal resource limits')
            chunks.extend(chunk_text(text, source_id=identifier, source_state=(version, checksum)))
        return self.engine.build(chunks)

    def rank(self, query, index):
        self._validate()
        return tuple(hit.chunk.source_id for hit in self.engine.rank(query, index))


def create_candidate(name, *, model_directory=None):
    if name == 'dense':
        return DenseCandidate(model_directory=model_directory)
    if model_directory is not None:
        raise EngineConfigurationError('Model directory is only supported for dense evaluation')
    if name == 'bm25':
        return Bm25Candidate()
    if name == 'tfidf':
        return TfidfCandidate()
    raise EngineConfigurationError('Unknown retrieval candidate')
