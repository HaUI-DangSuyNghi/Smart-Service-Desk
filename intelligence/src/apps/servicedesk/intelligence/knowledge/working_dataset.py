"""Explicit v0.2 working dataset using existing AI-assisted labels by opt-in."""
from collections import Counter
from dataclasses import dataclass
from hashlib import sha256

import yaml
from hdpi.evaluation.dataset import UniqueLoader, parse_json, require
from hdpi.retrieval.models import digest

from .annotations import ANNOTATION_POLICY_VERSION, validate_rationale
from .contracts import read_pin
from .dataset import load_dataset
from .projection import project


@dataclass(frozen=True)
class WorkingDataset:
    identity: dict
    component_digests: dict
    queries: tuple
    qrels: dict
    sources: tuple
    projection: dict
    judgment_basis: str
    rights_status: str


def load_working_dataset(root, directory):
    root, directory = root.resolve(), directory.resolve()
    path = directory / 'dataset-v0.2-provisional.yaml'
    raw = path.read_bytes()
    profile = yaml.load(raw, Loader=UniqueLoader)
    require(set(profile) == {'schemaVersion', 'id', 'version', 'status', 'annotationPolicyVersion',
                            'baseDataset', 'qrelsManifest', 'qrels', 'evaluation'}, 'Invalid working dataset profile')
    require(profile['schemaVersion'] == profile['version'] == '0.2.0'
            and profile['id'] == 'service-desk-ai05-provisional-v02'
            and profile['annotationPolicyVersion'] == ANNOTATION_POLICY_VERSION
            and profile['status'] == 'AI_ASSISTED_WORKING_DATASET', 'Unsupported working dataset identity')
    require(profile['evaluation'] == {
        'usage': 'PROVISIONAL_ENGINEERING_COMPARISON', 'split': 'DEV',
        'authoritative': False, 'humanReviewed': False, 'benchmarkClaim': False,
        'runtimePromotion': 'NOT_AUTHORIZED'}, 'Provisional profile claims unsupported acceptance')
    require(all(type(profile['evaluation'][key]) is bool for key in
                ('authoritative', 'humanReviewed', 'benchmarkClaim')), 'Invalid working provenance flag types')
    expected = {'baseDataset': directory / 'dataset-v0.2.yaml',
                'qrelsManifest': directory / 'retrieval/ai-assisted/manifest-v0.2.json',
                'qrels': directory / 'retrieval/ai-assisted/qrels-candidate-v0.2.json'}
    values = {}
    for name, target in expected.items():
        pin = profile[name]
        require((root / pin['path']).resolve() == target, 'Unexpected working dataset component path')
        values[name] = read_pin(root, pin)
    base = load_dataset(root, directory)
    manifest, qrels = parse_json(values['qrelsManifest']), parse_json(values['qrels'])
    require(manifest['kind'] == 'AI_ASSISTED_REVIEW_EVIDENCE'
            and qrels['kind'] == 'AI_ASSISTED_CANDIDATE_QRELS'
            and manifest['authoritative'] is qrels['authoritative'] is False
            and manifest['benchmarkClaim'] is qrels['benchmarkClaim'] is False,
            'Working labels must retain AI-assisted provenance')
    require(all(manifest['candidate'][key] == profile['qrels'][key] for key in ('path', 'sha256', 'byteSize')),
            'Working qrels differ from their source manifest')
    for source in (manifest['source'], qrels):
        require(source['queriesDigest'] == base.components['queries']
                and source['collectionDigest'] == base.qrels['collectionDigest']
                and source['annotationPolicyVersion'] == ANNOTATION_POLICY_VERSION,
                'Working qrels target another query/corpus/policy version')
    queries = {query['id']: query for query in base.queries}
    cases = qrels['cases']
    require([case['queryId'] for case in cases] == list(queries), 'Working qrel query matrix mismatch')
    known = set(base.bodies)
    grades = Counter()
    for case in cases:
        query = queries[case['queryId']]
        require(all(case[key] == query[key] for key in ('split', 'scenario', 'scenarioFamily')),
                'Working qrel query slice mismatch')
        cells = case['judgments']
        require(len(cells) == len(known) and {cell['artifactId'] for cell in cells} == known,
                'Working qrel artifact matrix mismatch')
        for cell in cells:
            require(type(cell['grade']) is int and cell['grade'] in (0, 1, 2)
                    and isinstance(cell['reason'], str) and bool(cell['reason'].strip()), 'Invalid working relevance cell')
            validate_rationale(cell['grade'], cell['rationaleCode'])
            grades[str(cell['grade'])] += 1
        require(any(cell['grade'] == 2 for cell in cells), 'Working query lacks a primary artifact')
    require(qrels['summary']['totalQueries'] == manifest['candidate']['totalQueries'] == len(queries)
            and qrels['summary']['totalJudgments'] == manifest['candidate']['totalJudgments'] == sum(grades.values())
            and dict(grades) == qrels['summary']['gradeDistribution'], 'Stale working qrel summary')
    sources, projection = project(root, directory, enriched=True, allow_draft=True)
    components = base.components | {'qrels': profile['qrels']['sha256'], 'workingProfile': sha256(raw).hexdigest(),
                                    'qrelsManifest': profile['qrelsManifest']['sha256'],
                                    'projection': projection['projectionDigest']}
    identity = {'id': profile['id'], 'version': profile['version'],
                'digest': 'sha256:' + digest([base.identity, components, profile['evaluation']])}
    return WorkingDataset(identity, components, base.queries, qrels, sources, projection,
                          'AI_ASSISTED_PROVISIONAL', base.spec['rightsStatus'])


def load_reviewed_dataset(root, directory):
    base = load_dataset(root, directory, require_reviewed=True)
    sources, projection = project(root, directory, enriched=True)
    return WorkingDataset(base.identity, base.components | {'projection': projection['projectionDigest']},
                          base.queries, base.qrels, sources, projection, 'HUMAN_REVIEWED', base.spec['rightsStatus'])
