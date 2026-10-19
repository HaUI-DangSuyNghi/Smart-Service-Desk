"""Compare only identical datasets and measurement definitions."""
from .baseline import validate_baseline
from .dataset import require


def compare_candidate(*, dataset, components, metric_version, case_count, metrics, baseline):
    validate_baseline(baseline)
    require(dataset == baseline['dataset'] and components == baseline['components'], 'Incompatible baseline dataset')
    require(metric_version == baseline['metricVersion'], 'Incompatible baseline metric definition')
    require(case_count == baseline['caseCount'], 'Incompatible baseline case count')
    require(set(baseline['metrics']) <= set(metrics), 'Candidate metrics missing')
    return {'evidenceClass': 'CANDIDATE_COMPARISON', 'benchmarkClaim': False,
            'baselineSystem': baseline['system'],
            'deltas': {key: metrics[key] - value for key, value in sorted(baseline['metrics'].items())}}
