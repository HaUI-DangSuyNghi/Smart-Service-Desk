from contextlib import contextmanager
from http.client import HTTPConnection
import json
import socket
from threading import Thread
from time import monotonic, sleep

import pytest

from hdpi.domain.errors import (
    AuthorizationRejected, EngineConfigurationError, EngineUnavailable,
    ProfileInvalid, ProfileNotFound, UnsupportedProfileVersion,
)
from hdpi.http_service import ENDPOINT, MAX_REQUEST, make_server
from hdpi.services.triage import TriageService

TOKEN = 'synthetic-test-service-credential-only'


@pytest.fixture
def payload():
    return {'contractVersion': '0.1.0', 'requestId': 'synthetic-http-test',
            'profile': 'service-desk', 'context': {'workspaceId': 'lab-a'},
            'input': {'title': 'Network support', 'description': 'Synthetic network support request'}}


@contextmanager
def running(service=None, timeout=3):
    server = make_server(('127.0.0.1', 0), service_token=TOKEN, service=service,
                         request_timeout=timeout)
    thread = Thread(target=server.serve_forever, kwargs={'poll_interval': 0.01}, daemon=True)
    thread.start()
    try:
        yield server.server_address
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=2)
        assert not thread.is_alive()


def call(address, payload=None, *, raw=None, token=TOKEN, method='POST', path=ENDPOINT, headers=None):
    body = raw if raw is not None else json.dumps(payload).encode('utf-8')
    request_headers = {'Content-Type': 'application/json'}
    if token is not None:
        request_headers['Authorization'] = 'Bearer ' + token
    request_headers.update(headers or {})
    connection = HTTPConnection(*address, timeout=2)
    try:
        connection.request(method, path, body, request_headers)
        response = connection.getresponse()
        raw_response = response.read()
        return response.status, json.loads(raw_response) if raw_response else None
    finally:
        connection.close()


def test_real_rules_success_and_authenticated_workspace_assertion(payload, monkeypatch):
    service = TriageService()
    calls = []
    original = service.engine.triage

    def observed(request, profile):
        calls.append((request.workspace_id, profile.identity))
        return original(request, profile)

    monkeypatch.setattr(service.engine, 'triage', observed)
    with running(service) as address:
        for workspace in ('lab-a', 'lab-b'):
            payload['context']['workspaceId'] = workspace
            status, response = call(address, payload)
            assert status == 200
            service.contract.response(response, payload)
            assert response['status'] == 'SUCCESS'
            assert response['workspaceId'] == workspace
            assert response['runtime']['profileVersion'] == 'service-desk@0.2.0'
    assert calls == [('lab-a', 'service-desk@0.2.0'), ('lab-b', 'service-desk@0.2.0')]


@pytest.mark.parametrize('token', [None, 'wrong-service-credential', TOKEN + 'x'])
def test_authentication_before_body_or_engine(token):
    with running() as address:
        with socket.create_connection(address, timeout=1) as connection:
            credential = '' if token is None else f'Authorization: Bearer {token}\r\n'
            connection.sendall((f'POST {ENDPOINT} HTTP/1.1\r\nHost: localhost\r\n{credential}'
                                'Content-Length: 30000\r\n\r\n').encode('ascii'))
            assert b' 401 ' in connection.recv(4096)


@pytest.mark.parametrize(('method', 'path', 'expected'), [
    ('GET', ENDPOINT, 405), ('PUT', ENDPOINT, 405), ('DELETE', ENDPOINT, 405),
    ('PATCH', ENDPOINT, 405), ('OPTIONS', ENDPOINT, 405), ('HEAD', ENDPOINT, 405),
    ('POST', '/v1/triage', 404), ('POST', ENDPOINT + '?profile=other', 404),
])
def test_method_and_exact_path(payload, method, path, expected):
    with running() as address:
        assert call(address, payload, method=method, path=path)[0] == expected


@pytest.mark.parametrize('raw', [b'', b'{', b'null', b'[]', b'{}{}', b'\xff',
                                b'{"profile":"service-desk","profile":"other"}', b'{"x":NaN}',
                                b'[' * 2000 + b']' * 2000])
def test_invalid_json(raw):
    with running() as address:
        assert call(address, raw=raw)[0] == 400


@pytest.mark.parametrize(('key', 'value'), [
    ('contractVersion', '9.0.0'), ('profile', 'unknown'), ('profile', 'service-desk@0.1.0'),
    ('profile', 'service-desk@0.2.0'), ('context', {'workspaceId': ''}),
    ('context', {'workspaceId': 'lab-a', 'subject': 'browser'}), ('profileVersion', '0.1.0'),
    ('trustedContext', {'workspaceId': 'lab-b'}),
])
def test_schema_and_profile_rejections(payload, key, value):
    payload[key] = value
    with running() as address:
        assert call(address, payload)[0] == 400


@pytest.mark.parametrize('headers', [
    {'Content-Type': 'text/plain'}, {'Content-Type': 'application/json; charset=latin1'},
    {'Content-Length': '-1'}, {'Content-Length': 'invalid'}, {'Transfer-Encoding': 'chunked'},
])
def test_invalid_transport_headers(payload, headers):
    with running() as address:
        assert call(address, payload, headers=headers)[0] == 400


@pytest.mark.parametrize('header', [
    '', 'Content-Length: 2\r\nContent-Length: 2\r\n',
    'Content-Length: 2\r\nAuthorization: Bearer duplicate\r\n',
    'Content-Length: 2\r\nContent-Type: application/json\r\n',
])
def test_missing_length_and_duplicate_headers(header):
    with running() as address:
        with socket.create_connection(address, timeout=1) as connection:
            connection.sendall((f'POST {ENDPOINT} HTTP/1.1\r\nHost: localhost\r\n'
                                f'Authorization: Bearer {TOKEN}\r\nContent-Type: application/json\r\n'
                                f'{header}\r\n{{}}').encode('ascii'))
            response = connection.recv(4096)
            assert b' 400 ' in response or b' 401 ' in response


