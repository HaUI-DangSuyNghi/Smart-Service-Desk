"""Dedicated private assistance transport. Never route this port through a gateway."""

import argparse
from hashlib import sha256
import hmac
from http.server import BaseHTTPRequestHandler
import json
import os
from pathlib import Path
import re
import socket
from threading import Timer

from .domain.errors import ContractValidationError
from .generation.deterministic import CitationBoundTemplateProvider
from .generation.errors import GenerationConfigurationError
from .generation.policy import GenerationPolicy
from .http_service import PrivateServer, reject_constant, unique_object
from .http_rejection import finish_rejection
from .services.assistance_runtime import AssistanceRuntime


ENDPOINT = "/internal/intelligence/v1/assistance"
MAX_REQUEST = 256 * 1024
MAX_RESPONSE = 128 * 1024


def make_server(address, *, service_token, runtime, request_timeout=15):
    if not isinstance(service_token, str) or not re.fullmatch(
        r"[A-Za-z0-9._~+/-]{32,}=*",
        service_token,
    ):
        raise ValueError("Invalid private assistance credential")

    credential = ("Bearer " + service_token).encode("ascii")

    class Handler(BaseHTTPRequestHandler):
        server_version = "Intelligence"
        sys_version = ""

        def setup(self):
            super().setup()
            self.connection.settimeout(request_timeout)
            self.deadline = Timer(request_timeout, self.expire)
            self.deadline.daemon = True
            self.deadline.start()

        def expire(self):
            try:
                self.connection.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass

        def finish(self):
            self.deadline.cancel()
            super().finish()

        def log_message(self, *_):
            pass

        def send_error(self, code, message=None, explain=None):
            self.respond(400, {"code": "INVALID_REQUEST"})

        def respond(self, status, payload):
            body = json.dumps(payload, ensure_ascii=False, allow_nan=False).encode(
                "utf-8"
            )

            if len(body) > MAX_RESPONSE:
                status, body = 502, b'{"code":"ASSISTANCE_CONTRACT_FAILED"}'

            self.close_connection = True

            try:
                self.send_response(status)
                self.send_header("Content-Type", "application/json; charset=utf-8")
                self.send_header("Content-Length", str(len(body)))
                self.send_header("Cache-Control", "no-store")
                self.send_header("Connection", "close")
                self.end_headers()

                if self.command != "HEAD":
                    self.wfile.write(body)
                finish_rejection(self, status)
            except OSError:
                pass

        def do_POST(self):
            headers = self.headers.get_all("Authorization", [])
            supplied = headers[0].encode("utf-8") if len(headers) == 1 else b""

            if not hmac.compare_digest(supplied, credential):
                self.respond(401, {"code": "UNAUTHENTICATED"})
                return

            if self.path != ENDPOINT:
                self.respond(404, {"code": "NOT_FOUND"})
                return

            if self.command != "POST":
                self.respond(405, {"code": "METHOD_NOT_ALLOWED"})
                return

            lengths = self.headers.get_all("Content-Length", [])

            if (
                self.headers.get_all("Transfer-Encoding")
                or self.headers.get_all("Content-Encoding")
                or len(lengths) != 1
                or not re.fullmatch(r"[0-9]{1,6}", lengths[0])
                or len(self.headers.get_all("Content-Type", [])) != 1
                or self.headers.get_content_type() != "application/json"
                or self.headers.get_content_charset("utf-8") != "utf-8"
            ):
                self.respond(400, {"code": "INVALID_REQUEST"})
                return

            length = int(lengths[0])

            if not 0 < length <= MAX_REQUEST:
                self.respond(413, {"code": "INVALID_REQUEST"})
                return

            try:
                raw = self.rfile.read(length)
                self._received_body = len(raw)

                if len(raw) != length:
                    raise ValueError("Truncated body")

                payload = json.loads(
                    raw.decode("utf-8"),
                    object_pairs_hook=unique_object,
                    parse_constant=reject_constant,
                )
            except OSError:
                self.respond(408, {"code": "REQUEST_TIMEOUT"})
                return
            except (ValueError, UnicodeError, RecursionError):
                self.respond(400, {"code": "INVALID_REQUEST"})
                return

            try:
                response = runtime.assist(payload)
            except ContractValidationError:
                self.respond(400, {"code": "INVALID_REQUEST"})
                return
            except GenerationConfigurationError:
                self.respond(502, {"code": "ASSISTANCE_CONFIGURATION_FAILED"})
                return
            except Exception:
                self.respond(502, {"code": "ASSISTANCE_CONTRACT_FAILED"})
                return

            self.respond(200, response)

        do_GET = do_PUT = do_PATCH = do_DELETE = do_HEAD = do_OPTIONS = do_POST

    return PrivateServer(address, Handler)


def reference_runtime(path, expected_digest, *, profiles=None):
    """Explicit engineering composition; no implicit model/template fallback."""
    raw = Path(path).read_bytes()

    if (
        not re.fullmatch(r"[a-f0-9]{64}", expected_digest or "")
        or sha256(raw).hexdigest() != expected_digest
    ):
        raise ValueError("Assistance configuration digest mismatch")

    config = json.loads(raw.decode("utf-8"), object_pairs_hook=unique_object)

    if (
        set(config) != {"mode", "profileId", "profileVersion", "policy"}
        or config["mode"] != "deterministic-reference"
    ):
        raise ValueError("Only explicit reference composition is available")

    policy = GenerationPolicy(**config["policy"])

    return AssistanceRuntime(
        provider=CitationBoundTemplateProvider(app_prompt_version=policy.identity),
        policy=policy,
        profile_id=config["profileId"],
        profile_version=config["profileVersion"],
        profiles=profiles,
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8001)
    args = parser.parse_args()

    try:
        if os.environ.get("HDPI_ASSISTANCE_REFERENCE_ENABLED") != "1":
            raise ValueError("Reference deployment is disabled")

        runtime = reference_runtime(
            os.environ["HDPI_ASSISTANCE_CONFIG"],
            os.environ["HDPI_ASSISTANCE_CONFIG_SHA256"],
        )
        server = make_server(
            (args.host, args.port),
            service_token=os.environ["INTELLIGENCE_ASSISTANCE_TOKEN"],
            runtime=runtime,
        )
    except Exception:
        parser.exit(1, "Assistance startup failed [INVALID_CONFIGURATION]\n")

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
