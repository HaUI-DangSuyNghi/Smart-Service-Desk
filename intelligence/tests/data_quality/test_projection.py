from copy import deepcopy
from pathlib import Path

import pytest
import yaml
from hdpi.evaluation.errors import EvaluationError
from servicedesk.intelligence.knowledge.lexicon import validate_lexicon
from servicedesk.intelligence.knowledge.projection import load_corpus, project, render

ROOT = Path(__file__).resolve().parents[3]
DATA = ROOT / 'fixtures/intelligence/service-desk'


def test_projection_is_rebuildable_and_preserves_lineage():
    first = project(ROOT, DATA, allow_draft=True)
    assert first == project(ROOT, DATA, allow_draft=True)
    assert len(first[0]) == 10 and first[1]['status'] == 'DRAFT_ONLY'
    assert first[1]['runtimeCitations'] is False
    assert all(row['sourceChecksum'] and row['metadataDigest'] for row in first[1]['lineage'])
    assert (
        project(ROOT, DATA, enriched=False, allow_draft=True)[1]['projectionDigest']
        != first[1]['projectionDigest']
    )


def test_no_implicit_approval_or_query_expansion():
    with pytest.raises(EvaluationError, match='Unreviewed'):
        project(ROOT, DATA)


def test_metadata_changes_projection_bytes():
    manifest, bodies, concepts, _ = load_corpus(ROOT, DATA)
    item = deepcopy(manifest['artifacts'][0])
    before = render(item, bodies[item['artifactId']], concepts, enriched=True, allow_draft=True)
    item['positiveSignals'].append('Synthetic additional observation')
    assert (
        render(item, bodies[item['artifactId']], concepts, enriched=True, allow_draft=True)
        != before
    )


@pytest.mark.parametrize('kind', ['alias', 'conflict', 'broad', 'approval'])
def test_lexicon_rejects_invalid_governance(kind):
    value = yaml.safe_load((DATA / 'knowledge/lexicon-v0.2.yaml').read_text('utf-8'))
    if kind == 'alias':
        value['concepts'][0]['variants'].append('wi-fi')
    elif kind == 'conflict':
        value['concepts'][1]['variants'].append('wi-fi')
    elif kind == 'broad':
        value['concepts'][-1]['ambiguity'] = 'low'
    else:
        value['concepts'][0]['reviewStatus'] = 'REVIEWED'
    with pytest.raises(EvaluationError):
        validate_lexicon(value, ROOT)