def test_oversized_body_rejected_without_reading():
    with running() as address:
        with socket.create_connection(address, timeout=1) as connection:
            connection.sendall((f'POST {ENDPOINT} HTTP/1.1\r\nHost: localhost\r\n'
                                f'Authorization: Bearer {TOKEN}\r\nContent-Type: application/json\r\n'
                                f'Content-Length: {MAX_REQUEST + 1}\r\n\r\n').encode('ascii'))
            assert b' 413 ' in connection.recv(4096)


def test_exact_request_cap_and_success_log_privacy(payload, capsys):
    payload['input']['description'] = 'x'
    payload['input']['description'] += 'x' * (MAX_REQUEST - len(json.dumps(payload).encode('utf-8')))
    raw = json.dumps(payload).encode('utf-8')
    assert len(raw) == MAX_REQUEST
    with running() as address:
        assert call(address, raw=raw)[0] == 200
    captured = capsys.readouterr()
    assert captured.out == captured.err == ''


def test_truncated_body_does_not_reach_engine():
    with running() as address:
        with socket.create_connection(address, timeout=1) as connection:
            connection.sendall((f'POST {ENDPOINT} HTTP/1.1\r\nHost: localhost\r\n'
                                f'Authorization: Bearer {TOKEN}\r\nContent-Type: application/json\r\n'
                                'Content-Length: 100\r\n\r\n{}').encode('ascii'))
            connection.shutdown(socket.SHUT_WR)
            assert b' 400 ' in connection.recv(4096)


@pytest.mark.parametrize('phase', ['headers', 'body'])
def test_absolute_deadline_bounds_slow_drip(phase):
    with running(timeout=0.2) as address:
        with socket.create_connection(address, timeout=1) as connection:
            prefix = f'POST {ENDPOINT} HTTP/1.1\r\nHost: localhost\r\n'
            if phase == 'body':
                prefix += (f'Authorization: Bearer {TOKEN}\r\nContent-Type: application/json\r\n'
                           'Content-Length: 100\r\n\r\n')
            connection.sendall(prefix.encode('ascii'))
            started = monotonic()
            for iteration in range(15):
                try:
                    connection.sendall(b' ')
                except OSError:
                    break
                sleep(0.03)
            try:
                assert connection.recv(4096) == b''
            except (ConnectionResetError, ConnectionAbortedError):
                pass
            assert monotonic() - started < 1


@pytest.mark.parametrize(('error', 'expected'), [
    (EngineUnavailable, 200), (EngineConfigurationError, 503), (ProfileInvalid, 503),
    (ProfileNotFound, 503), (UnsupportedProfileVersion, 503),
    (AuthorizationRejected, 403), (RuntimeError, 500),
])
def test_engine_failure_boundaries(payload, monkeypatch, capsys, error, expected):
    service = TriageService()

    def fail(*args):
        raise error(TOKEN + payload['input']['description'] + '/private/config.yaml')

    monkeypatch.setattr(service.engine, 'triage', fail)
    with running(service) as address:
        status, response = call(address, payload)
        assert status == expected
        if expected == 200:
            service.contract.response(response, payload)
            assert response['status'] == 'DEGRADED'
            assert response['runtime'] is None and response['needsReview'] is True
        else:
            assert set(response) == {'code'}
    captured = capsys.readouterr()
    output = captured.out + captured.err + json.dumps(response)
    assert TOKEN not in output
    assert payload['input']['description'] not in output
    assert '/private/config.yaml' not in output


@pytest.mark.parametrize('field', ['workspaceId', 'requestId', 'runtime'])
def test_output_correlation_and_profile_pin(payload, monkeypatch, field):
    service = TriageService()
    original = service.triage

    def corrupt(*args, **kwargs):
        run = original(*args, **kwargs)
        run.response[field] = {'engine': 'rules', 'rulesVersion': 'x',
                               'profileVersion': 'service-desk@0.1.0'} if field == 'runtime' else 'other'
        return run

    monkeypatch.setattr(service, 'triage', corrupt)
    with running(service) as address:
        assert call(address, payload) == (500, {'code': 'INTERNAL_ERROR'})


@pytest.mark.parametrize('token', [None, '', 'short', 'a' * 31, 'a' * 31 + '=',
                                 'a b', 'x\ny', '\u00e9', 'a' * 32 + '\r\nInjected: yes'])
def test_no_default_or_malformed_credential(token):
    with pytest.raises(ValueError):
        make_server(('127.0.0.1', 0), service_token=token)


def test_startup_rejects_non_executable_profile(monkeypatch):
    service = TriageService()

    def unavailable(*args):
        raise UnsupportedProfileVersion('Synthetic invalid deployment')

    monkeypatch.setattr(service.registry, 'get', unavailable)
    with pytest.raises(UnsupportedProfileVersion):
        make_server(('127.0.0.1', 0), service_token=TOKEN, service=service)


def test_operational_service_failure_is_not_fabricated_degraded(payload, monkeypatch):
    service = TriageService()

    def fail(*args, **kwargs):
        raise EngineUnavailable('Synthetic service boundary failure')

    monkeypatch.setattr(service, 'triage', fail)
    with running(service) as address:
        assert call(address, payload) == (503, {'code': 'INTELLIGENCE_UNAVAILABLE'})
