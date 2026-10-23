"""Capture immutable authored evidence from an explicit base, never approve it."""

import argparse
import json
import subprocess
import sys
from hashlib import sha256
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-revision', required=True)
    parser.add_argument('--dataset', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    sys.path.insert(0, str(root / 'intelligence/src'))
    sys.path.insert(0, str(root / 'intelligence/src/apps'))
    from servicedesk.intelligence.evaluation.retrieval import evaluate
    from hdpi.evaluation.retrieval import stable_report

    revision = subprocess.check_output(
        ['git', 'rev-parse', args.base_revision + '^{commit}'], text=True, cwd=root
    ).strip()
    names = subprocess.check_output(
        ['git', 'ls-tree', '-r', '--name-only', revision, '--', args.dataset.as_posix()],
        text=True,
        cwd=root,
    ).splitlines()
    pins = []
    for name in names:
        raw = subprocess.check_output(['git', 'show', revision + ':' + name], cwd=root)
        if (root / name).read_bytes() != raw:
            raise ValueError('Legacy bytes differ from base: ' + name)
        pins.append(dict(path=name, sha256=sha256(raw).hexdigest(), byteSize=len(raw)))
    report = stable_report(
        evaluate(root, args.dataset, baseline=args.dataset / 'evaluation/baseline-v0.1.json')
    )
    causes = {
        'TICKET-002': ['MULTI_INTENT_QUERY', 'CROSS_CUTTING_POLICY_COLLISION'],
        'TICKET-005': ['LEXICAL_SIGNAL_DILUTION', 'CROSS_CUTTING_POLICY_COLLISION'],
        'TICKET-007': ['DOCUMENT_TOO_BROAD'],
        'TICKET-008': ['GENERIC_OPERATIONAL_LANGUAGE_COLLISION'],
        'TICKET-011': ['QREL_INCOMPLETENESS', 'AMBIGUOUS_QUERY'],
    }
    result = dict(
        schemaVersion='1.0',
        baseRevision=revision,
        corpusTag='legacy-corpus@0.1.0',
        benchmarkClaim=False,
        pins=pins,
        report=report,
        errorAnalysis=[
            dict(
                ticketId=case['ticketId'],
                firstRelevantRank=case['firstRelevantRank'],
                hypotheses=causes.get(case['ticketId'], []),
                reviewStatus='PROPOSED',
            )
            for case in report['cases']
            if case['firstRelevantRank'] != 1
        ],
    )
    raw = json.dumps(result, sort_keys=True, ensure_ascii=False, indent=2) + '\n'
    if args.output.exists() and args.output.read_text('utf-8') != raw:
        raise ValueError('Refusing to overwrite frozen legacy evidence')
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(raw, encoding='utf-8', newline='\n')
    print(json.dumps(report['metrics'], sort_keys=True))


if __name__ == '__main__':
    main()
