"""Trusted retrieval-to-generation composition, using a synthetic Core double."""

from dataclasses import replace
from hashlib import sha256
from unittest.mock import Mock

import pytest

from hdpi.citations.base import VerifiedCitation
from hdpi.citations.verifier import CoreCitationVerifier
from hdpi.domain.errors import AuthorizationRejected
from hdpi.domain.models import TrustedContext
from hdpi.generation.deterministic import CitationBoundTemplateProvider
from hdpi.generation.models import AuthorizedKnowledgeBundle, RetrievalRuntimeIdentity
from hdpi.generation.policy import GenerationPolicy
from hdpi.profiles.registry import ProfileRegistry
from hdpi.retrieval.index import build_index
from hdpi.retrieval.models import AuthorizedDocument, PartitionKey, scope_hash
from hdpi.retrieval.registry import IndexRegistry
from hdpi.services.assist import GroundedAssistanceService
from hdpi.services.search import SearchService


@pytest.fixture
def flow():
    context = TrustedContext(
        workspace_id='synthetic-flow',
        auth_scope_fingerprint='synthetic-scope@1',
    )
    content = 'Verify the device connection.'.encode('utf-8')
    document = AuthorizedDocument(
        workspace_id=context.workspace_id,
        metadata_id='11111111-1111-4111-8111-111111111111',
        source_version='synthetic-v1',
        source_checksum=sha256(content).hexdigest(),
        content=content,
    )
    profile = ProfileRegistry().get('service-desk', '0.2.0')
    partition = PartitionKey(
        workspace_id=context.workspace_id,
        profile_id=profile.profile_id,
        profile_version='0.2.0',
        access_scope_hash=scope_hash(context),
        corpus_version='synthetic-corpus@1',
    )
    indexes = IndexRegistry()
    indexes.register(build_index((document,), partition))

    adapter = Mock()
    adapter.resolve_partition.return_value = partition
    adapter.resolve.return_value = document
    search = SearchService(
        corpus_source=adapter,
        indexes=indexes,
        citation_verifier=CoreCitationVerifier(adapter),
    )
    policy = GenerationPolicy(identity='synthetic-flow-policy@1', instructions='Quote the evidence.')
    baseline = CitationBoundTemplateProvider(app_prompt_version=policy.identity)
    provider = Mock(wraps=baseline)
    provider.name = baseline.name
    provider.version = baseline.version
    provider.config_version = baseline.config_version
    provider.runtime_identity = baseline.runtime_identity

    return context, adapter, search, policy, provider


def compose(flow, *, question='device connection', context_override=None):
    context, adapter, search, policy, provider = flow
    context = context_override or context
    run = search.search(
        {
            'contractVersion': '0.1.0',
            'requestId': 'synthetic-flow-request',
            'profile': 'service-desk',
            'context': {'workspaceId': context.workspace_id},
            'query': question,
            'topK': 3,
        },
        trusted_context=context,
        profile_version='0.2.0',
    )
    response = run.response

    # Only this trusted composition boundary converts already verified results.
    # A public request or raw RankedHit must never enter this conversion.
    citations = tuple(
        VerifiedCitation(
            workspace_id=item['workspaceId'],
            metadata_id=item['metadataId'],
            source_version=item['sourceVersion'],
            source_checksum=item['sourceChecksum'],
            chunk_id=item['chunkId'],
            excerpt=item['excerpt'],
        )
        for item in response['results']
    )
    bundle = AuthorizedKnowledgeBundle.from_verified_citations(
        request_id=response['requestId'],
        profile_id=response['profile'],
        profile_version='0.2.0',
        retrieval=RetrievalRuntimeIdentity(
            engine=run.trace.engine,
            engine_version=run.trace.engine_version,
            config_version=run.trace.engine_config_version,
            corpus_version=run.trace.corpus_version,
            index_version=run.trace.index_version,
        ),
        citations=citations,
        needs_review=response['needsReview'],
    )

    return GroundedAssistanceService(provider=provider).assist(
        question, bundle=bundle, policy=policy,
    )


def test_verified_retrieval_provenance_is_preserved_in_assistance(flow):
    run = compose(flow)
    context, adapter, search, policy, provider = flow
    citation, = run.response.citations

    assert run.response.status == 'SUCCESS'
    assert citation.workspace_id == context.workspace_id
    assert citation.metadata_id == adapter.resolve.return_value.metadata_id
    assert citation.source_checksum == adapter.resolve.return_value.source_checksum
    assert run.response.answer.claims[0].text == adapter.resolve.return_value.text
    adapter.resolve.assert_called_once()
    provider.generate.assert_called_once()


@pytest.mark.parametrize('source_state', ['removed', 'changed'])
def test_stale_core_evidence_never_reaches_generation(flow, source_state):
    context, adapter, search, policy, provider = flow
    document = adapter.resolve.return_value
    adapter.resolve.return_value = (
        None if source_state == 'removed' else replace(document, source_version='synthetic-v2')
    )

    run = compose(flow)

    assert run.response.status == 'INSUFFICIENT_EVIDENCE'
    assert run.response.answer is None
    provider.generate.assert_not_called()


def test_no_match_never_reaches_generation(flow):
    run = compose(flow, question='zzzzunknown')

    assert run.response.status == 'INSUFFICIENT_EVIDENCE'
    flow[-1].generate.assert_not_called()


def test_foreign_workspace_is_rejected_before_generation(flow):
    with pytest.raises(AuthorizationRejected):
        compose(flow, context_override=replace(flow[0], workspace_id='foreign-workspace'))

    flow[1].resolve.assert_not_called()
    flow[-1].generate.assert_not_called()
