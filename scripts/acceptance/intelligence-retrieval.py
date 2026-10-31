"""Local real-Core acceptance. Uses configured demo/admin secrets, never logs them.

Creates independent synthetic knowledge and a temporary identity; deletes only
that identity. Core exposes no metadata delete/update API: test rows remain in
the isolated acceptance project. Never uploads pending-rights repository fixtures.
"""
import argparse
from dataclasses import replace
from hashlib import sha256
from importlib.metadata import version
import json
from pathlib import Path
import secrets
import socket
import subprocess
from time import perf_counter
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import Request, urlopen
from uuid import uuid4

from hdpi.citations.verifier import CoreCitationVerifier
from hdpi.domain.errors import AuthorizationRejected, CitationVerificationError, CorpusUnavailable
from hdpi.integrations.core import CoreAuthorizedCorpusAdapter, CoreCorpusCatalog, CoreHttpClient
from hdpi.profiles.registry import ProfileRegistry
from hdpi.retrieval.index import build_authorized_index
from hdpi.retrieval.registry import IndexRegistry
from hdpi.retrieval.tfidf.engine import TfidfSearchEngine
from hdpi.services.search import SearchService
from hdpi.services.triage import TriageService


def http(method, url, *, token=None, body=None, content_type=None, expected=200):
    headers = {'X-Request-ID': 'ai03-' + str(uuid4())}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if content_type:
        headers['Content-Type'] = content_type
    request = Request(url, data=body, headers=headers, method=method)
    try:
        response = urlopen(request, timeout=30)
    except HTTPError as error:
        response = error
    with response:
        if response.status != expected:
            raise RuntimeError(f'Acceptance HTTP expected {expected}, received {response.status}')
        raw = response.read()
        return json.loads(raw) if raw else None


def expect_error(error_type, action):
    try:
        action()
    except error_type:
        return
    raise AssertionError('Expected ' + error_type.__name__)


