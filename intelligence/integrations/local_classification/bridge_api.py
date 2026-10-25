"""Authenticated, single-worker lab bridge. No logs of requests or model output."""

import argparse
import hmac
from http.server import BaseHTTPRequestHandler, HTTPServer
import json
import os
from pathlib import Path
import socket
from time import monotonic

from hdpi.classification.protocols import ClassificationError, require
from hdpi.generation.errors import GenerationTimeout, GenerationUnavailable
from local_classification.ollama_transport import OllamaTransport, strict_json
from local_classification.runtime import LocalClassificationRuntime
from local_classification.diagnostics import ObservationRejected, safe_telemetry


class BridgeServer(HTTPServer):
    request_queue_size = 2
    allow_reuse_address = True

    def __init__(self, address, *, runtime, token):
        require(type(token) is str and 32 <= len(token) <= 128 and token.isascii())
        self.runtime = runtime
        self.token = token
        super().__init__(address, BridgeHandler)

    def get_request(self):
        connection, address = super().get_request()
        connection.settimeout(5)
        return connection, address


class BridgeHandler(BaseHTTPRequestHandler):
    server_version = "LocalClassificationBridge"
    sys_version = ""

    def log_message(self, *args):
        pass

    def _authorized(self):
        values = self.headers.get_all("Authorization", [])
        return (
            len(values) == 1
            and len(values[0]) <= 160
            and values[0].isascii()
            and hmac.compare_digest(values[0], "Bearer " + self.server.token)
        )

    def _reply(self, status, value):
        raw = json.dumps(
            value, ensure_ascii=True, allow_nan=False, separators=(",", ":")
        ).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(raw)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(raw)
        self.wfile.flush()
        self.close_connection = True

        if status >= 400:
            # A bounded drain prevents TCP reset from discarding an early 4xx on Windows.
            # Send the response first; never wait for an unbounded unauthorized body.
            lengths = self.headers.get_all("Content-Length", [])
            if (
                len(lengths) == 1
                and len(lengths[0]) <= 9
                and lengths[0].isascii()
                and lengths[0].isdecimal()
            ):
                remaining = min(int(lengths[0]), 16385) - getattr(
                    self, "_received_body", 0
                )
                deadline = monotonic() + 0.2
                try:
                    while remaining > 0:
                        budget = deadline - monotonic()
                        if budget <= 0:
                            break
                        self.connection.settimeout(budget)
                        block = self.rfile.read1(min(4096, remaining))
                        if not block:
                            break
                        remaining -= len(block)
                except OSError:
                    pass

        try:
            self.connection.shutdown(socket.SHUT_WR)
        except OSError:
            pass

    def do_GET(self):
        if not self._authorized():
            self._reply(403, {"code": "BRIDGE_AUTH_REQUIRED"})
            return

        if self.path not in ("/v1/identity", "/health/ready"):
            self._reply(404, {"code": "BRIDGE_ROUTE_NOT_FOUND"})
            return

        try:
            identity = self.server.runtime.identity()
            self._reply(
                200,
                identity
                if self.path == "/v1/identity"
                else {
                    "ready": True,
                    "runtimeDigest": identity["runtimeDigest"],
                    "operationalAuthority": False,
                },
            )
        except Exception:
            self._reply(503, {"code": "BRIDGE_NOT_READY"})

    def do_POST(self):
        if not self._authorized():
            self._reply(403, {"code": "BRIDGE_AUTH_REQUIRED"})
            return

        if self.path != "/v1/observe":
            self._reply(404, {"code": "BRIDGE_ROUTE_NOT_FOUND"})
            return

        validated = False

        try:
            lengths = self.headers.get_all("Content-Length", [])
            require(
                len(lengths) == 1 and lengths[0].isascii() and lengths[0].isdecimal()
            )
            length = int(lengths[0])
            require(
                0 < length <= 16384 and self.headers.get("Transfer-Encoding") is None
            )
            require(self.headers.get("Content-Type") == "application/json")
            raw = self.rfile.read(length)
            self._received_body = len(raw)
            require(len(raw) == length)
            value = strict_json(raw, 16384)
            require(
                type(value) is dict
                and set(value)
                == {
                    "candidateId",
                    "runtimeDigest",
                    "policyDigest",
                    "dataScope",
                    "input",
                }
            )
            require(value["dataScope"] == "SYNTHETIC_DEV_ONLY")
            require(
                all(
                    type(value[key]) is str
                    for key in ("candidateId", "runtimeDigest", "policyDigest")
                )
            )
            validated = True
            result = self.server.runtime.infer(
                value["candidateId"],
                value["input"],
                runtime_digest=value["runtimeDigest"],
                policy_digest=value["policyDigest"],
            )
            self._reply(200, result)
        except GenerationTimeout as error:
            self._reply(
                504,
                {
                    "code": "LOCAL_INFERENCE_TIMEOUT",
                    "reasonCode": "TIMEOUT",
                    "diagnostics": safe_telemetry(getattr(error, "telemetry", {})),
                },
            )
        except GenerationUnavailable as error:
            self._reply(
                503,
                {
                    "code": "LOCAL_INFERENCE_UNAVAILABLE",
                    "reasonCode": "UNAVAILABLE",
                    "diagnostics": safe_telemetry(getattr(error, "telemetry", {})),
                },
            )
        except ObservationRejected as error:
            self._reply(
                502 if validated else 400,
                {
                    "code": "LOCAL_OBSERVATION_REJECTED",
                    "reasonCode": error.reason_code,
                    "diagnostics": safe_telemetry(error.telemetry),
                },
            )
        except ClassificationError as error:
            self._reply(
                502 if validated else 400,
                {
                    "code": "LOCAL_OBSERVATION_REJECTED",
                    "reasonCode": "SCHEMA_MISMATCH",
                    "diagnostics": safe_telemetry(getattr(error, "telemetry", {})),
                },
            )
        except (ValueError, TypeError, KeyError, OSError):
            self._reply(400, {"code": "LOCAL_OBSERVATION_REJECTED"})
        except Exception:
            self._reply(503, {"code": "LOCAL_OBSERVATION_FAILED"})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    require(os.environ.get("AI07_LOCAL_ENABLED") == "1")
    config = os.environ["AI07_RUNTIME_CONFIG"]
    runtime = LocalClassificationRuntime(
        config_path=config,
        policy_directory=os.environ["AI07_POLICY_DIRECTORY"],
        model_directory=os.environ["AI07_MODEL_DIRECTORY"],
        transport=OllamaTransport(timeout_seconds=120),
        enabled=True,
        gpu_required=os.environ.get("AI07_GPU_REQUIRED") == "1",
    )
    require(
        Path("/opt/bridge-revision").read_text().strip()
        == runtime.config["bridgeRevision"]
    )
    token = Path(os.environ["AI07_AUTH_TOKEN_FILE"]).read_text("ascii").strip()

    if args.check:
        print(
            json.dumps(
                {
                    "ready": True,
                    "runtimeDigest": runtime.digest,
                    "bridgeRevision": runtime.config["bridgeRevision"],
                }
            )
        )
        return

    with BridgeServer(("127.0.0.1", 8002), runtime=runtime, token=token) as server:
        server.serve_forever(poll_interval=0.2)


if __name__ == "__main__":
    main()
