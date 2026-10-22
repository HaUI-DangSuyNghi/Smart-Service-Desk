from dataclasses import replace
from hashlib import sha256
from unittest.mock import Mock

import pytest

from hdpi.citations.base import CitationCandidate
from hdpi.citations.verifier import CoreCitationVerifier
from hdpi.domain.errors import AuthorizationRejected, CitationVerificationError, CorpusUnavailable
from hdpi.profiles.registry import ProfileRegistry
from hdpi.retrieval.index import build_authorized_index
from hdpi.services.search import SearchService
from hdpi.retrieval.registry import IndexRegistry


class FakeCoreSourceResolver:
    """Simulated Core authorization and source state, not a real Core integration."""
    def __init__(self, documents):
        self.documents = {d.metadata_id: d for d in documents}
        self.failure = None

    def resolve(self, context, metadata_id):
        if self.failure:
            raise self.failure
        return self.documents.get(metadata_id)


def test_authoritative_source_verifies_exact_chunk(index, documents, search_context):
    resolver = FakeCoreSourceResolver(documents)
    candidate = CitationCandidate(documents[0], index.chunks[0])
    verifier = CoreCitationVerifier(resolver)
    verified = verifier.verify(search_context, candidate)
    assert verified.metadata_id == documents[0].metadata_id
    assert verified.excerpt in documents[0].text
    for changed in (replace(candidate.chunk, original_text='invented'), replace(candidate.chunk, chunk_id='fake'),
                    replace(candidate.chunk, ordinal=-1), replace(candidate.chunk, source_id='kb-local')):
        with pytest.raises(CitationVerificationError):
            verifier.verify(search_context, replace(candidate, chunk=changed))


@pytest.mark.parametrize('change', ['removed', 'version', 'checksum'])
def test_stale_source_is_removed(index, documents, search_context, change):
    resolver = FakeCoreSourceResolver(documents)
    doc = documents[0]
    if change == 'removed':
        del resolver.documents[doc.metadata_id]
    elif change == 'version':
        resolver.documents[doc.metadata_id] = replace(doc, source_version='test-v2')
    else:
        raw = b'new source'
        resolver.documents[doc.metadata_id] = replace(doc, content=raw, source_checksum=sha256(raw).hexdigest())
    assert CoreCitationVerifier(resolver).verify(search_context, CitationCandidate(doc, index.chunks[0])) is None


@pytest.mark.parametrize('failure', [AuthorizationRejected, CorpusUnavailable])
def test_core_denial_and_outage_propagate(index, documents, search_context, failure):
    resolver = FakeCoreSourceResolver(documents)
    resolver.failure = failure('simulated Core failure')
    with pytest.raises(failure):
        CoreCitationVerifier(resolver).verify(search_context, CitationCandidate(documents[0], index.chunks[0]))


def test_foreign_source_and_candidate_fail_closed(index, documents, search_context):
    resolver = FakeCoreSourceResolver(documents)
    doc = documents[0]
    candidate = CitationCandidate(doc, index.chunks[0])
    verifier = CoreCitationVerifier(resolver)
    resolver.documents[doc.metadata_id] = replace(doc, workspace_id='foreign')
    with pytest.raises(CitationVerificationError):
        verifier.verify(search_context, candidate)
    with pytest.raises(AuthorizationRejected):
        verifier.verify(search_context, replace(candidate, document=replace(doc, workspace_id='foreign')))


def test_authorized_build_orders_access_before_load(partition, search_context, documents):
    source = Mock()
    profile = ProfileRegistry().get('service-desk', '0.3.0')
    source.resolve_partition.side_effect = AuthorizationRejected('denied')
    with pytest.raises(AuthorizationRejected):
        build_authorized_index(source=source, context=search_context, profile=profile)
    source.load_documents.assert_not_called()
    source.resolve_partition.side_effect = None
    source.resolve_partition.return_value = partition
    source.load_documents.return_value = documents
    built = build_authorized_index(source=source, context=search_context, profile=profile)
    assert built.manifest.document_count == 2


def test_end_to_end_real_verifier_with_fake_core(index, documents, partition, search_context):
    source = Mock()
    source.resolve_partition.return_value = partition
    indexes = IndexRegistry()
    indexes.register(index)
    resolver = FakeCoreSourceResolver(documents)
    service = SearchService(corpus_source=source, indexes=indexes, citation_verifier=CoreCitationVerifier(resolver))
    payload = {'contractVersion': '0.1.0', 'requestId': 'test-e2e', 'profile': 'service-desk',
               'context': {'workspaceId': search_context.workspace_id}, 'query': 'đăng nhập', 'topK': 1}
    first = service.search(payload, trusted_context=search_context, profile_version='0.3.0')
    assert first.response['results'][0]['sourceChecksum'] == documents[0].source_checksum
    resolver.documents.clear()
    stale = service.search(payload, trusted_context=search_context, profile_version='0.3.0')
    assert stale.response['results'] == [] and stale.trace.stale_hit_count == 1
