"""Frozen BM25 behavior. A behavior change requires a new configuration identity."""
from dataclasses import asdict, dataclass
import sys
import unicodedata

from ...domain.errors import EngineConfigurationError


@dataclass(frozen=True)
class Bm25Config:
    version: str = 'bm25-config@0.2.0'
    algorithm: str = 'okapi-bm25-log1p-idf'
    k1: float = 1.2
    b: float = 0.75
    token_pattern: str = r'(?u)\b\w\w+\b'
    ngram_range: tuple[int, int] = (1, 2)
    query_terms: str = 'unique-presence'
    length: str = 'unigram-and-bigram-count'
    normalization: str = 'text-normalization@0.2.0'
    chunk_policy: str = 'markdown-paragraph@0.1.0'
    query_strategy: str = 'caller-provided-query@0.1.0'
    numerical_runtime: str = 'cpython@3.12;unicode@15.0.0;stdlib-math@3.12'

    def validate(self):
        # Deliberately independent of dataclass defaults: editing a default must
        # not silently change the meaning of the published config identity.
        expected = {
            'version': 'bm25-config@0.2.0', 'algorithm': 'okapi-bm25-log1p-idf',
            'k1': 1.2, 'b': 0.75, 'token_pattern': r'(?u)\b\w\w+\b',
            'ngram_range': (1, 2), 'query_terms': 'unique-presence',
            'length': 'unigram-and-bigram-count',
            'normalization': 'text-normalization@0.2.0',
            'chunk_policy': 'markdown-paragraph@0.1.0',
            'query_strategy': 'caller-provided-query@0.1.0',
            'numerical_runtime': 'cpython@3.12;unicode@15.0.0;stdlib-math@3.12',
        }
        if asdict(self) != expected:
            raise EngineConfigurationError('Unsupported BM25 configuration or unversioned behavior change')
        if (sys.implementation.name, sys.version_info[:2], unicodedata.unidata_version) != (
                'cpython', (3, 12), '15.0.0'):
            raise EngineConfigurationError('BM25 requires the pinned CPython / Unicode runtime')

    def system(self):
        self.validate()
        return {'engine': 'bm25', 'engineVersion': '0.1.0',
                'engineConfigVersion': self.version, 'normalizationVersion': self.normalization,
                'chunkPolicyVersion': self.chunk_policy, 'queryStrategyVersion': self.query_strategy,
                'numericalRuntime': self.numerical_runtime}
