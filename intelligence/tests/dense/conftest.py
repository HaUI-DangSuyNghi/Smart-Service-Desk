import os

import pytest

from hdpi.retrieval.dense.artifacts import ARTIFACT_DIGEST, MODEL, REVISION


def vector(first=1, second=0):
    return (first, second) + (0,) * 382


class FakeEmbeddings:
    """Synthetic vectors for math/error tests only; never used by a shipping CLI."""
    name = 'onnx-e5-cpu'
    model_version = MODEL + '@' + REVISION
    artifact_digest = ARTIFACT_DIGEST
    dimensions = 384

    def __init__(self):
        self.documents = None
        self.query = vector()
        self.document_calls = self.query_calls = 0

    def encode_documents(self, texts):
        self.document_calls += 1
        return self.documents if self.documents is not None else tuple(vector() for _ in texts)

    def encode_query(self, text):
        self.query_calls += 1
        return self.query


@pytest.fixture
def provider():
    return FakeEmbeddings()


@pytest.fixture(scope='session')
def real_provider():
    directory = os.environ.get('HDPI_DENSE_MODEL_ROOT')
    if not directory:
        if os.environ.get('HDPI_REQUIRE_DENSE_MODEL') == '1':
            pytest.fail('The dense reference CI path requires explicit pinned model artifacts')
        pytest.skip('Optional real-model checks require HDPI_DENSE_MODEL_ROOT')
    from unittest.mock import patch
    import socket
    import urllib.request
    from hdpi.retrieval.dense.provider import OnnxEmbeddingProvider

    def forbidden(*args, **kwargs):
        pytest.fail('Offline provider attempted network access')

    # Proves both model construction and subsequent inference work offline.
    with patch.object(socket.socket, 'connect', forbidden), patch.object(urllib.request, 'urlopen', forbidden):
        provider = OnnxEmbeddingProvider(directory)
    return provider
