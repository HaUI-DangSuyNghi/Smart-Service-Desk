"""Read-only versioned baseline. Updating it is a separate reviewed change."""
from pathlib import Path

from .dataset import parse_json, require
from .errors import EvaluationError
from .models import DATASET_SCHEMA, METRICS_SCHEMA, SYSTEM_SCHEMA, TEXT, validate

BASELINE_SCHEMA = {
    'type': 'object',
    'additionalProperties': False,
    'required': [
        'schemaVersion', 'dataset', 'components', 'system', 'metricVersion',
        'evaluatorVersion', 'caseCount', 'metrics', 'gatedMetrics',
        'reviewStatus', 'referenceEvidence',
    ],
    'properties': {
        'schemaVersion': {'const': '1.0'},
        'dataset': DATASET_SCHEMA,
        'system': SYSTEM_SCHEMA,
        'components': {
            'type': 'object',
            'minProperties': 1,
            'additionalProperties': {
                'type': 'string',
                'pattern': '^[a-f0-9]{64}$',
            },
        },
        'metricVersion': TEXT,
        'evaluatorVersion': TEXT,
        'caseCount': {'type': 'integer', 'minimum': 1},
        'metrics': METRICS_SCHEMA,
        'gatedMetrics': {
            'type': 'array',
            'minItems': 1,
            'uniqueItems': True,
            'items': TEXT,
        },
        'reviewStatus': {'enum': ['PROPOSED', 'REVIEWED']},
        'referenceEvidence': TEXT,
    },
}


def validate_baseline(baseline):
    validate(baseline, BASELINE_SCHEMA)
    require(
        {'hitAt1', 'hitAt3'} <= set(baseline['gatedMetrics']),
        'Baseline must gate Hit@1 and Hit@3',
    )
    require(
        set(baseline['gatedMetrics']) <= set(baseline['metrics']),
        'Missing baseline gated metric',
    )
    require(
        all(0 <= value <= 1 for value in baseline['metrics'].values()),
        'Invalid baseline metric',
    )
    return baseline


def load_baseline(path: Path):
    try:
        return validate_baseline(parse_json(path.read_bytes()))
    except (OSError, ValueError, TypeError) as error:
        raise EvaluationError('Invalid baseline: ' + str(error)) from error
