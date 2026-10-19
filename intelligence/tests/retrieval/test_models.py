from dataclasses import FrozenInstanceError, replace
from hashlib import sha256

import pytest

from hdpi.domain.errors import AuthorizationRejected, CitationVerificationError, EngineConfigurationError
from hdpi.domain.models import TrustedContext
from hdpi.retrieval.models import AuthorizedDocument, PartitionKey, RankedHit, scope_hash


def test_runtime_source_integrity_and_immutability():
    doc = AuthorizedDocument(workspace_id='w', metadata_id='11111111-1111-4111-8111-111111111111',
                             source_version='v1', source_checksum=sha256(b'abc').hexdigest(), content=b'abc')
    with pytest.raises(FrozenInstanceError):
        doc.source_version = 'v2'
    for change in ({'metadata_id': 'kb-local'}, {'content': b'changed'}, {'source_checksum': '0'*64},
                   {'media_type': 'application/pdf'}, {'source_version': ''}):
        with pytest.raises(CitationVerificationError):
            replace(doc, **change)


def test_partition_exact_versions_and_scope():
    ctx = TrustedContext(workspace_id='w', auth_scope_fingerprint='opaque-v1')
    key = PartitionKey(workspace_id='w', profile_id='service-desk', profile_version='0.2.0',
                       access_scope_hash=scope_hash(ctx), corpus_version='v1')
    key.authorize(ctx)
    for changed in (replace(ctx, workspace_id='foreign'), replace(ctx, auth_scope_fingerprint='new')):
        with pytest.raises(AuthorizationRejected):
            key.authorize(changed)
    with pytest.raises(AuthorizationRejected):
        scope_hash(TrustedContext(workspace_id='w'))
    with pytest.raises(EngineConfigurationError):
        replace(key, engine_config='latest')
    for score in (float('nan'), float('inf'), -1, 0, True):
        with pytest.raises(CitationVerificationError):
            RankedHit(None, score)
