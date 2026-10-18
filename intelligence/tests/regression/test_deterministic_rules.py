import copy
from dataclasses import asdict, replace

import pytest

from hdpi_intelligence.domain.models import TriageRequest, TrustedContext
from hdpi_intelligence.profiles.models import freeze


def request(title, description):
    return {'contractVersion': '0.1.0', 'requestId': 'regression', 'profile': 'service-desk',
            'context': {'workspaceId': 'lab-a'}, 'input': {'title': title, 'description': description}}


@pytest.mark.parametrize('title,description', [
    ('Không rõ', 'Chưa xác định vấn đề, xin hỗ trợ.'),
    ('Wifi và tài khoản bị khóa', 'Một máy có thể chờ.'),
    ('Wifi', 'Một máy và một nhóm bị ảnh hưởng, có thể chờ.'),
    ('Wifi', 'Một máy có thể chờ nhưng không thể tiếp tục công việc.'),
    ('Wifi', 'Một máy gặp lỗi, chưa biết phải xử lý lúc nào.'),
    ('Wifi', 'Có thể chờ, chưa xác định phạm vi ảnh hưởng.'),
    ('xwifi', 'Một máy có thể chờ.'),
    ('Wifi tài khoản phần mềm', 'Bị khóa; wifi vẫn hoạt động với người khác. Một máy có thể chờ.'),
])
def test_ambiguous_cases_use_complete_fallback(service, trusted, title, description):
    result = service.triage(request(title, description), trusted_context=trusted, profile_version='0.2.0').response
    assert result['result'] == {'category': 'OTHER', 'priority': 'MEDIUM', 'suggestedGroup': 'GENERAL-SUPPORT'}
    assert result['needsReview'] is True
    assert result['explanation']['impact'] is None and result['explanation']['urgency'] is None
    assert result['explanation']['reasonCode'] == 'INSUFFICIENT_INFORMATION'


def reverse(value):
    if hasattr(value, 'items'):
        return {k: reverse(v) for k, v in reversed(list(value.items()))}
    if isinstance(value, (list, tuple)):
        return [reverse(v) for v in reversed(value)]
    return value


def test_configuration_order_does_not_choose_winner(fixtures, service):
    profile = service.registry.get('service-desk', '0.2.0')
    reordered = replace(profile, taxonomy=freeze(reverse(profile.taxonomy)),
                        rules=freeze(reverse(profile.rules)), policy=freeze(reverse(profile.policy)))
    for fixture in fixtures:
        payload = fixture['request']
        req = TriageRequest(payload['requestId'], 'lab-a', 'service-desk', **payload['input'])
        assert service.engine.triage(req, profile) == service.engine.triage(req, reordered)


def test_identical_versions_identical_response_and_no_request_mutation(fixtures, service, trusted):
    for fixture in fixtures:
        payload = copy.deepcopy(fixture['request'])
        before = copy.deepcopy(payload)
        first = service.triage(payload, trusted_context=trusted, profile_version='0.2.0')
        second = service.triage(payload, trusted_context=trusted, profile_version='0.2.0')
        assert first.response == second.response
        assert payload == before
        assert first.trace.duration_ms >= 0
        assert not {'title', 'description', 'jwt', 'token'} & set(asdict(first.trace))


def test_cross_workspace_correlation(fixtures, service):
    responses = []
    for workspace in ('lab-a', 'lab-b'):
        payload = copy.deepcopy(fixtures[0]['request'])
        payload['context']['workspaceId'] = workspace
        payload['requestId'] = workspace + '-request'
        run = service.triage(payload, trusted_context=TrustedContext(workspace_id=workspace), profile_version='0.2.0')
        assert run.response['workspaceId'] == run.trace.workspace_id == workspace
        assert run.response['requestId'] == run.trace.request_id == workspace + '-request'
        responses.append(run.response)
    assert responses[0]['result'] == responses[1]['result']


def test_whitespace_and_nfd_evidence(service, trusted):
    payload = request('WI\t FI', 'MO\u0323\u0302T   MA\u0301Y có thể\n\tchờ hỗ trợ.')
    run = service.triage(payload, trusted_context=trusted, profile_version='0.2.0')
    assert run.response['result']['category'] == 'NETWORK'
    assert run.response['explanation']['impact'] == 'I1'
    assert run.response['explanation']['urgency'] == 'U2'
    for evidence in run.response['evidence']:
        assert evidence['text'] in payload['input'][evidence['field']]
    assert any(e['text'] == 'WI\t FI' for e in run.response['evidence'])


def test_count_rules_generalize_beyond_fixture_counts(service, trusted):
    for count in (2, 3, 17, 68, 100, 9999):
        payload = request('Wifi lỗi', f'{count} máy không thể tiếp tục công việc.')
        run = service.triage(payload, trusted_context=trusted, profile_version='0.2.0')
        assert run.response['explanation']['impact'] == 'I2'
        assert run.response['result']['priority'] == 'HIGH'


@pytest.mark.parametrize('impact,text', [('I1', 'Một máy'), ('I2', 'Một nhóm'), ('I3', 'Cả phòng'), ('I4', 'Toàn trường')])
@pytest.mark.parametrize('urgency,text_urgency', [('U1', 'có giải pháp thay thế'), ('U2', 'có thể chờ'),
                                                ('U3', 'không thể tiếp tục'), ('U4', 'kỳ thi đang bị chặn')])
def test_all_priority_matrix_cells_execute(service, trusted, impact, text, urgency, text_urgency):
    payload = request('Wifi lỗi', text + ' bị ảnh hưởng; ' + text_urgency + '.')
    run = service.triage(payload, trusted_context=trusted, profile_version='0.2.0')
    matrix = service.registry.get('service-desk', '0.2.0').policy['matrix']
    assert run.response['explanation']['impact'] == impact
    assert run.response['explanation']['urgency'] == urgency
    assert run.response['result']['priority'] == matrix[impact][urgency]


def test_multiple_supported_root_causes_do_not_depend_on_order(service, trusted):
    profile = service.registry.get('service-desk', '0.2.0')
    rules = reverse(reverse(profile.rules))
    overrides = rules['conflictRules']['CONFLICT-ROOT-001']['overrides']
    for override in overrides:
        override['overruledRules'] = [r for r in rules['categoryRules'] if r != override['winnerRule']]
    ambiguous = replace(profile, rules=freeze(rules))
    req = TriageRequest('conflict', 'lab-a', 'service-desk', 'Wifi tài khoản phần mềm',
                        'Bị khóa, wifi vẫn hoạt động với người khác; website khác bình thường, lỗi sau cập nhật. Một máy có thể chờ.')
    assert service.engine.triage(req, ambiguous).needs_review


def test_routing_and_signals_are_profile_owned(service):
    profile = service.registry.get('service-desk', '0.2.0')
    taxonomy, rules = reverse(reverse(profile.taxonomy)), reverse(reverse(profile.rules))
    taxonomy['categories']['NETWORK']['suggestedGroup'] = 'CUSTOM-ROUTE'
    rules['categoryRules']['CAT-NET-001']['positiveSignals'] = ['tín hiệu thử nghiệm']
    custom = replace(profile, rules=freeze(rules), taxonomy=freeze(taxonomy))
    req = TriageRequest('custom', 'lab-a', 'service-desk', 'Tín hiệu thử nghiệm', 'Một máy có thể chờ.')
    assert service.engine.triage(req, custom).suggested_group == 'CUSTOM-ROUTE'
