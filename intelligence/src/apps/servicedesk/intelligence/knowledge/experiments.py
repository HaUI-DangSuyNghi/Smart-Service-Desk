"""Corpus controls with unchanged TF-IDF; no unreviewed v0.2 judgments."""

from types import SimpleNamespace

from hdpi.evaluation.candidates import Bm25Candidate, TfidfCandidate
from hdpi.evaluation.dataset import load_dataset as load_legacy
from hdpi.evaluation.dataset import require
from hdpi.evaluation.evidence import build_report
from hdpi.evaluation.gates import evaluate_gates
from hdpi.evaluation.graded import GRADED_METRIC_VERSION, graded_retrieval_metrics
from hdpi.evaluation.metrics import evaluate_retrieval_metrics, first_relevant_rank
from hdpi.evaluation.retrieval import evaluate as evaluate_legacy
from hdpi.evaluation.retrieval import stable_report
from hdpi.normalization.v1 import NormalizerV1
from hdpi.retrieval.models import digest

from .dataset import load_dataset
from .projection import project

FAILURE_TAXONOMY = (
    'LEXICAL_GAP',
    'LEXICAL_SIGNAL_DILUTION',
    'DOCUMENT_TOO_BROAD',
    'DOCUMENT_TOO_NARROW',
    'CROSS_CUTTING_POLICY_COLLISION',
    'GENERIC_OPERATIONAL_LANGUAGE_COLLISION',
    'MULTI_INTENT_QUERY',
    'AMBIGUOUS_QUERY',
    'ROOT_CAUSE_CONFLICT',
    'CHUNKING_ERROR',
    'MISSING_KNOWLEDGE',
    'QREL_INCOMPLETENESS',
    'QREL_ERROR',
    'NOISY_QUERY',
    'TYPO_OR_LANGUAGE_VARIANT',
)


def evaluate_family_control(
    root, directory, *, enriched, reference, candidate=None, experiment_id=None
):
    manifest = load_dataset(root, directory).manifest
    sources, projection = project(root, directory, enriched=enriched, allow_draft=True)
    legacy = load_legacy(directory, root)
    families = {a['artifactId']: a['legacyFamily'] for a in manifest['artifacts']}
    known = set(families)
    candidate = candidate or TfidfCandidate()
    index = candidate.build(sources)
    normalizer = NormalizerV1()
    tickets = {ticket['id']: ticket for ticket in legacy.tickets}
    pairs, cases = [], []

    for expected in legacy.gold['cases']:
        fields = tickets[expected['ticketId']]['request']['input']
        query = normalizer.normalize(fields['title'] + '\n' + fields['description']).value
        ranked = tuple(dict.fromkeys(candidate.rank(query, index)))
        require(set(ranked) <= known, 'Candidate returned unknown enriched artifact')
        grouped = tuple(dict.fromkeys(families[identifier] for identifier in ranked))
        relevant = expected['expectedRelevantLocalArtifacts']
        rank = first_relevant_rank(grouped, set(relevant))
        pairs.append((grouped, relevant))
        cases.append(
            {
                'ticketId': expected['ticketId'],
                'rankedCuratedArtifacts': list(ranked),
                'rankedLegacyFamilies': list(grouped),
                'expectedRelevantLegacyFamilies': relevant,
                'firstRelevantRank': rank,
                'needsErrorAnalysis': rank != 1,
            }
        )

    metrics = evaluate_retrieval_metrics(pairs)
    metrics.pop('caseCount')
    floor = reference['metrics']
    invariants = (
        {}
        if not enriched or experiment_id == 'A3'
        else {
            'hitAt1DidNotImprove': int(metrics['hitAt1'] <= floor['hitAt1']),
            'mrrDidNotImprove': int(metrics['mrr'] <= floor['mrr']),
        }
    )
    gates = evaluate_gates(
        metrics=metrics,
        policy={'hitAt1': floor['hitAt1'], 'hitAt3': floor['hitAt3'], 'mrr': floor['mrr']},
        invariants=invariants,
    )
    state = {'legacyDataset': legacy.identity, 'projection': projection}
    dataset = SimpleNamespace(
        identity={
            'id': 'legacy-queries-enriched-source-families',
            'version': '0.2.0',
            'digest': 'sha256:' + digest(state),
        },
        component_digests={
            'legacyTickets': legacy.component_digests['tickets'],
            'legacyGold': legacy.component_digests['retrieval'],
            'collection': projection['collectionDigest'],
            'projection': projection['projectionDigest'],
        },
    )
    system = candidate.system | {
        'projectionVersion': projection['projectionVersion'],
        'experiment': experiment_id or ('A2' if enriched else 'A1'),
        'rankingUnit': 'legacy-source-family; max child score; first-occurrence deduplication',
    }
    return build_report(
        evidence_class='AUTHORED_RETRIEVAL_REGRESSION',
        dataset=dataset,
        system=system,
        metric_version='legacy-family-retrieval@0.2.0',
        metrics=metrics,
        cases=cases,
        gates=gates,
        runtimeCitations=False,
        rightsStatus='PENDING_OWNER_APPROVAL',
        comparison={
            'kind': 'CROSS_CORPUS_LEGACY_FAMILY_CONTROL',
            'sameDatasetComparison': False,
            'v02DocumentQrels': False,
            'interpretation': 'Coarse lineage projection of frozen v0.1 judgments; human v0.2 qrels still required.',
            'baselineFingerprint': reference['fingerprint'],
            'familyMap': families,
            'deltas': {key: value - floor[key] for key, value in metrics.items()},
            'failureTaxonomy': list(FAILURE_TAXONOMY),
        },
    )


