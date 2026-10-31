import base64
import http.client
import json
import subprocess
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path

root = Path.cwd()
suffix = uuid.uuid4().hex[:10]
network = 'r2-e2e-' + suffix
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
    for attempt in range(120):
        try:
            return request(url)
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
    docker('network', 'create', network)
    with tempfile.TemporaryDirectory(prefix='r2-e2e-') as temporary:
        realm = json.loads((root / 'infra/keycloak/realm-template.json').read_text(encoding='utf-8'))
        for client in realm['clients']:
            if client['clientId'] == 'dx-directory':
                client['secret'] = password
        for user in realm['users']:
            if user['username'] in ('requester1', 'agent1', 'coordinator1'):
                user['credentials'] = [{'type': 'password', 'value': password, 'temporary': False}]
            # Template đã có sẵn tổ cho agent1; chỉ thêm khi thiếu để không trùng nhóm
            if user['username'] == 'agent1' and '/support-groups/lab-a/hardware' not in user['groups']:
                user['groups'].append('/support-groups/lab-a/hardware')
        seed = Path(temporary) / 'realm.json'
        seed.write_text(json.dumps(realm), encoding='utf-8')
        database = 'r2-pg-' + suffix
        containers.append(database)
        docker('run', '-d', '--name', database, '--network', network, '--network-alias', 'database',
            '--tmpfs', '/var/lib/postgresql/data', '-e', 'POSTGRES_USER=workflow', '-e', 'POSTGRES_DB=core',
            '-e', 'POSTGRES_PASSWORD=' + password, 'postgres:16.6-bookworm')
        keycloak = run('r2-kc-' + suffix, '--network-alias', 'keycloak', '-p', '127.0.0.1::8080',
            '-e', 'KC_BOOTSTRAP_ADMIN_USERNAME=admin', '-e', 'KC_BOOTSTRAP_ADMIN_PASSWORD=' + password,
            '--mount', f'type=bind,source={seed},target=/opt/keycloak/data/import/realm.json,readonly',
            'quay.io/keycloak/keycloak:26.0.7', 'start-dev', '--import-realm')
        wait(keycloak + '/realms/dx-lab/.well-known/openid-configuration')
        backend = run('r2-backend-' + suffix, '-p', '127.0.0.1::8080', '--mount',
            f'type=bind,source={root / "backend/target/dx-platform-0.1.0-SNAPSHOT.jar"},target=/app.jar,readonly',
            '-e', 'DATABASE_URL=jdbc:postgresql://database:5432/core', '-e', 'SPRING_DATASOURCE_USERNAME=workflow',
            '-e', 'POSTGRES_PASSWORD=' + password, '-e', 'DX_DIRECTORY_URL=http://keycloak:8080',
            '-e', 'DX_DIRECTORY_CLIENT_SECRET=' + password, '-e', 'JWT_ISSUER=' + keycloak + '/realms/dx-lab',
            '-e', 'JWT_JWKS=http://keycloak:8080/realms/dx-lab/protocol/openid-connect/certs',
            '-e', 'DX_STORAGE_ROOT=/private/files', 'eclipse-temurin:21-jre-alpine', 'java', '-jar', '/app.jar',
            '--service-desk.background.scheduled=false')
        wait(backend + '/actuator/health')
        tokens = {user: request(keycloak + '/realms/dx-lab/protocol/openid-connect/token', 'POST',
            {'grant_type': 'password', 'client_id': 'core-probe', 'username': user, 'password': password}, form=True)['access_token']
            for user in ('requester1', 'agent1', 'coordinator1')}
        subject = json.loads(base64.urlsafe_b64decode(tokens['agent1'].split('.')[1] + '=='))['sub']
        def call(path, actor='coordinator1', method='GET', data=None):
            return request(backend + path, method, data, tokens[actor])
        base = '/api/apps/servicedesk/v1/workspaces/lab-a/tickets'
        ticket = call(base, 'requester1', 'POST', {'clientRequestId': str(uuid.uuid4()), 'title': 'Real R2 smoke', 'description': 'Synthetic request'})
        preview = base + '/' + ticket['id'] + '/assignment-suggestion?category=HARDWARE&priority=HIGH'
        check('off-duty yields no recommendation', call(preview)['recommended'] is None)
        call('/api/hpdi/v1/workspaces/lab-a/duty/me', 'agent1', 'PUT', {'onDuty': True})
        def snapshot():
            return docker('exec', database, 'psql', '-U', 'workflow', '-d', 'core', '-Atc',
                'select (select count(*) from audit_event),(select count(*) from servicedesk_assignment_event),(select count(*) from servicedesk_assignment_cursor),(select count(*) from act_ru_task),(select count(*) from act_ru_variable)')
        before = snapshot()
        suggestion = call(preview)
        check('real current support-group membership', suggestion['recommended'] == subject)
        check('GET polling is stable and read-only', call(preview) == suggestion and snapshot() == before)
        admin = request(keycloak + '/realms/master/protocol/openid-connect/token', 'POST',
            {'grant_type': 'password', 'client_id': 'admin-cli', 'username': 'admin', 'password': password}, form=True)['access_token']
        groups = request(keycloak + '/admin/realms/dx-lab/users/' + subject + '/groups', token=admin)
        support = next(group['id'] for group in groups if group['path'] == '/support-groups/lab-a/hardware')
        membership = keycloak + '/admin/realms/dx-lab/users/' + subject + '/groups/' + support
        request(membership, 'DELETE', token=admin)
        check('support-group revocation is fresh', call(preview)['recommended'] is None)
        decision = {'category': 'HARDWARE', 'priority': 'HIGH', 'assignee': subject, 'reason': 'Explicit coordinator decision'}
        try:
            call(base + '/' + ticket['id'] + '/review', method='POST', data=decision)
            raise AssertionError('Revoked assignment must fail')
        except urllib.error.HTTPError as error:
            check('assignment rechecks real revoked membership', error.code == 400)
        request(membership, 'PUT', token=admin)
        user_url = keycloak + '/admin/realms/dx-lab/users/' + subject
        request(user_url, 'PUT', {'enabled': False}, admin)
        check('disabled Keycloak account is excluded', call(preview)['recommended'] is None)
        request(user_url, 'PUT', {'enabled': True}, admin)
        reviewed = call(base + '/' + ticket['id'] + '/review', method='POST', data=decision)
        check('coordinator alone commits assignment', reviewed['assignee'] == subject)
        result = call('/api/hpdi/v1/workspaces/lab-a/tasks/' + reviewed['taskId'] + '/assignment-suggestion')
        candidate = next(candidate for candidate in result['candidates'] if candidate['subject'] == subject)
        queue = call('/api/hpdi/v1/workspaces/lab-a/queue?assignee=' + subject)
        check('actual daily assignment and workload', candidate['assignedToday'] == 1 and candidate['loadMinutes'] == 0 and queue['loadMinutes'] == 90)
        report.update(status='PASS', suggestion=result)
except Exception as error:
    report.update(status='ERROR', error=type(error).__name__ + ': ' + str(error))
    for name in containers:
        try:
            (root / '.local' / ('r2-' + name.split('-')[1] + '-runtime.log')).write_text(docker('logs', name), encoding='utf-8')
        except Exception:
            pass
    raise
finally:
    (root / '.local/r2-real-e2e.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    for name in reversed(containers):
        subprocess.run(['docker', 'rm', '-f', '-v', name], capture_output=True)
    subprocess.run(['docker', 'network', 'rm', network], capture_output=True)
