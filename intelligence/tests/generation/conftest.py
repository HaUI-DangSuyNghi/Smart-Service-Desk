from hashlib import sha256

import pytest

from hdpi.citations.base import VerifiedCitation
from hdpi.generation.models import AuthorizedKnowledgeBundle, RetrievalRuntimeIdentity


@pytest.fixture
def verified_citations():
    return tuple(
        VerifiedCitation(
            workspace_id='synthetic-workspace',
            metadata_id=f'{index:08d}-1111-4111-8111-111111111111',
            source_version='v1',
            source_checksum=sha256(excerpt.encode('utf-8')).hexdigest(),
            chunk_id=sha256(f'chunk-{index}'.encode()).hexdigest(),
            excerpt=excerpt,
        )
        for index, excerpt in enumerate(
            ('Verify the device connection.', 'Đọc hướng dẫn đã được xác minh.'), start=1,
        )
    )


@pytest.fixture
def bundle(verified_citations):
    return AuthorizedKnowledgeBundle.from_verified_citations(
        request_id='synthetic-request',
        profile_id='synthetic-profile',
        profile_version='1.0.0',
        retrieval=RetrievalRuntimeIdentity(
            engine='synthetic-engine',
            engine_version='1.0.0',
            config_version='synthetic-config@1',
            corpus_version='synthetic-corpus@1',
            index_version='synthetic-index@1',
        ),
        citations=verified_citations,
    )
