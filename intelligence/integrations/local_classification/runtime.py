"""One-slot, pinned native local inference. This runtime has no ticket authority."""

from dataclasses import asdict
from copy import deepcopy
import platform
import re
from threading import BoundedSemaphore
from time import monotonic

from hdpi.classification.protocols import (
    ClassificationError,
    ClassificationRequest,
    require,
)
from hdpi.generation.errors import GenerationTimeout, GenerationUnavailable
from local_classification.adapters import gemma_chat_json, tev1_systemone
from local_classification.bootstrap import (
    ArtifactLease,
    canonical_digest,
    load_configuration,
    load_policy,
)


class LocalClassificationRuntime:
    def __init__(
        self,
        *,
        config_path,
        policy_directory,
        model_directory,
        transport,
        enabled=False,
        require_readonly=True,
        gpu_required=False,
    ):
        require(enabled is True)
        self.config, self.digest, config_snapshot = load_configuration(config_path)
        self.transport = transport
        self.gpu_required = gpu_required
        self._slot = BoundedSemaphore(1)
        self._requests = []
        self._revoked = False
        self._policies, snapshots = {}, [config_snapshot]
        self._artifacts = ArtifactLease.verify(
            model_directory,
            self.config["modelFiles"],
            require_readonly=require_readonly,
        )

        for name, candidate in self.config["candidates"].items():
            verified_digests = {item["sha256"] for item in self.config["modelFiles"]}
            require(candidate["modelArtifactDigest"] in verified_digests)
            require(candidate["tokenizerDigest"] == candidate["modelArtifactDigest"])
            require(candidate["apiDigest"] in verified_digests)
            require(candidate["sourceModelDigest"] in verified_digests)
            policy, snapshot = load_policy(policy_directory, candidate)
            self._policies[name] = policy
            snapshots.append(snapshot)

        self._configuration = ArtifactLease(tuple(snapshots))
        self._check_identity()

        for candidate in self.config["candidates"].values():
            shown = self.transport.exchange(
                "POST", "/api/show", {"model": candidate["model"]}
            )
            require(shown["details"]["quantization_level"] == candidate["quantization"])

            if candidate["adapter"] == tev1_systemone.VERSION:
                require("decision" in shown["capabilities"])
                match = re.search(
                    r"^\s*num_ctx\s+(\d+)\s*$", shown.get("parameters", ""), re.M
                )
                require(match is not None and match.group(1) == "2048")
            else:
                require(
                    "completion" in shown["capabilities"]
                    and "decision" not in shown["capabilities"]
                )

    def _check_identity(self):
        require(not self._revoked)

        try:
            self._artifacts.check()
            self._configuration.check()
            require(
                self.transport.exchange("GET", "/api/version")
                == {"version": self.config["ollamaVersion"]}
            )
            tags = self.transport.exchange("GET", "/api/tags")
            require(type(tags) is dict and type(tags.get("models")) is list)
            actual = {item["name"]: item["digest"] for item in tags["models"]}

            for candidate in self.config["candidates"].values():
                require(actual.get(candidate["model"]) == candidate["apiDigest"])
        except Exception:
            self._revoked = True
            raise ClassificationError("Local deployment identity rejected") from None

    def identity(self):
        self._check_identity()
        return {
            "contractVersion": "local-classification-bridge@0.1.0",
            "runtimeDigest": self.digest,
            "bridgeRevision": self.config["bridgeRevision"],
            "ollamaVersion": self.config["ollamaVersion"],
            "ollamaImage": self.config["ollamaImage"],
            "mode": "SYNTHETIC_LAB_ONLY",
            "contextTokens": 2048,
            "concurrency": 1,
            "operationalAuthority": False,
            "candidates": deepcopy(self.config["candidates"]),
            "experimentBindings": {
                name: {
                    "experimentId": policy["experimentId"],
                    "taxonomyDigest": canonical_digest(
                        {
                            "allowedClasses": policy["allowedClasses"],
                            "inputFields": policy["inputFields"],
                        }
                    ),
                }
                for name, policy in self._policies.items()
            },
            "runtimeArch": platform.system() + "/" + platform.machine(),
            "gpuRequired": self.gpu_required,
            "inferenceParameters": {
                "tev1": {
                    "generationControls": "NOT_SUPPORTED",
                    "contextSource": "PINNED_MODEL_ALIAS",
                    "keepAliveSeconds": 30,
                },
                "gemma3": {
                    **dict(gemma_chat_json.OPTIONS),
                    "truncate": False,
                    "shift": False,
                    "keepAliveSeconds": 30,
                },
            },
            "validatorVersions": [
                "decision-observation@0.1.0",
                "bounded-classification@0.1.0",
                "exact-unique-quotes@0.1.0",
            ],
            "tokenBudgetEnforcement": "PINNED_NATIVE_TOKENIZER_REJECT_OVERSIZE",
            "egress": "REQUIRES_DEPLOYMENT_VERIFICATION",
            "logging": "NO_REQUEST_BODY_LOGGING",
        }

    def infer(self, candidate_id, fields, *, runtime_digest, policy_digest):
        require(runtime_digest == self.digest and candidate_id in self._policies)
        candidate = self.config["candidates"][candidate_id]
        require(policy_digest == candidate["policyDigest"])
        policy = self._policies[candidate_id]
        require(type(fields) is dict)
        require(tuple(fields) == tuple(policy["inputFields"]))
        request = ClassificationRequest(
            fields=tuple(fields.items()),
            allowed_classes=tuple(policy["allowedClasses"]),
            policy_digest=policy_digest,
        )

        if not self._slot.acquire(blocking=False):
            raise GenerationUnavailable("Local inference capacity unavailable")

        started = monotonic()

        try:
            now = monotonic()
            self._requests[:] = [
                instant for instant in self._requests if instant > now - 60
            ]

            if len(self._requests) >= 120:
                raise GenerationUnavailable("Local inference rate budget exceeded")

            self._requests.append(now)
            self._check_identity()
            running = self.transport.exchange("GET", "/api/ps")["models"]
            loaded_before = any(item["name"] == candidate["model"] for item in running)

            for item in running:
                if item["name"] != candidate["model"]:
                    self.transport.exchange(
                        "POST",
                        "/api/generate",
                        {"model": item["name"], "keep_alive": 0},
                    )

            adapter = (
                tev1_systemone
                if candidate["adapter"] == tev1_systemone.VERSION
                else gemma_chat_json
            )
            payload = adapter.request_payload(
                request,
                model=candidate["model"],
                policy=policy["policy"],
                keep_alive=30,
            )
            path = "/v1/systemone" if adapter is tev1_systemone else "/api/chat"
            native_started = monotonic()
            value = self.transport.exchange("POST", path, payload)
            native_elapsed_ms = round((monotonic() - native_started) * 1000, 3)
            options = {"model": candidate["model"]}

            if adapter is tev1_systemone:
                options["abstain_id"] = policy["policy"]["abstainId"]

            observation, usage = adapter.decode_response(value, request, **options)
            running = self.transport.exchange("GET", "/api/ps")["models"]
            require(len(running) == 1 and running[0]["name"] == candidate["model"])
            placement = running[0]
            require(placement.get("context_length") == 2048)
            require(
                type(placement.get("size_vram")) is int and placement["size_vram"] >= 0
            )

            if self.gpu_required:
                require(placement["size_vram"] > 0)

            self._check_identity()
            return {
                "contractVersion": "local-classification-result@0.1.0",
                "candidateId": candidate_id,
                "runtimeDigest": self.digest,
                "policyDigest": policy_digest,
                "operationalAuthority": False,
                "observation": asdict(observation),
                "usage": usage,
                "placement": {
                    key: placement[key]
                    for key in ("size", "size_vram", "context_length")
                },
                "loadedBefore": loaded_before,
                "nativeElapsedMs": native_elapsed_ms,
                "elapsedMs": round((monotonic() - started) * 1000, 3),
            }
        except (ClassificationError, GenerationTimeout, GenerationUnavailable):
            raise
        except Exception:
            raise ClassificationError("Local inference rejected") from None
        finally:
            self._slot.release()
