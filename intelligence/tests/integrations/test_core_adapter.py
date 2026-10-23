"""HTTP boundary simulation; real Core acceptance lives in scripts/acceptance."""
from dataclasses import replace
from hashlib import sha256
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from threading import Thread
from unittest.mock import Mock

import pytest

from hdpi.citations.verifier import CoreCitationVerifier
from hdpi.domain.errors import (AuthorizationRejected, CitationVerificationError,
    CorpusUnavailable, EngineConfigurationError)
from hdpi.integrations.core import CoreAuthorizedCorpusAdapter, CoreCorpusCatalog, CoreHttpClient
from hdpi.profiles.registry import ProfileRegistry
from hdpi.retrieval.index import build_authorized_index
from hdpi.retrieval.registry import IndexRegistry
from hdpi.retrieval.tfidf.engine import TfidfSearchEngine
from hdpi.services.search import SearchService

MID = '11111111-1111-4111-8111-111111111111'
FID = '22222222-2222-4222-8222-222222222222'
BASE = '/workspaces/lab-a'


@pytest.fixture
def core_http():
    raw = 'Đăng nhập tài khoản.'.encode()
    state = {'sub': 'test-principal', 'member': True, 'calls': [], 'routes': {
        BASE+'/metadata/'+MID: {'id': MID, 'workspace': 'lab-a', 'version': 'v1', 'title': 'Guide',
                                'fileId': FID, 'checksum': sha256(raw).hexdigest()},
        BASE+'/files/'+FID: {'id': FID, 'workspace': 'lab-a', 'mediaType': 'text/markdown',
                             'size': len(raw), 'checksum': sha256(raw).hexdigest()},
        BASE+'/files/'+FID+'/content': raw}}

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *args):
            pass

        def do_GET(self):
            path = self.path.removeprefix('/api/core/v1')
            state['calls'].append(path)
            if path == '/me':
                result = {'sub': state['sub'], 'memberships': [{'workspace': 'lab-a'}] if state['member'] else []}
            else:
                result = state['routes'].get(path, 404)
            if isinstance(result, int):
                self.send_response(result)
                if 300 <= result <= 399:
                    self.send_header('Location', '/must-not-follow')
                self.end_headers()
                return
            self.send_response(200)
            self.end_headers()
            self.wfile.write(result if isinstance(result, bytes) else json.dumps(result).encode())

    server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
    thread = Thread(target=server.serve_forever, daemon=True)
    thread.start()
    client = CoreHttpClient(f'http://127.0.0.1:{server.server_port}', token_provider=lambda: 'test-token', allow_local_http=True)
    adapter = CoreAuthorizedCorpusAdapter(client, CoreCorpusCatalog('lab-a', 'catalog-v1', (MID,)))
    try:
        yield state, client, adapter
    finally:
        server.shutdown()
        server.server_close()
        thread.join()


def test_http_adapter_end_to_end_and_equivalent_scope(core_http):
    state, _, adapter = core_http
    context = adapter.trusted_context()
    profile = ProfileRegistry().get('service-desk', '0.3.0')
    key = adapter.resolve_partition(context, profile)
    assert context.auth_scope_fingerprint not in repr(key)
    assert context == adapter.trusted_context()
    state['sub'] = 'equivalent-principal'
    equivalent = adapter.trusted_context()
    assert context.auth_scope_fingerprint == equivalent.auth_scope_fingerprint
    assert key == adapter.resolve_partition(equivalent, profile)
    with pytest.raises(AuthorizationRejected):
        adapter.resolve_partition(context, profile)
    context = equivalent
    index = build_authorized_index(source=adapter, context=context, profile=profile)
    indexes = IndexRegistry()
    indexes.register(index)
    service = SearchService(corpus_source=adapter, indexes=indexes, citation_verifier=CoreCitationVerifier(adapter))
    payload = {'contractVersion': '0.1.0', 'requestId': 'http-test', 'profile': 'service-desk',
               'context': {'workspaceId': 'lab-a'}, 'query': 'đăng nhập', 'topK': 1}
    assert service.search(payload, trusted_context=context, profile_version='0.3.0').response['results'][0]['metadataId'] == MID


