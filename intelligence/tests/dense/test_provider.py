from hashlib import sha256

import pytest

from hdpi.domain.errors import EngineConfigurationError, EngineUnavailable
from hdpi.retrieval.dense.provider import read_artifact
from hdpi.retrieval.dense.config import DenseConfig
from hdpi.retrieval.dense.provider import OnnxEmbeddingProvider


def test_exact_artifact_bytes_and_corrupt_or_missing_files(tmp_path):
    raw = b'authored-artifact-for-test'
    path = tmp_path / 'model.onnx'
    path.write_bytes(raw)
    checksum = sha256(raw).hexdigest()
    assert read_artifact(tmp_path, 'model.onnx', len(raw), checksum) == raw
    with pytest.raises(EngineConfigurationError, match='size'):
        read_artifact(tmp_path, 'model.onnx', len(raw) + 1, checksum)
    with pytest.raises(EngineConfigurationError, match='checksum'):
        read_artifact(tmp_path, 'model.onnx', len(raw), '0' * 64)
    with pytest.raises(EngineUnavailable):
        read_artifact(tmp_path, 'missing.onnx', len(raw), checksum)
    with pytest.raises(EngineConfigurationError, match='escapes'):
        read_artifact(tmp_path, '../model.onnx', len(raw), checksum)


def test_real_provider_runs_offline_with_pinned_dimension(real_provider, monkeypatch):
    import socket
    import urllib.request
    from hdpi.retrieval.dense.index import normalize_vector
    from hdpi.retrieval.models import digest

    def forbidden(*args, **kwargs):
        pytest.fail('Inference attempted network access')
    monkeypatch.setattr(socket.socket, 'connect', forbidden)
    monkeypatch.setattr(urllib.request, 'urlopen', forbidden)
    first = real_provider.encode_query('reset a forgotten password')
    assert len(first) == 384
    assert digest(first) == digest(real_provider.encode_query('reset a forgotten password'))
    assert normalize_vector(first, 384)


def synthetic_provider(hidden):
    from types import SimpleNamespace

    provider = OnnxEmbeddingProvider.__new__(OnnxEmbeddingProvider)
    provider.config = DenseConfig()
    provider._inputs = ('input_ids', 'attention_mask', 'token_type_ids')
    provider._session = SimpleNamespace(run=lambda names, feed: [hidden])
    class Tokenizer:
        def encode(self, text, **options):
            self.text = text
            return SimpleNamespace(ids=[0, 2, 1], attention_mask=[1, 1, 0], type_ids=[0, 0, 0])
    provider._tokenizer = Tokenizer()
    return provider


def test_attention_mask_pooling_and_model_prefixes():
    import numpy as np

    hidden = np.zeros((1, 3, 384), dtype=np.float32)
    hidden[0, :, 0] = [2, 4, 100]
    provider = synthetic_provider(hidden)
    assert provider.encode_query('text')[0] == 3
    assert provider._tokenizer.text == 'query: text'
    assert provider.encode_documents(['text'])[0][0] == 3
    assert provider._tokenizer.text == 'passage: text'


@pytest.mark.parametrize('kind', ['dimension', 'nonfinite', 'dtype'])
def test_invalid_model_tensor_rejected(kind):
    import numpy as np

    hidden = np.zeros((1, 3, 384 if kind != 'dimension' else 128), dtype=np.float32)
    if kind == 'nonfinite':
        hidden[0, 0, 0] = float('nan')
    if kind == 'dtype':
        hidden = hidden.astype(np.float64)
    with pytest.raises(EngineConfigurationError):
        synthetic_provider(hidden).encode_query('text')
