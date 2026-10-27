import base64
import http.client
import json
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path

root = Path.cwd()
intake = '--intake' in sys.argv
jar = root / '.local/r5-runtime.jar'
suffix = uuid.uuid4().hex[:10]
network = 'r5-e2e-' + suffix
password = uuid.uuid4().hex
containers = []
report = {'stack': 'PostgreSQL 16.6 / Flowable 7.1 / Keycloak 26.0.7 / signed JWT / direct HTTP, no gateway', 'checks': []}


def docker(*arguments):
    return subprocess.run(['docker', *map(str, arguments)], capture_output=True, text=True, check=True).stdout.strip()


def request(url, method='GET', data=None, token=None, form=False):
    headers = {'Authorization': 'Bearer ' + token} if token else {}
    if data is not None:
        data = urllib.parse.urlencode(data).encode() if form else json.dumps(data).encode()
        headers['Content-Type'] = 'application/x-www-form-urlencoded' if form else 'application/json'
    with urllib.request.urlopen(urllib.request.Request(url, data, headers, method=method), timeout=30) as response:
        body = response.read()
        return json.loads(body) if body else None


def wait(url):
    deadline = time.monotonic() + 180
    while time.monotonic() < deadline:
        try:
            with urllib.request.urlopen(url, timeout=2) as response:
                return json.load(response)
        except (urllib.error.URLError, OSError, http.client.HTTPException):
            time.sleep(1)
    raise RuntimeError('Service readiness timeout: ' + url)


def run(name, *arguments):
    containers.append(name)
    docker('run', '-d', '--name', name, '--network', network, *arguments)
    return 'http://' + docker('port', name, '8080/tcp')


def check(name, condition):
    assert condition, name
    report['checks'].append({'name': name, 'status': 'PASS'})
    print('PASS', name, flush=True)


