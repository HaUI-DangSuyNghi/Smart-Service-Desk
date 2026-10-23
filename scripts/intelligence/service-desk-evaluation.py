"""Service Desk evaluation CLI that composes external app adapters with the generic HPDI wheel."""

import argparse
import json
from pathlib import Path
import sys


def bootstrap(repo_root, installed):
    if not installed:
        sys.path.insert(0, str(repo_root / 'intelligence/src'))
    sys.path.insert(0, str(repo_root / 'intelligence/src/apps'))

    import hdpi
    if installed and 'site-packages' not in Path(hdpi.__file__).resolve().parts:
        raise RuntimeError('Installed evaluation must use the generic wheel from site-packages')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo-root', type=Path, default=Path('.'))
    parser.add_argument('--installed', action='store_true')
    sub = parser.add_subparsers(dest='command', required=True)

    retrieval = sub.add_parser('retrieval')
    retrieval.add_argument('--dataset', '--fixtures-root', dest='dataset', required=True, type=Path)
    retrieval.add_argument('--baseline', type=Path)
    retrieval.add_argument('--candidate', choices=('tfidf', 'bm25', 'dense', 'hybrid'), default='tfidf')
    retrieval.add_argument('--model-directory', type=Path)
    retrieval.add_argument('--gates', type=json.loads, default={})
    retrieval.add_argument('--output', type=Path)

    conformance = sub.add_parser('conformance')
    conformance.add_argument('--dataset', required=True, type=Path)
    conformance.add_argument('--config', required=True, type=Path)
    conformance.add_argument('--output', type=Path)

    args = parser.parse_args()
    root = args.repo_root.resolve()
    bootstrap(root, args.installed)

    from hdpi.domain.errors import EngineConfigurationError, EngineUnavailable
    from hdpi.evaluation.dataset import parse_json
    from hdpi.evaluation.evidence import write_report
    from hdpi.evaluation.errors import EvaluationError
    from hdpi.evaluation.gates import exit_code

    try:
        if args.command == 'retrieval':
            from hdpi.evaluation.candidates import create_candidate
            from hdpi.evaluation.retrieval import stable_report
            from servicedesk.intelligence.evaluation.retrieval import evaluate

            candidate = create_candidate(args.candidate, model_directory=args.model_directory)
            report = stable_report(
                evaluate(root, args.dataset, gates=args.gates, baseline=args.baseline, candidate=candidate)
            )
        else:
            from servicedesk.intelligence.evaluation.conformance import evaluate

            config = parse_json(args.config.read_bytes())
            report = evaluate(
                root,
                args.dataset,
                profile_version=config['profileVersion'],
                expected_runtime=config['expectedRuntime'],
                normalization_version=config['normalizationVersion'],
            )
    except (EvaluationError, EngineConfigurationError, EngineUnavailable,
            OSError, ValueError, TypeError, KeyError) as error:
        print('Evaluation rejected: ' + str(error), file=sys.stderr)
        return 2

    write_report(report, args.output)
    print(json.dumps({
        'evidenceClass': report['evidenceClass'],
        'dataset': report['dataset'],
        'caseCount': report['caseCount'],
        'metrics': report['metrics'],
        'result': report['result'],
    }, sort_keys=True))
    return exit_code(report)


if __name__ == '__main__':
    raise SystemExit(main())
