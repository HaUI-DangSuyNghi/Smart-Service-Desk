"""Check frozen/source/wheel evidence without importing the source runtime."""

import argparse
import json
from pathlib import Path

PARAGRAPH = ('a0-report.json', 'a1-report.json', 'a2-report.json', 'comparison-summary.json')
SECTION = ('a2-report.json', 'a3-report.json', 'chunk-comparison.json')
ENGINE = ('b0-tfidf-report.json', 'b1-bm25-report.json', 'engine-comparison-summary.json')


def verify(first, second, *, section=False, engine=False):
    if section and engine:
        raise ValueError('Choose only one knowledge evidence mode')
    names = ENGINE if engine else (SECTION if section else PARAGRAPH)
    for name in names:
        actual = json.loads((first / name).read_text('utf-8'))
        expected = json.loads((second / name).read_text('utf-8'))
        if actual != expected:
            raise ValueError('Knowledge evidence differs: ' + name)
        if name.endswith('-report.json') and actual.get('benchmarkClaim') is not False:
            raise ValueError('Authored evidence must not claim a production benchmark')

    if engine:
        summary = json.loads((first / 'engine-comparison-summary.json').read_text('utf-8'))
        if (summary.get('result') != 'PASS' or summary.get('holdout') != 'NOT_EVALUATED'
                or summary.get('sameA2ProjectionAndJudgmentBasis') is not True
                or summary.get('runtimePromotion') != 'NOT_AUTHORIZED'):
            raise ValueError('Invalid A2 engine comparison decision')
    elif not section:
        summary = json.loads((first / 'comparison-summary.json').read_text('utf-8'))
        if summary.get('result') != 'PASS' or summary.get('holdout') != 'NOT_EVALUATED':
            raise ValueError('Invalid draft experiment decision')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('first', type=Path)
    parser.add_argument('second', type=Path)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--section', action='store_true')
    mode.add_argument('--engine', action='store_true')
    args = parser.parse_args()
    verify(args.first, args.second, section=args.section, engine=args.engine)
    print('PASS identical knowledge evidence, rankings, metrics and fingerprints')


if __name__ == '__main__':
    main()
