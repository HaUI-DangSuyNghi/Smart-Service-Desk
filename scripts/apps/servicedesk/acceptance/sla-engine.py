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
alerts = '--alerts' in sys.argv
handover = '--handover' in sys.argv
jar = root / ('.local/r2-r6-runtime.jar' if handover else '.local/r7-r8-runtime.jar' if alerts else '.local/r5-runtime.jar')
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
            if client['clientId'] == 'dx-directory' or alerts and client['clientId'] == 'dx-assessment-worker':
                client['secret'] = password
        for user in realm['users']:
            if user['username'] in ('requester1', 'agent1', 'coordinator1'):
                user['credentials'] = [{'type': 'password', 'value': password, 'temporary': False}]
            if user['username'] == 'agent1':
                user['groups'].append('/support-groups/lab-a/hardware')
        if handover:
            replacement = json.loads(json.dumps(next(user for user in realm['users'] if user['username'] == 'agent1')))
            replacement.update(id=str(uuid.uuid4()), username='handover-agent', email='handover-agent@example.test')
            realm['users'].append(replacement)
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
            *(['-e', 'KC_HOSTNAME=http://keycloak:8080'] if alerts else []),
            'quay.io/keycloak/keycloak:26.0.7', 'start-dev', '--import-realm')
        wait(keycloak + '/realms/dx-lab/.well-known/openid-configuration')
        backend_name = 'r5-backend-' + suffix
        backend = run(backend_name, '-p', '127.0.0.1::8080', '--mount', f'type=bind,source={jar},target=/app.jar,readonly',
            '-e', 'DATABASE_URL=jdbc:postgresql://database:5432/core', '-e', 'SPRING_DATASOURCE_USERNAME=workflow',
            '-e', 'POSTGRES_PASSWORD=' + password, '-e', 'DX_DIRECTORY_URL=http://keycloak:8080',
            '-e', 'DX_DIRECTORY_CLIENT_SECRET=' + password, '-e', 'JWT_ISSUER=' + ('http://keycloak:8080' if alerts else keycloak) + '/realms/dx-lab',
            '-e', 'JWT_JWKS=http://keycloak:8080/realms/dx-lab/protocol/openid-connect/certs',
            '-e', 'DX_STORAGE_ROOT=/private/files', 'eclipse-temurin:21-jre-alpine', 'java', '-jar', '/app.jar',
            '--service-desk.background.scheduled=false', '--service-desk.sla.poll-millis=1000',
            '--serviceDesk.intake.new-tickets=' + str(intake).lower(), '--serviceDesk.intake.true-wait=' + str(intake).lower(),
            *(['--dx.background.token-url=http://keycloak:8080/realms/dx-lab/protocol/openid-connect/token',
               '--dx.background.client-secret=' + password, '--hpdi.alerts.poll-millis=1000', '--service-desk.alerts.poll-millis=1000'] if alerts else []))
        wait(backend + '/actuator/health')
        tokens = {user: request(keycloak + '/realms/dx-lab/protocol/openid-connect/token', 'POST',
            {'grant_type': 'password', 'client_id': 'core-probe', 'username': user, 'password': password}, form=True)['access_token']
            for user in (('requester1', 'agent1', 'coordinator1', 'handover-agent') if handover else ('requester1', 'agent1', 'coordinator1'))}
        subject = json.loads(base64.urlsafe_b64decode(tokens['agent1'].split('.')[1] + '=='))['sub']

        def call(path, actor='coordinator1', method='GET', data=None):
            return request(backend + path, method, data, tokens[actor])

        base = '/api/apps/servicedesk/v1/workspaces/lab-a/tickets'
        hpdi = '/api/hpdi/v1/workspaces/lab-a'
        call(hpdi + '/duty/me', 'agent1', 'PUT', {'onDuty': True})
        ticket = call(base, 'requester1', 'POST', {'clientRequestId': str(uuid.uuid4()), 'title': 'Real R5 acceptance', 'description': 'Synthetic SLA request'})
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
        if handover:
            estimate = call(task + '/estimate-requests', 'agent1', 'POST', {'proposedMinutes': 120, 'reason': 'Replace mainboard'})
            replacement_subject = json.loads(base64.urlsafe_b64decode(tokens['handover-agent'].split('.')[1] + '=='))['sub']
            call(hpdi + '/duty/me', 'handover-agent', 'PUT', {'onDuty': True})
            call(task + '/assign', method='POST', data={'assignee': replacement_subject})
            superseded = call(hpdi + '/estimate-requests?status=SUPERSEDED')['items']
            retired = next(item for item in superseded if item['id'] == estimate['id'])
            check('same-task reassignment supersedes with provenance not decision', retired['supersededReason'] == 'ASSIGNMENT_CHANGED'
                and retired['supersededAt'] is not None and retired['supersededBy'] is not None and retired['decidedAt'] is None)
            for route, actor, data, expected in (
                (task + '/estimate-requests', 'agent1', {'proposedMinutes': 180, 'reason': 'Old assignee'}, 403),
                (hpdi + '/estimate-requests/' + estimate['id'] + '/decision', 'coordinator1', {'approved': True}, 409)):
                try:
                    call(route, actor, 'POST', data)
                    raise AssertionError('Old assignee request must not control ET')
                except urllib.error.HTTPError as failure:
                    check('reassignment rejects stale control ' + str(expected), failure.code == expected)
            replacement_request = call(task + '/estimate-requests', 'handover-agent', 'POST', {'proposedMinutes': 130, 'reason': 'New assignee'})
            check('new assignee can immediately request ET', replacement_request['status'] == 'PENDING')
            call(task + '/assign', method='POST', data={'assignee': subject})
            retired_ids = {item['id'] for item in call(hpdi + '/estimate-requests?status=SUPERSEDED')['items']
                if item['taskId'] == reviewed['taskId']}
            check('assignment cycle never revives prior request', retired_ids == {estimate['id'], replacement_request['id']})
            report['supersededRequest'] = retired
            estimate = call(task + '/estimate-requests', 'agent1', 'POST', {'proposedMinutes': 120, 'reason': 'Replace mainboard'})
            report['estimateRequest'] = estimate
            check('request does not change ET', call(path)['estimate']['minutes'] == reviewed['estimate']['minutes'])
            docker('restart', backend_name)
            backend = 'http://' + docker('port', backend_name, '8080/tcp')
            wait(backend + '/actuator/health')
            pending = call(hpdi + '/estimate-requests?status=PENDING')['items']
            check('estimate request survives real restart', any(item['id'] == estimate['id'] for item in pending))
            report['estimateDecision'] = call(hpdi + '/estimate-requests/' + estimate['id'] + '/decision', method='POST', data={'approved': True})
            check('approved estimate uses canonical ET storage', call(path)['estimate'] == {'minutes': 120, 'basis': 'COORDINATOR'})
            call(task + '/start', 'agent1', 'POST')
            call(task + '/pause', 'agent1', 'POST', {'reason': 'WAITING_EXTERNAL'})
            stale = call(task + '/estimate-requests', 'agent1', 'POST', {'proposedMinutes': 180, 'reason': 'More diagnostics'})
            call(task + '/release', 'agent1', 'POST', {'reason': 'HANDOVER', 'note': 'Hardware checked; continue diagnostics'})
            docker('restart', backend_name)
            backend = 'http://' + docker('port', backend_name, '8080/tcp')
            wait(backend + '/actuator/health')
            returned = call(path)
            check('release survives real restart without closing process', returned['state'] == 'AWAITING_REVIEW' and returned['process'] == reviewed['process'])
            try:
                call(hpdi + '/estimate-requests/' + stale['id'] + '/decision', method='POST', data={'approved': True})
                raise AssertionError('Stale estimate must conflict')
            except urllib.error.HTTPError as failure:
                check('released task estimate is stale', failure.code == 409)
            decision = {'category': 'HARDWARE', 'priority': 'HIGH', 'assignee': subject, 'reason': 'Continue handed-over work'}
            if intake:
                state = call(path + '/intake')
                check('handover advances intake generation', state['reviewRound'] == 2 and state['status'] == 'REVIEW_RELEASED')
                decision.update(expectedContextRevision=state['contextRevision'], expectedIntakeVersion=state['version'])
            continued = call(path + '/review', method='POST', data=decision)
            check('handover preserves deadline and estimate', continued['sla']['resolveBy'] == reviewed['sla']['resolveBy'] and continued['estimate']['minutes'] == 120)
            check('handover creates one fresh handle', continued['taskId'] != reviewed['taskId'] and continued['state'] == 'ASSIGNED')
            reviewed = continued
            task = hpdi + '/tasks/' + reviewed['taskId']
        started = call(task + '/start', 'agent1', 'POST')
        check('START freezes response result', not started['sla']['startBreached'])
        paused = call(task + '/pause', 'agent1', 'POST', {'reason': 'WAITING_REQUESTER'})
        check('requester wait pauses SLA', paused['sla']['clockPaused'])
        docker('restart', backend_name)
        backend = 'http://' + docker('port', backend_name, '8080/tcp')
        wait(backend + '/actuator/health')
        persisted = call(path, 'requester1')
        check('pause and original start deadline survive backend restart', persisted['sla']['clockPaused'] and persisted['sla']['startBy'] == reviewed['sla']['startBy'])
        call(task + '/resume', 'agent1', 'POST')
        call(task + '/complete', 'agent1', 'POST', {'expectedTaskKey': 'handle', 'outcome': 'resolved'})
        completed = call(path, 'requester1')
        check('handle resolved stops SLA before requester confirmation', completed['state'] == 'AWAITING_CONFIRMATION' and not completed['sla']['resolveBreached'] and not completed['sla']['clockPaused'])
        call(hpdi + '/tasks/' + completed['taskId'] + '/complete', 'requester1', 'POST', {'expectedTaskKey': 'confirm', 'outcome': 'rework'})
        check('rework awaiting approval has no fabricated SLA', call(path, 'requester1')['sla'] is None)
        reviewed = approve('LOW', None)
        check('new round starts a fresh LOW policy with no assignment', reviewed['classification']['basePriority'] == 'LOW' and reviewed['sla'] is not None and reviewed['assignee'] is None)
        docker('exec', database, 'psql', '-U', 'workflow', '-d', 'core', '-v', 'ON_ERROR_STOP=1', '-c',
            "update servicedesk_sla set approved_at=approved_at-interval '10 days', start_by=start_by-interval '10 days', resolve_by=resolve_by-interval '10 days' where ticket_id='" + str(uuid.UUID(ticket['id'])) + "' and resolved_at is null")
        for attempt in range(30):
            escalated = call(path, 'requester1')
            if escalated['classification']['escalated']:
                break
            time.sleep(1)
        check('scheduled worker escalates unassigned LOW once without changing base priority', escalated['classification']['priority'] == 'MEDIUM' and escalated['classification']['basePriority'] == 'LOW')
        count = docker('exec', database, 'psql', '-U', 'workflow', '-d', 'core', '-Atc',
            "select count(*) from audit_event where action='servicedesk.sla.escalated' and resource='" + str(uuid.UUID(ticket['id'])) + "'")
        check('system escalation records exactly one transactional audit', count == '1')
        if alerts:
            breached_id = ticket['id']
            ticket = call(base, 'requester1', 'POST', {'clientRequestId': str(uuid.uuid4()), 'title': 'Real R7 critical', 'description': 'Synthetic critical request'})
            path = base + '/' + ticket['id']
            critical = approve('CRITICAL', subject)
            for attempt in range(60):
                inbox = call(hpdi + '/alerts')
                critical_events = [item for item in inbox['items'] if item['businessKey'] == ticket['id'] and item['type'] == 'CRITICAL_NEW']
                breached = [item for item in inbox['items'] if item['businessKey'] == breached_id and item['type'] == 'SLA_BREACHED']
                if critical_events and breached:
                    break
                time.sleep(1)
            check('real signed worker delivers approved critical and SLA breach without opening queue', bool(critical_events and breached))
            agent_inbox = call(hpdi + '/alerts', 'agent1')
            check('assigned agent receives persistent critical alert', any(item['businessKey'] == ticket['id'] and item['type'] == 'CRITICAL_NEW' for item in agent_inbox['items']))
            check('critical notification does not auto START', call(path)['state'] == 'ASSIGNED')
            alert_id = critical_events[0]['id']
            call(hpdi + '/alerts/' + alert_id + '/read', method='POST')
            call(hpdi + '/alerts/' + alert_id + '/read', method='POST')
            check('read retry leaves agent unread state independent', any(item['id'] == alert_id and item['readAt'] is None for item in call(hpdi + '/alerts', 'agent1')['items']))
            docker('restart', backend_name)
            backend = 'http://' + docker('port', backend_name, '8080/tcp')
            wait(backend + '/actuator/health')
            check('alert and read timestamp survive real backend restart', any(item['id'] == alert_id and item['readAt'] is not None for item in call(hpdi + '/alerts')['items']))
            try:
                call(hpdi + '/alerts', 'requester1')
                raise AssertionError('Requester must not read operational alerts')
            except urllib.error.HTTPError as failure:
                check('requester cannot read operational alerts', failure.code == 403)
        report['status'] = 'PASS'
except Exception as error:
    report.update(status='ERROR', error=type(error).__name__ + ': ' + str(error))
    raise
finally:
    (root / ('.local/r2-r6-intake-runtime.json' if handover and intake else '.local/r2-r6-runtime.json' if handover else '.local/r7-r8-intake-runtime.json' if alerts and intake else '.local/r7-r8-real-runtime.json' if alerts else '.local/r5-intake-runtime.json' if intake else '.local/r5-real-runtime.json')).write_text(json.dumps(report, indent=2), encoding='utf-8')
    for name in reversed(containers):
        subprocess.run(['docker', 'rm', '-f', '-v', name], capture_output=True)
    subprocess.run(['docker', 'network', 'rm', network], capture_output=True)
