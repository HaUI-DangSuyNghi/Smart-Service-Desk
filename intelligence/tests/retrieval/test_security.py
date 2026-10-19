from dataclasses import replace
from hashlib import sha256
import json
from types import SimpleNamespace
from unittest.mock import Mock, patch

import pytest

from hdpi.contracts.search import SearchContractValidator
from hdpi.domain.errors import (AuthorizationRejected, CitationVerificationError,
    ContractValidationError, EngineConfigurationError)
from hdpi.retrieval.corpus import load_index, publish_index
from hdpi.retrieval.index import build_index
from hdpi.retrieval.models import RankedHit, MAX_SOURCE_BYTES
from hdpi.retrieval.registry import IndexRegistry
from hdpi.retrieval.tfidf.engine import TfidfSearchEngine
from hdpi.services.search import SearchService


def service_for(index, partition, hits):
    indexes = IndexRegistry()
    indexes.register(index)
    source = Mock()
    source.resolve_partition.return_value = partition
    engine = TfidfSearchEngine()
    engine.search = Mock(return_value=hits)
    verifier = Mock()
    service = SearchService(corpus_source=source, indexes=indexes, citation_verifier=verifier, engine=engine)
    return service


def request_for(context):
    return {'contractVersion': '0.1.0', 'requestId': 'security', 'profile': 'service-desk',
            'context': {'workspaceId': context.workspace_id}, 'query': 'đăng nhập', 'topK': 1}


@pytest.mark.parametrize('kind', ['foreign', 'duplicate', 'nan', 'infinity', 'zero', 'negative', 'excerpt'])
def test_malicious_engine_never_reaches_verifier(index, partition, search_context, kind):
    chunk = index.chunks[0]
    hit = RankedHit(chunk, .5)
    payload = request_for(search_context)
    if kind == 'foreign':
        hits = [RankedHit(replace(chunk, source_id='kb-network-001', chunk_id='foreign'), .5)]
    elif kind == 'excerpt':
        hits = [RankedHit(replace(chunk, original_text='generated excerpt'), .5)]
    elif kind == 'duplicate':
        hits = [hit, hit]
    else:
        score = {'nan': float('nan'), 'infinity': float('inf'), 'zero': 0, 'negative': -1}[kind]
        hits = [SimpleNamespace(chunk=chunk, score=score)]
    service = service_for(index, partition, hits)
    with pytest.raises(CitationVerificationError):
        service.search(payload, trusted_context=search_context, profile_version='0.2.0')
    service.verifier.verify.assert_not_called()


@pytest.mark.parametrize('kind', ['workspace', 'scope', 'profile', 'engine-config'])
def test_partition_mismatch_rejected_before_scoring(index, partition, search_context, kind):
    service = service_for(index, partition, [])
    if kind == 'engine-config':
        service.engine.config_version = 'unknown'
    else:
        changes = {'workspace': {'workspace_id': 'foreign'}, 'scope': {'access_scope_hash': '0'*64},
                   'profile': {'profile_version': '0.1.0'}}[kind]
        service.source.resolve_partition.return_value = replace(partition, **changes)
    with pytest.raises((AuthorizationRejected, EngineConfigurationError)):
        service.search(request_for(search_context), trusted_context=search_context, profile_version='0.2.0')
    service.engine.search.assert_not_called()


def test_registry_rejects_corrupt_first_registration(index):
    with pytest.raises(EngineConfigurationError):
        IndexRegistry().register(replace(index, manifest=replace(index.manifest, chunk_count=999)))
    index.lexical.matrix.data[0] += .01
    with pytest.raises(EngineConfigurationError):
        IndexRegistry().register(index)


@pytest.mark.parametrize('kind', ['bytes', 'partition', 'duplicate-json-key', 'unknown-format', 'invalid-json'])
def test_snapshot_rejects_invalid_state(index, partition, tmp_path, kind):
    path = tmp_path / 'snapshot.json'
    publish_index(index, path)
    data = json.loads(path.read_text('utf-8'))
    if kind == 'bytes':
        data['documents'][0]['content'] = 'Y2hhbmdlZA=='
    elif kind == 'partition':
        data['manifest']['partition']['workspace_id'] = 'foreign'
    elif kind == 'unknown-format':
        data['format'] = 'latest'
    raw = json.dumps(data)
    if kind == 'duplicate-json-key':
        raw = raw[:-1] + ', "format": "ai03-json@0.1.0"}'
    if kind == 'invalid-json':
        raw = '{'
    path.write_text(raw, 'utf-8')
    with pytest.raises(EngineConfigurationError):
        load_index(path, partition)


def test_source_and_runtime_version_guards(documents, partition):
    content = b'x' * (MAX_SOURCE_BYTES + 1)
    with pytest.raises(CitationVerificationError):
        replace(documents[0], content=content, source_checksum=sha256(content).hexdigest())
    with patch('hdpi.retrieval.tfidf.config.version', return_value='unknown'):
        with pytest.raises(EngineConfigurationError):
            build_index(documents, partition)


def test_response_schema_and_semantic_guards(search_context):
    validator = SearchContractValidator()
    payload = request_for(search_context)
    response = {k: payload[k] for k in ('contractVersion', 'requestId', 'profile')}
    response.update(workspaceId=search_context.workspace_id, status='SUCCESS', results=[],
                    runtime={'engine': 'tfidf', 'engineVersion': '0.1.0', 'profileVersion': 'service-desk@0.2.0',
                             'corpusVersion': 'test', 'indexVersion': 'test'}, needsReview=True, error=None)
    validator.response(response, payload)
    for change in ({'requestId': 'foreign'}, {'needsReview': False}, {'runtime': None}, {'confidence': .9}):
        with pytest.raises(ContractValidationError):
            validator.response({**response, **change}, payload)
