"""Run authored v0.1 scenarios with new runtime version pins; never edit fixtures."""

import argparse
from collections.abc import Mapping
import json
from pathlib import Path

from ..domain.models import TrustedContext
from ..services.triage import TriageService
from ..retrieval.models import digest
from .dataset import load_dataset, parse_json, require as require_dataset
from .evidence import build_report, write_report
from .gates import evaluate_gates, exit_code


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def check_case(fixture, service, *, profile_version, expected_runtime, normalization_version):
    request, expected = fixture['request'], fixture['expected']
    run = service.triage(request, trusted_context=TrustedContext(workspace_id=request['context']['workspaceId']),
                         profile_version=profile_version)
    actual = run.response
    require(actual['status'] == expected['status'], 'Status mismatch')
    require(actual['result'] == expected['result'], 'Category/priority/routing mismatch')
    require(actual['needsReview'] == expected['needsReview'], 'Review mismatch')
    for field in ('impact', 'urgency', 'reasonCode'):
        require(actual['explanation'][field] == expected['explanation'][field], field)
    require(set(expected['explanation']['matchedRules']) <= set(actual['explanation']['matchedRules']), 'Required rule missing')
    require(actual['runtime'] == expected_runtime, 'Runtime version mismatch')
    require(run.trace.normalization_version == normalization_version, 'Normalization trace mismatch')
    require('confidence' not in actual and 'normalizationVersion' not in actual['runtime'], 'Unsupported wire property')
    return run


def plain(value):
    if isinstance(value, Mapping):
        return {key: plain(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [plain(item) for item in value]
    return value


def evaluate(root, dataset_root, *, profile_version, expected_runtime, normalization_version, service=None):
    dataset = load_dataset(dataset_root, root)
    service = service or TriageService()
    cases, profiles = [], {}
    for fixture in dataset.tickets:
        profile = service.registry.get(fixture['request']['profile'], profile_version)
        profiles[profile.identity] = plain({'taxonomy': profile.taxonomy, 'rules': profile.rules, 'policy': profile.policy})
        try:
            check_case(fixture, service, profile_version=profile_version, expected_runtime=expected_runtime,
                       normalization_version=normalization_version)
            cases.append({'id': fixture['id'], 'status': 'PASS'})
        except AssertionError as error:
            cases.append({'id': fixture['id'], 'status': 'FAIL', 'diagnostic': str(error)})
    passed = sum(case['status'] == 'PASS' for case in cases)
    metrics = {'conformancePassRate': passed / len(cases)}
    system = {'engine': service.engine.name, 'engineVersion': service.engine.build_version,
              'engineConfigVersion': 'sha256:' + digest(profiles), 'normalizationVersion': normalization_version,
              'chunkPolicyVersion': 'not-applicable', 'queryStrategyVersion': 'authored-request@0.1.0',
              'profileVersion': profile_version, 'expectedRuntimeDigest': 'sha256:' + digest(expected_runtime)}
    return build_report(evidence_class='AUTHORED_CONFORMANCE', dataset=dataset, system=system,
        metric_version='exact-triage-conformance@1.0', metrics=metrics, cases=cases,
        gates=evaluate_gates(metrics=metrics, policy={'conformancePassRate': 1}))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--fixtures', type=Path)
    parser.add_argument('--dataset', type=Path)
    parser.add_argument('--repo-root', type=Path, default=Path('.'))
    parser.add_argument('--config', type=Path, help='Application-owned JSON conformance pins')
    parser.add_argument('--output', type=Path)
    parser.add_argument('--profile-version')
    parser.add_argument('--expected-runtime', type=json.loads)
    parser.add_argument('--normalization-version')
    args = parser.parse_args()
    dataset_root = args.dataset or (args.fixtures.parent.parent if args.fixtures else None)
    if dataset_root is None:
        parser.error('--dataset is required')
    config = parse_json(args.config.read_bytes()) if args.config else {
        'profileVersion': args.profile_version, 'expectedRuntime': args.expected_runtime,
        'normalizationVersion': args.normalization_version}
    if not all(config.get(key) for key in ('profileVersion', 'expectedRuntime', 'normalizationVersion')):
        parser.error('Provide --config or all three explicit conformance pins')
    if args.fixtures:
        dataset = load_dataset(dataset_root, args.repo_root)
        expected_path = dataset_root / dataset.manifest['components']['tickets']['path']
        require_dataset(args.fixtures.resolve() == expected_path.resolve(), 'Fixture differs from declared dataset tickets')
    report = evaluate(args.repo_root, dataset_root, profile_version=config['profileVersion'],
                      expected_runtime=config['expectedRuntime'], normalization_version=config['normalizationVersion'])
    write_report(report, args.output)
    print(json.dumps({key: report[key] for key in ('evidenceClass', 'caseCount', 'metrics', 'result')}, sort_keys=True))
    return exit_code(report)


if __name__ == '__main__':
    raise SystemExit(main())
