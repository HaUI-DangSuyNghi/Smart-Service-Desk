from copy import deepcopy
import json
from types import SimpleNamespace

import pytest

from hdpi.evaluation.evidence import build_report, serialize_report
from hdpi.evaluation.errors import EvaluationError
from hdpi.evaluation.models import validate_report


def report_fixture():
    dataset = SimpleNamespace(identity={'id': 'synthetic', 'version': '0.1.0', 'digest': 'sha256:' + 'a'*64},
                              component_digests={'corpus': 'b'*64})
    system = {key: 'test@1' for key in ('engine', 'engineVersion', 'engineConfigVersion', 'normalizationVersion',
                                       'chunkPolicyVersion', 'queryStrategyVersion')}
    return build_report(evidence_class='AUTHORED_CONFORMANCE', dataset=dataset, system=system,
                        metric_version='conformance@1', metrics={'conformancePassRate': 1}, cases=[{'id': '1'}],
                        gates={'result': 'PASS', 'violations': []})


def test_report_round_trip_is_canonical():
    report = report_fixture()
    assert json.loads(serialize_report(report)) == report
    assert serialize_report(dict(reversed(list(report.items())))) == serialize_report(report)
    assert report['benchmarkClaim'] is False


@pytest.mark.parametrize('kind', ['missing', 'schema', 'benchmark', 'nan', 'count', 'fingerprint', 'result', 'gates', 'system', 'class'])
def test_invalid_evidence_rejected(kind):
    report = deepcopy(report_fixture())
    if kind == 'missing':
        del report['dataset']['id']
    elif kind == 'schema':
        report['schemaVersion'] = '999'
    elif kind == 'benchmark':
        report['benchmarkClaim'] = True
    elif kind == 'nan':
        report['metrics']['conformancePassRate'] = float('nan')
    elif kind == 'count':
        report['caseCount'] = 9
    elif kind == 'fingerprint':
        report['fingerprint'] = 'sha256:' + '0'*64
    elif kind == 'result':
        report['result'] = 'FAIL'
    elif kind == 'gates':
        report['gates']['result'] = 'FAIL'
    elif kind == 'system':
        del report['system']['engineVersion']
    else:
        report['evidenceClass'] = 'PRODUCTION_ACCURACY'
    with pytest.raises(EvaluationError):
        validate_report(report)
