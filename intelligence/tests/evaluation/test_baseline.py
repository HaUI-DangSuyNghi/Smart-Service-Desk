from copy import deepcopy

import pytest

from hdpi.evaluation.baseline import validate_baseline
from hdpi.evaluation.comparison import compare_candidate
from hdpi.evaluation.errors import EvaluationError


def baseline_fixture():
    return dict(schemaVersion='1.0', dataset={'id': 'synthetic', 'version': '0.1.0', 'digest': 'sha256:' + 'a'*64},
                components={key: 'a'*64 for key in ('knowledge', 'tickets', 'ticketManifest', 'retrieval')},
                system={key: 'test@1' for key in ('engine', 'engineVersion', 'engineConfigVersion', 'normalizationVersion',
                                                'chunkPolicyVersion', 'queryStrategyVersion')},
                metricVersion='artifact-retrieval@1.0', evaluatorVersion='evaluation@1.0', caseCount=2,
                metrics={'hitAt1': .5, 'hitAt3': 1}, gatedMetrics=['hitAt1', 'hitAt3'], reviewStatus='PROPOSED',
                referenceEvidence='Synthetic baseline for tests')


def compare(baseline, **changes):
    args = dict(dataset=baseline['dataset'], components=baseline['components'], metric_version=baseline['metricVersion'],
                case_count=baseline['caseCount'], metrics={'hitAt1': 0, 'hitAt3': 1}, baseline=baseline)
    return compare_candidate(**(args | changes))


def test_comparison_records_regression_without_mutating_baseline():
    baseline = baseline_fixture()
    before = deepcopy(baseline)
    assert compare(baseline)['deltas'] == {'hitAt1': -.5, 'hitAt3': 0}
    assert baseline == before


@pytest.mark.parametrize('kind', ['dataset', 'digest', 'components', 'metric', 'count', 'missing-metric', 'gate', 'nan'])
def test_incompatible_baseline_rejected(kind):
    baseline = baseline_fixture()
    changes = {}
    if kind in ('dataset', 'digest'):
        changes['dataset'] = baseline['dataset'] | ({'id': 'other'} if kind == 'dataset' else {'digest': 'sha256:' + 'b'*64})
    elif kind == 'components':
        changes['components'] = baseline['components'] | {'retrieval': 'b'*64}
    elif kind == 'metric':
        changes['metric_version'] = 'other@1'
    elif kind == 'count':
        changes['case_count'] = 99
    elif kind == 'missing-metric':
        changes['metrics'] = {'hitAt1': 1}
    elif kind == 'gate':
        baseline['gatedMetrics'] = []
    else:
        baseline['metrics']['hitAt1'] = float('nan')
    with pytest.raises(EvaluationError):
        compare(baseline, **changes)


def test_invalid_baseline_required_field():
    baseline = baseline_fixture()
    del baseline['system']['queryStrategyVersion']
    with pytest.raises(EvaluationError):
        validate_baseline(baseline)
