import json
import shutil
from pathlib import Path

import pytest
import yaml
from hdpi.evaluation.errors import EvaluationError
from servicedesk.intelligence.knowledge.contracts import pin_file
from servicedesk.intelligence.knowledge.dataset import load_dataset

ROOT = Path(__file__).resolve().parents[3]
RELATIVE = Path('fixtures/intelligence/service-desk')


@pytest.fixture
def copied(tmp_path):
    shutil.copytree(ROOT / RELATIVE, tmp_path / RELATIVE)
    shutil.copytree(ROOT / 'contracts/apps', tmp_path / 'contracts/apps')
    return tmp_path, tmp_path / RELATIVE


def write_json(path, value):
    path.write_text(
        json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8', newline='\n'
    )


def repin(root, directory):
    path = directory / 'dataset-v0.2.yaml'
    spec = yaml.safe_load(path.read_text('utf-8'))
    for name, pin in spec['components'].items():
        spec['components'][name] = pin_file(root, pin['path'])
    path.write_text(yaml.safe_dump(spec), encoding='utf-8', newline='\n')


@pytest.mark.parametrize(
    'kind',
    [
        'owner',
        'category',
        'rights',
        'related',
        'checksum',
        'path',
        'secret',
        'duplicate-artifact',
        'duplicate-query',
        'split-leakage',
        'qrel-unknown',
        'qrel-missing',
        'qrel-orphan',
        'auto-grade',
        'reviewer-duplicate',
        'holdout-lock',
        'component-checksum',
        'schema-version',
    ],
)
def test_bad_data_fails_even_if_outer_manifest_is_repinned(copied, kind):
    root, directory = copied
    corpus_path = directory / 'knowledge/manifest-v0.2.json'
    corpus = json.loads(corpus_path.read_text('utf-8'))
    qrels_path = directory / 'retrieval/qrels-v0.2.json'
    qrels = json.loads(qrels_path.read_text('utf-8'))
    if kind in (
        'owner',
        'category',
        'rights',
        'related',
        'checksum',
        'path',
        'secret',
        'duplicate-artifact',
    ):
        artifact = corpus['artifacts'][0]
        if kind == 'owner':
            del artifact['owner']
        elif kind == 'category':
            artifact['categories'] = ['ROOT_ADMIN']
        elif kind == 'rights':
            artifact['rightsStatus'] = 'APPROVED_INTERNAL_EVAL'
        elif kind == 'related':
            artifact['relatedArtifacts'] = ['unknown']
        elif kind == 'checksum':
            artifact['curated']['sha256'] = '0' * 64
        elif kind == 'path':
            artifact['curated']['path'] = '../outside.md'
        elif kind == 'secret':
            path = root / artifact['curated']['path']
            path.write_text(
                path.read_text('utf-8') + '\npassword: RealValueShouldBeRejected\n',
                encoding='utf-8',
                newline='\n',
            )
            artifact['curated'] = pin_file(root, artifact['curated']['path'])
        else:
            corpus['artifacts'][1]['artifactId'] = artifact['artifactId']
        write_json(corpus_path, corpus)
    elif kind in ('duplicate-query', 'split-leakage'):
        path = directory / 'tickets/tickets-v0.2.jsonl'
        queries = [json.loads(line) for line in path.read_text('utf-8').splitlines()]
        if kind == 'duplicate-query':
            queries[1]['id'] = queries[0]['id']
        else:
            queries[8]['scenarioFamily'] = queries[0]['scenarioFamily']
        path.write_text(
            ''.join(json.dumps(q, ensure_ascii=False) + '\n' for q in queries),
            encoding='utf-8',
            newline='\n',
        )
    elif kind in (
        'qrel-unknown',
        'qrel-missing',
        'qrel-orphan',
        'auto-grade',
        'reviewer-duplicate',
    ):
        cell = qrels['cases'][0]['judgments'][0]
        if kind == 'qrel-unknown':
            cell['artifactId'] = 'unknown'
        elif kind == 'qrel-missing':
            qrels['cases'][0]['judgments'].pop()
        elif kind == 'qrel-orphan':
            qrels['cases'][0]['queryId'] = 'orphan'
        elif kind == 'auto-grade':
            cell.update(grade=2, reason='Automatically assigned')
        else:
            cell.update(
                grade=2,
                reason='Invalid duplicate votes',
                reviews=[
                    dict(
                        reviewer='same-human',
                        grade=2,
                        reason='test',
                        independent=True,
                        queryReviewed=True,
                    )
                ]
                * 2,
            )
        write_json(qrels_path, qrels)
    repin(root, directory)
    if kind in ('holdout-lock', 'component-checksum', 'schema-version'):
        path = directory / 'dataset-v0.2.yaml'
        value = yaml.safe_load(path.read_text('utf-8'))
        if kind == 'holdout-lock':
            value['holdout']['state'] = 'LOCKED'
        elif kind == 'component-checksum':
            value['components']['queries']['sha256'] = '0' * 64
        else:
            value['schemaVersion'] = '999'
        path.write_text(yaml.safe_dump(value), encoding='utf-8', newline='\n')
    with pytest.raises(EvaluationError):
        load_dataset(root, directory)


def test_legacy_byte_change_cannot_be_hidden_by_v02_pin(copied):
    root, directory = copied
    path = directory / 'knowledge/sources/wifi-troubleshooting.md'
    path.write_bytes(path.read_bytes() + b'\n')
    with pytest.raises(EvaluationError, match='mismatch|Bronze source changed'):
        load_dataset(root, directory)
