"""Require identical B0-B3 graded evidence with explicit label provenance."""
import argparse
from hashlib import sha256
import json
from pathlib import Path

REPORTS = ('b0-tfidf-report.json', 'b1-bm25-report.json', 'b2-dense-report.json', 'b3-hybrid-report.json')
KEYS = ('B0_TFIDF', 'B1_BM25', 'B2_DENSE', 'B3_HYBRID')


def digest(value):
    return 'sha256:' + sha256(json.dumps(value, ensure_ascii=False, sort_keys=True,
                              separators=(',', ':'), allow_nan=False).encode('utf-8')).hexdigest()


def read(path):
    def reject(value):
        raise ValueError('Non-finite matrix evidence')
    return json.loads(path.read_text('utf-8'), parse_constant=reject)


def verify(first, second):
    summary = read(first / 'v02-comparison-summary.json')
    if summary != read(second / 'v02-comparison-summary.json'):
        raise ValueError('Candidate matrix summaries differ')
    reviewed = summary.get('judgmentBasis') == 'HUMAN_REVIEWED'
    if (summary.get('engineeringResult') != 'PASS' or summary.get('benchmarkClaim') is not False
            or summary.get('humanReviewed') is not reviewed
            or summary.get('runtimePromotion') != 'NOT_AUTHORIZED'
            or summary.get('freezeDigest') != digest(summary['freeze'])
            or set(summary['reports']) != set(KEYS)
            or (not reviewed and (summary['judgmentBasis'] != 'AI_ASSISTED_PROVISIONAL'
                                  or summary.get('split') != 'DEV' or summary.get('holdout') != 'NOT_EVALUATED'))):
        raise ValueError('Invalid matrix provenance, freeze or promotion decision')
    for name, key in zip(REPORTS, KEYS):
        report = read(first / name)
        if report != read(second / name):
            raise ValueError('Candidate evidence differs: ' + name)
        fingerprint_fields = ('dataset', 'components', 'system', 'evaluatorVersion', 'metricVersion')
        if (report.get('benchmarkClaim') is not False or report.get('runtimeCitations') is not False
                or report['comparison'].get('humanReviewed') is not reviewed
                or report['comparison'].get('judgmentBasis') != summary['judgmentBasis']
                or report['comparison'].get('runtimePromotion') != 'NOT_AUTHORIZED'
                or report.get('dataset') != summary['dataset'] or report.get('components') != summary['components']
                or report.get('caseCount') != summary['caseCount'] or len(report['cases']) != summary['caseCount']
                or report.get('fingerprint') != digest({field: report[field] for field in fingerprint_fields})
                or report['system']['judgmentBasis'] != summary['judgmentBasis']
                or summary['reports'][key]['fingerprint'] != report['fingerprint']
                or summary['reports'][key]['metrics'] != report['metrics']):
            raise ValueError('Invalid report association or provenance: ' + name)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('first', type=Path)
    parser.add_argument('second', type=Path)
    args = parser.parse_args()
    verify(args.first, args.second)
    print('PASS identical B0-B3 graded evidence, data/config digests and label provenance')


if __name__ == '__main__':
    main()
