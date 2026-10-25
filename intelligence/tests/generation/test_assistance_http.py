import http.client
import json
from threading import Thread
from unittest.mock import Mock

import pytest

from hdpi.assistance_http import ENDPOINT, make_server
from hdpi.generation.errors import GenerationConfigurationError


TOKEN = "synthetic-assistance-token-with-32-characters"


@pytest.fixture
def endpoint():
    runtime = Mock()
    runtime.assist.return_value = {"status": "INSUFFICIENT_EVIDENCE"}
    server = make_server(("127.0.0.1", 0), service_token=TOKEN, runtime=runtime)
    thread = Thread(target=server.serve_forever, daemon=True)
    thread.start()

    yield server.server_address, runtime

    server.shutdown()
    server.server_close()
    thread.join(timeout=2)


def send(endpoint, body=b"{}", *, token=TOKEN, method="POST", path=ENDPOINT):
    address, _ = endpoint
    connection = http.client.HTTPConnection(*address, timeout=2)

    try:
        connection.request(
            method,
            path,
            body,
            {
                "Authorization": "Bearer " + token,
                "Content-Type": "application/json",
            },
        )
        response = connection.getresponse()
        return response.status, dict(response.getheaders()), response.read()
    finally:
        connection.close()


@pytest.mark.parametrize(
    "body",
    [
        b'{"key":1,"key":2}',
        b"{} trailing",
        b"[] trailing",
        b'{"x":NaN}',
        b"\xef\xbb\xbf{}",
        b"\xff",
        b"[" * 1200,
    ],
)
def test_malformed_wire_cannot_reach_runtime(endpoint, body):
    assert send(endpoint, body)[0] == 400
    endpoint[1].assist.assert_not_called()


def test_authentication_route_and_method_precede_runtime(endpoint):
    assert send(endpoint, token="wrong")[0] == 401
    assert send(endpoint, path="/internal/intelligence/v1/triage")[0] == 404
    assert send(endpoint, method="GET")[0] == 405
    endpoint[1].assist.assert_not_called()


def test_safe_response_headers_and_fixed_contract_failure(endpoint):
    status, headers, body = send(endpoint)
    assert status == 200
    assert headers["Cache-Control"] == "no-store"
    assert json.loads(body)["status"] == "INSUFFICIENT_EVIDENCE"

    endpoint[1].assist.side_effect = RuntimeError("private prompt or credential")
    status, _, body = send(endpoint)
    assert status == 502
    assert json.loads(body) == {"code": "ASSISTANCE_CONTRACT_FAILED"}
    assert b"private" not in body


def test_identity_failure_is_never_operational_degraded(endpoint):
    endpoint[1].assist.side_effect = GenerationConfigurationError("secret")
    assert send(endpoint)[0] == 502
