from dataclasses import FrozenInstanceError, replace

import pytest

from hdpi.domain.errors import EngineConfigurationError, EngineUnavailable
from hdpi.retrieval.dense import config as module
from hdpi.retrieval.dense.artifacts import ARTIFACT_DIGEST, FILES, MODEL, REVISION
from hdpi.retrieval.dense.config import DenseConfig, check_dependencies, provider_identity


@pytest.mark.parametrize('change', [
    {'version': 'other'}, {'dimensions': 128}, {'max_tokens': 256}, {'batch_size': 8},
    {'similarity': 'dot'}, {'pooling': 'cls'}, {'zero_vector': 'allow'},
    {'query_prefix': ''}, {'document_prefix': ''}, {'model_dtype': 'float16'},
    {'vector_dtype': 'float32'}, {'execution': 'gpu'}, {'normalization': 'changed'},
    {'chunk_policy': 'changed'}, {'query_strategy': 'changed'},
])
def test_unversioned_config_changes_rejected(change):
    with pytest.raises(EngineConfigurationError):
        replace(DenseConfig(), **change).validate()


def test_frozen_model_and_config_identity():
    config = DenseConfig()
    with pytest.raises(FrozenInstanceError):
        config.dimensions = 2
    system = config.system()
    assert system['engine'] == 'dense'
    assert system['modelVersion'] == MODEL + '@' + REVISION
    assert system['modelArtifactDigest'] == ARTIFACT_DIGEST
    assert REVISION == '614241f622f53c4eeff9890bdc4f31cfecc418b3'
    assert FILES[0][2] == 'ca456c06b3a9505ddfd9131408916dd79290368331e7d76bb621f1cba6bc8665'


@pytest.mark.parametrize('field', ['name', 'model_version', 'artifact_digest', 'dimensions'])
def test_foreign_model_provider_rejected(provider, field):
    setattr(provider, field, 'changed')
    with pytest.raises(EngineConfigurationError):
        provider_identity(provider)


def test_wrong_or_missing_dependencies_fail_closed(monkeypatch):
    monkeypatch.setattr(module, 'version', lambda package: 'unknown')
    with pytest.raises(EngineConfigurationError):
        check_dependencies()
    def missing(package):
        raise module.PackageNotFoundError(package)
    monkeypatch.setattr(module, 'version', missing)
    with pytest.raises(EngineUnavailable):
        check_dependencies()
