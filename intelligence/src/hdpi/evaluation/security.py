"""Generic executable security/provenance/contract evidence gates."""

from dataclasses import dataclass
from hashlib import sha256
import xml.etree.ElementTree as ET

from .dataset import parse_json, require
from .evidence import build_report
from .gates import evaluate_gates
from .models import validate, TEXT


TEST_SCHEMA = {
    'type': 'object',
    'required': ['module', 'name'],
    'additionalProperties': False,
    'properties': {'module': TEXT, 'name': TEXT},
}
CHECK_SCHEMA = {
    'type': 'object',
    'required': ['id', 'dimension', 'tests'],
    'additionalProperties': False,
    'properties': {
        'id': TEXT,
        'dimension': {'enum': ['SECURITY', 'PROVENANCE', 'CONTRACT']},
        'tests': {'type': 'array', 'minItems': 1, 'items': TEST_SCHEMA},
    },
}
CHECKS_SCHEMA = {
    'type': 'object',
    'required': ['schemaVersion', 'checks'],
    'additionalProperties': False,
    'properties': {
        'schemaVersion': {'const': '1.0'},
        'checks': {'type': 'array', 'minItems': 1, 'items': CHECK_SCHEMA},
    },
}


@dataclass(frozen=True)
class EvidenceDataset:
    identity: dict
    component_digests: dict


def evaluate(dataset, *, junit, checks):
    raw = checks.read_bytes()
    spec = validate(parse_json(raw), CHECKS_SCHEMA)
    require(
        len({check['id'] for check in spec['checks']}) == len(spec['checks']),
        'Duplicate invariant id',
    )
    require(
        {check['dimension'] for check in spec['checks']}
        == {'SECURITY', 'PROVENANCE', 'CONTRACT'},
        'Missing invariant dimension',
    )
    actual = list(ET.parse(junit).getroot().iter('testcase'))
    require(bool(actual), 'No executable security evidence')

    cases, invariants = [], {}
    for check in spec['checks']:
        failures = 0
        for expected in check['tests']:
            matches = [
                node for node in actual
                if (
                    node.get('classname', '') == expected['module']
                    or node.get('classname', '').endswith('.' + expected['module'])
                )
                and node.get('name') == expected['name']
            ]
            failures += int(
                len(matches) != 1
                or any(
                    child.tag in ('failure', 'error', 'skipped')
                    for node in matches
                    for child in node
                )
            )
        invariants[check['id']] = failures
        cases.append({
            'id': check['id'],
            'dimension': check['dimension'],
            'status': 'FAIL' if failures else 'PASS',
            'requiredTestCount': len(check['tests']),
            'failedTestCount': failures,
        })

    invariants['executionFailures'] = sum(
        1 for node in actual
        if any(child.tag in ('failure', 'error') for child in node)
    )
    evidence_dataset = EvidenceDataset(
        dataset.identity,
        dataset.component_digests | {'securityChecks': sha256(raw).hexdigest()},
    )
    dimensions = {
        dimension: (
            'FAIL'
            if any(
                case['status'] == 'FAIL'
                for case in cases
                if case['dimension'] == dimension
            )
            else 'PASS'
        )
        for dimension in ('SECURITY', 'PROVENANCE', 'CONTRACT')
    }
    system = {
        'engine': 'runtime-negative-tests',
        'engineVersion': '0.3.0',
        'engineConfigVersion': 'security-checks@1.0',
        'normalizationVersion': 'not-applicable',
        'chunkPolicyVersion': 'not-applicable',
        'queryStrategyVersion': 'required-junit-identities@1.0',
    }
    return build_report(
        evidence_class='SECURITY_INVARIANT',
        dataset=evidence_dataset,
        system=system,
        metric_version='zero-invariant-failures@1.0',
        metrics=invariants,
        cases=cases,
        gates=evaluate_gates(metrics={}, invariants=invariants),
        dimensions=dimensions,
    )
