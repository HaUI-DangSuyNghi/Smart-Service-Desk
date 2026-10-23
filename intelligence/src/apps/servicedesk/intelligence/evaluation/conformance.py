"""Service Desk-authored triage conformance adapter."""

from collections.abc import Mapping

from hdpi.domain.models import TrustedContext
from hdpi.evaluation.evidence import build_report
from hdpi.evaluation.gates import evaluate_gates
from hdpi.retrieval.models import digest
from hdpi.services.triage import TriageService

from .dataset import load_dataset


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def check_case(fixture, service, *, profile_version, expected_runtime, normalization_version):
    request, expected = fixture['request'], fixture['expected']
    run = service.triage(
        request,
        trusted_context=TrustedContext(workspace_id=request['context']['workspaceId']),
        profile_version=profile_version,
    )
    actual = run.response
    require(actual['status'] == expected['status'], 'Status mismatch')
    require(actual['result'] == expected['result'], 'Category/priority/routing mismatch')
    require(actual['needsReview'] == expected['needsReview'], 'Review mismatch')
    for field in ('impact', 'urgency', 'reasonCode'):
        require(actual['explanation'][field] == expected['explanation'][field], field)
    require(
        set(expected['explanation']['matchedRules']) <= set(actual['explanation']['matchedRules']),
        'Required rule missing',
    )
    require(actual['runtime'] == expected_runtime, 'Runtime version mismatch')
    require(run.trace.normalization_version == normalization_version, 'Normalization trace mismatch')
    require(
        'confidence' not in actual and 'normalizationVersion' not in actual['runtime'],
        'Unsupported wire property',
    )
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
        profiles[profile.identity] = plain({
            'taxonomy': profile.taxonomy,
            'rules': profile.rules,
            'policy': profile.policy,
        })
        try:
            check_case(
                fixture,
                service,
                profile_version=profile_version,
                expected_runtime=expected_runtime,
                normalization_version=normalization_version,
            )
            cases.append({'id': fixture['id'], 'status': 'PASS'})
        except AssertionError as error:
            cases.append({'id': fixture['id'], 'status': 'FAIL', 'diagnostic': str(error)})

    passed = sum(case['status'] == 'PASS' for case in cases)
    metrics = {'conformancePassRate': passed / len(cases)}
    system = {
        'engine': service.engine.name,
        'engineVersion': service.engine.build_version,
        'engineConfigVersion': 'sha256:' + digest(profiles),
        'normalizationVersion': normalization_version,
        'chunkPolicyVersion': 'not-applicable',
        'queryStrategyVersion': 'authored-request@0.1.0',
        'profileVersion': profile_version,
        'expectedRuntimeDigest': 'sha256:' + digest(expected_runtime),
    }
    return build_report(
        evidence_class='AUTHORED_CONFORMANCE',
        dataset=dataset,
        system=system,
        metric_version='exact-triage-conformance@1.0',
        metrics=metrics,
        cases=cases,
        gates=evaluate_gates(metrics=metrics, policy={'conformancePassRate': 1}),
    )
