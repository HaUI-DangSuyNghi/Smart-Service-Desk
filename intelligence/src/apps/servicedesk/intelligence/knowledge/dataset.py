"""Application-owned v0.2 validation; the AI-04 legacy loader stays unchanged."""

import json
from collections import Counter
from dataclasses import dataclass
from hashlib import sha256
from itertools import combinations

import yaml
from hdpi.evaluation.dataset import UniqueLoader, parse_json, require
from hdpi.retrieval.models import digest

from .annotations import validate_judgments
from .contracts import SCHEMA_DIRECTORY, read_pin, review_consistency, validate_schema
from .projection import load_corpus, project
from .quality import jaccard, longest_phrase, safety_findings, tokens, trigram_containment


@dataclass(frozen=True)
class KnowledgeDataset:
    identity: dict
    spec: dict
    manifest: dict
    bodies: dict
    concepts: dict
    queries: tuple
    qrels: dict
    components: dict
    findings: dict


def load_components(root, directory):
    raw = (directory / 'dataset-v0.2.yaml').read_bytes()
    spec = yaml.load(raw, Loader=UniqueLoader)
    validate_schema(spec, 'dataset-v0.2', root)

    for name, pin in spec['schemas'].items():
        expected = root / SCHEMA_DIRECTORY / (name + '.schema.json')
        require((root / pin['path']).resolve() == expected, 'Unexpected application schema path')
        read_pin(root, pin)

    values = {}
    for name, pin in spec['components'].items():
        path = (root / pin['path']).resolve()
        require(path.is_relative_to(directory), 'Component outside dataset')
        values[name] = read_pin(root, pin)

    return raw, spec, values


def validate_legacy(root, directory, manifest, raw):
    for pin in parse_json(raw)['pins']:
        read_pin(root, pin)

    sources = parse_json((directory / 'knowledge/manifest.json').read_bytes())
    families = {artifact['artifactId'] for artifact in sources['artifacts']}
    declared = {artifact['legacyFamily'] for artifact in manifest['artifacts']}
    require(declared == families, 'Legacy family coverage incomplete')


def validate_query_splits(queries):
    require(
        len(queries) == 72 and len({q['id'] for q in queries}) == 72, 'Expected 72 distinct queries'
    )
    require(
        Counter(q['split'] for q in queries) == {'DEV': 48, 'HOLDOUT': 24},
        'Invalid DEV/HOLDOUT counts',
    )

    scenarios = ('NETWORK', 'ACCOUNT', 'SOFTWARE', 'HARDWARE', 'OTHER', 'AMBIGUOUS')
    expected = {
        (scenario, split): count
        for scenario in scenarios
        for split, count in [('DEV', 8), ('HOLDOUT', 4)]
    }
    require(
        Counter((q['scenario'], q['split']) for q in queries) == expected,
        'Scenario/split coverage mismatch',
    )

    families = {'DEV': set(), 'HOLDOUT': set()}
    texts = set()
    for query in queries:
        split = query['split']
        family = query['scenarioFamily']
        require(family not in families[split], 'Duplicate scenario family')
        families[split].add(family)

        normalized = ' '.join(tokens(query['title'] + '\n' + query['description']))
        require(normalized not in texts, 'Exact duplicate queries')
        texts.add(normalized)

    require(not families['DEV'] & families['HOLDOUT'], 'Scenario family crosses DEV/HOLDOUT')
    for first, second in combinations(queries, 2):
        if first['split'] == second['split']:
            continue

        left = first['title'] + ' ' + first['description']
        right = second['title'] + ' ' + second['description']
        require(jaccard(left, right) < 0.60, 'Near-duplicate query crosses DEV/HOLDOUT')


def validate_safety(root, manifest, queries, values):
    texts = [json.dumps(query, ensure_ascii=False) for query in queries]
    texts.extend(values[name].decode('utf-8') for name in ('lexicon', 'qrels'))

    for artifact in manifest['artifacts']:
        texts.append(json.dumps(artifact, ensure_ascii=False))
        texts.extend(
            read_pin(root, artifact[stage]).decode('utf-8') for stage in ('raw', 'curated')
        )

    require(
        not any(safety_findings(text) for text in texts),
        'Secret/PII finding; redact input before evaluation',
    )


def phrase_overlap(queries, bodies):
    findings = []
    for query in queries:
        text = query['title'] + '\n' + query['description']
        for identifier, body in bodies.items():
            count = longest_phrase(text, body)
            trigram = trigram_containment(text, body)
            if count > 8 or trigram > 0.35:
                findings.append(
                    {
                        'queryId': query['id'],
                        'artifactId': identifier,
                        'wordCount': count,
                        'trigramContainment': trigram,
                    }
                )
    return findings


