"""Require identical PASS evidence across source/wheel or operating systems."""
import argparse
import json
from pathlib import Path

REPORTS = ('retrieval-report.json', 'conformance-report.json', 'security-report.json', 'evaluation-summary.json')


def verify(first, second):
    for name in REPORTS:
        left = json.loads((first / name).read_text('utf-8'))
        right = json.loads((second / name).read_text('utf-8'))
        if left.get('schemaVersion') != '1.0' or left.get('result') != 'PASS' or right.get('result') != 'PASS':
            raise ValueError('Missing valid PASS evidence: ' + name)
        if left != right:
            raise ValueError('Evaluation differs: ' + name)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('first', type=Path)
    parser.add_argument('second', type=Path)
    args = parser.parse_args()
    verify(args.first, args.second)
    print('PASS identical rankings, metrics, gates, fingerprints and evidence')


if __name__ == '__main__':
    main()
