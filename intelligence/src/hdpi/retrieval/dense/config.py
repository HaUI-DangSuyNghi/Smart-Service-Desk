from dataclasses import asdict, dataclass
from importlib.metadata import PackageNotFoundError, version

from ...domain.errors import EngineConfigurationError, EngineUnavailable
from ...normalization.v1 import NormalizerV1
from .artifacts import ARTIFACT_DIGEST, MODEL, REVISION


@dataclass(frozen=True)
class DenseConfig:
    version: str = 'dense-e5-cpu-config@0.2.0'
    dimensions: int = 384
    similarity: str = 'cosine-f64-fsum-positive-only'
    pooling: str = 'attention-mask-mean-f64'
    zero_vector: str = 'reject'
    max_tokens: int = 512
    batch_size: int = 1
    query_prefix: str = 'query: '
    document_prefix: str = 'passage: '
    model_dtype: str = 'float32'
    vector_dtype: str = 'float64'
    execution: str = 'cpu-sequential-1-thread-no-graph-optimization'
    normalization: str = 'text-normalization@0.2.0'
    chunk_policy: str = 'markdown-paragraph@0.1.0'
    query_strategy: str = 'caller-provided-query@0.1.0'

    def validate(self):
        expected = {
            'version': 'dense-e5-cpu-config@0.2.0', 'dimensions': 384,
            'similarity': 'cosine-f64-fsum-positive-only', 'pooling': 'attention-mask-mean-f64',
            'zero_vector': 'reject', 'max_tokens': 512, 'batch_size': 1,
            'query_prefix': 'query: ', 'document_prefix': 'passage: ',
            'model_dtype': 'float32', 'vector_dtype': 'float64',
            'execution': 'cpu-sequential-1-thread-no-graph-optimization',
            'normalization': 'text-normalization@0.2.0',
            'chunk_policy': 'markdown-paragraph@0.1.0',
            'query_strategy': 'caller-provided-query@0.1.0',
        }
        if asdict(self) != expected:
            raise EngineConfigurationError('Unsupported dense configuration or unversioned behavior change')
        NormalizerV1()  # Enforces the inherited Python/Unicode reference identity.

    def system(self):
        self.validate()
        return {'engine': 'dense', 'engineVersion': '0.1.0', 'engineConfigVersion': self.version,
                'normalizationVersion': self.normalization, 'chunkPolicyVersion': self.chunk_policy,
                'queryStrategyVersion': self.query_strategy, 'modelVersion': MODEL + '@' + REVISION,
                'modelArtifactDigest': ARTIFACT_DIGEST,
                'numericalRuntime': 'onnxruntime@1.20.1;tokenizers@0.21.0;numpy@2.2.6;cpython@3.12;unicode@15.0.0'}


def check_dependencies():
    try:
        for package, pin in (('onnxruntime', '1.20.1'), ('tokenizers', '0.21.0'), ('numpy', '2.2.6')):
            if version(package) != pin:
                raise EngineConfigurationError('Dense numerical runtime differs from pinned configuration')
    except PackageNotFoundError:
        raise EngineUnavailable('Install the locked optional dense dependencies') from None


def provider_identity(provider):
    expected = ('onnx-e5-cpu', MODEL + '@' + REVISION, ARTIFACT_DIGEST, 384)
    observed = tuple(getattr(provider, key, None) for key in ('name', 'model_version', 'artifact_digest', 'dimensions'))
    if observed != expected:
        raise EngineConfigurationError('Embedding provider model/artifact identity mismatch')
    return observed