class ObservedEngine(TfidfSearchEngine):
    calls = 0

    def search(self, *args):
        self.calls += 1
        return super().search(*args)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--core-url', default='http://127.0.0.1:19080')
    parser.add_argument('--identity-url', default='http://127.0.0.1:18081')
    parser.add_argument('--env-file', type=Path, default=Path('.env'))
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    # Refuse to seed arbitrary external services with local acceptance credentials.
    from urllib.parse import urlsplit
    for url in (args.core_url, args.identity_url):
        parsed = urlsplit(url)
        if parsed.scheme != 'http' or parsed.hostname not in ('localhost', '127.0.0.1') or parsed.username or parsed.password:
            parser.error('Acceptance is restricted to explicit loopback HTTP services')
    settings = dict(line.split('=', 1) for line in args.env_file.read_text().splitlines()
                    if '=' in line and not line.startswith('#'))
    marker = 'ai03-' + uuid4().hex
    realm = args.identity_url + '/realms/dx-lab'
    admin_api = args.identity_url + '/admin/realms/dx-lab'

    def token(username, password, *, admin=False):
        url = args.identity_url + '/realms/master/protocol/openid-connect/token' if admin else realm + '/protocol/openid-connect/token'
        data = urlencode({'grant_type': 'password', 'client_id': 'admin-cli' if admin else 'core-probe',
                          'username': username, 'password': password}).encode()
        return http('POST', url, body=data, content_type='application/x-www-form-urlencoded')['access_token']

    report = {'evidenceClass': 'REAL_CORE_HTTP_ACCEPTANCE', 'packageVersion': version('smart-service-intelligence'),
              'coreBaseline': subprocess.check_output(['git', 'rev-parse', 'origin/develop'], text=True).strip(),
              'aiCommit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip(),
              'correlationId': marker, 'scenarios': [], 'limits': [
                  'Revocation verified with a refreshed JWT; existing JWT remains valid until Core expiry.',
                  'Core metadata is immutable; version change uses a new record and trusted catalog revision.',
                  'Core has no delete API; not-found is verified, actual metadata deletion is not claimed.',
                  'Checksum corruption is injected locally using real Core bytes, not by editing Core storage.',
                  'Outage uses an unavailable loopback authority; no running Core service is stopped.']}

    def record(identifier, detail, **fields):
        report['scenarios'].append({'id': identifier, 'status': 'PASS', 'detail': detail, **fields})
        print('PASS ' + identifier + ': ' + detail, flush=True)

    user_id, admin = None, None
    try:
        admin = token('admin', settings['KC_ADMIN_PASSWORD'], admin=True)
        agent = token('user2', settings['DEMO_PASSWORD'])
        password = secrets.token_urlsafe(24)
        http('POST', admin_api+'/users', token=admin, expected=201, content_type='application/json', body=json.dumps({
            'username': marker, 'enabled': True, 'emailVerified': True, 'email': marker+'@example.invalid',
            'firstName': 'Acceptance', 'lastName': 'AI03', 'groups': ['/workspaces/lab-a/agent'],
            'credentials': [{'type': 'password', 'value': password, 'temporary': False}]}).encode())
        users = http('GET', admin_api+'/users?'+urlencode({'username': marker, 'exact': 'true'}), token=admin)
        if len(users) != 1:
            raise AssertionError('Temporary acceptance identity not uniquely resolved')
        user_id = users[0]['id']
        active_token = [token(marker, password)]
        client = CoreHttpClient(args.core_url, token_provider=lambda: active_token[0], allow_local_http=True)

        def seed(text, source_version):
            raw = text.encode('utf-8')
            boundary = 'ai03-' + uuid4().hex
            body = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{marker}.md"\r\n'
                    'Content-Type: text/markdown\r\n\r\n').encode() + raw + f'\r\n--{boundary}--\r\n'.encode()
            file = http('POST', args.core_url+'/api/core/v1/workspaces/lab-a/files', token=agent, body=body,
                        content_type='multipart/form-data; boundary='+boundary, expected=201)
            metadata = http('POST', args.core_url+'/api/core/v1/workspaces/lab-a/metadata', token=agent,
                content_type='application/json', expected=201, body=json.dumps({'title': marker,
                    'source': 'synthetic-internal-acceptance:'+marker, 'version': source_version,
                    'usageRights': 'INTERNAL_SYNTHETIC_ACCEPTANCE_ONLY', 'fileId': file['id']}).encode())
            assert metadata['checksum'] == file['checksum'] == sha256(raw).hexdigest()
            return metadata, raw

        metadata, raw = seed('# Tài khoản\n\nHướng dẫn đăng nhập và mật khẩu.', 'acceptance-v1')
        adapter = CoreAuthorizedCorpusAdapter(client, CoreCorpusCatalog('lab-a', marker+'-v1', (metadata['id'],)))
        requester = token('user1', settings['DEMO_PASSWORD'])
        preview = http('GET', args.core_url+'/api/hpdi/v1/workspaces/lab-a/knowledge/'+metadata['id'], token=requester)
        assert preview['metadataId'] == metadata['id'] and preview['text'] == raw.decode('utf-8')
        http('GET', args.core_url+'/api/core/v1/workspaces/lab-a/files/'+metadata['fileId']+'/content', token=requester, expected=403)
        requester_client = CoreHttpClient(args.core_url, token_provider=lambda: requester, allow_local_http=True)
        requester_adapter = CoreAuthorizedCorpusAdapter(requester_client, adapter.catalog)
        expect_error(AuthorizationRejected, requester_adapter.trusted_context)
        record('CORE-AI03-03', 'Requester can preview but cannot download originals or ingest raw Core files')
        context = adapter.trusted_context()
        profile = ProfileRegistry().get('service-desk', '0.3.0')
        index = build_authorized_index(source=adapter, context=context, profile=profile)
        indexes = IndexRegistry()
        indexes.register(index)
        engine = ObservedEngine()
        service = SearchService(corpus_source=adapter, indexes=indexes, citation_verifier=CoreCitationVerifier(adapter), engine=engine)
        request = {'contractVersion': '0.1.0', 'requestId': marker, 'profile': 'service-desk',
                   'context': {'workspaceId': 'lab-a'}, 'query': 'đăng nhập mật khẩu', 'topK': 1}
        started = perf_counter()
        run = service.search(request, trusted_context=context, profile_version='0.3.0')
        elapsed = round((perf_counter()-started)*1000, 3)
        hit, = run.response['results']
        assert (hit['workspaceId'], hit['metadataId'], hit['sourceVersion'], hit['sourceChecksum']) == (
            'lab-a', metadata['id'], metadata['version'], metadata['checksum'])
        assert hit['excerpt'] in raw.decode()
        record('CORE-AI03-01', 'Authenticated ingest, authorized search and current Core citation',
               metadataId=metadata['id'], sourceChecksum=metadata['checksum'], searchWithCoreVerificationMs=elapsed)

        foreign_token = token('user4', settings['DEMO_PASSWORD'])
        foreign_client = CoreHttpClient(args.core_url, token_provider=lambda: foreign_token, allow_local_http=True)
        expect_error(AuthorizationRejected, lambda: foreign_client.json('/workspaces/lab-a/metadata/'+metadata['id']))
        foreign_adapter = CoreAuthorizedCorpusAdapter(foreign_client, adapter.catalog)
        service.source = foreign_adapter
        engine.calls = 0
        expect_error(AuthorizationRejected, lambda: service.search(request, trusted_context=context, profile_version='0.3.0'))
        assert engine.calls == 0
        service.source = adapter
        record('CORE-AI03-02', 'Core denies cross-workspace metadata and no ranking occurs')

        newer, _ = seed('# Tài khoản v2\n\nHướng dẫn đăng nhập mới.', 'acceptance-v2')
        newer_adapter = CoreAuthorizedCorpusAdapter(client, CoreCorpusCatalog('lab-a', marker+'-v2', (newer['id'],)))
        service.source = newer_adapter
        expect_error(AuthorizationRejected, lambda: service.search(request, trusted_context=context, profile_version='0.3.0'))
        assert engine.calls == 0
        record('CORE-AI03-04', 'New immutable Core version/catalog cannot select or score old context',
               replacementMetadataId=newer['id'], mechanism='IMMUTABLE_RECORD_AND_CATALOG_ROTATION')
        service.source = adapter

        real_document = adapter.resolve(context, metadata['id'])
        expect_error(CitationVerificationError, lambda: replace(real_document, source_checksum='0'*64))
        record('CORE-AI03-05', 'Corrupted checksum rejected against real downloaded Core bytes', injection='LOCAL_GUARD')

        missing_id = str(uuid4())
        missing_adapter = CoreAuthorizedCorpusAdapter(client, CoreCorpusCatalog('lab-a', marker+'-missing', (missing_id,)))
        missing_context = missing_adapter.trusted_context()
        assert missing_adapter.resolve(missing_context, missing_id) is None
        record('CORE-AI03-06', 'Real Core 404 resolves to None; no citation fabricated', deletionApiAvailable=False)

        admin = token('admin', settings['KC_ADMIN_PASSWORD'], admin=True)
        groups = http('GET', admin_api+'/users/'+user_id+'/groups', token=admin)
        assert groups, 'Acceptance user must initially have workspace membership'
        for group in groups:
            http('DELETE', admin_api+'/users/'+user_id+'/groups/'+group['id'], token=admin, expected=204)
        active_token[0] = token(marker, password)
        engine.calls = 0
        expect_error(AuthorizationRejected, lambda: service.search(request, trusted_context=context, profile_version='0.3.0'))
        assert engine.calls == 0
        record('CORE-AI03-03', 'Keycloak membership revoked; refreshed JWT rejected before scoring', tokenSemantics='REFRESHED_JWT')

        with socket.socket() as probe:
            probe.bind(('127.0.0.1', 0))
            unavailable_port = probe.getsockname()[1]
            # Bound but not listening: real connection failure, no fake HTTP resolver.
            offline = CoreAuthorizedCorpusAdapter(CoreHttpClient(f'http://127.0.0.1:{unavailable_port}',
                token_provider=lambda: active_token[0], allow_local_http=True, timeout=1), adapter.catalog)
            expect_error(CorpusUnavailable, offline.trusted_context)
        record('CORE-AI03-07', 'Unavailable Core authority raises CorpusUnavailable; DEGRADED disabled')

        triage_request = {k: request[k] for k in ('contractVersion', 'requestId', 'profile', 'context')}
        triage_request['input'] = {'title': 'Wi-Fi chập chờn', 'description': 'Một máy bị ảnh hưởng; có thể chờ.'}
        assert TriageService().triage(triage_request, trusted_context=context, profile_version='0.3.0').response['status'] == 'SUCCESS'
        record('CORE-AI03-08', 'AI-02 triage remains functional independently of retrieval/Core availability')
        report['status'] = 'PASS_WITH_DOCUMENTED_CORE_LIMITS'
    finally:
        if user_id is not None:
            admin = token('admin', settings['KC_ADMIN_PASSWORD'], admin=True)
            http('DELETE', admin_api+'/users/'+user_id, token=admin, expected=204)
            report['temporaryIdentityRemoved'] = True
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, ensure_ascii=True, indent=2)+'\n', 'utf-8')


if __name__ == '__main__':
    main()
