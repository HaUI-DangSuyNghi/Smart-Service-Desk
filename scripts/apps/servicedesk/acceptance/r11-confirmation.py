"""Real JWT/Flowable/PostgreSQL worker and restart probe; test-only clock injection."""
import base64
import datetime
import http.client
import json
import shutil
import subprocess
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path

root = Path(__file__).resolve().parents[4]
jar = root / 'backend/target/dx-platform-0.1.0-SNAPSHOT.jar'
suffix = uuid.uuid4().hex[:10]
network = 'r11-probe-' + suffix
password = uuid.uuid4().hex
containers = []
report = {'checks': [], 'clock': 'test-classpath-only UTC offset; real DB and scheduled worker'}
responses = []


def docker(*args):
    return subprocess.run(['docker', *map(str, args)], capture_output=True, text=True, check=True).stdout.strip()


def request(url, method='GET', data=None, token=None, form=False):
    headers = {'Authorization': 'Bearer ' + token} if token else {}
    if data is not None:
        data = urllib.parse.urlencode(data).encode() if form else json.dumps(data).encode()
        headers['Content-Type'] = 'application/x-www-form-urlencoded' if form else 'application/json'
    with urllib.request.urlopen(urllib.request.Request(url, data, headers, method=method), timeout=30) as response:
        payload = response.read()
        value = json.loads(payload) if payload else None
        prefix = '/api/apps/servicedesk/v1'
        split = urllib.parse.urlsplit(url).path
        if split.startswith(prefix) and response.status == 200:
            responses.append({'path': split[len(prefix):], 'method': method, 'status': response.status, 'bodyJson': payload.decode('utf-8')})
        return value


def wait(url):
    until = time.monotonic() + 180
    while time.monotonic() < until:
        try:
            return request(url)
        except (urllib.error.URLError, OSError, http.client.HTTPException):
            time.sleep(1)
    raise RuntimeError('Readiness timeout: ' + url)


def check(name, condition):
    assert condition, name
    report['checks'].append({'name': name, 'status': 'PASS'})
    print('PASS', name, flush=True)


def run(name, *args):
    containers.append(name)
    docker('run', '-d', '--name', name, '--network', network, *args)
    return 'http://' + docker('port', name, '8080/tcp')


