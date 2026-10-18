from dataclasses import replace
import json
from unittest.mock import patch

import pytest

from smart_service_intelligence.domain.errors import AuthorizationRejected, CorpusUnavailable, EngineConfigurationError
from smart_service_intelligence.normalization.v1 import NormalizerV1
from smart_service_intelligence.retrieval.corpus import load_index, publish_index
from smart_service_intelligence.retrieval.index import build_index
from smart_service_intelligence.retrieval.registry import IndexRegistry
from smart_service_intelligence.retrieval.tfidf.engine import fit_chunks, TfidfSearchEngine


def test_rebuild_and_safe_snapshot(index, documents, partition, tmp_path):
    engine = TfidfSearchEngine()
    rebuilt = build_index(reversed(documents), partition)
    path = tmp_path / 'index.json'
    publish_index(index, path)
    loaded = load_index(path, partition)
    for other in (rebuilt, loaded):
        assert other.manifest == index.manifest
        assert engine.search('đăng nhập', other, 3) == engine.search('đăng nhập', index, 3)
    before = path.read_bytes()
    with patch('smart_service_intelligence.retrieval.corpus.load_index', side_effect=EngineConfigurationError):
        with pytest.raises(EngineConfigurationError):
            publish_index(index, path)
    assert path.read_bytes() == before
    assert not list(tmp_path.glob('.ai03-*'))


@pytest.mark.parametrize('field', ['index_version', 'chunk_count', 'numerical_runtime', 'source_state_digest'])
def test_corrupt_manifest_rejected(index, partition, tmp_path, field):
    path = tmp_path / 'index.json'
    publish_index(index, path)
    data = json.loads(path.read_text('utf-8'))
    data['manifest'][field] = 'corrupt'
    path.write_text(json.dumps(data), 'utf-8')
    with pytest.raises(EngineConfigurationError):
        load_index(path, partition)


def test_registry_never_falls_back(index, partition, search_context):
    registry = IndexRegistry()
    registry.register(index)
    assert registry.get(partition, search_context) is index
    with pytest.raises(CorpusUnavailable):
        registry.get(replace(partition, corpus_version='missing'), search_context)
    for key in (replace(partition, workspace_id='foreign'), replace(partition, access_scope_hash='0'*64)):
        with pytest.raises(AuthorizationRejected):
            registry.get(key, search_context)
    with pytest.raises(EngineConfigurationError):
        registry.register(replace(index, manifest=replace(index.manifest, index_version='changed')))


def test_tfidf_bounds_empty_and_no_filler(index, partition):
    engine = TfidfSearchEngine()
    query = NormalizerV1().normalize('ĐĂNG NHẬP').value
    assert len(engine.search(query, index, 1)) == 1
    assert engine.search(query, index, 10)[0].chunk.source_id.startswith('1111')
    assert engine.search('zzzznonexistent', index, 10) == ()
    assert engine.search('!!!', index, 10) == ()
    assert engine.search('query', build_index([], partition), 10) == ()
    for top_k in (0, 11, True):
        with pytest.raises(EngineConfigurationError):
            engine.search(query, index, top_k)
    with pytest.raises(EngineConfigurationError):
        fit_chunks((index.chunks[0], index.chunks[0]))


def test_partition_sources_and_stable_ties(documents, partition):
    with pytest.raises(AuthorizationRejected):
        build_index([replace(documents[0], workspace_id='foreign')], partition)
    with pytest.raises(EngineConfigurationError):
        build_index([documents[0], documents[0]], partition)
    same = replace(documents[0], metadata_id=documents[1].metadata_id)
    tied = build_index([same, documents[0]], partition)
    hits = TfidfSearchEngine().search('đăng nhập', tied, 10)
    assert hits[0].score == hits[1].score
    assert [h.chunk.source_id for h in hits] == sorted(d.metadata_id for d in (same, documents[0]))
