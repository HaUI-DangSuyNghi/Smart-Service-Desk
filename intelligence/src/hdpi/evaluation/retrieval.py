"""Authored document retrieval regression; local IDs are never runtime citations."""
import argparse
import json
from pathlib import Path
import sys

from ..normalization.v1 import NormalizerV1
from ..domain.errors import EngineConfigurationError, EngineUnavailable
from .baseline import load_baseline
from .candidates import TfidfCandidate, create_candidate
from .comparison import compare_candidate
from .dataset import load_dataset, require
from .errors import EvaluationError
from .evidence import build_report, write_report
from .gates import evaluate_gates, exit_code
from .metrics import METRIC_VERSION, evaluate_retrieval_metrics, first_relevant_rank


def load_authoring_index(root, fixtures_root):
    """Compatibility entry point, now validating the complete dataset."""
    dataset = load_dataset(fixtures_root, root)
    return TfidfCandidate().build(dataset.sources), dataset.knowledge


def evaluate(root: Path, fixtures_root: Path, *, gates=None, baseline=None, candidate=None):
    dataset = load_dataset(fixtures_root, root)
    candidate = candidate or TfidfCandidate()
    require(candidate.system['normalizationVersion'] == NormalizerV1.version, 'Unsupported query normalization')
    index = candidate.build(dataset.sources)
    by_id = {ticket['id']: ticket for ticket in dataset.tickets}
    known = {source[0] for source in dataset.sources}
    normalizer, cases, pairs = NormalizerV1(), [], []
    for case in dataset.gold['cases']:
        fields = by_id[case['ticketId']]['request']['input']
        query = normalizer.normalize(fields['title'] + '\n' + fields['description']).value
        ranked = list(dict.fromkeys(candidate.rank(query, index)))
        require(all(isinstance(item, str) for item in ranked) and set(ranked) <= known, 'Candidate returned unknown artifact')
        relevant = case['expectedRelevantLocalArtifacts']
        pairs.append((ranked, relevant))
        cases.append({'ticketId': case['ticketId'], 'rankedLocalArtifacts': ranked,
                      'expectedRelevantLocalArtifacts': relevant,
                      'firstRelevantRank': first_relevant_rank(ranked, set(relevant))})
    measured = evaluate_retrieval_metrics(pairs)
    case_count = measured.pop('caseCount')
    policy, extra = dict(gates or {}), {}
    if baseline is not None:
        reference = load_baseline(baseline) if isinstance(baseline, Path) else baseline
        extra['comparison'] = compare_candidate(dataset=dataset.identity, components=dataset.component_digests,
            metric_version=METRIC_VERSION, case_count=case_count, metrics=measured, baseline=reference)
        for name in reference['gatedMetrics']:
            policy[name] = max(policy.get(name, 0), reference['metrics'][name])
    decision = evaluate_gates(metrics=measured, policy=policy)
    report = build_report(evidence_class='AUTHORED_RETRIEVAL_REGRESSION', dataset=dataset, system=candidate.system,
        metric_version=METRIC_VERSION, metrics=measured, cases=cases, gates=decision,
        runtimeCitations=False, rightsStatus=dataset.gold['rightsStatus'], **extra)
    # Compatibility for AI-03 library consumers; CLI emits only the stable schema.
    return {**report, **measured, 'suggestedGates': {key: measured[key] >= value for key, value in policy.items()}}


def stable_report(report):
    return {key: value for key, value in report.items() if key not in report['metrics'] and key != 'suggestedGates'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo-root', type=Path, default=Path('.'))
    parser.add_argument('--dataset', '--fixtures-root', dest='dataset', required=True, type=Path)
    parser.add_argument('--baseline', type=Path)
    parser.add_argument('--candidate', choices=('tfidf', 'bm25', 'dense', 'hybrid'), default='tfidf',
                        help='Authoring evaluation only; does not select the runtime engine')
    parser.add_argument('--model-directory', type=Path)
    parser.add_argument('--gates', type=json.loads, default={})
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    try:
        candidate = create_candidate(args.candidate, model_directory=args.model_directory)
        report = stable_report(evaluate(args.repo_root, args.dataset, gates=args.gates,
                                       baseline=args.baseline, candidate=candidate))
        write_report(report, args.output)
        print(json.dumps({key: report[key] for key in ('evidenceClass', 'dataset', 'caseCount', 'metrics', 'gates', 'result')}, sort_keys=True))
        return exit_code(report)
    except (EvaluationError, EngineConfigurationError, EngineUnavailable, OSError, ValueError, TypeError, KeyError) as error:
        print('Evaluation rejected: ' + str(error), file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
