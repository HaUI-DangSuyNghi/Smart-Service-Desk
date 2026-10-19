"""Minimal candidate protocol; engine-specific construction stays in its adapter."""
from typing import Protocol

from ..retrieval.chunking import chunk_text
from ..retrieval.models import CHUNK_POLICY, ENGINE_CONFIG, NORMALIZATION, NUMERICAL_RUNTIME
from ..retrieval.tfidf.engine import fit_chunks, TfidfSearchEngine


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
