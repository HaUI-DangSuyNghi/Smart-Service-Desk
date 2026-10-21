"""Run all evaluation tracks with explicit application inputs and safe outputs."""
import argparse
from hashlib import sha256
import json
from pathlib import Path
import sys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo-root', type=Path, default=Path('.'))
    parser.add_argument('--dataset', type=Path, required=True)
    parser.add_argument('--baseline', type=Path, required=True)
    parser.add_argument('--checks', type=Path, required=True)
    parser.add_argument('--junit', type=Path, required=True)
    parser.add_argument('--conformance-config', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--installed', action='store_true')
    parser.add_argument('--candidate', choices=('tfidf', 'bm25'), default='tfidf')
    args = parser.parse_args()
    if not args.installed:
        sys.path.insert(0, str(args.repo_root.resolve() / 'intelligence/src'))
    import hdpi
    if args.installed and 'site-packages' not in Path(hdpi.__file__).resolve().parts:
        raise RuntimeError('Installed evaluation must load the wheel in site-packages')
    from hdpi.evaluation.conformance import evaluate as conformance
    from hdpi.evaluation.evidence import serialize_report, write_report
    from hdpi.evaluation.retrieval import evaluate as retrieval, stable_report
    from hdpi.evaluation.security import evaluate as security
    from hdpi.evaluation.dataset import parse_json
    from hdpi.evaluation.candidates import Bm25Candidate, TfidfCandidate

    candidate = Bm25Candidate() if args.candidate == 'bm25' else TfidfCandidate()

    config = parse_json(args.conformance_config.read_bytes())

    reports = {
        'retrieval': stable_report(retrieval(args.repo_root, args.dataset, baseline=args.baseline, candidate=candidate)),
        'conformance': conformance(args.repo_root, args.dataset, profile_version=config['profileVersion'],
                                  expected_runtime=config['expectedRuntime'], normalization_version=config['normalizationVersion']),
        'security': security(args.repo_root, args.dataset, junit=args.junit, checks=args.checks),
    }
    repeated = stable_report(retrieval(args.repo_root, args.dataset, baseline=args.baseline, candidate=candidate))
    if serialize_report(reports['retrieval']) != serialize_report(repeated):
        raise RuntimeError('Deterministic retrieval run changed')
    for name, report in reports.items():
        write_report(report, args.output / (name + '-report.json'))
    summary = {'schemaVersion': '1.0', 'benchmarkClaim': False, 'dataset': reports['retrieval']['dataset'],
               'reports': {name: {'result': report['result'], 'fingerprint': report['fingerprint'],
                    'reportDigest': 'sha256:' + sha256(serialize_report(report).encode('utf-8')).hexdigest()}
                    for name, report in reports.items()},
               'result': 'PASS' if all(report['result'] == 'PASS' for report in reports.values()) else 'FAIL'}
    (args.output / 'evaluation-summary.json').write_text(json.dumps(summary, indent=2, sort_keys=True) + '\n',
                                                       encoding='utf-8', newline='\n')
    print(json.dumps(summary, sort_keys=True))
    return 0 if summary['result'] == 'PASS' else 1


if __name__ == '__main__':
    raise SystemExit(main())
