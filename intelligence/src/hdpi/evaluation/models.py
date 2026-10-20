"""Stable JSON evidence schema; all measurements are synthetic engineering evidence."""
import json

from jsonschema import Draft202012Validator, ValidationError

from ..retrieval.models import digest
from .dataset import require
from .errors import EvaluationError

SCHEMA_VERSION = '1.0'
EVALUATOR_VERSION = 'evaluation@1.0'
TEXT = {'type': 'string', 'minLength': 1}
HASH = {'type': 'string', 'pattern': '^sha256:[a-f0-9]{64}$'}
DATASET_SCHEMA = {'type': 'object', 'required': ['id', 'version', 'digest'], 'additionalProperties': False,
                  'properties': {'id': TEXT, 'version': TEXT, 'digest': HASH}}
SYSTEM_FIELDS = ('engine', 'engineVersion', 'engineConfigVersion', 'normalizationVersion',
                 'chunkPolicyVersion', 'queryStrategyVersion')
SYSTEM_SCHEMA = {'type': 'object', 'required': list(SYSTEM_FIELDS), 'properties': {key: TEXT for key in SYSTEM_FIELDS},
                 'additionalProperties': TEXT}
METRICS_SCHEMA = {'type': 'object', 'minProperties': 1, 'additionalProperties': {'type': 'number', 'minimum': 0}}
VIOLATION_SCHEMA = {'type': 'object', 'required': ['metric', 'constraint', 'expected', 'actual'],
                    'additionalProperties': False, 'properties': {'metric': TEXT, 'constraint': TEXT,
                    'expected': {'type': 'number'}, 'actual': {'type': 'number'}}}
GATES_SCHEMA = {'type': 'object', 'required': ['result', 'violations'], 'additionalProperties': False,
                'properties': {'result': {'enum': ['PASS', 'FAIL']},
                               'violations': {'type': 'array', 'items': VIOLATION_SCHEMA}}}
REPORT_SCHEMA = {
    'type': 'object', 'additionalProperties': False,
    'required': ['schemaVersion', 'evidenceClass', 'benchmarkClaim', 'dataset', 'components', 'system',
                 'evaluatorVersion', 'metricVersion', 'fingerprint', 'caseCount', 'metrics', 'gates', 'cases', 'result'],
    'properties': {
        'schemaVersion': {'const': SCHEMA_VERSION},
        'evidenceClass': {'enum': ['AUTHORED_RETRIEVAL_REGRESSION', 'AUTHORED_CONFORMANCE', 'SECURITY_INVARIANT', 'CANDIDATE_COMPARISON']},
        'benchmarkClaim': {'const': False}, 'runtimeCitations': {'const': False}, 'rightsStatus': TEXT,
        'dataset': DATASET_SCHEMA, 'components': {'type': 'object', 'minProperties': 1,
            'additionalProperties': {'type': 'string', 'pattern': '^[a-f0-9]{64}$'}},
        'system': SYSTEM_SCHEMA, 'evaluatorVersion': TEXT, 'metricVersion': TEXT, 'fingerprint': HASH,
        'caseCount': {'type': 'integer', 'minimum': 1}, 'metrics': METRICS_SCHEMA, 'gates': GATES_SCHEMA,
        'cases': {'type': 'array', 'minItems': 1, 'items': {'type': 'object'}}, 'result': {'enum': ['PASS', 'FAIL']},
        'comparison': {'type': 'object'}, 'dimensions': {'type': 'object', 'additionalProperties': {'enum': ['PASS', 'FAIL']}},
    },
}


def validate(value, schema):
    try:
        json.dumps(value, allow_nan=False)
        Draft202012Validator(schema).validate(value)
    except (ValueError, TypeError, ValidationError) as error:
        raise EvaluationError('Invalid evaluation evidence: ' + str(error).splitlines()[0]) from error
    return value


def fingerprint(report):
    keys = ('dataset', 'components', 'system', 'evaluatorVersion', 'metricVersion')
    return 'sha256:' + digest({key: report[key] for key in keys})


def validate_report(report):
    validate(report, REPORT_SCHEMA)
    require(report['caseCount'] == len(report['cases']), 'Report case count mismatch')
    require(report['fingerprint'] == fingerprint(report), 'Report fingerprint mismatch')
    require((report['gates']['result'] == 'PASS') == (not report['gates']['violations']), 'Gate decision mismatch')
    expected = 'FAIL' if report['gates']['result'] == 'FAIL' or 'FAIL' in report.get('dimensions', {}).values() else 'PASS'
    require(report['result'] == expected, 'Overall result mismatch')
    return report
