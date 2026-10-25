"""Literal-loopback native Ollama transport. No pull, proxy, redirect or retry API."""

from dataclasses import dataclass
import http.client
import ipaddress
import json
import socket
from threading import Event, Timer
from time import monotonic
from urllib.parse import urlsplit

from hdpi.classification.protocols import ClassificationError, require
from hdpi.generation.errors import GenerationTimeout, GenerationUnavailable


def strict_object(pairs):
    result = {}

    for key, value in pairs:
        require(key not in result)
        result[key] = value

    return result


def strict_json(raw, maximum=65536):
    def reject_constant(_value):
        raise ClassificationError("Non-finite JSON number rejected")

    try:
        require(type(raw) is bytes and 0 < len(raw) <= maximum)
        return json.loads(
            raw.decode("utf-8"),
            object_pairs_hook=strict_object,
            parse_constant=reject_constant,
        )
    except (ValueError, TypeError, RecursionError):
        raise ClassificationError("Invalid bounded JSON response") from None


@dataclass(frozen=True, kw_only=True)
class OllamaTransport:
    endpoint: str = "http://127.0.0.1:11434"
    timeout_seconds: int = 30
    max_response_bytes: int = 65536

    def __post_init__(self):
        try:
            parsed = urlsplit(self.endpoint)
            valid = (
                parsed.scheme == "http"
                and ipaddress.ip_address(parsed.hostname).is_loopback
                and parsed.port is not None
                and 1 <= parsed.port <= 65535
                and not parsed.path
                and not parsed.query
                and not parsed.fragment
                and parsed.username is None
                and parsed.password is None
                and all(32 < ord(char) < 127 for char in self.endpoint)
                and all(char not in self.endpoint for char in "%\\?#")
            )
        except (ValueError, TypeError):
            valid = False

        require(valid)
        require(type(self.timeout_seconds) is int and 1 <= self.timeout_seconds <= 120)
        require(
            type(self.max_response_bytes) is int
            and 1024 <= self.max_response_bytes <= 262144
        )

    def exchange(self, method, path, payload=None):
        allowed = {
            ("GET", "/api/version"),
            ("GET", "/api/tags"),
            ("GET", "/api/ps"),
            ("POST", "/api/show"),
            ("POST", "/v1/systemone"),
            ("POST", "/api/chat"),
            ("POST", "/api/generate"),
        }
        require((method, path) in allowed)

        if path == "/api/generate":
            require(type(payload) is dict and set(payload) == {"model", "keep_alive"})
            require(payload["keep_alive"] == 0)
        body = (
            None
            if payload is None
            else json.dumps(
                payload, ensure_ascii=True, allow_nan=False, separators=(",", ":")
            ).encode("utf-8")
        )
        require(body is None or len(body) <= 16384)
        parsed = urlsplit(self.endpoint)
        connection = http.client.HTTPConnection(
            parsed.hostname, parsed.port, timeout=self.timeout_seconds
        )
        expired = Event()
        active = None
        deadline = monotonic() + self.timeout_seconds

        def expire():
            expired.set()

            if active is not None:
                try:
                    active.shutdown(socket.SHUT_RDWR)
                except OSError:
                    pass
                active.close()

        timer = Timer(self.timeout_seconds, expire)
        timer.daemon = True
        timer.start()

        try:
            connection.connect()
            active = connection.sock
            require(not expired.is_set())
            connection.request(
                method,
                path,
                body,
                {
                    "Accept": "application/json",
                    "Content-Type": "application/json",
                    "Connection": "close",
                },
            )
            response = connection.getresponse()

            if response.status in (429, 500, 502, 503, 504):
                raise GenerationUnavailable("Local model unavailable")

            require(response.status == 200)
            require(
                response.getheader("Content-Type", "").split(";")[0]
                == "application/json"
            )
            require(response.getheader("Content-Encoding") is None)
            length = response.getheader("Content-Length")
            require(
                length is None
                or length.isdecimal()
                and 0 < int(length) <= self.max_response_bytes
            )
            require(response.getheader("Transfer-Encoding") in (None, "chunked"))
            chunks, size = [], 0

            while length is None or size < int(length):
                remaining = deadline - monotonic()

                if expired.is_set() or remaining <= 0:
                    raise GenerationTimeout("Local inference deadline exceeded")

                active.settimeout(remaining)
                chunk = response.read1(min(4096, self.max_response_bytes + 1 - size))

                if not chunk:
                    break

                size += len(chunk)
                require(size <= self.max_response_bytes)
                chunks.append(chunk)

            require(length is None or size == int(length))

            if expired.is_set() or monotonic() >= deadline:
                raise GenerationTimeout("Local inference deadline exceeded")

            return strict_json(b"".join(chunks), self.max_response_bytes)
        except (TimeoutError, socket.timeout):
            raise GenerationTimeout("Local inference deadline exceeded") from None
        except OSError:
            if expired.is_set():
                raise GenerationTimeout("Local inference deadline exceeded") from None
            raise GenerationUnavailable("Local inference connection failed") from None
        except (ClassificationError, GenerationTimeout, GenerationUnavailable):
            raise
        except Exception:
            raise ClassificationError("Local inference transport rejected") from None
        finally:
            timer.cancel()
            connection.close()

            if active is not None:
                active.close()
