"""Real T3 -> T2 -> T1 and private Python triage proof using isolated synthetic data."""
import argparse
import base64
from hashlib import sha256
import json
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import urlencode, urlsplit
from urllib.request import Request, urlopen
from uuid import uuid4


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--gateway', default='http://127.0.0.1:19080')
    parser.add_argument('--identity', default='http://127.0.0.1:18081')
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    for url in (args.gateway, args.identity):
        parsed = urlsplit(url)
        if parsed.scheme != 'http' or parsed.hostname not in ('localhost', '127.0.0.1') or parsed.username or parsed.password:
            parser.error('Acceptance requires loopback HTTP services')
    settings = dict(line.split('=', 1) for line in Path('.env').read_text().splitlines()
                    if '=' in line and not line.startswith('#'))
    tokens = {}
    for user in ('user1', 'user2', 'user3', 'user4'):
        raw = urlencode({'grant_type': 'password', 'client_id': 'core-probe', 'username': user,
                         'password': settings['DEMO_PASSWORD']}).encode()
        with urlopen(args.identity + '/realms/dx-lab/protocol/openid-connect/token', raw, timeout=30) as response:
            tokens[user] = json.load(response)['access_token']
    captures = {'room': [], 'hpdi': []}
    passed = []

    def call(method, path, user, expected, body=None, *, raw=None, content_type=None, contract=None):
        request_id = str(uuid4())
        headers = {'X-Request-ID': request_id}
        if user:
            headers['Authorization'] = 'Bearer ' + tokens[user]
        if body is not None:
            raw = json.dumps(body).encode()
            content_type = 'application/json'
        if content_type:
            headers['Content-Type'] = content_type
        try:
            response = urlopen(Request(args.gateway + path, raw, headers, method=method), timeout=30)
        except HTTPError as error:
            response = error
        with response:
            if response.status != expected:
                raise RuntimeError(f'Acceptance expected {expected}, received {response.status}')
            assert response.headers['X-Request-ID'] == request_id
            data = response.read()
            if contract and data:
                captures[contract].append({'method': method, 'path': path, 'status': response.status, 'bodyJson': data.decode()})
            return json.loads(data) if data else None

    content = b'Independent synthetic room booking guide. One room request may be approved by a coordinator.'
    boundary = 'proof-' + uuid4().hex
    multipart = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="room-guide.md"\r\n'
                 'Content-Type: text/markdown\r\n\r\n').encode() + content + f'\r\n--{boundary}--\r\n'.encode()
    file = call('POST', '/api/core/v1/workspaces/lab-a/files', 'user2', 201, raw=multipart,
                content_type='multipart/form-data; boundary=' + boundary)
    metadata = call('POST', '/api/core/v1/workspaces/lab-a/metadata', 'user2', 201,
                    {'title': 'Synthetic room guide', 'source': 'src:room-proof:' + uuid4().hex, 'version': 'proof-v1',
                     'usageRights': 'CC0', 'fileId': file['id']})
    knowledge = call('GET', '/api/hpdi/v1/workspaces/lab-a/knowledge/' + metadata['id'], 'user1', 200, contract='hpdi')
    assert base64.b64decode(knowledge['content']) == content
    assert knowledge['checksum'] == sha256(content).hexdigest()
    passed.append('authorized-knowledge-exact-provenance')

    actor = call('GET', '/api/hpdi/v1/workspaces/lab-a/actor', 'user1', 200, contract='hpdi')
    assert actor['workspace'] == 'lab-a' and 'requester' in actor['roles']
    base = '/api/apps/roomrequest/v1/workspaces/lab-a/requests'
    room = call('POST', base, 'user1', 201, {'room': 'A-101', 'knowledgeId': metadata['id']}, contract='room')
    assert room['creator'] == actor['subject'] and room['process']['processKey'] == 'room-request'
    passed.append('trusted-human-and-app-persistence')

    suggestion = call('POST', base + '/' + room['id'] + '/suggestion', 'user1', 200, contract='room')
    assert suggestion['status'] == 'SUCCESS' and suggestion['profileVersion'] == 'room-request@0.2.0'
    assert suggestion['result'] == {'category': 'BOOKING', 'priority': 'NORMAL', 'suggestedGroup': 'FACILITIES'}
    assert suggestion['evidence'] and not suggestion['needsReview']
    passed.append('real-java-private-python-triage')

    timeline = call('GET', base + '/' + room['id'] + '/progress', 'user1', 200, contract='room')
    task = timeline['currentTasks'][0]
    inbox = call('GET', '/api/hpdi/v1/workspaces/lab-a/inbox', 'user3', 200, contract='hpdi')
    assert any(item['id'] == task['id'] and item['businessKey'] == room['id'] for item in inbox['items'])
    path = '/api/hpdi/v1/workspaces/lab-a/tasks/' + task['id']
    call('POST', path + '/claim', 'user3', 200, contract='hpdi')
    call('POST', path + '/complete', 'user3', 204, {'expectedTaskKey': 'approve', 'outcome': 'approved'})
    ended = call('GET', base + '/' + room['id'] + '/progress', 'user1', 200, contract='room')
    assert ended['ended'] and not ended['currentTasks']
    passed.append('shared-inbox-claim-complete-timeline')

    call('POST', base + '/' + room['id'] + '/suggestion', None, 401, contract='room')
    call('POST', base + '/' + room['id'] + '/suggestion', 'user4', 403, contract='room')
    passed.append('authentication-and-cross-workspace-denial')
    args.output.parent.mkdir(parents=True, exist_ok=True)
    for name, records in captures.items():
        args.output.with_name(args.output.stem + '-' + name + '.json').write_text(json.dumps(records), 'utf-8')
    args.output.write_text(json.dumps({'evidenceClass': 'REAL_THREE_TIER_AND_PYTHON_HTTP_ACCEPTANCE',
                                      'status': 'PASS', 'scenarios': passed}, indent=2) + '\n', 'utf-8')
    print('PASS real three-tier acceptance: ' + ', '.join(passed))


if __name__ == '__main__':
    main()
