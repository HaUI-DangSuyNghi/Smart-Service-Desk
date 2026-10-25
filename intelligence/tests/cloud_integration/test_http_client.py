from email.message import Message
import socket

import pytest

from cloud_providers.contracts import CloudFailure
from cloud_providers.http_client import BoundedHTTPSClient
from cloud_providers.runtime import CloudAssistanceRuntime

from .conftest import KEYS


class Response:
    def __init__(self, status=200, body=b"{}", headers=None):
        self.status = status
        self.body = body
        self.headers = Message()

        for name, value in headers or [("Content-Type", "application/json")]:
            self.headers[name] = value

    def getheader(self, name):
        return self.headers.get(name)

    def read(self, maximum):
        return self.body[:maximum]


class Connection:
    def __init__(self, response):
        self.response = response
        self.requests = []
        self.closed = False

    def request(self, *args):
        self.requests.append(args)

    def getresponse(self):
        if isinstance(self.response, Exception):
            raise self.response

        return self.response

    def close(self):
        self.closed = True


@pytest.mark.parametrize(
    "response, code",
    [
        (Response(429, headers=[("Retry-After", "999999")]), "PROVIDER_RATE_LIMITED"),
        (Response(401), "PROVIDER_CONFIGURATION_ERROR"),
        (Response(403), "PROVIDER_CONFIGURATION_ERROR"),
        (Response(503), "GENERATION_UNAVAILABLE"),
        (Response(302), "EGRESS_NOT_AUTHORIZED"),
        (Response(400), "GENERATION_CONTRACT_FAILED"),
        (Response(body=b"x" * 131073), "GENERATION_CONTRACT_FAILED"),
        (
            Response(headers=[("Content-Type", "text/html")]),
            "GENERATION_CONTRACT_FAILED",
        ),
        (
            Response(
                headers=[
                    ("Content-Type", "application/json"),
                    ("Content-Encoding", "gzip"),
                ]
            ),
            "GENERATION_CONTRACT_FAILED",
        ),
        (
            Response(
                headers=[
                    ("Content-Type", "application/json"),
                    ("Content-Length", "999"),
                ]
            ),
            "GENERATION_CONTRACT_FAILED",
        ),
        (socket.timeout("RAW SECRET MUST NOT ESCAPE"), "GENERATION_TIMEOUT"),
    ],
)
def test_bounded_http_status_and_body_fail_without_retry(
    monkeypatch, registry, response, code
):
    connection = Connection(response)
    destinations = []

    def connect(host, **kwargs):
        destinations.append(host)
        return connection

    monkeypatch.setattr("cloud_providers.http_client.PinnedHTTPS", connect)
    monkeypatch.setenv("HTTPS_PROXY", "http://untrusted.example:8080")

    with pytest.raises(CloudFailure) as error:
        BoundedHTTPSClient().post(
            registry.routes["gemini-primary"], b"{}", "synthetic-key"
        )

    assert error.value.code == code
    assert "SECRET" not in str(error.value)
    assert destinations == ["generativelanguage.googleapis.com"]
    assert len(connection.requests) == 1
    assert connection.requests[0][1] == "/v1beta/models/fixture-gemini:generateContent"
    assert "synthetic-key" not in connection.requests[0][1]
    assert connection.closed


def test_fixture_receipt_cannot_activate_real_transport(registry):
    from hdpi.generation.errors import GenerationConfigurationError

    with pytest.raises(GenerationConfigurationError):
        CloudAssistanceRuntime(registry, environment=KEYS)


@pytest.mark.parametrize(
    "proxy",
    [
        "https://cloud-egress:8080",
        "http://evil.example:8080",
        "http://user:pass@localhost:8080",
        "http://localhost:8080/path",
    ],
)
def test_proxy_is_explicit_deployment_endpoint_only(registry, proxy):
    with pytest.raises(CloudFailure) as error:
        BoundedHTTPSClient(proxy=proxy).post(
            registry.routes["gemini-primary"], b"{}", "synthetic-key"
        )

    assert error.value.code == "EGRESS_NOT_AUTHORIZED"
