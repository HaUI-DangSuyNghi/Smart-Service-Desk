import json
from pathlib import Path
import shutil

import pytest
import yaml
from hdpi.evaluation.errors import EvaluationError
from servicedesk.intelligence.knowledge.contracts import pin_file
from servicedesk.intelligence.knowledge.working_dataset import load_working_dataset, load_reviewed_dataset

ROOT = Path(__file__).resolve().parents[3]
RELATIVE = Path('fixtures/intelligence/service-desk')
DATA = ROOT / RELATIVE


def test_v02_working_profile_uses_the_complete_new_qrels_without_invented_review():
    dataset = load_working_dataset(ROOT, DATA)
    assert dataset.judgment_basis == 'AI_ASSISTED_PROVISIONAL'
    assert len(dataset.queries) == 72
    assert sum(len(case['judgments']) for case in dataset.qrels['cases']) == 720
    assert all(type(cell['grade']) is int for case in dataset.qrels['cases'] for cell in case['judgments'])
    authoritative = json.loads((DATA / 'retrieval/qrels-v0.2.json').read_text('utf-8'))
    assert all(cell['grade'] is None for case in authoritative['cases'] for cell in case['judgments'])
    with pytest.raises(EvaluationError):
        load_reviewed_dataset(ROOT, DATA)


@pytest.fixture
def copied_working(tmp_path):
    shutil.copytree(DATA, tmp_path / RELATIVE)
    shutil.copytree(ROOT / 'contracts/apps', tmp_path / 'contracts/apps')
    return tmp_path, tmp_path / RELATIVE


def rewrite(root, directory, candidate, profile):
    path = directory / 'retrieval/ai-assisted/qrels-candidate-v0.2.json'
    path.write_text(json.dumps(candidate, ensure_ascii=False, indent=2) + '\n', encoding='utf-8', newline='\n')
    manifest_path = directory / 'retrieval/ai-assisted/manifest-v0.2.json'
    manifest = json.loads(manifest_path.read_text('utf-8'))
    pin = pin_file(root, path.relative_to(root).as_posix())
    manifest['candidate'].update(pin)
    manifest_path.write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8', newline='\n')
    profile['qrels'] = pin
    profile['qrelsManifest'] = pin_file(root, manifest_path.relative_to(root).as_posix())
    (directory / 'dataset-v0.2-provisional.yaml').write_text(yaml.safe_dump(profile), encoding='utf-8', newline='\n')


@pytest.mark.parametrize('mutation', ['pin', 'authoritative', 'query-digest', 'missing-query', 'duplicate-query',
    'foreign-artifact', 'duplicate-cell', 'grade-bool', 'missing-primary', 'rationale', 'scenario', 'summary', 'review-claim'])
def test_working_qrels_mutation_rejected_even_when_source_bytes_are_repinned(copied_working, mutation):
    root, directory = copied_working
    profile = yaml.safe_load((directory / 'dataset-v0.2-provisional.yaml').read_text('utf-8'))
    path = directory / 'retrieval/ai-assisted/qrels-candidate-v0.2.json'
    candidate = json.loads(path.read_text('utf-8'))
    case, cell = candidate['cases'][0], candidate['cases'][0]['judgments'][0]
    if mutation == 'authoritative':
        candidate['authoritative'] = True
    elif mutation == 'query-digest':
        candidate['queriesDigest'] = '0' * 64
    elif mutation == 'missing-query':
        candidate['cases'].pop()
    elif mutation == 'duplicate-query':
        candidate['cases'][-1] = case
    elif mutation == 'foreign-artifact':
        cell['artifactId'] = 'foreign'
    elif mutation == 'duplicate-cell':
        case['judgments'][-1] = cell
    elif mutation == 'grade-bool':
        cell['grade'] = True
    elif mutation == 'missing-primary':
        cell.update(grade=0, rationaleCode='IRRELEVANT_DOMAIN')
    elif mutation == 'rationale':
        cell['rationaleCode'] = 'IRRELEVANT_DOMAIN'
    elif mutation == 'scenario':
        case['scenario'] = 'ACCOUNT'
    elif mutation == 'summary':
        candidate['summary']['totalJudgments'] = 719
    elif mutation == 'review-claim':
        profile['evaluation']['humanReviewed'] = True
    rewrite(root, directory, candidate, profile)
    if mutation == 'pin':
        profile = yaml.safe_load((directory / 'dataset-v0.2-provisional.yaml').read_text('utf-8'))
        profile['qrels']['sha256'] = '0' * 64
        (directory / 'dataset-v0.2-provisional.yaml').write_text(yaml.safe_dump(profile), encoding='utf-8')
    with pytest.raises(EvaluationError):
        load_working_dataset(root, directory)
