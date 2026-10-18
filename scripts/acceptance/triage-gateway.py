"""Real Keycloak/APISIX triage acceptance, only on a disposable int01 Compose project."""

import argparse
import json
from pathlib import Path
import re
import secrets
import subprocess
from urllib.error import HTTPError
from urllib.parse import urlencode, urlsplit
from urllib.request import Request, urlopen
from uuid import uuid4

from smart_service_intelligence.contracts.validator import ContractValidator


def http(method, url, *, token=None, payload=None, form=None, expected=200):
    headers = {'X-Request-ID': 'int01-' + uuid4().hex}
    body = None
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if payload is not None:
        body = json.dumps(payload).encode('utf-8')
        headers['Content-Type'] = 'application/json'
    if form is not None:
        body = urlencode(form).encode('utf-8')
        headers['Content-Type'] = 'application/x-www-form-urlencoded'
    try:
        response = urlopen(Request(url, body, headers, method=method), timeout=15)
    except HTTPError as error:
        response = error
    with response:
        expected_statuses = (expected,) if isinstance(expected, int) else expected
        assert response.status in expected_statuses, f'Expected HTTP {expected}, received {response.status}'
        raw = response.read()
        result = json.loads(raw) if raw and response.headers.get_content_type() in ('application/json', 'application/problem+json') else None
        if response.status in (503, 504):
            assert result['code'] == {503: 'INTELLIGENCE_UNAVAILABLE', 504: 'INTELLIGENCE_TIMEOUT'}[response.status]
        return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--project', required=True)
    parser.add_argument('--core-url', default='http://127.0.0.1:19080')
    parser.add_argument('--identity-url', default='http://127.0.0.1:18081')
    parser.add_argument('--env-file', type=Path, default=Path('.env'))
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if not re.fullmatch(r'int01-[a-z0-9-]{1,40}', args.project):
        parser.error('Only a dedicated int01 project is allowed')
    for url in (args.core_url, args.identity_url):
        parsed = urlsplit(url)
        if parsed.scheme != 'http' or parsed.hostname not in ('127.0.0.1', 'localhost') or parsed.username or parsed.password:
            parser.error('Only loopback disposable acceptance services are allowed')
    settings = dict(line.split('=', 1) for line in args.env_file.read_text().splitlines()
                    if '=' in line and not line.startswith('#'))
    compose = ['docker', 'compose', '--env-file', str(args.env_file), '-p', args.project,
               '-f', 'compose.yaml', '-f', 'compose.triage.yaml',
               '-f', 'scripts/acceptance/integration-compose.yaml', '--profile', 'intelligence']
    container = subprocess.check_output(compose + ['ps', '-q', 'intelligence'], text=True).strip()
    assert container, 'Disposable Python service must already be running'
    assert not subprocess.check_output(['docker', 'port', container], text=True).strip(), 'Python must not publish host ports'
    validator = ContractValidator()
    cases = []
    user_id = None
    stopped = False
    admin_api = args.identity_url + '/admin/realms/dx-lab'

    def login(username, password, admin=False):
        realm = 'master' if admin else 'dx-lab'
        return http('POST', args.identity_url + '/realms/' + realm + '/protocol/openid-connect/token',
                    form={'grant_type': 'password', 'client_id': 'admin-cli' if admin else 'core-probe',
                          'username': username, 'password': password})['access_token']

    def counts():
        query = 'select ' + ','.join('(select count(*) from ' + table + ')' for table in
                                    ('servicedesk_ticket', 'process_scope', 'act_hi_procinst', 'act_ru_task', 'audit_event'))
        return subprocess.check_output(compose + ['exec', '-T', 'postgres', 'psql', '-U', 'core', '-d', 'core', '-Atc', query], text=True).strip()

    try:
        identities = {f'user{number}': login(f'user{number}', settings['DEMO_PASSWORD']) for number in range(1, 8)}
        admin = login('admin', settings['KC_ADMIN_PASSWORD'], admin=True)
        username = 'int01-' + uuid4().hex
        password = secrets.token_urlsafe(32)
        http('POST', admin_api + '/users', token=admin, expected=201, payload={
            'username': username, 'enabled': True, 'emailVerified': True, 'email': username + '@example.invalid',
            'firstName': 'Synthetic', 'lastName': 'Requester', 'groups': ['/workspaces/lab-a/requester'],
            'credentials': [{'type': 'password', 'value': password, 'temporary': False}]})
        users = http('GET', admin_api + '/users?' + urlencode({'username': username, 'exact': 'true'}), token=admin)
        assert len(users) == 1
        user_id = users[0]['id']
        other_requester = login(username, password)
        tickets = []
        for workspace, roles, foreign in [('lab-a', ('user1', 'user2', 'user3'), 'user4'),
                                           ('lab-b', ('user4', 'user5', 'user6'), 'user1')]:
            title, description = 'Synthetic network support', 'Synthetic network request for integration acceptance'
            base = args.core_url + '/api/apps/servicedesk/v1/workspaces/' + workspace + '/tickets'
            owner = identities[roles[0]]
            ticket = http('POST', base, token=owner, expected=201, payload={
                'clientRequestId': str(uuid4()), 'title': title, 'description': description})
            route = base + '/' + ticket['id']
            before = counts()
            for role, username in zip(('requester', 'agent', 'coordinator'), roles):
                response = http('POST', route + '/triage', token=identities[username])
                assert response['status'] == 'SUCCESS'
                assert response['runtime']['profileVersion'] == 'service-desk@0.2.0'
                validator.response(response, {'contractVersion': '0.1.0', 'requestId': response['requestId'],
                    'profile': 'service-desk', 'context': {'workspaceId': workspace},
                    'input': {'title': title, 'description': description}})
                cases.append(f'{workspace}:{role}:real-triage-success')
            http('POST', route + '/triage', expected=401)
            http('POST', route + '/triage', token=identities[foreign], expected=403)
            foreign_workspace = 'lab-b' if workspace == 'lab-a' else 'lab-a'
            http('POST', route.replace('/' + workspace + '/', '/' + foreign_workspace + '/') + '/triage',
                 token=identities[foreign], expected=404)
            http('POST', route + '/triage', token=identities['user7'], expected=403)
            http('POST', route + '/triage', token=owner, payload={}, expected=400)
            if workspace == 'lab-a':
                http('POST', route + '/triage', token=other_requester, expected=404)
                cases.append('requester-cannot-read-another-requester-ticket')
            assert before == counts(), 'Triage or denial changed persisted state'
            assert http('GET', route, token=owner) == ticket
            cases.append(workspace + ':authorization-isolation-and-no-state-mutation')
            tickets.append((route, ticket, owner))
        http('POST', args.core_url + '/internal/intelligence/v1/triage', token=identities['user1'], payload={}, expected=404)
        cases.append('private-python-route-not-exposed-by-gateway')
        subprocess.run(compose + ['stop', 'intelligence'], check=True, stdout=subprocess.DEVNULL)
        stopped = True
        route, ticket, owner = tickets[0]
        before = counts()
        problem = http('POST', route + '/triage', token=owner, expected=(503, 504))
        assert 'result' not in problem
        assert before == counts() and http('GET', route, token=owner) == ticket
        progress = http('GET', route + '/progress', token=owner)
        task_id = progress['currentTasks'][0]['id']
        task_route = (args.core_url + '/api/core/v1/workspaces/lab-a/instances/'
                      + ticket['process']['processInstanceId'] + '/tasks/' + task_id)
        http('POST', task_route + '/claim', token=identities['user2'])
        http('POST', task_route + '/complete', token=identities['user2'], expected=204,
             payload={'expectedTaskKey': 'handle', 'outcome': 'resolved'})
        cases.append('real-python-outage-and-manual-workflow-completion')
    finally:
        if stopped:
            subprocess.run(compose + ['up', '-d', '--no-build', '--wait', '--wait-timeout', '60', 'intelligence'], check=True)
        if user_id is not None:
            http('DELETE', admin_api + '/users/' + user_id,
                 token=login('admin', settings['KC_ADMIN_PASSWORD'], admin=True), expected=204)
    report = {'status': 'PASS', 'sourceCommit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip(),
              'identity': 'Real Keycloak password login, JWT verification, APISIX and Java HTTP; no browser UI',
              'project': args.project, 'outageCode': problem['code'], 'cases': cases}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(f'PASS real Keycloak/gateway/Java/Python acceptance: {len(cases)} checks')


if __name__ == '__main__':
    main()
