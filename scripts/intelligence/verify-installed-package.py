"""Run with python -I after installing the wheel; never import from source."""

from pathlib import Path
from importlib.metadata import version
import platform

import hdpi
from hdpi.domain.models import TrustedContext
from hdpi.resources import resource_root
from hdpi.services.triage import TriageService


def verify_retrieval():
    # Independent authored bytes and FakeCore IDs; never bind repository knowledge.
    from hashlib import sha256
    import tempfile
    from hdpi.citations.verifier import CoreCitationVerifier
    from hdpi.profiles.registry import ProfileRegistry
    from hdpi.retrieval.corpus import load_index, publish_index
    from hdpi.retrieval.index import build_authorized_index
    from hdpi.retrieval.models import AuthorizedDocument, PartitionKey, scope_hash
    from hdpi.retrieval.registry import IndexRegistry
    from hdpi.services.search import SearchService

    context = TrustedContext(workspace_id='fake-core-wheel', auth_scope_fingerprint='fake-core-scope@1')
    content = 'Hướng dẫn đăng nhập tài khoản.'.encode('utf-8')
    document = AuthorizedDocument(workspace_id=context.workspace_id,
                                  metadata_id='33333333-3333-4333-8333-333333333333',
                                  source_version='fake-core-v1', source_checksum=sha256(content).hexdigest(), content=content)
    key = PartitionKey(workspace_id=context.workspace_id, profile_id='service-desk', profile_version='0.2.0',
                        access_scope_hash=scope_hash(context), corpus_version='fake-core-corpus@1')

    class FakeCoreAdapter:
        def resolve_partition(self, trusted, profile):
            key.authorize(trusted)
            return key

        def load_documents(self, trusted, profile):
            key.authorize(trusted)
            return (document,)

        def resolve(self, trusted, metadata_id):
            key.authorize(trusted)
            return document if metadata_id == document.metadata_id else None

    adapter = FakeCoreAdapter()
    index = build_authorized_index(source=adapter, context=context, profile=ProfileRegistry().get('service-desk', '0.2.0'))
    with tempfile.TemporaryDirectory() as directory:
        snapshot = Path(directory) / 'index.json'
        publish_index(index, snapshot)
        loaded = load_index(snapshot, key)
    indexes = IndexRegistry()
    indexes.register(loaded)
    service = SearchService(corpus_source=adapter, indexes=indexes, citation_verifier=CoreCitationVerifier(adapter))
    payload = {'contractVersion': '0.1.0', 'requestId': 'wheel-search', 'profile': 'service-desk',
               'context': {'workspaceId': context.workspace_id}, 'query': 'ĐĂNG NHẬP', 'topK': 1}
    run = service.search(payload, trusted_context=context, profile_version='0.2.0')
    if len(run.response['results']) != 1 or run.response['results'][0]['excerpt'] != document.text:
        raise RuntimeError('Installed wheel retrieval/citation mismatch')
    print('PASS installed wheel: authorized partition, snapshot rebuild, TF-IDF, search schema and FakeCore citation')


def main():
    expected_version = '0.3.0'
    if version('smart-service-intelligence') != expected_version:
        raise RuntimeError('Installed package version must be ' + expected_version)
    print(f'Python {platform.python_version()}; smart-service-intelligence {expected_version}')
    location = Path(hdpi.__file__).resolve()
    if 'site-packages' not in location.parts:
        raise RuntimeError('Verification must load the installed wheel')
    if '_resources' not in resource_root('profiles').parts or '_resources' not in resource_root('contracts').parts:
        raise RuntimeError('Wheel must contain profiles and shared contracts')
    payload = {'contractVersion': '0.1.0', 'requestId': 'installed-wheel', 'profile': 'service-desk',
               'context': {'workspaceId': 'local-wheel-check'},
               'input': {'title': 'Wi-Fi chập chờn', 'description': 'Một máy bị ảnh hưởng; có thể chờ.'}}
    run = TriageService().triage(payload, trusted_context=TrustedContext(workspace_id='local-wheel-check'),
                                profile_version='0.2.0')
    if run.response['result'] != {'category': 'NETWORK', 'priority': 'LOW', 'suggestedGroup': 'IT-NETWORK'}:
        raise RuntimeError('Installed package triage mismatch')
    print('PASS installed wheel: immutable resources, schema, triage and literal evidence')
    verify_retrieval()


if __name__ == '__main__':
    main()
