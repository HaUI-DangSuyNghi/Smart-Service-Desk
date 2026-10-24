"""Bounded HTTPS, fixed vendor destinations, no ambient proxies or retries."""

from datetime import datetime, timezone
from email.utils import parsedate_to_datetime
import http.client
import ipaddress
from queue import Queue, Empty
import re
import socket
import ssl
from threading import BoundedSemaphore, Event, Thread, Timer
from time import monotonic
from urllib.parse import urlsplit

from .contracts import CloudFailure


HOSTS = {"gemini": "generativelanguage.googleapis.com", "groq": "api.groq.com"}
DNS_SLOTS = BoundedSemaphore(4)


def public_address(host, timeout, *, allow_private=False):
    if not DNS_SLOTS.acquire(blocking=False):
        raise CloudFailure("GENERATION_UNAVAILABLE")
    result = Queue(maxsize=1)

    def resolve():
        try:
            addresses = socket.getaddrinfo(host, 443, type=socket.SOCK_STREAM)
            if not addresses or (
                not allow_private
                and any(
                    not ipaddress.ip_address(item[4][0]).is_global for item in addresses
                )
            ):
                result.put(CloudFailure("EGRESS_NOT_AUTHORIZED", "DISABLED", 0))
            else:
                result.put(addresses[0][4][0])
        except Exception:
            result.put(CloudFailure("GENERATION_UNAVAILABLE"))
        finally:
            DNS_SLOTS.release()

    # A timed-out OS lookup cannot be killed by Future.cancel. Bound leftover
    # lookups to four daemon threads; no prompt or credential enters that thread.
    Thread(target=resolve, daemon=True).start()
    try:
        value = result.get(timeout=timeout)
    except Empty:
        raise CloudFailure("GENERATION_TIMEOUT") from None
    if isinstance(value, CloudFailure):
        raise value
    return value


def retry_after(value, now=None):
    try:
        if re.fullmatch(r"[0-9]{1,6}", value or ""):
            seconds = int(value)
        else:
            stamp = parsedate_to_datetime(value)
            if stamp.tzinfo is None:
                raise ValueError()
            seconds = int((stamp - (now or datetime.now(timezone.utc))).total_seconds())
        return min(3600, max(1, seconds))
    except (ValueError, TypeError, OverflowError):
        return 60


class PinnedHTTPS(http.client.HTTPSConnection):
    def connect(self):
        deadline = monotonic() + self.timeout

        def remaining():
            seconds = deadline - monotonic()

            if seconds <= 0:
                raise CloudFailure("GENERATION_TIMEOUT")

            return seconds

        # The only private destination is an explicitly validated deployment
        # relay. That relay separately validates the public vendor destination.
        address = public_address(
            self.host, min(2, remaining()), allow_private=self._tunnel_host is not None
        )
        raw = socket.create_connection((address, self.port), timeout=remaining())
        self.sock = raw

        try:
            if raw.getpeername()[0] != address:
                raise CloudFailure("EGRESS_NOT_AUTHORIZED", "DISABLED", 0)

            raw.settimeout(remaining())

            if self._tunnel_host:
                self._tunnel()

            raw.settimeout(remaining())
            self.sock = self._context.wrap_socket(
                raw, server_hostname=self._tunnel_host or self.host
            )
            self.sock.settimeout(remaining())
        except Exception:
            raw.close()
            raise


