import importlib.util
import json
import shutil
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[3]
SCRIPT = ROOT / 'scripts/intelligence/verify-knowledge-evidence.py'
spec = importlib.util.spec_from_file_location('knowledge_evidence', SCRIPT)
evidence = importlib.util.module_from_spec(spec)
spec.loader.exec_module(evidence)
FROZEN = ROOT / 'fixtures/intelligence/service-desk/evaluation/corpus-experiments/paragraph-v0.2'


def test_frozen_evidence_requires_identical_rankings_and_fingerprints(tmp_path):
    shutil.copytree(FROZEN, tmp_path / 'candidate')
    evidence.verify(tmp_path / 'candidate', FROZEN)
    path = tmp_path / 'candidate/a2-report.json'
    value = json.loads(path.read_text('utf-8'))
    value['cases'][0]['rankedCuratedArtifacts'].reverse()
    path.write_text(json.dumps(value), encoding='utf-8')
    with pytest.raises(ValueError, match='differs'):
        evidence.verify(tmp_path / 'candidate', FROZEN)


def test_active_normalization_reproduces_separate_reference_candidates(tmp_path):
    from types import SimpleNamespace
    from servicedesk.intelligence.knowledge.cli import comparison, chunk_experiment

    dataset = ROOT / 'fixtures/intelligence/service-desk'
    references = FROZEN.parent
    paragraph = tmp_path / 'paragraph'
    assert comparison(SimpleNamespace(repo_root=ROOT, dataset=dataset, output=paragraph)) == 0
    report = json.loads((paragraph / 'a2-report.json').read_text('utf-8'))
    assert report['system']['normalizationVersion'] == 'text-normalization@0.2.0'
    evidence.verify(paragraph, references / 'paragraph-normalization-v0.2')

    section = tmp_path / 'section'
    assert chunk_experiment(SimpleNamespace(repo_root=ROOT, dataset=dataset, output=section)) == 0
    evidence.verify(section, references / 'section-normalization-v0.2', section=True)