try:
    assert jar.is_file(), 'Build .local/r5-runtime.jar from this branch first'
    docker('network', 'create', network)
    with tempfile.TemporaryDirectory(prefix='r5-e2e-') as temporary:
        realm = json.loads((root / 'infra/keycloak/realm-template.json').read_text(encoding='utf-8'))
        for client in realm['clients']:
            if client['clientId'] == 'dx-directory':
                client['secret'] = password
        for user in realm['users']:
            if user['username'] in ('user1', 'user2', 'user3'):
                user['credentials'] = [{'type': 'password', 'value': password, 'temporary': False}]
            if user['username'] == 'user2':
                user['groups'].append('/support-groups/lab-a/hardware')
        seed = Path(temporary) / 'realm.json'
        seed.write_text(json.dumps(realm), encoding='utf-8')
        database = 'r5-pg-' + suffix
        containers.append(database)
        docker('run', '-d', '--name', database, '--network', network, '--network-alias', 'database',
            '--tmpfs', '/var/lib/postgresql/data', '-e', 'POSTGRES_USER=workflow', '-e', 'POSTGRES_DB=core',
            '-e', 'POSTGRES_PASSWORD=' + password,
            'postgres:16.6-bookworm@sha256:557fea37a744d5f4c8faab304b0a90858b53ab119735a88c131fd19dab802f36')
        keycloak = run('r5-kc-' + suffix, '--network-alias', 'keycloak', '-p', '127.0.0.1::8080',
            '-e', 'KC_BOOTSTRAP_ADMIN_USERNAME=admin', '-e', 'KC_BOOTSTRAP_ADMIN_PASSWORD=' + password,
            '--mount', f'type=bind,source={seed},target=/opt/keycloak/data/import/realm.json,readonly',
            'quay.io/keycloak/keycloak:26.0.7', 'start-dev', '--import-realm')
        wait(keycloak + '/realms/dx-lab/.well-known/openid-configuration')
        backend_name = 'r5-backend-' + suffix
        backend = run(backend_name, '-p', '127.0.0.1::8080', '--mount', f'type=bind,source={jar},target=/app.jar,readonly',
            '-e', 'DATABASE_URL=jdbc:postgresql://database:5432/core', '-e', 'SPRING_DATASOURCE_USERNAME=workflow',
            '-e', 'POSTGRES_PASSWORD=' + password, '-e', 'DX_DIRECTORY_URL=http://keycloak:8080',
            '-e', 'DX_DIRECTORY_CLIENT_SECRET=' + password, '-e', 'JWT_ISSUER=' + keycloak + '/realms/dx-lab',
            '-e', 'JWT_JWKS=http://keycloak:8080/realms/dx-lab/protocol/openid-connect/certs',
            '-e', 'DX_STORAGE_ROOT=/private/files', 'eclipse-temurin:21-jre-alpine', 'java', '-jar', '/app.jar',
            '--service-desk.background.scheduled=false', '--service-desk.sla.poll-millis=1000',
            '--serviceDesk.intake.new-tickets=' + str(intake).lower(), '--serviceDesk.intake.true-wait=' + str(intake).lower())
        wait(backend + '/actuator/health')
        tokens = {user: request(keycloak + '/realms/dx-lab/protocol/openid-connect/token', 'POST',
            {'grant_type': 'password', 'client_id': 'core-probe', 'username': user, 'password': password}, form=True)['access_token']
            for user in ('user1', 'user2', 'user3')}
        subject = json.loads(base64.urlsafe_b64decode(tokens['user2'].split('.')[1] + '=='))['sub']

        def call(path, actor='user3', method='GET', data=None):
            return request(backend + path, method, data, tokens[actor])

        base = '/api/apps/servicedesk/v1/workspaces/lab-a/tickets'
        hpdi = '/api/hpdi/v1/workspaces/lab-a'
        call(hpdi + '/duty/me', 'user2', 'PUT', {'onDuty': True})
        ticket = call(base, 'user1', 'POST', {'clientRequestId': str(uuid.uuid4()), 'title': 'Real R5 acceptance', 'description': 'Synthetic SLA request'})
        check('unreviewed ticket has no SLA', ticket['sla'] is None)
        path = base + '/' + ticket['id']
        def approve(priority, assignee):
            decision = {'category': 'HARDWARE', 'priority': priority, 'assignee': assignee, 'reason': 'Coordinator approval'}
            if intake:
                state = call(path + '/intake')
                call(path + '/intake/manual-release', method='POST', data={'expectedContextRevision': state['contextRevision'],
                    'expectedVersion': state['version'], 'operationId': str(uuid.uuid4()), 'reason': 'Human release for SLA acceptance'})
                state = call(path + '/intake')
                check('human release does not initialize handle SLA', call(path)['sla'] is None)
                decision.update(expectedContextRevision=state['contextRevision'], expectedIntakeVersion=state['version'])
            return call(path + '/review', method='POST', data=decision)
        reviewed = approve('HIGH', subject)
        check('approval initializes authoritative SLA', reviewed['sla'] is not None and reviewed['classification']['basePriority'] == 'HIGH')
        task = hpdi + '/tasks/' + reviewed['taskId']
        started = call(task + '/start', 'user2', 'POST')
        check('START freezes response result', not started['sla']['startBreached'])
        paused = call(task + '/pause', 'user2', 'POST', {'reason': 'WAITING_REQUESTER'})
        check('requester wait pauses SLA', paused['sla']['clockPaused'])
        docker('restart', backend_name)
        backend = 'http://' + docker('port', backend_name, '8080/tcp')
        wait(backend + '/actuator/health')
        persisted = call(path, 'user1')
        check('pause and original start deadline survive backend restart', persisted['sla']['clockPaused'] and persisted['sla']['startBy'] == reviewed['sla']['startBy'])
        call(task + '/resume', 'user2', 'POST')
        call(task + '/complete', 'user2', 'POST', {'expectedTaskKey': 'handle', 'outcome': 'resolved'})
        completed = call(path, 'user1')
        check('handle resolved stops SLA before requester confirmation', completed['state'] == 'AWAITING_CONFIRMATION' and not completed['sla']['resolveBreached'] and not completed['sla']['clockPaused'])
        call(hpdi + '/tasks/' + completed['taskId'] + '/complete', 'user1', 'POST', {'expectedTaskKey': 'confirm', 'outcome': 'rework'})
        check('rework awaiting approval has no fabricated SLA', call(path, 'user1')['sla'] is None)
        reviewed = approve('LOW', None)
        check('new round starts a fresh LOW policy with no assignment', reviewed['classification']['basePriority'] == 'LOW' and reviewed['sla'] is not None and reviewed['assignee'] is None)
        docker('exec', database, 'psql', '-U', 'workflow', '-d', 'core', '-v', 'ON_ERROR_STOP=1', '-c',
            "update servicedesk_sla set approved_at=approved_at-interval '10 days', start_by=start_by-interval '10 days', resolve_by=resolve_by-interval '10 days' where ticket_id='" + str(uuid.UUID(ticket['id'])) + "' and resolved_at is null")
        for attempt in range(30):
            escalated = call(path, 'user1')
            if escalated['classification']['escalated']:
                break
            time.sleep(1)
        check('scheduled worker escalates unassigned LOW once without changing base priority', escalated['classification']['priority'] == 'MEDIUM' and escalated['classification']['basePriority'] == 'LOW')
        count = docker('exec', database, 'psql', '-U', 'workflow', '-d', 'core', '-Atc',
            "select count(*) from audit_event where action='servicedesk.sla.escalated' and resource='" + str(uuid.UUID(ticket['id'])) + "'")
        check('system escalation records exactly one transactional audit', count == '1')
        report['status'] = 'PASS'
except Exception as error:
    report.update(status='ERROR', error=type(error).__name__ + ': ' + str(error))
    raise
finally:
    (root / ('.local/r5-intake-runtime.json' if intake else '.local/r5-real-runtime.json')).write_text(json.dumps(report, indent=2), encoding='utf-8')
    for name in reversed(containers):
        subprocess.run(['docker', 'rm', '-f', '-v', name], capture_output=True)
    subprocess.run(['docker', 'network', 'rm', network], capture_output=True)
