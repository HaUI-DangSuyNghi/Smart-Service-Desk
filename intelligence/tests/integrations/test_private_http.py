import json
import threading
from urllib.error import HTTPError
from urllib.request import Request, urlopen
import pytest
from hdpi_intelligence.http_service import make_server


@pytest.fixture
def private_runtime(service):
    server = make_server(('127.0.0.1', 0), service=service, service_token='s' * 32)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    yield f'http://127.0.0.1:{server.server_port}/v1/triage'
    server.shutdown()
    thread.join(timeout=5)
    server.server_close()


def envelope():
    return {'profileVersion': '0.2.0', 'trustedContext': {'workspaceId': 'lab-a', 'subject': 'verified-subject'},
            'request': {'contractVersion': '0.1.0', 'requestId': 'private-adapter', 'profile': 'service-desk',
                        'context': {'workspaceId': 'lab-a'},
                        'input': {'title': 'Wi-Fi', 'description': 'Một máy bị ảnh hưởng; có thể chờ.'}}}


def send(url, payload, token='s' * 32):
    return urlopen(Request(url, json.dumps(payload).encode(),
                          headers={'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json'}), timeout=5)


def test_authenticated_adapter_uses_real_engine(private_runtime):
    with send(private_runtime, envelope()) as response:
        result = json.load(response)
    assert result['status'] == 'SUCCESS'
    assert result['runtime']['profileVersion'] == 'service-desk@0.2.0'
    assert result['result']['category'] == 'NETWORK'


def test_browser_or_foreign_context_cannot_grant_authority(private_runtime):
    with pytest.raises(HTTPError) as denied:
        send(private_runtime, {}, token='untrusted')
    assert denied.value.code == 401
    payload = envelope()
    payload['request']['context']['workspaceId'] = 'lab-b'
    with pytest.raises(HTTPError) as foreign:
        send(private_runtime, payload)
    assert foreign.value.code == 403
    assert b'lab-b' not in foreign.value.read()
