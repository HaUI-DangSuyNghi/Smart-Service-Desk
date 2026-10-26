"""Private Qwen assembly of the existing neutral classification HTTP protocol."""

import argparse
from http.server import HTTPServer
import os
from pathlib import Path
from socketserver import ThreadingMixIn
from threading import BoundedSemaphore

from hdpi.classification.protocols import require
from hdpi.classification.service import ClassificationInferenceService
from local_classification.classification_api import ClassificationHandler
from local_classification.qwen_artifacts import verify_bundle
from local_classification.qwen_lora_runtime import ProcessGenerator, QwenLoraRuntime


class QwenHandler(ClassificationHandler):
    def do_GET(self):
        if not self._authorized():
            self._reply(403, {"code": "CLASSIFICATION_REJECTED"})
        elif self.path != "/health/ready":
            self._reply(404, {"code": "CLASSIFICATION_ROUTE_NOT_FOUND"})
        elif not self.server.runtime.ready:
            self._reply(503, {"code": "CLASSIFICATION_NOT_READY"})
        else:
            self._reply(
                200,
                {
                    "ready": True,
                    "modelDigest": self.server.service.model_digest,
                    "policyDigest": self.server.service.policy_digest,
                    "operationalAuthority": False,
                },
            )


class QwenServer(ThreadingMixIn, HTTPServer):
    daemon_threads = True
    block_on_close = False
    request_queue_size = 2

    def __init__(self, address, *, runtime, token):
        require(type(token) is str and 32 <= len(token) <= 128 and token.isascii())
        self.runtime, self.token = runtime, token
        config = runtime.configuration
        self.service = ClassificationInferenceService(
            model_digest=runtime.model_digest,
            policy_digest=config["policyDigest"],
            allowed_classes=config["allowedClasses"],
            abstain_id=config["abstainId"],
            observe=runtime.observe,
        )
        self.connections = BoundedSemaphore(4)
        super().__init__(address, QwenHandler)

    def get_request(self):
        connection, address = super().get_request()
        connection.settimeout(2)
        return connection, address

    def process_request(self, request, client_address):
        if self.connections.acquire(blocking=False):
            try:
                super().process_request(request, client_address)
            except Exception:
                self.connections.release()
                raise
        else:
            self.shutdown_request(request)

    def process_request_thread(self, request, client_address):
        try:
            super().process_request_thread(request, client_address)
        finally:
            self.connections.release()

    def handle_error(self, request, client_address):
        # Never write request/provider tracebacks to telemetry.
        pass


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bundle", type=Path, required=True)
    parser.add_argument("--model-digest", required=True)
    parser.add_argument("--runtime-identity", required=True)
    parser.add_argument("--token-file", type=Path, required=True)
    parser.add_argument("--bind", choices=("127.0.0.1", "0.0.0.0"), default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8003)
    args = parser.parse_args()
    require(os.environ.get("AI07_QWEN_ENABLED") == "1")
    require(1024 <= args.port <= 65535)
    # This assembly is app-owned; the reusable runtime has no Service Desk import.
    from servicedesk.intelligence.triage_ai07.guarded_decision import (
        POLICY_DIGEST as guard_digest,
    )
    from servicedesk.intelligence.triage_ai07.qwen_policy import adapter_configuration

    config = adapter_configuration()
    verification = {
        "expected_digest": args.model_digest,
        "policy_digest": config["policyDigest"],
        "guard_digest": guard_digest,
        "runtime_identity": args.runtime_identity,
    }
    verify_bundle(args.bundle, **verification)
    generator = ProcessGenerator(
        args.bundle,
        verification=verification,
        warmup_messages=config["messages"](
            {"title": "Synthetic warmup", "description": ""}
        ),
        generation=config["generation"],
    )
    runtime = QwenLoraRuntime(
        model_digest=args.model_digest,
        configuration=config,
        generator=generator,
        enabled=True,
    )
    token = args.token_file.read_text("ascii").strip()
    try:
        with QwenServer((args.bind, args.port), runtime=runtime, token=token) as server:
            server.serve_forever(poll_interval=0.2)
    finally:
        runtime.close()


if __name__ == "__main__":
    main()
