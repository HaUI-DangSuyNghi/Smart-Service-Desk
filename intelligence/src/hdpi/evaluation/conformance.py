"""Run authored v0.1 scenarios with new runtime version pins; never edit fixtures."""

import argparse
from dataclasses import asdict
import json
from pathlib import Path

from ..domain.models import TrustedContext
from ..services.triage import TriageService


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


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--fixtures', type=Path, required=True)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--profile-version', required=True)
    parser.add_argument('--expected-runtime', required=True, type=json.loads)
    parser.add_argument('--normalization-version', required=True)
    args = parser.parse_args()
    service, cases = TriageService(), []
    for line in args.fixtures.read_text(encoding='utf-8').splitlines():
        fixture = json.loads(line)
        run = check_case(fixture, service, profile_version=args.profile_version,
                         expected_runtime=args.expected_runtime, normalization_version=args.normalization_version)
        cases.append({'id': fixture['id'], 'status': 'PASS', 'trace': asdict(run.trace)})
    report = {'evidenceClass': 'AUTHORED_CONFORMANCE', 'passed': len(cases), 'total': len(cases),
              'statement': f'{len(cases)}/{len(cases)} kịch bản conformance tự biên soạn đạt với rules deterministic.',
              'cases': cases}
    if args.output:
        args.output.write_text(json.dumps(report, ensure_ascii=True, indent=2) + '\n', encoding='utf-8')
    print(f'PASS {len(cases)}/{len(cases)} authored conformance scenarios (not a model benchmark)')


if __name__ == '__main__':
    main()
