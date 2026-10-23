from contextlib import contextmanager
from dataclasses import replace
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from threading import Event, Thread
from time import monotonic

import pytest

from hdpi.generation.errors import (
    GenerationConfigurationError,
    GenerationContractError,
    GenerationTimeout,
    GenerationUnavailable,
)
from hdpi.generation.model_manifest import InferenceLimits
from hdpi.generation.transport import InferenceResponse, LoopbackSelectionTransport, response_json


@pytest.mark.parametrize(
    'endpoint',
    [
        'https://example.com:443',
        'http://10.0.0.1:8000',
        'http://169.254.169.254:80',
        'http://localhost:8000',
        'http://127.0.0.1',
        'http://127.0.0.1:8000/',
        'http://user:secret@127.0.0.1:8000',
        'http://127.0.0.1:8000?endpoint=example.com',
        'http://127.0.0.1:8000#fragment',
        'file:///etc/passwd',
        'http://2130706433:8000',
        'http://127.0.0.1:8000\r\nHost: example.com',
        'http://[::1%25zone]:8000',
    ],
)
def test_endpoint_allowlist_rejects_ssrf_origins_before_socket(endpoint, monkeypatch):
    def forbidden(*args, **kwargs):
        pytest.fail('Endpoint validation opened a socket')

    monkeypatch.setattr('socket.create_connection', forbidden)

    with pytest.raises(GenerationConfigurationError):
        LoopbackSelectionTransport(endpoint=endpoint)


@contextmanager
def loopback_server(*, status=200, content_type='application/json', size=None, hold=False):
    release = Event()
    calls = []

    class Handler(BaseHTTPRequestHandler):
        def do_POST(self):
            self.rfile.read(int(self.headers.get('Content-Length', '0')))
            calls.append((self.path, dict(self.headers)))
            body = json.dumps({'decision': 'REFUSE', 'citation_ids': []}).encode()
            self.send_response(status)
            self.send_header('Content-Type', content_type)
            self.send_header('Content-Length', str(len(body) if size is None else size))

            if status == 302:
                self.send_header('Location', 'https://example.invalid/DUMMY_CANARY')

            self.end_headers()

            if hold:
                self.wfile.write(body[:1])
                self.wfile.flush()
                release.wait(5)
            else:
                self.wfile.write(body)

        def log_message(self, *args):
            pass

    server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
    server.daemon_threads = True
    thread = Thread(target=server.serve_forever, daemon=True)
    thread.start()

    try:
        endpoint = f'http://127.0.0.1:{server.server_port}'

        yield LoopbackSelectionTransport(endpoint=endpoint), calls
    finally:
        release.set()
        server.shutdown()
        server.server_close()
        thread.join(timeout=2)


def test_transport_ignores_environment_proxies_and_sends_no_credentials(monkeypatch):
    monkeypatch.setenv('HTTP_PROXY', 'http://example.invalid:9999')
    monkeypatch.setenv('HTTPS_PROXY', 'http://example.invalid:9999')

    with loopback_server() as (transport, calls):
        response = transport.select(b'{}', limits=InferenceLimits())

    assert response_json(response, maximum=8192)['decision'] == 'REFUSE'
    assert len(calls) == 1
    assert calls[0][0] == '/v1/select'
    assert 'Authorization' not in calls[0][1]
    assert 'Proxy-Authorization' not in calls[0][1]
    assert 'Cookie' not in calls[0][1]


@pytest.mark.parametrize(
    'status,expected', [(302, GenerationContractError), (503, GenerationUnavailable)]
)
def test_transport_never_redirects_or_retries(status, expected):
    with loopback_server(status=status) as (transport, calls):
        response = transport.select(b'{}', limits=InferenceLimits())

    with pytest.raises(expected):
        response_json(response, maximum=8192)

    assert len(calls) == 1
    assert response.body == b''


@pytest.mark.parametrize(
    'options',
    [
        {'content_type': 'text/event-stream'},
        {'content_type': 'application/json; charset=utf-8'},
        {'size': 1000000},
        {'size': 500},
    ],
)
def test_transport_rejects_streaming_oversize_and_partial_responses(options):
    with loopback_server(**options) as (transport, _):
        with pytest.raises(GenerationContractError):
            transport.select(b'{}', limits=InferenceLimits())


def test_overall_deadline_closes_detached_response_socket():
    limits = replace(InferenceLimits(), connect_seconds=1, read_seconds=1, total_seconds=1)

    with loopback_server(hold=True) as (transport, calls):
        started = monotonic()

        with pytest.raises(GenerationTimeout) as caught:
            transport.select(b'{}', limits=limits)

        elapsed = monotonic() - started

    assert elapsed < 3
    assert len(calls) == 1
    assert 'DUMMY_CANARY' not in str(caught.value)


@pytest.mark.parametrize(
    'response',
    [
        InferenceResponse(200, 'text/html', b'DUMMY_CANARY'),
        InferenceResponse(200, 'application/json', b'\xff'),
        InferenceResponse(200, 'application/json', b'{} trailing DUMMY_CANARY'),
        InferenceResponse(True, 'application/json', b'{}'),
    ],
)
def test_untrusted_transport_response_types_are_not_coerced(response):
    with pytest.raises(GenerationContractError) as caught:
        response_json(response, maximum=8192)

    assert 'DUMMY_CANARY' not in str(caught.value)
    assert 'DUMMY_CANARY' not in repr(response)
