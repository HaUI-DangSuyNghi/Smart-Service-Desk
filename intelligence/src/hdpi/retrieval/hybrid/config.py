from dataclasses import asdict, dataclass

from ...domain.errors import EngineConfigurationError
from ..bm25.config import Bm25Config
from ..dense.config import DenseConfig


@dataclass(frozen=True)
class HybridConfig:
    version: str = 'hybrid-rrf-config@0.1.0'
    algorithm: str = 'equal-weight-reciprocal-rank-fusion'
    constant: int = 60
    retrieval_depth: int = 20
    default_top_k: int = 3
    deduplication: str = 'source-first-hit-before-depth'
    representative: str = 'lowest-ordinal-then-chunk-id-of-contributors'
    tie_break: str = 'exact-fraction-desc-source-ordinal-chunk-asc'
    lexical_config: str = 'bm25-config@0.1.0'
    dense_config: str = 'dense-e5-cpu-config@0.1.0'

    def validate(self):
        expected = {
            'version': 'hybrid-rrf-config@0.1.0', 'algorithm': 'equal-weight-reciprocal-rank-fusion',
            'constant': 60, 'retrieval_depth': 20, 'default_top_k': 3,
            'deduplication': 'source-first-hit-before-depth',
            'representative': 'lowest-ordinal-then-chunk-id-of-contributors',
            'tie_break': 'exact-fraction-desc-source-ordinal-chunk-asc',
            'lexical_config': 'bm25-config@0.1.0', 'dense_config': 'dense-e5-cpu-config@0.1.0',
        }
        if asdict(self) != expected or any(type(getattr(self, key)) is not int for key in
                                         ('constant', 'retrieval_depth', 'default_top_k')):
            raise EngineConfigurationError('Unsupported hybrid configuration or unversioned behavior change')
        Bm25Config().validate()
        DenseConfig().validate()

    def system(self):
        self.validate()
        lexical, dense = Bm25Config().system(), DenseConfig().system()
        return dense | {
            'engine': 'hybrid', 'engineVersion': '0.1.0', 'engineConfigVersion': self.version,
            'lexicalEngine': lexical['engine'], 'lexicalEngineVersion': lexical['engineVersion'],
            'lexicalEngineConfigVersion': self.lexical_config,
            'denseEngine': dense['engine'], 'denseEngineVersion': dense['engineVersion'],
            'denseEngineConfigVersion': self.dense_config,
            'fusionAlgorithm': self.algorithm, 'fusionConstant': str(self.constant),
            'retrievalDepth': str(self.retrieval_depth), 'defaultTopK': str(self.default_top_k),
            'fusionDeduplication': self.deduplication,
            'numericalRuntime': dense['numericalRuntime'] + ';stdlib-fractions@3.12',
        }
