"""Bounded loopback-only inference HTTP. No proxies, redirects, retries or streaming.

This is a deliberately small sidecar protocol, not a hosted chat API client.
Sidecar egress denial and disabled request logging need deployment enforcement;
an HTTP identity assertion alone cannot prove either property.
"""

from dataclasses import dataclass, field
import http.client
import ipaddress
import socket
import ssl
from threading import Event, Timer
from time import monotonic
from typing import Protocol
from urllib.parse import urlsplit

from .errors import (
    GenerationConfigurationError,
    GenerationContractError,
    GenerationTimeout,
    GenerationUnavailable,
)
from .selection_codec import strict_json
from .model_manifest import InferenceLimits


@dataclass(frozen=True)
class InferenceResponse:
    status: int
    content_type: str
    body: bytes = field(repr=False)


class SelectionTransport(Protocol):
    def identity(self, *, limits: InferenceLimits) -> InferenceResponse: ...

    def select(self, body: bytes, *, limits: InferenceLimits) -> InferenceResponse: ...


class TokenCounter(Protocol):
    """Trusted offline adapter must count actual pinned chat-template + schema tokens.

    The manifest fingerprint binds tokenizer/config/template bytes and backend.
    Deployment supplies this adapter; a character estimate is not acceptable.
    """

    manifest_digest: str

    def count(self, body: bytes) -> int: ...


def response_json(response, *, maximum):
    if type(response) is not InferenceResponse:
        raise GenerationContractError('Invalid inference response')

    if type(response.status) is not int:
        raise GenerationContractError('Invalid inference status')

    if response.status == 429 or 500 <= response.status <= 599:
        raise GenerationUnavailable('Inference service unavailable')

    if response.status != 200 or response.content_type != 'application/json':
        raise GenerationContractError('Unexpected inference status or media type')

    return strict_json(response.body, maximum=maximum)


@dataclass(frozen=True, kw_only=True)
class LoopbackSelectionTransport:
    """Endpoint is trusted server configuration. Literal loopback IPs prevent DNS SSRF.

    HTTP is allowed only on this host's loopback interface. Container private-network
    addressing/mTLS requires a separate reviewed transport, rather than widening
    this allowlist. A sidecar must bind loopback and must not log request bodies.
    """

    endpoint: str = field(repr=False)

    def __post_init__(self):
        try:
            parsed = urlsplit(self.endpoint)
            address = ipaddress.ip_address(parsed.hostname or '')
            valid = (
                parsed.scheme in ('http', 'https')
                and address.is_loopback
                and parsed.port is not None
                and 1 <= parsed.port <= 65535
                and parsed.username is None
                and parsed.password is None
                and parsed.path == ''
                and not parsed.query
                and not parsed.fragment
                and not any(ord(c) <= 32 or ord(c) >= 127 for c in self.endpoint)
                and '%' not in self.endpoint
                and '\\' not in self.endpoint
                and '?' not in self.endpoint
                and '#' not in self.endpoint
            )
        except (ValueError, TypeError, AttributeError):
            valid = False
        if not valid:
            raise GenerationConfigurationError(
                'Inference endpoint must be a fixed literal loopback origin'
            )

    def identity(self, *, limits):
        return self._exchange('GET', '/v1/identity', None, limits)

    def select(self, body, *, limits):
        if type(body) is not bytes or len(body) > limits.max_request_bytes:
            raise GenerationContractError('Inference request exceeds byte budget')

        return self._exchange('POST', '/v1/select', body, limits)

    def _exchange(self, method, path, body, limits):
        parsed = urlsplit(self.endpoint)
        cls = (
            http.client.HTTPSConnection if parsed.scheme == 'https' else http.client.HTTPConnection
        )
        kwargs = {'context': ssl.create_default_context()} if parsed.scheme == 'https' else {}
        connection = cls(parsed.hostname, parsed.port, timeout=limits.connect_seconds, **kwargs)
        active_socket = None
        expired = Event()
        deadline = monotonic() + limits.total_seconds

        def expire():
            expired.set()
            active = active_socket

            if active is not None:
                try:
                    active.shutdown(socket.SHUT_RDWR)
                except OSError:
                    pass

                active.close()

        timer = Timer(limits.total_seconds, expire)
        timer.daemon = True
        timer.start()

        try:
            connection.connect()
            active_socket = connection.sock

            if expired.is_set() or monotonic() >= deadline:
                raise GenerationTimeout('Inference deadline exceeded')
            active_socket.settimeout(min(limits.read_seconds, deadline - monotonic()))

            connection.request(
                method,
                path,
                body=body,
                headers={
                    'Accept': 'application/json',
                    'Content-Type': 'application/json',
                    'Connection': 'close',
                },
            )
            response = connection.getresponse()

            # Never read a redirect/error body or attempt a second endpoint.
            if response.status != 200:
                return InferenceResponse(response.status, '', b'')

            if (
                response.getheader('Content-Type') != 'application/json'
                or response.getheader('Content-Encoding') is not None
                or response.getheader('Transfer-Encoding') is not None
            ):
                raise GenerationContractError('Unsupported inference encoding or media type')

            length = response.getheader('Content-Length')
            if length is None or not length.isascii() or not length.isdecimal():
                raise GenerationContractError('Bounded inference content length required')

            length = int(length)
            if not 0 < length <= limits.max_response_bytes:
                raise GenerationContractError('Inference response exceeds byte budget')

            chunks = []
            received = 0

            while received < length:
                remaining = deadline - monotonic()

                if expired.is_set() or remaining <= 0:
                    raise GenerationTimeout('Inference deadline exceeded')
                active_socket.settimeout(min(limits.read_seconds, remaining))
                chunk = response.read1(min(1024, length - received))

                if not chunk:
                    raise GenerationContractError('Incomplete inference response')

                chunks.append(chunk)
                received += len(chunk)

            if expired.is_set() or monotonic() >= deadline:
                raise GenerationTimeout('Inference deadline exceeded')

            return InferenceResponse(200, 'application/json', b''.join(chunks))
        except (TimeoutError, socket.timeout):
            raise GenerationTimeout('Inference deadline exceeded') from None
        except (OSError, http.client.HTTPException):
            if expired.is_set() or monotonic() >= deadline:
                raise GenerationTimeout('Inference deadline exceeded') from None

            raise GenerationUnavailable('Inference connection failed') from None
        except (GenerationContractError, GenerationTimeout):
            raise
        except Exception:
            raise GenerationContractError('Inference transport failed') from None
        finally:
            timer.cancel()
            connection.close()

            if active_socket is not None:
                active_socket.close()
