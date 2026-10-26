"""Separate authenticated inference service; never exposes the lab bridge as a public API."""

from http.server import HTTPServer

from hdpi.classification.protocols import ClassificationError, require
from hdpi.generation.errors import GenerationTimeout, GenerationUnavailable
from local_classification.ollama_transport import strict_json
from local_classification.bridge_api import BridgeHandler


class ClassificationServer(HTTPServer):
    request_queue_size = 2

    def __init__(self, address, *, service, token):
        require(type(token) is str and 32 <= len(token) <= 128 and token.isascii())
        self.service, self.token = service, token
        super().__init__(address, ClassificationHandler)


class ClassificationHandler(BridgeHandler):
    def do_GET(self):
        self._reply(404, {"code": "CLASSIFICATION_ROUTE_NOT_FOUND"})

    def do_POST(self):
        self.connection.settimeout(5)
        status, response = 400, {"code": "CLASSIFICATION_REJECTED"}
        try:
            if not self._authorized():
                status = 403
            elif self.path != "/internal/intelligence/v1/classify":
                status = 404
            else:
                lengths = self.headers.get_all("Content-Length", [])
                require(
                    len(lengths) == 1
                    and lengths[0].isascii()
                    and lengths[0].isdecimal()
                )
                length = int(lengths[0])
                require(
                    0 < length <= 16384
                    and self.headers.get("Transfer-Encoding") is None
                )
                require(self.headers.get("Content-Type") == "application/json")
                raw = self.rfile.read(length)
                self._received_body = len(raw)
                require(len(raw) == length)
                response = self.server.service.infer(strict_json(raw, 16384))
                status = 200
        except GenerationTimeout:
            status = 504
        except GenerationUnavailable:
            status = 503
        except ClassificationError:
            status = 502
        except Exception:
            status = 400
        self._reply(
            status, response if status == 200 else {"code": "CLASSIFICATION_REJECTED"}
        )


def main():
    """Reference assembly for private engineering acceptance, never automatic production."""
    import argparse
    from dataclasses import fields
    from pathlib import Path

    from hdpi.classification.decision_observation import DecisionObservation
    from hdpi.classification.service import ClassificationInferenceService
    from local_classification.ollama_transport import OllamaTransport
    from local_classification.runtime import LocalClassificationRuntime

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runtime-config", required=True)
    parser.add_argument("--policy-directory", required=True)
    parser.add_argument("--model-directory", required=True)
    parser.add_argument("--token-file", required=True)
    parser.add_argument("--candidate", required=True)
    parser.add_argument("--engineering-dataset-digest", required=True)
    parser.add_argument("--port", type=int, default=8003)
    args = parser.parse_args()
    require(1024 <= args.port <= 65535)
    runtime = LocalClassificationRuntime(
        config_path=args.runtime_config,
        policy_directory=args.policy_directory,
        model_directory=args.model_directory,
        transport=OllamaTransport(timeout_seconds=3),
        enabled=True,
        gpu_required=True,
    )
    require(runtime.config["mode"] == "ENGINEERING_LAB_ONLY")
    require(
        runtime.config["engineeringDatasetDigest"] == args.engineering_dataset_digest
    )
    candidate = runtime.config["candidates"][args.candidate]
    require(
        candidate["adapter"]
        in (
            "tev1-systemone@0.1.0",
            "gemma-classify-only@0.2.0",
            "gemma-classify-only@0.3.0",
        )
    )
    policy = runtime._policies[args.candidate]

    def observe(request):
        result = runtime.infer(
            args.candidate,
            dict(request.fields),
            runtime_digest=runtime.digest,
            policy_digest=candidate["policyDigest"],
        )
        value = result["observation"]
        return DecisionObservation(
            **{item.name: value[item.name] for item in fields(DecisionObservation)}
        )

    service = ClassificationInferenceService(
        model_digest=candidate["modelArtifactDigest"],
        policy_digest=candidate["policyDigest"],
        allowed_classes=policy["allowedClasses"],
        abstain_id=policy["policy"]["abstainId"],
        observe=observe,
    )
    token = Path(args.token_file).read_text("ascii").strip()
    with ClassificationServer(
        ("127.0.0.1", args.port), service=service, token=token
    ) as server:
        server.serve_forever(poll_interval=0.2)


if __name__ == "__main__":
    main()