@pytest.mark.parametrize('change', ['revoked', 'hidden-file', 'source-version', 'catalog-revision'])
def test_scope_change_never_scores_old_partition(core_http, change):
    state, client, adapter = core_http
    context = adapter.trusted_context()
    if change == 'revoked':
        state['member'] = False
    elif change == 'hidden-file':
        state['routes'][BASE+'/files/'+FID] = 404
    elif change == 'source-version':
        state['routes'][BASE+'/metadata/'+MID]['version'] = 'v2'
    else:
        adapter = CoreAuthorizedCorpusAdapter(client, CoreCorpusCatalog('lab-a', 'catalog-v2', (MID,)))
    engine = TfidfSearchEngine()
    engine.search = Mock()
    indexes = Mock()
    service = SearchService(corpus_source=adapter, indexes=indexes, citation_verifier=Mock(), engine=engine)
    payload = {'contractVersion': '0.1.0', 'requestId': 'denied', 'profile': 'service-desk',
               'context': {'workspaceId': 'lab-a'}, 'query': 'đăng nhập', 'topK': 1}
    with pytest.raises(AuthorizationRejected):
        service.search(payload, trusted_context=context, profile_version='0.3.0')
    indexes.get.assert_not_called()
    engine.search.assert_not_called()


@pytest.mark.parametrize('status,error', [(401, AuthorizationRejected), (403, AuthorizationRejected),
    (429, CorpusUnavailable), (500, CorpusUnavailable), (503, CorpusUnavailable), (302, EngineConfigurationError)])
def test_http_status_taxonomy_and_no_redirect(core_http, status, error):
    state, _, adapter = core_http
    state['routes'][BASE+'/metadata/'+MID] = status
    with pytest.raises(error):
        adapter.trusted_context()
    assert '/must-not-follow' not in state['calls']


@pytest.mark.parametrize('change', ['checksum', 'file-checksum', 'foreign-workspace', 'oversized', 'bad-json',
                                   'duplicate-json', 'nonfinite-json', 'no-file'])
def test_invalid_provenance_fails_closed(core_http, change):
    state, _, adapter = core_http
    metadata = state['routes'][BASE+'/metadata/'+MID]
    file = state['routes'][BASE+'/files/'+FID]
    if change == 'checksum':
        state['routes'][BASE+'/files/'+FID+'/content'] = b'corrupt'
    elif change == 'file-checksum':
        file['checksum'] = '0'*64
    elif change == 'foreign-workspace':
        metadata['workspace'] = 'lab-b'
    elif change == 'oversized':
        file['size'] = 3*1024*1024
    elif change == 'bad-json':
        state['routes'][BASE+'/metadata/'+MID] = b'{'
    elif change == 'duplicate-json':
        state['routes'][BASE+'/metadata/'+MID] = b'{"id":"first","id":"second"}'
    elif change == 'nonfinite-json':
        state['routes'][BASE+'/metadata/'+MID] = b'{"value":NaN}'
    else:
        metadata['fileId'] = None
    with pytest.raises(CitationVerificationError):
        adapter.trusted_context()


def test_context_workspace_and_catalog_boundary(core_http):
    state, _, adapter = core_http
    context = adapter.trusted_context()
    state['calls'].clear()
    with pytest.raises(AuthorizationRejected):
        adapter.resolve_partition(replace(context, workspace_id='lab-b'), ProfileRegistry().get('service-desk', '0.3.0'))
    assert state['calls'] == []
    with pytest.raises(AuthorizationRejected):
        adapter.resolve(context, FID)
    state['routes'][BASE+'/metadata/'+MID] = 404
    assert adapter.resolve(context, MID) is None


@pytest.mark.parametrize('method', ['resolve_partition', 'load_documents', 'resolve'])
@pytest.mark.parametrize('kind', ['none', 'no-scope'])
def test_public_adapter_methods_require_context_before_any_http(core_http, method, kind):
    state, _, adapter = core_http
    context = adapter.trusted_context()
    context = None if kind == 'none' else replace(context, auth_scope_fingerprint=None)
    state['calls'].clear()
    argument = MID if method == 'resolve' else ProfileRegistry().get('service-desk', '0.3.0')
    with pytest.raises(AuthorizationRejected):
        getattr(adapter, method)(context, argument)
    assert state['calls'] == []


@pytest.mark.parametrize('url', ['http://example.com', 'https://user:password@example.com',
    'https://example.com/?token=secret', 'https://example.com/#x', 'file:///etc/passwd'])
def test_transport_authority_is_trusted_configuration(url):
    with pytest.raises(EngineConfigurationError):
        CoreHttpClient(url, token_provider=lambda: 'token')
