"""B0-B3 graded comparison with explicit provisional/reviewed label provenance."""
from collections import defaultdict

from hdpi.evaluation.candidates import Bm25Candidate, DenseCandidate, HybridCandidate, TfidfCandidate
from hdpi.evaluation.dataset import require
from hdpi.evaluation.evidence import build_report
from hdpi.evaluation.gates import evaluate_gates
from hdpi.evaluation.graded import GRADED_METRIC_VERSION, graded_retrieval_metrics
from hdpi.evaluation.metrics import first_relevant_rank
from hdpi.normalization.v1 import NormalizerV1
from hdpi.retrieval.models import digest

MATRIX_VERSION = 'ai05-candidate-matrix@0.1.0'
KEYS = ('B0_TFIDF', 'B1_BM25', 'B2_DENSE', 'B3_HYBRID')
PRIMARY_METRICS = ('primaryHitAt1', 'hitAt3', 'mrr', 'recallRelevantAt3', 'ndcgAt3')


def candidates(provider):
    return dict(zip(KEYS, (TfidfCandidate(), Bm25Candidate(), DenseCandidate(provider), HybridCandidate(provider))))


def freeze_descriptor(dataset, engines):
    systems = {key: candidate.system | {'evaluationMatrixVersion': MATRIX_VERSION,
               'projectionVersion': dataset.projection['projectionVersion'],
               'judgmentBasis': dataset.judgment_basis} for key, candidate in engines.items()}
    return {'matrixVersion': MATRIX_VERSION, 'dataset': dataset.identity,
            'components': dataset.component_digests, 'systems': systems,
            'metricVersion': GRADED_METRIC_VERSION, 'judgmentBasis': dataset.judgment_basis}


def evaluate_matrix(dataset, engines, *, split='DEV'):
    require(split in ('DEV', 'HOLDOUT'), 'Unknown matrix split')
    require(dataset.judgment_basis == 'HUMAN_REVIEWED' or split == 'DEV',
            'Provisional qrels support DEV engineering only; HOLDOUT is not a reviewed benchmark')
    require(tuple(engines) == KEYS, 'The frozen matrix requires exactly B0-B3')
    selected = [query for query in dataset.queries if query['split'] == split]
    require(bool(selected), 'Empty matrix split')
    judgments = {case['queryId']: case['judgments'] for case in dataset.qrels['cases']}
    normalizer, reports = NormalizerV1(), {}
    for name, candidate in engines.items():
        for key in ('normalizationVersion', 'chunkPolicyVersion', 'queryStrategyVersion'):
            require(candidate.system[key] == engines[KEYS[0]].system[key], 'Matrix input representation differs')
        index = candidate.build(dataset.sources)
        cases, pairs, slices = [], [], defaultdict(list)
        for query in selected:
            text = normalizer.normalize(query['title'] + '\n' + query['description']).value
            ranked = tuple(dict.fromkeys(candidate.rank(text, index)))
            cells = judgments[query['id']]
            grades = {cell['artifactId']: cell['grade'] for cell in cells}
            pair = (ranked, grades)
            metrics = graded_retrieval_metrics([pair])
            metrics.pop('caseCount')
            relevant = {identifier for identifier, grade in grades.items() if grade > 0}
            cases.append({'queryId': query['id'], 'scenario': query['scenario'],
                          'rankedArtifacts': list(ranked), 'metrics': metrics,
                          'firstRelevantRank': first_relevant_rank(ranked, relevant)})
            pairs.append(pair)
            slices['scenario:' + query['scenario']].append(pair)
            for tag in query['coverage']:
                slices['coverage:' + tag].append(pair)
            if any(cell['rationaleCode'].startswith('HARD_NEGATIVE_') for cell in cells):
                slices['hard-negative'].append(pair)
        metrics = graded_retrieval_metrics(pairs)
        metrics.pop('caseCount')
        system = candidate.system | {'evaluationMatrixVersion': MATRIX_VERSION,
                    'projectionVersion': dataset.projection['projectionVersion'],
                    'judgmentBasis': dataset.judgment_basis, 'split': split}
        reports[name] = build_report(
            evidence_class='CANDIDATE_COMPARISON', dataset=dataset, system=system,
            metric_version=GRADED_METRIC_VERSION, metrics=metrics, cases=cases,
            gates=evaluate_gates(metrics={}), runtimeCitations=False, rightsStatus=dataset.rights_status,
            comparison={'judgmentBasis': dataset.judgment_basis,
                        'humanReviewed': dataset.judgment_basis == 'HUMAN_REVIEWED',
                        'runtimePromotion': 'NOT_AUTHORIZED',
                        'slices': {key: graded_retrieval_metrics(value) for key, value in sorted(slices.items())}})
    baseline = reports[KEYS[0]]
    paired = {}
    for name, report in reports.items():
        require(report['dataset'] == baseline['dataset'] and report['components'] == baseline['components'],
                'Matrix data identities differ')
        for key in ('normalizationVersion', 'chunkPolicyVersion', 'queryStrategyVersion'):
            require(report['system'][key] == baseline['system'][key], 'Matrix input representation differs')
        changes = []
        for before, after in zip(baseline['cases'], report['cases']):
            require(before['queryId'] == after['queryId'], 'Matrix query association differs')
            changes.append({'queryId': after['queryId'],
                'baselineFirstRelevantRank': before['firstRelevantRank'],
                'candidateFirstRelevantRank': after['firstRelevantRank'],
                'deltas': {key: after['metrics'][key] - before['metrics'][key] for key in PRIMARY_METRICS}})
        paired[name] = {
            'aggregateDeltas': {key: report['metrics'][key] - baseline['metrics'][key] for key in PRIMARY_METRICS},
            'changes': changes,
            'counts': {key: {label: sum((cell['deltas'][key] > 0 if label == 'improved' else
                                       cell['deltas'][key] < 0 if label == 'regressed' else
                                       cell['deltas'][key] == 0) for cell in changes)
                             for label in ('improved', 'unchanged', 'regressed')} for key in PRIMARY_METRICS}}
    freeze = freeze_descriptor(dataset, engines)
    summary = {'schemaVersion': '0.2.0', 'engineeringResult': 'PASS', 'benchmarkClaim': False,
               'comparisonKind': 'V02_GRADED_CANDIDATE_MATRIX', 'split': split,
               'caseCount': len(selected), 'judgmentBasis': dataset.judgment_basis,
               'humanReviewed': dataset.judgment_basis == 'HUMAN_REVIEWED',
               'holdout': 'NOT_EVALUATED' if split == 'DEV' else 'EVALUATED_ONCE',
               'runtimePromotion': 'NOT_AUTHORIZED', 'dataset': dataset.identity,
               'components': dataset.component_digests,
               'reports': {key: {field: report[field] for field in ('metrics', 'fingerprint', 'result')}
                           for key, report in reports.items()},
               'pairedComparisons': paired, 'freeze': freeze, 'freezeDigest': 'sha256:' + digest(freeze)}
    return reports, summary