try:
    assert jar.is_file(), 'Build backend and test classes first'
    docker('network', 'create', network)
    with tempfile.TemporaryDirectory(prefix='r11-probe-') as temporary:
        temporary = Path(temporary)
        probe = temporary / 'probe'
        classes = probe / 'vn/haui/dx/core/workflow/internal'
        classes.mkdir(parents=True)
        compiled = list((root / 'backend/target/test-classes/vn/haui/dx/core/workflow/internal').glob('TimerRuntimeProbe*.class'))
        assert compiled, 'Compile TimerRuntimeProbe (test-only) first'
        for path in compiled:
            shutil.copy2(path, classes / path.name)
        offset = probe / 'offset'
        offset.write_text('0', encoding='ascii')
        realm = json.loads((root / 'infra/keycloak/realm-template.json').read_text(encoding='utf-8'))
        for client in realm['clients']:
            if client['clientId'] in ('dx-directory', 'dx-assessment-worker'):
                client['secret'] = password
        for user in realm['users']:
            if user['username'] in ('user1', 'user2', 'user3'):
                user['credentials'] = [{'type': 'password', 'value': password, 'temporary': False}]
            if user['username'] == 'user2':
                user['groups'].append('/support-groups/lab-a/hardware')
            if user['username'] == 'service-account-dx-assessment-worker':
                user['groups'].append('/workspaces/lab-a/workflow-timer')
        seed = temporary / 'realm.json'
        seed.write_text(json.dumps(realm), encoding='utf-8')
        database = 'r11-pg-' + suffix
        containers.append(database)
        docker('run', '-d', '--name', database, '--network', network, '--network-alias', 'database',
               '--tmpfs', '/var/lib/postgresql/data', '-e', 'POSTGRES_USER=workflow', '-e', 'POSTGRES_DB=core',
               '-e', 'POSTGRES_PASSWORD=' + password,
               'postgres:16.6-bookworm@sha256:557fea37a744d5f4c8faab304b0a90858b53ab119735a88c131fd19dab802f36')
        keycloak = run('r11-kc-' + suffix, '--network-alias', 'keycloak', '-p', '127.0.0.1::8080',
                       '-e', 'KC_BOOTSTRAP_ADMIN_USERNAME=admin', '-e', 'KC_BOOTSTRAP_ADMIN_PASSWORD=' + password,
                       '-e', 'KC_HOSTNAME=http://keycloak:8080', '--mount', f'type=bind,source={seed},target=/opt/keycloak/data/import/realm.json,readonly',
                       'quay.io/keycloak/keycloak:26.0.7', 'start-dev', '--import-realm')
        wait(keycloak + '/realms/dx-lab/.well-known/openid-configuration')
        backend_name = 'r11-backend-' + suffix
        backend = run(backend_name, '-p', '127.0.0.1::8080', '--mount', f'type=bind,source={jar},target=/app.jar,readonly',
                      '--mount', f'type=bind,source={probe},target=/probe,readonly',
                      '-e', 'DATABASE_URL=jdbc:postgresql://database:5432/core', '-e', 'SPRING_DATASOURCE_USERNAME=workflow',
                      '-e', 'POSTGRES_PASSWORD=' + password, '-e', 'DX_DIRECTORY_URL=http://keycloak:8080',
                      '-e', 'DX_DIRECTORY_CLIENT_SECRET=' + password, '-e', 'JWT_ISSUER=http://keycloak:8080/realms/dx-lab',
                      '-e', 'JWT_JWKS=http://keycloak:8080/realms/dx-lab/protocol/openid-connect/certs',
                      '-e', 'DX_STORAGE_ROOT=/private/files', 'eclipse-temurin:21-jre-alpine',
                      'java', '-Dloader.path=/probe', '-cp', '/app.jar', 'org.springframework.boot.loader.launch.PropertiesLauncher',
                      '--spring.profiles.active=r11-clock-probe', '--service-desk.background.scheduled=false',
                      '--serviceDesk.confirmation.poll-ms=1000', '--hpdi.alerts.poll-millis=1000',
                      '--dx.background.token-url=http://keycloak:8080/realms/dx-lab/protocol/openid-connect/token',
                      '--dx.background.client-secret=' + password)
        wait(backend + '/actuator/health')
        tokens = {user: request(keycloak + '/realms/dx-lab/protocol/openid-connect/token', 'POST',
                  {'grant_type': 'password', 'client_id': 'core-probe', 'username': user, 'password': password}, form=True)['access_token']
                  for user in ('user1', 'user2', 'user3')}
        agent = json.loads(base64.urlsafe_b64decode(tokens['user2'].split('.')[1] + '=='))['sub']
        base = '/api/apps/servicedesk/v1/workspaces/lab-a/tickets'
        hpdi = '/api/hpdi/v1/workspaces/lab-a'

        def call(path, actor='user3', method='GET', data=None):
            return request(backend + path, method, data, tokens[actor])

        call(hpdi + '/duty/me', 'user2', 'PUT', {'onDuty': True})

        def waiting():
            ticket = call(base, 'user1', 'POST', {'clientRequestId': str(uuid.uuid4()), 'title': 'R11 runtime', 'description': 'Synthetic runtime probe'})
            path = base + '/' + ticket['id']
            reviewed = call(path + '/review', method='POST', data={'category': 'HARDWARE', 'priority': 'HIGH', 'assignee': agent, 'reason': 'R11 acceptance'})
            task = hpdi + '/tasks/' + reviewed['taskId']
            call(task + '/start', 'user2', 'POST')
            call(task + '/complete', 'user2', 'POST', {'expectedTaskKey': 'handle', 'outcome': 'resolved'})
            return call(path, 'user1')

        pending, accepted, reopened, legacy_overdue = waiting(), waiting(), waiting(), waiting()
        parse = datetime.datetime.fromisoformat
        check('deadline is exactly 72 continuous hours', parse(pending['confirmDeadlineAt'].replace('Z', '+00:00')) - parse(pending['confirmStartedAt'].replace('Z', '+00:00')) == datetime.timedelta(hours=72))
        for ticket, outcome in ((accepted, 'accepted'), (reopened, 'rework')):
            call(hpdi + '/tasks/' + ticket['confirmGeneration'] + '/complete', 'user1', 'POST', {'expectedTaskKey': 'confirm', 'outcome': outcome})
        check('requester provenance is persisted', call(base + '/' + accepted['id'], 'user1')['closedBy'] == 'REQUESTER')
        try:
            call(hpdi + '/tasks/' + pending['confirmGeneration'] + '/complete', 'user2', 'POST', {'expectedTaskKey': 'confirm', 'outcome': 'accepted'})
            raise AssertionError('Agent completed Requester task')
        except urllib.error.HTTPError as failure:
            check('real JWT Agent cannot confirm', failure.code == 403)
        docker('stop', backend_name)
        docker('exec', database, 'psql', '-U', 'workflow', '-d', 'core', '-v', 'ON_ERROR_STOP=1', '-c',
               "delete from servicedesk_confirmation where generation='" + str(uuid.UUID(pending['confirmGeneration'])) + "'")
        docker('start', backend_name)
        backend = 'http://' + docker('port', backend_name, '8080/tcp')
        wait(backend + '/actuator/health')
        until = time.monotonic() + 60
        while time.monotonic() < until:
            restored = call(base + '/' + pending['id'], 'user1')
            if restored.get('confirmGeneration'):
                break
            time.sleep(1)
        check('restart enrolls legacy active confirm from native task time', restored.get('confirmGeneration') == pending['confirmGeneration'] and restored.get('confirmStartedAt') == pending['confirmStartedAt'])
        check('restart before deadline preserves generation and deadline', restored['confirmGeneration'] == pending['confirmGeneration'] and restored['confirmDeadlineAt'] == pending['confirmDeadlineAt'] and restored['closedBy'] is None)
        docker('stop', backend_name)
        docker('exec', database, 'psql', '-U', 'workflow', '-d', 'core', '-v', 'ON_ERROR_STOP=1', '-c',
               "delete from servicedesk_confirmation where generation='" + str(uuid.UUID(legacy_overdue['confirmGeneration'])) + "'")
        offset.write_text('259205', encoding='ascii')
        docker('start', backend_name)
        backend = 'http://' + docker('port', backend_name, '8080/tcp')
        wait(backend + '/actuator/health')
        until = time.monotonic() + 60
        while time.monotonic() < until:
            closed = call(base + '/' + pending['id'], 'user1')
            if closed.get('closedBy') == 'AUTO':
                break
            time.sleep(1)
        check('real scheduled worker closes due generation with signed service identity', closed.get('closedBy') == 'AUTO' and closed['state'] == 'CLOSED')
        check('auto-close preserves process and resolved SLA', closed['process'] == pending['process'] and closed['sla'] == pending['sla'] and closed['workedMinutes'] == pending['workedMinutes'])
        held = call(base + '/' + legacy_overdue['id'], 'user1')
        check('overdue legacy generation stays active on manual rollout hold', held.get('closedBy') is None and held['confirmationHistory'][0].get('rolloutHold') is True and held['confirmDeadlineAt'] == legacy_overdue['confirmDeadlineAt'])
        check('accepted generation remains REQUESTER' , call(base + '/' + accepted['id'], 'user1')['closedBy'] == 'REQUESTER')
        again = call(base + '/' + reopened['id'], 'user1')
        check('rework before deadline is never auto-closed', again['closedBy'] is None and again['state'] == 'REOPENED' and again['reopenCount'] == 1 and again['process'] == reopened['process'])
        until = time.monotonic() + 60
        while time.monotonic() < until:
            inbox = call(hpdi + '/alerts')['items']
            events = [event for event in inbox if event['businessKey'] == reopened['id'] and event['type'] in ('REOPENED', 'REOPEN_UNCLAIMED')]
            if len(events) == 2:
                break
            time.sleep(1)
        check('both persistent reopen events reach Coordinator exactly once', len(events) == 2 and len({event['type'] for event in events}) == 2)
        check('old Agent does not receive reopen events', not any(event['type'] in ('REOPENED', 'REOPEN_UNCLAIMED') for event in call(hpdi + '/alerts', 'user2')['items']))
        docker('restart', backend_name)
        backend = 'http://' + docker('port', backend_name, '8080/tcp')
        wait(backend + '/actuator/health')
        check('restart after commit preserves AUTO provenance', call(base + '/' + pending['id'], 'user1')['closedAt'] == closed['closedAt'])
        count = docker('exec', database, 'psql', '-U', 'workflow', '-d', 'core', '-Atc',
                       "select count(*) from audit_event where action='task.timer.complete' and resource='" + str(uuid.UUID(pending['confirmGeneration'])) + "'")
        check('restart and retries do not duplicate completion audit', count == '1')
        report['status'] = 'PASS'
except Exception as error:
    report.update(status='ERROR', error=type(error).__name__ + ': ' + str(error))
    for name in containers:
        if 'backend' in name:
            (root / '.local/r11-runtime-backend.log').write_text(subprocess.run(['docker', 'logs', name], capture_output=True, text=True).stdout, encoding='utf-8')
    raise
finally:
    (root / '.local/r11-runtime.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    (root / '.local/r11-http.json').write_text(json.dumps(responses, indent=2), encoding='utf-8')
    for name in reversed(containers):
        subprocess.run(['docker', 'rm', '-f', '-v', name], capture_output=True)
    subprocess.run(['docker', 'network', 'rm', network], capture_output=True)
