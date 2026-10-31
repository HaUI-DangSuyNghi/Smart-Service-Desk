"""Require identical B0-B3 graded evidence with explicit label provenance."""
import argparse
from hashlib import sha256
import json
from pathlib import Path

REPORTS = ('b0-tfidf-report.json', 'b1-bm25-report.json', 'b2-dense-report.json', 'b3-hybrid-report.json')
KEYS = ('B0_TFIDF', 'B1_BM25', 'B2_DENSE', 'B3_HYBRID')

# Application evaluation policy, outside the generic hdpi wheel. Keep historical
# evidence readable; CI explicitly opts into the current migration contract.
CURRENT_CONFIGS = ('tfidf-config@0.1.0', 'bm25-config@0.2.0',
                   'dense-e5-cpu-config@0.2.0', 'hybrid-rrf-config@0.2.0')


def verify_current_identities(summary):
    freeze = summary['freeze']
    if (freeze.get('dataset') != summary['dataset']
            or freeze.get('components') != summary['components']
            or freeze.get('judgmentBasis') != summary['judgmentBasis']
            or freeze.get('matrixVersion') != 'ai05-candidate-matrix@0.2.0'
            or freeze.get('metricVersion') != 'graded-artifact-retrieval@0.2.0'
            or set(freeze['systems']) != set(KEYS)):
        raise ValueError('Current matrix freeze association or identity mismatch')
    for key, engine, config in zip(KEYS, ('tfidf', 'bm25', 'dense', 'hybrid'), CURRENT_CONFIGS):
        system = freeze['systems'][key]
        expected = {
            'engine': engine, 'engineVersion': '0.1.0', 'engineConfigVersion': config,
            'normalizationVersion': 'text-normalization@0.2.0',
            'chunkPolicyVersion': 'markdown-paragraph@0.1.0',
            'queryStrategyVersion': 'service-desk-title-newline-description@0.1.0',
            'evaluationMatrixVersion': freeze['matrixVersion'],
            'projectionVersion': 'service-desk-knowledge-projection@0.2.0',
            'judgmentBasis': summary['judgmentBasis'],
            'numericalRuntime': {
                'tfidf': 'scikit-learn@1.6.1;numpy@2.2.6;scipy@1.15.3',
                'bm25': 'cpython@3.12;unicode@15.0.0;stdlib-math@3.12',
                'dense': 'onnxruntime@1.20.1;tokenizers@0.21.0;numpy@2.2.6;cpython@3.12;unicode@15.0.0',
                'hybrid': 'onnxruntime@1.20.1;tokenizers@0.21.0;numpy@2.2.6;cpython@3.12;unicode@15.0.0;stdlib-fractions@3.12',
            }[engine],
        }
        if engine in ('dense', 'hybrid'):
            expected.update(
                modelVersion='intfloat/multilingual-e5-small@614241f622f53c4eeff9890bdc4f31cfecc418b3',
                modelArtifactDigest='sha256:8f5c24508c0307b029da65796be624c0a3345be7f5b96622a04e0b6285bc53fb')
        if engine == 'hybrid':
            expected.update(lexicalEngine='bm25', lexicalEngineConfigVersion=CURRENT_CONFIGS[1],
                            lexicalEngineVersion='0.1.0', denseEngine='dense',
                            denseEngineConfigVersion=CURRENT_CONFIGS[2], denseEngineVersion='0.1.0',
                            defaultTopK='3', retrievalDepth='20', fusionConstant='60',
                            fusionAlgorithm='equal-weight-reciprocal-rank-fusion',
                            fusionDeduplication='source-first-hit-before-depth')
        if system != expected:
            raise ValueError('Stale or unsupported current candidate identity: ' + key)


def digest(value):
    return 'sha256:' + sha256(json.dumps(value, ensure_ascii=False, sort_keys=True,
                              separators=(',', ':'), allow_nan=False).encode('utf-8')).hexdigest()


def read(path):
    def reject(value):
        raise ValueError('Non-finite matrix evidence')
    return json.loads(path.read_text('utf-8'), parse_constant=reject)


def verify(first, second, *, require_current_identities=False):
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
    if require_current_identities:
        verify_current_identities(summary)
    for name, key in zip(REPORTS, KEYS):
        report = read(first / name)
        if report != read(second / name):
            raise ValueError('Candidate evidence differs: ' + name)
        if require_current_identities and (
                report['system'] != summary['freeze']['systems'][key] | {'split': summary['split']}
                or report['metricVersion'] != summary['freeze']['metricVersion']
                or report.get('result') != 'PASS'
                or summary['reports'][key].get('result') != report['result']):
            raise ValueError('Current report differs from frozen system or result: ' + name)
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
    parser.add_argument('--require-current-identities', action='store_true',
                        help='Reject pre-migration identities even when both inputs agree')
    args = parser.parse_args()
    verify(args.first, args.second, require_current_identities=args.require_current_identities)
    print('PASS identical B0-B3 graded evidence, data/config digests and label provenance')


if __name__ == '__main__':
    main()
