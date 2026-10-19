from hashlib import sha256
import json
from pathlib import Path
import shutil

import pytest
import yaml

from hdpi.evaluation.dataset import load_dataset
from hdpi.evaluation.errors import EvaluationError

ROOT = Path(__file__).resolve().parents[3]
FIXTURE = Path('fixtures/intelligence/service-desk')


@pytest.fixture
def copied(tmp_path):
    directory = tmp_path / FIXTURE
    shutil.copytree(ROOT / FIXTURE, directory)
    return tmp_path, directory


def repin(directory):
    path = directory / 'dataset.yaml'
    spec = yaml.safe_load(path.read_text('utf-8'))
    for pin in spec['components'].values():
        raw = (directory / pin['path']).read_bytes()
        pin.update(sha256=sha256(raw).hexdigest(), byteSize=len(raw))
    path.write_text(yaml.safe_dump(spec), 'utf-8')


def test_dataset_identity_and_portable_digest(copied):
    root, directory = copied
    original = load_dataset(ROOT / FIXTURE, ROOT)
    assert load_dataset(directory, root) == original
    assert len(original.tickets) == 12 and len(original.sources) == 5
    assert original.identity['id'] == 'service-desk-authored-eval'


@pytest.mark.parametrize('kind', ['checksum', 'size', 'missing', 'traversal', 'absolute', 'schema', 'version', 'rights',
                                  'duplicate-artifact', 'duplicate-ticket', 'duplicate-case', 'unknown', 'empty',
                                  'orphan-gold', 'orphan-ticket', 'artifact-traversal', 'component-version'])
def test_dataset_corruption_rejected(copied, kind):
    root, directory = copied
    spec_path = directory / 'dataset.yaml'
    spec = yaml.safe_load(spec_path.read_text('utf-8'))
    if kind in ('checksum', 'size', 'missing', 'traversal', 'absolute', 'schema', 'version', 'rights'):
        if kind == 'checksum':
            spec['components']['retrieval']['sha256'] = '0' * 64
        elif kind == 'size':
            spec['components']['retrieval']['byteSize'] += 1
        elif kind in ('missing', 'traversal', 'absolute'):
            spec['components']['retrieval']['path'] = {'missing': 'absent.json', 'traversal': '../outside.json', 'absolute': '/tmp/x'}[kind]
        elif kind == 'rights':
            spec['rights']['synthetic'] = False
        else:
            spec['schemaVersion' if kind == 'schema' else 'version'] = '999.0'
        spec_path.write_text(yaml.safe_dump(spec), 'utf-8')
    elif kind in ('duplicate-artifact', 'artifact-traversal'):
        path = directory / 'knowledge/manifest.json'
        data = json.loads(path.read_text('utf-8'))
        if kind == 'duplicate-artifact':
            data['artifacts'].append(data['artifacts'][0])
        else:
            data['artifacts'][0]['path'] = '../outside.md'
        path.write_text(json.dumps(data), 'utf-8')
        repin(directory)
    elif kind == 'duplicate-ticket':
        path = directory / 'tickets/tickets-v0.1.jsonl'
        path.write_text(path.read_text('utf-8') + path.read_text('utf-8').splitlines()[0] + '\n', 'utf-8')
        manifest_path = directory / 'tickets/manifest.json'
        data = json.loads(manifest_path.read_text('utf-8'))
        raw = path.read_bytes()
        data.update(sha256=sha256(raw).hexdigest(), byteSize=len(raw), recordCount=13)
        manifest_path.write_text(json.dumps(data), 'utf-8')
        repin(directory)
    else:
        path = directory / 'retrieval/gold-v0.1.json'
        data = json.loads(path.read_text('utf-8'))
        if kind == 'duplicate-case':
            data['cases'].append(data['cases'][0])
        elif kind in ('unknown', 'empty'):
            data['cases'][0]['expectedRelevantLocalArtifacts'] = ['unknown'] if kind == 'unknown' else []
        elif kind == 'orphan-gold':
            data['cases'][0]['ticketId'] = 'orphan'
        elif kind == 'orphan-ticket':
            data['cases'].pop()
        else:
            data['datasetVersion'] = '99.0'
        path.write_text(json.dumps(data), 'utf-8')
        repin(directory)
    with pytest.raises(EvaluationError):
        load_dataset(directory, root)


def test_gold_change_changes_digest(copied):
    root, directory = copied
    before = load_dataset(directory, root)
    path = directory / 'retrieval/gold-v0.1.json'
    data = json.loads(path.read_text('utf-8'))
    data['cases'][0]['expectedRelevantLocalArtifacts'] = ['kb-account-001']
    path.write_text(json.dumps(data), 'utf-8')
    repin(directory)
    assert before.identity['digest'] != load_dataset(directory, root).identity['digest']
