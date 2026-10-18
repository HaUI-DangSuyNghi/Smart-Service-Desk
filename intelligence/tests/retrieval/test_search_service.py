from dataclasses import asdict, replace
from unittest.mock import Mock

import pytest

from hdpi_intelligence.citations.base import VerifiedCitation
from hdpi_intelligence.domain.errors import (AuthorizationRejected, CitationVerificationError,
    ContractValidationError, CorpusUnavailable, EngineConfigurationError, EngineUnavailable)
from hdpi_intelligence.retrieval.registry import IndexRegistry
from hdpi_intelligence.retrieval.tfidf.engine import TfidfSearchEngine
from hdpi_intelligence.services.search import SearchService
from hdpi_intelligence.services.triage import TriageService


@pytest.fixture
def payload(search_context):
    return {'contractVersion': '0.1.0', 'requestId': 'test-search', 'profile': 'service-desk',
            'context': {'workspaceId': search_context.workspace_id}, 'query': 'ĐĂNG NHẬP', 'topK': 3}


class FakeCoreVerifier:
    """Service unit double; real verification is tested separately."""
    def verify(self, context, candidate):
        d, c = candidate.document, candidate.chunk
        return VerifiedCitation(d.workspace_id, d.metadata_id, d.source_version, d.source_checksum,
                                c.chunk_id, c.original_text)


@pytest.fixture
def search_service(index, partition):
    indexes = IndexRegistry()
    indexes.register(index)
    source = Mock()
    source.resolve_partition.return_value = partition
    return SearchService(corpus_source=source, indexes=indexes, citation_verifier=FakeCoreVerifier())


def run(service, payload, context):
    return service.search(payload, trusted_context=context, profile_version='0.2.0')


def test_service_success_and_content_safe_trace(search_service, payload, search_context, documents):
    first = run(search_service, payload, search_context)
    assert first.response == run(search_service, payload, search_context).response
    result, = first.response['results']
    assert result['metadataId'] == documents[0].metadata_id
    assert result['excerpt'] in documents[0].text
    assert first.trace.candidate_count == first.trace.verified_hit_count == 1
    assert not first.response['needsReview']
    assert payload['query'] not in repr(asdict(first.trace))
    assert documents[0].text not in repr(asdict(first.trace))
    assert 'test-scope' not in repr(asdict(first.trace))


@pytest.mark.parametrize('query', ['zzzzunknown', '!!!'])
def test_empty_success_requires_review(search_service, payload, search_context, query):
    response = run(search_service, {**payload, 'query': query}, search_context).response
    assert response['status'] == 'SUCCESS' and response['results'] == [] and response['needsReview']


@pytest.mark.parametrize('context_kind', ['missing', 'foreign-workspace', 'missing-scope'])
def test_authorization_before_source_registry_engine(search_service, payload, search_context, context_kind):
    context = {'missing': None, 'foreign-workspace': replace(search_context, workspace_id='foreign'),
               'missing-scope': replace(search_context, auth_scope_fingerprint=None)}[context_kind]
    search_service.indexes = Mock()
    engine = Mock(wraps=TfidfSearchEngine())
    search_service.engine = engine
    with pytest.raises(AuthorizationRejected):
        run(search_service, payload, context)
    search_service.source.resolve_partition.assert_not_called()
    search_service.indexes.get.assert_not_called()
    engine.search.assert_not_called()


def test_scope_denial_before_scoring_even_with_degraded_enabled(search_service, payload, search_context):
    search_service.allow_operational_degraded = True
    search_service.source.resolve_partition.side_effect = AuthorizationRejected('revoked')
    search_service.indexes = Mock()
    with pytest.raises(AuthorizationRejected):
        run(search_service, payload, search_context)
    search_service.indexes.get.assert_not_called()


@pytest.mark.parametrize('failure', [CorpusUnavailable, EngineUnavailable])
def test_only_operational_failures_can_degrade(search_service, payload, search_context, failure):
    search_service.source.resolve_partition.side_effect = failure('temporary')
    with pytest.raises(failure):
        run(search_service, payload, search_context)
    search_service.allow_operational_degraded = True
    response = run(search_service, payload, search_context).response
    assert response['status'] == 'DEGRADED' and response['results'] == []
    assert response['runtime'] is None and response['error']['code'] == 'INTELLIGENCE_UNAVAILABLE'


@pytest.mark.parametrize('failure', [RuntimeError, EngineConfigurationError, CitationVerificationError, AuthorizationRejected])
def test_unexpected_failures_never_degrade(search_service, payload, search_context, failure):
    search_service.allow_operational_degraded = True
    search_service.verifier = Mock()
    search_service.verifier.verify.side_effect = failure('failure')
    traces = []
    search_service.trace_sink = traces.append
    with pytest.raises(failure):
        run(search_service, payload, search_context)
    assert traces[0].status == 'ERROR' and traces[0].error_type == failure.__name__


def test_stale_citation_and_fabricated_excerpt(search_service, payload, search_context):
    search_service.verifier = Mock()
    search_service.verifier.verify.return_value = None
    result = run(search_service, payload, search_context)
    assert result.response['results'] == [] and result.trace.stale_hit_count == 1
    search_service.verifier.verify.return_value = VerifiedCitation('w', 'kb-local', 'v', '0'*64, 'fake', 'fabricated')
    with pytest.raises(CitationVerificationError):
        run(search_service, payload, search_context)


@pytest.mark.parametrize('top_k', [1, 10])
def test_top_k(search_service, payload, search_context, top_k):
    response = run(search_service, {**payload, 'query': 'và', 'topK': top_k}, search_context).response
    assert len(response['results']) <= top_k


def test_request_injection_and_resource_cap(search_service, payload, search_context):
    for change in ({'accessScopeKey': 'admin'}, {'topK': True}, {'topK': 11}, {'query': 'x'*8193}):
        with pytest.raises(ContractValidationError):
            run(search_service, {**payload, **change}, search_context)
    search_service.source.resolve_partition.assert_not_called()


def test_triage_independent_when_search_unavailable(search_service, payload, search_context):
    search_service.source.resolve_partition.side_effect = CorpusUnavailable()
    with pytest.raises(CorpusUnavailable):
        run(search_service, payload, search_context)
    triage = {k: payload[k] for k in ('contractVersion', 'requestId', 'profile', 'context')}
    triage['input'] = {'title': 'Wi-Fi chập chờn', 'description': 'Một máy bị ảnh hưởng; có thể chờ.'}
    assert TriageService().triage(triage, trusted_context=search_context, profile_version='0.2.0').response['status'] == 'SUCCESS'