def validate_release(root, directory, spec, manifest, concepts, components, collection, findings):
    require(
        spec['status'] == 'REVIEWED' and spec['rightsStatus'] == 'APPROVED_INTERNAL_EVAL',
        'Dataset review/rights pending',
    )
    require(
        all(
            a['status'] == 'REVIEWED' and a['rightsStatus'] == 'APPROVED_INTERNAL_EVAL'
            for a in manifest['artifacts']
        ),
        'Corpus review/rights pending',
    )
    require(
        all(c['reviewStatus'] == 'REVIEWED' for c in concepts.values()), 'Lexicon review pending'
    )
    require(
        findings['unreviewedQueryCount'] == 0 and findings['pendingJudgmentCount'] == 0,
        'Human annotation pending',
    )
    require(not findings['phraseOverlapReview'], 'Unresolved query/document overlap review')
    require(
        spec['holdout']['state'] == 'LOCKED' and spec['holdout']['lock'] is not None,
        'Holdout not locked',
    )

    lock = spec['holdout']['lock']
    actual = {
        'collectionDigest': collection,
        'queriesDigest': components['queries'],
        'qrelsDigest': components['qrels'],
        'projectionDigest': project(root, directory)[1]['projectionDigest'],
    }
    require(all(lock[key] == value for key, value in actual.items()), 'Stale holdout lock')

    agreement = findings['agreement']
    require(agreement is not None, 'Missing independent annotation agreement')
    require(agreement['rawAgreement'] >= 0.88, 'Raw annotation agreement needs review')
    linear = agreement['linearWeightedCohenKappa']
    require(linear is not None and linear >= 0.70, 'Linear weighted annotation agreement needs review')
    alpha = agreement['krippendorffAlphaOrdinal']
    require(alpha is not None and alpha >= 0.70, 'Ordinal Krippendorff alpha needs review')
    positive = agreement['positiveSpecificAgreement']
    require(positive is not None and positive >= 0.75, 'Positive specific agreement needs review')


def load_dataset(root, directory, *, require_reviewed=False):
    root, directory = root.resolve(), directory.resolve()
    spec_raw, spec, values = load_components(root, directory)
    components = {name: pin['sha256'] for name, pin in spec['components'].items()}
    manifest, bodies, concepts, collection = load_corpus(root, directory)

    expected_paths = {
        'knowledge': 'knowledge/manifest-v0.2.json',
        'lexicon': 'knowledge/lexicon-v0.2.yaml',
    }
    for name, relative in expected_paths.items():
        declared = (root / spec['components'][name]['path']).resolve()
        require(declared == directory / relative, 'Corpus/lexicon component path mismatch')

    validate_legacy(root, directory, manifest, values['legacyEvidence'])
    queries = tuple(
        parse_json(line) for line in values['queries'].decode('utf-8').splitlines() if line.strip()
    )
    for query in queries:
        validate_schema(query, 'query', root)
        review_consistency(query, query['reviewStatus'])

    validate_query_splits(queries)
    validate_safety(root, manifest, queries, values)

    qrels = parse_json(values['qrels'])
    validate_schema(qrels, 'qrels', root)
    require(qrels['collectionDigest'] == collection, 'Qrels collection mismatch')
    require(qrels['queriesDigest'] == components['queries'], 'Qrels query mismatch')

    findings = validate_judgments(qrels, queries, bodies, require_reviewed=require_reviewed)
    findings.update(
        secretFindingCount=0,
        splitLeakageCount=0,
        phraseOverlapReview=phrase_overlap(queries, bodies),
        unreviewedQueryCount=sum(q['reviewStatus'] != 'REVIEWED' for q in queries),
    )

    reviewed = (
        require_reviewed or spec['status'] == 'REVIEWED' or spec['holdout']['state'] == 'LOCKED'
    )
    if reviewed:
        validate_release(
            root, directory, spec, manifest, concepts, components, collection, findings
        )
    else:
        require(
            spec['holdout'] == {'state': 'DRAFT_NOT_LOCKED', 'lock': None},
            'Draft claims holdout lock',
        )

    state = {
        'spec': sha256(spec_raw).hexdigest(),
        'components': components,
        'collectionDigest': collection,
    }
    identity = {'id': spec['id'], 'version': spec['version'], 'digest': 'sha256:' + digest(state)}
    return KnowledgeDataset(
        identity, spec, manifest, bodies, concepts, queries, qrels, components, findings
    )
