"""Versioned loopback sidecar; no model downloads, cloud fallback or app semantics."""

from dataclasses import dataclass, field
import json
from pathlib import Path
from threading import BoundedSemaphore, Lock
from time import monotonic
from typing import Protocol

from hdpi.classification.protocols import (
    ClassificationError,
    ClassificationRequest,
    require,
)
from hdpi.classification.validation import decode_proposal
from hdpi.generation.errors import (
    GenerationConfigurationError,
    GenerationTimeout,
    GenerationUnavailable,
)
from hdpi.generation.model_manifest import ModelManifest
from hdpi.generation.transport import (
    LoopbackSelectionTransport,
    TokenCounter,
    response_json,
)


CONTRACT = "bounded-classification@0.1.0"


class ClassificationTransport(Protocol):
    def identity(self, *, limits): ...

    def classify(self, body: bytes, *, limits): ...


@dataclass(frozen=True, kw_only=True)
class LoopbackClassificationTransport(LoopbackSelectionTransport):
    def classify(self, body, *, limits):
        require(type(body) is bytes and len(body) <= limits.max_request_bytes)
        return self._exchange("POST", "/v1/classify", body, limits)


@dataclass(frozen=True, kw_only=True)
class PinnedLocalClassificationCandidate:
    """A reviewed deployment supplies the model root, transport and real tokenizer.

    Approval hashes are references, not signatures. The operator must authenticate
    those receipts and enforce egress denial and disabled body logging separately.
    """

    manifest: ModelManifest = field(repr=False)
    model_directory: Path = field(repr=False)
    transport: ClassificationTransport = field(repr=False)
    tokenizer: TokenCounter = field(repr=False)
    policy_instructions: str = field(repr=False)
    policy_digest: str
    enabled: bool = False

    def __post_init__(self):
        from hashlib import sha256

        require(self.enabled is True and type(self.manifest) is ModelManifest)
        require(
            type(self.policy_instructions) is str
            and 0 < len(self.policy_instructions) <= 4096
        )
        require(
            sha256(self.policy_instructions.encode("utf-8")).hexdigest()
            == self.policy_digest
        )
        require(self.manifest.limits.concurrency == 1)
        require(self.manifest.limits.context_tokens <= 2048)
        require(self.manifest.limits.output_tokens <= 256)
        require(self.manifest.limits.total_seconds <= 10)
        object.__setattr__(self, "_slot", BoundedSemaphore(1))
        object.__setattr__(self, "_rate_lock", Lock())
        object.__setattr__(self, "_calls", [])
        self._preflight()

    def _preflight(self):
        self.manifest.verify_directory(self.model_directory)
        require(self.tokenizer.manifest_digest == self.manifest.digest)
        identity = response_json(
            self.transport.identity(limits=self.manifest.limits),
            maximum=self.manifest.limits.max_response_bytes,
        )
        require(
            identity
            == {
                "manifest_digest": self.manifest.digest,
                "contract_version": CONTRACT,
                "capabilities": [],
                "logging": "disabled",
                "egress": "denied",
            }
        )

    def predict(self, request):
        require(
            type(request) is ClassificationRequest
            and request.policy_digest == self.policy_digest
        )

        if not self._slot.acquire(blocking=False):
            raise GenerationUnavailable("Classification capacity unavailable")

        try:
            with self._rate_lock:
                now = monotonic()
                self._calls[:] = [
                    instant for instant in self._calls if instant > now - 60
                ]

                if len(self._calls) >= self.manifest.limits.requests_per_minute:
                    raise GenerationUnavailable("Classification rate budget exceeded")

                self._calls.append(now)

            self._preflight()
            limits = self.manifest.limits
            payload = {
                "contract_version": CONTRACT,
                "manifest_digest": self.manifest.digest,
                "policy_digest": self.policy_digest,
                "instructions": self.policy_instructions,
                "untrusted_fields": dict(request.fields),
                "allowed_classes": list(request.allowed_classes),
                "temperature": 0,
                "max_tokens": limits.output_tokens,
                "stream": False,
                "output_contract": {
                    "contractVersion": "0.1.0",
                    "decisions": ["PROPOSE", "ABSTAIN"],
                    "classId": list(request.allowed_classes),
                    "spans": "exact-input-unicode-codepoint-offsets",
                    "additionalProperties": False,
                },
            }
            body = json.dumps(payload, ensure_ascii=True, separators=(",", ":")).encode(
                "utf-8"
            )
            require(len(body) <= limits.max_request_bytes)
            tokens = self.tokenizer.count(body)
            require(
                type(tokens) is int
                and 0 < tokens <= limits.context_tokens - limits.output_tokens
            )
            started = monotonic()
            envelope = response_json(
                self.transport.classify(body, limits=limits),
                maximum=limits.max_response_bytes,
            )
            require(monotonic() - started <= limits.total_seconds)
            require(
                type(envelope) is dict
                and set(envelope)
                == {
                    "contract_version",
                    "manifest_digest",
                    "policy_digest",
                    "finish_reason",
                    "proposal",
                }
            )
            require(envelope["contract_version"] == CONTRACT)
            require(envelope["manifest_digest"] == self.manifest.digest)
            require(envelope["policy_digest"] == self.policy_digest)
            require(envelope["finish_reason"] == "stop")
            proposal = decode_proposal(
                json.dumps(envelope["proposal"]).encode("utf-8"), request
            )
            self._preflight()
            return proposal
        except GenerationUnavailable:
            raise GenerationUnavailable("Classification service unavailable") from None
        except GenerationTimeout:
            raise GenerationTimeout("Classification deadline exceeded") from None
        except GenerationConfigurationError:
            raise GenerationConfigurationError(
                "Classification deployment identity rejected"
            ) from None
        except Exception:
            raise ClassificationError("Classification inference rejected") from None
        finally:
            self._slot.release()