def compare_corpora(root, directory):
    baseline = stable_report(
        evaluate_legacy(root, directory, baseline=directory / 'evaluation/baseline-v0.1.json')
    )
    structural = evaluate_family_control(root, directory, enriched=False, reference=baseline)
    enriched = evaluate_family_control(root, directory, enriched=True, reference=baseline)
    return {'A0': baseline, 'A1': structural, 'A2': enriched}


def compare_a2_engines(root, directory, *, dense_provider=None, include_hybrid=False):
    """Compare engines on one frozen A2 projection and one legacy-family judgment basis.

    This is an engineering control while v0.2 document qrels remain pending. It
    intentionally does not convert legacy-family relevance into v0.2 gold.
    """
    baseline = stable_report(
        evaluate_legacy(root, directory, baseline=directory / 'evaluation/baseline-v0.1.json')
    )
    reports = {
        'B0': evaluate_family_control(
            root,
            directory,
            enriched=True,
            reference=baseline,
            candidate=TfidfCandidate(),
            experiment_id='A2-B0-TFIDF',
        ),
        'B1': evaluate_family_control(
            root,
            directory,
            enriched=True,
            reference=baseline,
            candidate=Bm25Candidate(),
            experiment_id='A2-B1-BM25',
        ),
    }
    if dense_provider is not None:
        from hdpi.evaluation.candidates import DenseCandidate

        reports['B2'] = evaluate_family_control(
            root, directory, enriched=True, reference=baseline,
            candidate=DenseCandidate(dense_provider), experiment_id='A2-B2-DENSE')
    if include_hybrid:
        from hdpi.evaluation.candidates import HybridCandidate

        require(dense_provider is not None, 'Hybrid comparison requires the pinned dense provider')
        reports['B3'] = evaluate_family_control(
            root, directory, enriched=True, reference=baseline,
            candidate=HybridCandidate(dense_provider), experiment_id='A2-B3-HYBRID')
    for report in reports.values():
        for key in ('dataset', 'components', 'metricVersion'):
            require(reports['B0'][key] == report[key], 'A2 engine candidates used different evidence ' + key)
        for key in ('normalizationVersion', 'chunkPolicyVersion', 'queryStrategyVersion',
                    'projectionVersion', 'rankingUnit'):
            require(reports['B0']['system'][key] == report['system'][key],
                    'A2 engine comparison changed a frozen representation variable')
    return reports


def evaluate_reviewed_split(root, directory, *, split='DEV', enriched=True):
    require(split in ('DEV', 'HOLDOUT'), 'Unknown evaluation split')
    dataset = load_dataset(root, directory, require_reviewed=split == 'HOLDOUT')
    selected = [query for query in dataset.queries if query['split'] == split]
    by_id = {case['queryId']: case for case in dataset.qrels['cases']}
    require(
        all(query['reviewStatus'] == 'REVIEWED' for query in selected), 'Queries await human review'
    )
    require(
        all(
            cell['grade'] is not None
            for query in selected
            for cell in by_id[query['id']]['judgments']
        ),
        'Qrels await human judgments',
    )

    sources, projection = project(root, directory, enriched=enriched)
    candidate = TfidfCandidate()
    index = candidate.build(sources)
    pairs, cases = [], []
    for query in selected:
        text = NormalizerV1().normalize(query['title'] + '\n' + query['description']).value
        ranked = candidate.rank(text, index)
        judgments = {cell['artifactId']: cell['grade'] for cell in by_id[query['id']]['judgments']}
        pairs.append((ranked, judgments))
        cases.append({'queryId': query['id'], 'rankedArtifacts': list(ranked)})

    metrics = graded_retrieval_metrics(pairs)
    metrics.pop('caseCount')
    identity = dataset.identity | {
        'digest': 'sha256:' + digest([dataset.identity, split, projection])
    }
    evidence_dataset = SimpleNamespace(identity=identity, component_digests=dataset.components)
    return build_report(
        evidence_class='AUTHORED_RETRIEVAL_REGRESSION',
        dataset=evidence_dataset,
        system=candidate.system
        | {'projectionVersion': projection['projectionVersion'], 'split': split},
        metric_version=GRADED_METRIC_VERSION,
        metrics=metrics,
        cases=cases,
        gates=evaluate_gates(metrics={}),
        runtimeCitations=False,
        rightsStatus=dataset.spec['rightsStatus'],
    )


def acceptance(root, directory):
    """Requires reviewed locked data, then compares projections on the same qrels."""
    load_dataset(root, directory, require_reviewed=True)
    reports = {}
    for split in ('DEV', 'HOLDOUT'):
        reference = evaluate_reviewed_split(root, directory, split=split, enriched=False)
        candidate = evaluate_reviewed_split(root, directory, split=split, enriched=True)
        policy = {
            key: reference['metrics'][key]
            for key in ('hitAt1', 'hitAt3', 'mrr', 'primaryHitAt1', 'ndcgAt3')
        }
        decision = evaluate_gates(metrics=candidate['metrics'], policy=policy)
        if split == 'DEV':
            require(
                any(candidate['metrics'][key] > reference['metrics'][key] for key in policy),
                'DEV did not improve',
            )
        reports[split] = {'reference': reference, 'candidate': candidate, 'gates': decision}
    return reports
