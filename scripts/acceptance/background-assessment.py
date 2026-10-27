"""Disposable real Keycloak/Java/Python acceptance; preserves PostgreSQL across JVM restarts."""
import argparse
import copy
import hashlib
import json
import os
from pathlib import Path
import secrets
import socket
import subprocess
import time
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen
from uuid import uuid4


ROOT = Path(__file__).resolve().parents[2]


def http(method, url, *, token=None, body=None, form=None, expected=200):
    headers = {}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if form is not None:
        data = urlencode(form).encode()
        headers['Content-Type'] = 'application/x-www-form-urlencoded'
    elif body is not None:
        data = json.dumps(body).encode()
        headers['Content-Type'] = 'application/json'
    else:
        data = None
    try:
        response = urlopen(Request(url, data=data, headers=headers, method=method), timeout=20)
    except HTTPError as failure:
        response = failure
    with response:
        assert response.status == expected, f'HTTP {method}: expected {expected}, received {response.status}'
        raw = response.read()
        return json.loads(raw) if raw else None


def wait(operation, predicate=bool, seconds=180):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        try:
            result = operation()
            if predicate(result):
                return result
        except (URLError, TimeoutError, ConnectionError):
            pass
        time.sleep(.25)
    raise AssertionError('Acceptance deadline exceeded')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--build-image', default='maven:3.9.9-eclipse-temurin-17')
    parser.add_argument('--runtime-image', default='eclipse-temurin:17.0.20.1_1-jre-jammy')
    parser.add_argument('--intelligence-image', default='dx-intelligence:0.1.0')
    parser.add_argument('--gateway-image', default='dx-apisix:3.11.0-sec01')
    parser.add_argument('--postgres-image', help='Optional verified PostgreSQL image override for disposable acceptance')
    parser.add_argument('--jar', type=Path, help='Previously built current-source jar; hash recorded in evidence')
    args = parser.parse_args()
    project = 'assessment-' + uuid4().hex[:12]
    evidence = ROOT / '.local' / project
    evidence.mkdir(parents=True)
    environment = dict(os.environ)
    for name in ('POSTGRES_PASSWORD', 'KC_ADMIN_PASSWORD', 'DEMO_PASSWORD', 'DIRECTORY_CLIENT_SECRET',
                 'BACKGROUND_CLIENT_SECRET', 'INTELLIGENCE_TRIAGE_TOKEN'):
        environment[name] = secrets.token_hex(32)
    environment['INTELLIGENCE_TRIAGE_BASE_URL'] = 'http://intelligence:8000'
    summary = {'project': project, 'checks': [], 'result': 'ERROR'}

    def command(arguments, *, log='docker.log', capture=False):
        result = subprocess.run(arguments, cwd=ROOT, env=environment, capture_output=True, text=True, encoding='utf-8')
        if capture and result.returncode == 0:
            return result.stdout.strip()
        with (evidence / log).open('a', encoding='utf-8') as stream:
            output = result.stdout + result.stderr
            for name in ('POSTGRES_PASSWORD', 'KC_ADMIN_PASSWORD', 'DEMO_PASSWORD', 'DIRECTORY_CLIENT_SECRET',
                         'BACKGROUND_CLIENT_SECRET', 'INTELLIGENCE_TRIAGE_TOKEN'):
                output = output.replace(environment[name], '[REDACTED]')
            stream.write(output)
        assert result.returncode == 0, f'Command failed; see {log}'

    compose_file = evidence / 'compose.json'

    def compose(*arguments, capture=False, log='docker.log'):
        return command(['docker', 'compose', '-p', project, '-f', str(compose_file), *arguments], capture=capture, log=log)

    def passed(name):
        summary['checks'].append({'name': name, 'status': 'PASS'})
        print('PASS ' + name, flush=True)

    started = False
    try:
        realm = json.loads((ROOT / 'infra/keycloak/realm-template.json').read_text(encoding='utf-8'))
        for user in realm['users']:
            if not user.get('serviceAccountClientId'):
                user['credentials'] = [{'type': 'password', 'value': environment['DEMO_PASSWORD'], 'temporary': False}]
        for client in realm['clients']:
            key = {'dx-directory': 'DIRECTORY_CLIENT_SECRET', 'dx-assessment-worker': 'BACKGROUND_CLIENT_SECRET'}.get(client['clientId'])
            if key:
                client['secret'] = environment[key]
        realm_path = evidence / 'realm.json'
        realm_path.write_text(json.dumps(realm), encoding='utf-8')
        with socket.socket() as listener:
            listener.bind(('127.0.0.1', 0))
            keycloak_port = listener.getsockname()[1]
        keycloak = f'http://localhost:{keycloak_port}'
        configuration = json.loads(command(['docker', 'compose', '-f', 'compose.yaml', '-f',
            'infra/compose/compose.triage.yaml', '--profile', 'intelligence', 'config', '--format', 'json'], capture=True))
        configuration.pop('name', None)
        for section in ('volumes', 'networks'):
            for resource in configuration.get(section, {}).values():
                resource.pop('name', None)
        services = configuration['services']
        if args.postgres_image:
            services['postgres']['image'] = args.postgres_image
        del services['frontend']
        services['keycloak']['ports'] = [f'127.0.0.1:{keycloak_port}:8080']
        services['keycloak']['environment']['KC_HOSTNAME'] = keycloak
        services['keycloak']['volumes'] = [f'{realm_path.as_posix()}:/opt/keycloak/data/import/dx-lab-realm.json:ro']
        services['platform'].pop('build', None)
        services['platform']['image'] = args.runtime_image
        services['platform']['entrypoint'] = ['java', '-jar', '/app/app.jar']
        services['platform']['ports'] = ['127.0.0.1::8080']
        services['platform']['volumes'].append(f'{evidence.as_posix()}/app.jar:/app/app.jar:ro')
        services['platform']['environment'].update({
            'JWT_ISSUER': keycloak + '/realms/dx-lab',
            'SERVICE_DESK_BACKGROUND_SCHEDULED': 'false',
            'SERVICE_DESK_BACKGROUND_WORKSPACES': 'lab-a',
            'SERVICE_DESK_BACKGROUND_POLL_MS': '200',
        })
        services['gateway'].pop('build', None)
        services['gateway']['image'] = args.gateway_image
        services['gateway']['ports'] = ['127.0.0.1::9080']
        services['intelligence'].pop('build', None)
        services['intelligence']['image'] = args.intelligence_image
        services['intelligence']['entrypoint'] = ['python', '-m', 'hdpi.http_service', '--host', '0.0.0.0']
        services['intelligence']['environment']['PYTHONPATH'] = '/source/intelligence/src'
        services['intelligence']['volumes'].append(f'{ROOT.as_posix()}:/source:ro')
        services['intelligence'].pop('profiles', None)
        build = 'tar -C /source --exclude=backend/target -cf - backend contracts docs/architecture | tar -C /work -xf -; mvn -B -o -f /work/backend/pom.xml package -DskipTests && cp /work/backend/target/dx-platform-0.1.0-SNAPSHOT.jar /evidence/app.jar'
        if args.jar:
            (evidence / 'app.jar').write_bytes(args.jar.read_bytes())
        else:
            command(['docker', 'run', '--rm', '--entrypoint', 'sh', '-w', '/work',
                '--mount', f'type=bind,source={ROOT},target=/source,readonly',
                '--mount', f'type=bind,source={Path.home() / ".m2/repository"},target=/root/.m2/repository,readonly',
                '--mount', f'type=bind,source={evidence},target=/evidence', args.build_image, '-c', build], log='build.log')
        summary['jarSha256'] = hashlib.sha256((evidence / 'app.jar').read_bytes()).hexdigest()
        summary['images'] = {name: value for name, value in vars(args).items() if name.endswith('_image')}

        def save():
            compose_file.write_text(json.dumps(configuration), encoding='utf-8')

        def sql(statement):
            return compose('exec', '-T', 'postgres', 'psql', '-U', 'core', '-d', 'core', '-Atc', statement, capture=True)

        def start_worker():
            services['platform']['environment']['SERVICE_DESK_BACKGROUND_SCHEDULED'] = 'true'
            save()
            compose('up', '-d', '--no-deps', '--force-recreate', 'platform')
            return backend_ready()

        def backend_ready():
            address = compose('port', 'platform', '8080', capture=True)
            url = 'http://' + address
            wait(lambda: http('GET', url + '/actuator/health'))
            return url

        def login(username):
            return http('POST', keycloak + '/realms/dx-lab/protocol/openid-connect/token', form={
                'grant_type': 'password', 'client_id': 'core-probe', 'username': username,
                'password': environment['DEMO_PASSWORD']})['access_token']

        save()
        started = True
        compose('up', '-d', 'postgres', 'keycloak', 'intelligence', 'platform', 'gateway')
        base = backend_ready()
        requester, coordinator = login('user1'), login('user3')
        prefix = '/api/apps/servicedesk/v1/workspaces/lab-a/tickets'

        def create(title='wifi', description='m\u1ed9t m\u00e1y kh\u00f4ng v\u00e0o m\u1ea1ng, c\u00f3 th\u1ec3 ch\u1edd'):
            response = http('POST', base + prefix, token=login('user1'), body={
                'clientRequestId': str(uuid4()), 'title': title, 'description': description}, expected=201)
            assert response['assessment']['status'] == 'PENDING'
            return response

        def read(ticket, token=None):
            return http('GET', base + prefix + '/' + ticket['id'], token=token or login('user3'))

        def state(ticket, wanted):
            return wait(lambda: read(ticket), lambda result: result['assessment']['status'] == wanted)

        def attempts(ticket):
            return sql(f"select attempts from servicedesk_assessment where ticket_id='{ticket['id']}'")

        compose('stop', 'intelligence')
        ticket = create()
        (evidence / 'pending.json').write_text(json.dumps(ticket, indent=2), encoding='utf-8')
        assert attempts(ticket) == '0'
        passed('create succeeds with Python stopped; persisted PENDING without AI dependency')
        compose('stop', 'platform')
        compose('start', 'intelligence')
        base = start_worker()
        ready = state(ticket, 'READY')
        result = ready['assessment']['result']
        assert result['profileVersion'] == 'service-desk@0.3.0' and result['engine'] == 'rules'
        assert result['evidence'] and result['contractVersion'] == '0.1.0'
        assert (result['category'], result['priority'], result['impact'], result['urgency']) == ('NETWORK', 'LOW', 'I1', 'U2')
        assert ready['classification'] is None and ready['state'] == 'AWAITING_REVIEW'
        (evidence / 'ready.json').write_text(json.dumps(ready, indent=2), encoding='utf-8')
        passed('PENDING survives real JVM replacement on same PostgreSQL volume; real Java to Python READY')
        before = attempts(ticket)
        compose('restart', 'platform')
        base = backend_ready()
        assert read(ticket)['assessment'] == ready['assessment'] and attempts(ticket) == before
        passed('READY survives JVM restart without reevaluation')
        override = http('POST', base + prefix + '/' + ticket['id'] + '/review', token=coordinator, body={
            'category': 'HARDWARE', 'priority': 'HIGH', 'assignee': None, 'reason': 'Coordinator acceptance override'})
        assert override['classification']['source'] == 'COORDINATOR'
        assert override['classification']['category'] == 'HARDWARE'
        assert override['assessment'] == ready['assessment']
        passed('coordinator override independent from AI recommendation; no automatic approval or assignment')
        insufficient = create('Help', 'Something is wrong')
        insufficient_ready = state(insufficient, 'READY')['assessment']['result']
        assert insufficient_ready['needsReview'] and insufficient_ready['reasonCode'] == 'INSUFFICIENT_INFORMATION'
        assert insufficient_ready['impact'] is None and insufficient_ready['urgency'] is None
        passed('insufficient evidence preserves needsReview; literal ABSTAIN not in rules contract')
        http('GET', base + prefix.replace('lab-a', 'lab-b') + '/' + ticket['id'], token=requester, expected=403)
        http('GET', base + prefix + '/' + ticket['id'], token=login('user4'), expected=403)
        passed('workspace isolation with real Keycloak requester tokens')
        compose('stop', 'intelligence')
        unavailable = create()
        wait(lambda: sql(f"select error_code from servicedesk_assessment where ticket_id='{unavailable['id']}'"),
             lambda value: value in ('INTELLIGENCE_UNAVAILABLE', 'INTELLIGENCE_TIMEOUT'))
        assert read(unavailable)['assessment']['status'] == 'PENDING'
        compose('start', 'intelligence')
        state(unavailable, 'READY')
        passed('real Python unavailable retries with bounded backoff and recovers')
        compose('pause', 'intelligence')
        timeout = create()
        wait(lambda: sql(f"select error_code from servicedesk_assessment where ticket_id='{timeout['id']}'"),
             lambda value: value == 'INTELLIGENCE_TIMEOUT')
        compose('unpause', 'intelligence')
        state(timeout, 'READY')
        passed('real Python timeout classified distinctly and recovers')
        compose('pause', 'intelligence')
        crashed = create()
        wait(lambda: sql(f"select lease_token is not null from servicedesk_assessment where ticket_id='{crashed['id']}'"),
             lambda value: value == 't')
        compose('kill', '--signal', 'SIGKILL', 'platform')
        compose('unpause', 'intelligence')
        time.sleep(31)
        compose('start', 'platform')
        base = backend_ready()
        state(crashed, 'READY')
        assert attempts(crashed) == '2'
        passed('SIGKILL after durable claim; actual lease expiration; new JVM recovers same job')
        services['platform2'] = copy.deepcopy(services['platform'])
        services['platform2'].pop('ports', None)
        save()
        compose('up', '-d', '--wait', '--no-deps', 'platform2')
        compose('pause', 'intelligence')
        concurrent = [create() for _ in range(2)]
        identifiers = ','.join("'" + item['id'] + "'" for item in concurrent)
        wait(lambda: sql(f'select count(*) from servicedesk_assessment where ticket_id in ({identifiers}) and lease_token is not null'),
             lambda value: value == '2', seconds=3)
        compose('unpause', 'intelligence')
        concurrent.extend(create() for _ in range(4))
        for item in concurrent:
            state(item, 'READY')
            assert attempts(item) == '1'
            assert sql(f"select count(*) from servicedesk_assessment_history where ticket_id='{item['id']}' and event='READY'") == '1'
        compose('stop', 'platform2')
        passed('two real Backend JVMs share PostgreSQL; six jobs, one claim and READY completion each')
        services['platform']['environment']['INTELLIGENCE_TRIAGE_TOKEN'] = secrets.token_hex(32)
        save()
        base = start_worker()
        denied_python = create()
        degraded = state(denied_python, 'DEGRADED')
        assert degraded['assessment']['errorCode'] == 'INTELLIGENCE_INTEGRATION_FAILED' and attempts(denied_python) == '1'
        services['platform']['environment']['INTELLIGENCE_TRIAGE_TOKEN'] = environment['INTELLIGENCE_TRIAGE_TOKEN']
        passed('invalid private Python credential degrades permanently; not an availability retry')
        services['platform']['environment']['DX_BACKGROUND_CLIENT_SECRET'] = secrets.token_hex(32)
        base = start_worker()
        denied_identity = create()
        degraded = state(denied_identity, 'DEGRADED')
        assert degraded['assessment']['errorCode'] == 'AUTHORIZATION_REVOKED' and attempts(denied_identity) == '1'
        (evidence / 'degraded.json').write_text(json.dumps(degraded, indent=2), encoding='utf-8')
        passed('invalid real Keycloak service credential is permanent authorization failure')
        services['platform']['environment']['DX_BACKGROUND_CLIENT_SECRET'] = environment['BACKGROUND_CLIENT_SECRET']
        base = start_worker()
        admin = login('superadmin')
        admin_url = keycloak + '/admin/realms/dx-lab'
        group = http('GET', admin_url + '/group-by-path/workspaces/lab-a/assessment-worker', token=admin)
        clients = http('GET', admin_url + '/clients?clientId=dx-permissions', token=admin)
        mappings = admin_url + '/groups/' + group['id'] + '/role-mappings/clients/' + clients[0]['id']
        grants = http('GET', mappings, token=admin)
        http('DELETE', mappings, token=admin, body=grants, expected=204)
        revoked = create()
        degraded = state(revoked, 'DEGRADED')
        assert degraded['assessment']['errorCode'] == 'AUTHORIZATION_REVOKED' and attempts(revoked) == '1'
        passed('live Keycloak permission revocation bypasses cached directory grants and fails closed')
        summary['result'] = 'PASS'
    except Exception as failure:
        summary['errorType'] = type(failure).__name__
        print('ERROR ' + type(failure).__name__ + '; inspect isolated evidence: ' + str(evidence), flush=True)
        raise
    finally:
        (evidence / 'summary.json').write_text(json.dumps(summary, indent=2), encoding='utf-8')
        if started:
            compose('logs', '--no-color', log='runtime.log')
            try:
                compose('unpause', 'intelligence')
            except AssertionError:
                pass
            compose('down', '--remove-orphans')
        print('Evidence: ' + str(evidence), flush=True)


if __name__ == '__main__':
    main()
