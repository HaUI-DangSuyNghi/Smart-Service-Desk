"""Application-neutral authored retrieval measurement.

Applications own dataset schemas and query construction. This module receives
already-constructed query cases and only performs normalization, ranking and
metric calculation.
"""

from pathlib import Path

from ..normalization.v1 import NormalizerV1
from .baseline import load_baseline
from .candidates import TfidfCandidate
from .comparison import compare_candidate
from .dataset import require
from .evidence import build_report
from .gates import evaluate_gates
from .metrics import METRIC_VERSION, evaluate_retrieval_metrics, first_relevant_rank


def evaluate(dataset, *, gates=None, baseline=None, candidate=None, system_overrides=None):
    candidate = candidate or TfidfCandidate()
    require(
        candidate.system['normalizationVersion'] == NormalizerV1.version,
        'Unsupported query normalization',
    )
    require(bool(dataset.sources) and bool(dataset.cases), 'Empty authored evaluation dataset')

    index = candidate.build(dataset.sources)
    known = {source[0] for source in dataset.sources}
    normalizer, cases, pairs = NormalizerV1(), [], []

    for case in dataset.cases:
        query = normalizer.normalize(case.query).value
        ranked = list(dict.fromkeys(candidate.rank(query, index)))
        require(
            all(isinstance(item, str) for item in ranked) and set(ranked) <= known,
            'Candidate returned unknown artifact',
        )
        relevant = tuple(case.relevant)
        require(
            bool(relevant) and set(relevant) <= known,
            'Evaluation case references unknown or empty relevance',
        )
        pairs.append((ranked, relevant))
        cases.append({
            'caseId': case.case_id,
            'rankedArtifacts': ranked,
            'expectedRelevantArtifacts': list(relevant),
            'firstRelevantRank': first_relevant_rank(ranked, set(relevant)),
        })

    measured = evaluate_retrieval_metrics(pairs)
    case_count = measured.pop('caseCount')
    policy, extra = dict(gates or {}), {}

    if baseline is not None:
        reference = load_baseline(baseline) if isinstance(baseline, Path) else baseline
        extra['comparison'] = compare_candidate(
            dataset=dataset.identity,
            components=dataset.component_digests,
            metric_version=METRIC_VERSION,
            case_count=case_count,
            metrics=measured,
            baseline=reference,
        )
        for name in reference['gatedMetrics']:
            policy[name] = max(policy.get(name, 0), reference['metrics'][name])

    decision = evaluate_gates(metrics=measured, policy=policy)
    system = candidate.system | dict(system_overrides or {})
    report = build_report(
        evidence_class='AUTHORED_RETRIEVAL_REGRESSION',
        dataset=dataset,
        system=system,
        metric_version=METRIC_VERSION,
        metrics=measured,
        cases=cases,
        gates=decision,
        runtimeCitations=False,
        rightsStatus=dataset.rights_status,
        **extra,
    )
    return {
        **report,
        **measured,
        'suggestedGates': {
            key: measured[key] >= value for key, value in policy.items()
        },
    }


def stable_report(report):
    return {
        key: value
        for key, value in report.items()
        if key not in report['metrics'] and key != 'suggestedGates'
    }
