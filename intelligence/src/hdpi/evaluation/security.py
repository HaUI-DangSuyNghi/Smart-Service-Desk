"""Required synthetic negative tests become independent hard gates.

Missing, duplicated, skipped, failed or errored tests cannot prove an invariant.
JUnit detail and raw assertion messages are deliberately excluded from reports.
"""
import argparse
from dataclasses import replace
from hashlib import sha256
import json
from pathlib import Path
import xml.etree.ElementTree as ET

from .dataset import load_dataset, parse_json, require
from .evidence import build_report, write_report
from .gates import evaluate_gates, exit_code
from .models import validate, TEXT

TEST_SCHEMA = {'type': 'object', 'required': ['module', 'name'], 'additionalProperties': False,
               'properties': {'module': TEXT, 'name': TEXT}}
CHECK_SCHEMA = {'type': 'object', 'required': ['id', 'dimension', 'tests'], 'additionalProperties': False,
               'properties': {'id': TEXT, 'dimension': {'enum': ['SECURITY', 'PROVENANCE', 'CONTRACT']},
                              'tests': {'type': 'array', 'minItems': 1, 'items': TEST_SCHEMA}}}
CHECKS_SCHEMA = {'type': 'object', 'required': ['schemaVersion', 'checks'], 'additionalProperties': False,
                 'properties': {'schemaVersion': {'const': '1.0'},
                                'checks': {'type': 'array', 'minItems': 1, 'items': CHECK_SCHEMA}}}


def evaluate(root, dataset_root, *, junit, checks):
    dataset = load_dataset(dataset_root, root)
    raw = checks.read_bytes()
    spec = validate(parse_json(raw), CHECKS_SCHEMA)
    require(len({check['id'] for check in spec['checks']}) == len(spec['checks']), 'Duplicate invariant id')
    require({check['dimension'] for check in spec['checks']} == {'SECURITY', 'PROVENANCE', 'CONTRACT'},
            'Missing invariant dimension')
    actual = list(ET.parse(junit).getroot().iter('testcase'))
    require(bool(actual), 'No executable security evidence')
    cases, invariants = [], {}
    for check in spec['checks']:
        failures = 0
        for expected in check['tests']:
            matches = [node for node in actual if (node.get('classname', '') == expected['module'] or
                       node.get('classname', '').endswith('.' + expected['module'])) and node.get('name') == expected['name']]
            failures += int(len(matches) != 1 or any(child.tag in ('failure', 'error', 'skipped')
                                                     for node in matches for child in node))
        invariants[check['id']] = failures
        cases.append({'id': check['id'], 'dimension': check['dimension'], 'status': 'FAIL' if failures else 'PASS',
                      'requiredTestCount': len(check['tests']), 'failedTestCount': failures})
    invariants['executionFailures'] = sum(1 for node in actual if any(child.tag in ('failure', 'error') for child in node))
    dataset = replace(dataset, component_digests=dataset.component_digests | {'securityChecks': sha256(raw).hexdigest()})
    dimensions = {dimension: ('FAIL' if any(c['status'] == 'FAIL' for c in cases if c['dimension'] == dimension) else 'PASS')
                  for dimension in ('SECURITY', 'PROVENANCE', 'CONTRACT')}
    system = {'engine': 'runtime-negative-tests', 'engineVersion': '0.3.0', 'engineConfigVersion': 'security-checks@1.0',
              'normalizationVersion': 'not-applicable', 'chunkPolicyVersion': 'not-applicable',
              'queryStrategyVersion': 'required-junit-identities@1.0'}
    return build_report(evidence_class='SECURITY_INVARIANT', dataset=dataset, system=system,
        metric_version='zero-invariant-failures@1.0', metrics=invariants, cases=cases,
        gates=evaluate_gates(metrics={}, invariants=invariants), dimensions=dimensions)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo-root', type=Path, default=Path('.'))
    parser.add_argument('--dataset', type=Path, required=True)
    parser.add_argument('--junit', type=Path, required=True)
    parser.add_argument('--checks', type=Path, required=True)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    report = evaluate(args.repo_root, args.dataset, junit=args.junit, checks=args.checks)
    write_report(report, args.output)
    print(json.dumps({'result': report['result'], 'dimensions': report['dimensions']}, sort_keys=True))
    return exit_code(report)


if __name__ == '__main__':
    raise SystemExit(main())