class BoundedHTTPSClient:
    def __init__(self, *, proxy=None):
        self.proxy = proxy
        self.slots = BoundedSemaphore(2)

    def post(self, route, body, credential):
        if len(body) > route.max_input_bytes:
            raise CloudFailure("PROVIDER_BUDGET_EXCEEDED", "RATE_LIMITED", 60)
        if not self.slots.acquire(blocking=False):
            raise CloudFailure("GENERATION_UNAVAILABLE")

        connection = None
        timer = None
        expired = Event()

        try:
            host = HOSTS[route.vendor]
            context = ssl.create_default_context()
            if self.proxy:
                proxy = urlsplit(self.proxy)
                if (
                    proxy.scheme != "http"
                    or proxy.hostname not in {"cloud-egress", "127.0.0.1", "localhost"}
                    or proxy.port is None
                    or proxy.username
                    or proxy.password
                    or proxy.query
                    or proxy.fragment
                    or proxy.path not in {"", "/"}
                ):
                    raise CloudFailure("EGRESS_NOT_AUTHORIZED", "DISABLED", 0)
                connection = PinnedHTTPS(
                    proxy.hostname,
                    proxy.port,
                    timeout=route.timeout_seconds,
                    context=context,
                )
                connection.set_tunnel(host, 443)
            else:
                connection = PinnedHTTPS(
                    host, timeout=route.timeout_seconds, context=context
                )

            def expire():
                expired.set()
                try:
                    if connection.sock:
                        connection.sock.shutdown(socket.SHUT_RDWR)
                except OSError:
                    pass
                connection.close()

            timer = Timer(route.timeout_seconds, expire)
            timer.daemon = True
            timer.start()

            path = (
                f"/v1beta/models/{route.model}:generateContent"
                if route.vendor == "gemini"
                else "/openai/v1/chat/completions"
            )
            if route.vendor == "gemini" and not re.fullmatch(
                r"[A-Za-z0-9][A-Za-z0-9._-]{0,160}", route.model
            ):
                raise CloudFailure("PROVIDER_CONFIGURATION_ERROR", "DISABLED", 0)
            headers = {"Content-Type": "application/json", "Accept": "application/json"}
            headers[
                "x-goog-api-key" if route.vendor == "gemini" else "Authorization"
            ] = (credential if route.vendor == "gemini" else "Bearer " + credential)
            connection.request("POST", path, body, headers)
            response = connection.getresponse()

            if response.status == 429:
                raise CloudFailure(
                    "PROVIDER_RATE_LIMITED",
                    "RATE_LIMITED",
                    retry_after(response.getheader("Retry-After")),
                )
            if response.status in {401, 403}:
                raise CloudFailure("PROVIDER_CONFIGURATION_ERROR", "DISABLED", 0)
            if 500 <= response.status <= 599:
                raise CloudFailure("GENERATION_UNAVAILABLE")
            if 300 <= response.status <= 399:
                raise CloudFailure("EGRESS_NOT_AUTHORIZED", "DISABLED", 0)
            if response.status != 200:
                raise CloudFailure("GENERATION_CONTRACT_FAILED", "DISABLED", 0)
            if (
                len(response.headers.get_all("Content-Type", [])) != 1
                or response.headers.get_content_type() != "application/json"
                or response.getheader("Content-Encoding") is not None
            ):
                raise CloudFailure("GENERATION_CONTRACT_FAILED", "DISABLED", 0)
            lengths = response.headers.get_all("Content-Length", [])
            if len(lengths) > 1 or (
                lengths
                and (
                    not re.fullmatch(r"[0-9]{1,7}", lengths[0])
                    or int(lengths[0]) > 131072
                )
            ):
                raise CloudFailure("GENERATION_CONTRACT_FAILED", "DISABLED", 0)
            raw = response.read(131073)
            if expired.is_set():
                raise CloudFailure("GENERATION_TIMEOUT")
            if (
                not raw
                or len(raw) > 131072
                or (lengths and len(raw) != int(lengths[0]))
            ):
                raise CloudFailure("GENERATION_CONTRACT_FAILED", "DISABLED", 0)
            return raw
        except CloudFailure:
            raise
        except ssl.SSLCertVerificationError:
            raise CloudFailure("EGRESS_NOT_AUTHORIZED", "DISABLED", 0) from None
        except (TimeoutError, socket.timeout):
            raise CloudFailure("GENERATION_TIMEOUT") from None
        except http.client.HTTPException:
            raise CloudFailure(
                (
                    "GENERATION_TIMEOUT"
                    if expired.is_set()
                    else "GENERATION_CONTRACT_FAILED"
                ),
                "UNAVAILABLE" if expired.is_set() else "DISABLED",
            ) from None
        except OSError:
            raise CloudFailure(
                "GENERATION_TIMEOUT" if expired.is_set() else "GENERATION_UNAVAILABLE"
            ) from None
        finally:
            if timer:
                timer.cancel()
            if connection:
                connection.close()
            self.slots.release()
