from hashlib import sha256

import pytest

from hdpi_intelligence.domain.models import TrustedContext
from hdpi_intelligence.retrieval.index import build_index
from hdpi_intelligence.retrieval.models import AuthorizedDocument, PartitionKey, scope_hash


@pytest.fixture
def search_context():
    return TrustedContext(workspace_id='test-workspace', subject='test-user', auth_scope_fingerprint='test-scope@1')


@pytest.fixture
def partition(search_context):
    return PartitionKey(workspace_id=search_context.workspace_id, profile_id='service-desk', profile_version='0.2.0',
                        access_scope_hash=scope_hash(search_context), corpus_version='test-corpus@1')


@pytest.fixture
def documents(search_context):
    # Fake Core IDs ONLY for tests; never written into repository fixture manifests.
    result = []
    for identifier, text in [('11111111-1111-4111-8111-111111111111', '# Tài khoản\n\nĐăng nhập và mật khẩu.'),
                             ('22222222-2222-4222-8222-222222222222', '# Mạng\n\nKết nối Wi-Fi và mạng.')]:
        content = text.encode('utf-8')
        result.append(AuthorizedDocument(workspace_id=search_context.workspace_id, metadata_id=identifier,
                                         source_version='test-v1', source_checksum=sha256(content).hexdigest(), content=content))
    return tuple(result)


@pytest.fixture
def index(documents, partition):
    return build_index(documents, partition)
