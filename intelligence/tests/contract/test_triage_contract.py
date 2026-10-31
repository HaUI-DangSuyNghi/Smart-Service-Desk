import copy
from dataclasses import replace
from unittest.mock import Mock

import pytest

from hdpi.contracts.validator import ContractValidator
from hdpi.domain.errors import (
    AuthorizationRejected, ContractValidationError, EngineConfigurationError, EngineUnavailable,
)
from hdpi.domain.models import InputEvidence, TriageRequest, TrustedContext
from hdpi.services.triage import TriageService


@pytest.mark.parametrize('field,value', [('title', ''), ('title', ' \t\n'), ('description', ''), ('description', '   ')])
def test_blank_contract_input(fixtures, service, trusted, field, value):
    request = copy.deepcopy(fixtures[0]['request'])
    request['input'][field] = value
    with pytest.raises(ContractValidationError):
        service.triage(request, trusted_context=trusted, profile_version='0.3.0')


def test_auth_context_is_separate_and_checked_before_engine(fixtures):
    registry, engine = Mock(), Mock(name='engine')
    engine.name, engine.build_version = 'test-engine', 'test-build'
    service = TriageService(registry=registry, engine=engine)
    for context in ({'workspaceId': 'lab-a'}, None, TrustedContext(workspace_id='lab-b')):
        with pytest.raises(AuthorizationRejected):
            service.triage(fixtures[0]['request'], trusted_context=context, profile_version='0.3.0')
    registry.get.assert_not_called()
    engine.triage.assert_not_called()


@pytest.mark.parametrize('exception', [AuthorizationRejected, EngineConfigurationError, RuntimeError])
def test_nonoperational_failures_are_raised(fixtures, trusted, exception):
    traces = []
    engine = Mock(name='engine')
    engine.name, engine.build_version = 'rules', 'test'
    engine.triage.side_effect = exception('Sensitive details must not enter trace')
    service = TriageService(engine=engine, trace_sink=traces.append)
    with pytest.raises(exception):
        service.triage(fixtures[0]['request'], trusted_context=trusted, profile_version='0.3.0')
    assert traces[-1].status == 'ERROR'
    assert traces[-1].error_type == exception.__name__
    assert 'Sensitive' not in repr(traces[-1])


def test_operational_failure_is_contract_valid_degraded(fixtures, trusted):
    engine = Mock(name='engine')
    engine.name, engine.build_version = 'rules', 'test'
    engine.triage.side_effect = EngineUnavailable('not exposed')
    run = TriageService(engine=engine).triage(fixtures[0]['request'], trusted_context=trusted, profile_version='0.3.0')
    assert run.response['status'] == 'DEGRADED'
    assert run.response['result'] is None and run.response['runtime'] is None
    assert run.response['evidence'] == [] and run.response['needsReview']
    assert run.response['error'] == {'code': 'INTELLIGENCE_UNAVAILABLE'}
    assert run.trace.profile_version == 'service-desk@0.3.0'
    assert run.trace.normalization_version == 'text-normalization@0.2.0'
    assert run.trace.error_type == 'EngineUnavailable'


@pytest.mark.parametrize('mutation', [
    lambda response: response.update(confidence=0.9),
    lambda response: response['runtime'].update(normalizationVersion='text-normalization@0.1.0'),
    lambda response: response['evidence'][0].update(type='KNOWLEDGE'),
    lambda response: response['evidence'][0].update(text='wifi'),
    lambda response: response.update(workspaceId='lab-b'),
    lambda response: response.update(requestId='another-request'),
    lambda response: response['explanation'].update(impact=float('nan')),
    lambda response: response['explanation'].update(urgency=float('inf')),
])
def test_invalid_wire_response_rejected(fixtures, service, trusted, mutation):
    request = fixtures[0]['request']
    response = service.triage(request, trusted_context=trusted, profile_version='0.3.0').response
    mutation(response)
    with pytest.raises(ContractValidationError):
        ContractValidator().response(response, request)


def test_replacement_engine_cannot_fabricate_evidence(fixtures, service, trusted):
    payload = fixtures[0]['request']
    request = TriageRequest(payload['requestId'], 'lab-a', 'service-desk', **payload['input'])
    profile = service.registry.get('service-desk', '0.3.0')
    decision = service.engine.triage(request, profile)
    engine = Mock(name='engine')
    engine.name, engine.build_version = 'rules', 'test'
    engine.triage.return_value = replace(decision, evidence=(InputEvidence('title', 'wifi'),))
    with pytest.raises(ContractValidationError):
        TriageService(engine=engine).triage(payload, trusted_context=trusted, profile_version='0.3.0')


def test_no_input_authority_fields(fixtures, service, trusted):
    for key in ('owner', 'roles', 'permissions', 'jwt', 'authorizationGranted'):
        request = copy.deepcopy(fixtures[0]['request'])
        request['input'][key] = 'untrusted'
        with pytest.raises(ContractValidationError):
            service.triage(request, trusted_context=trusted, profile_version='0.3.0')
