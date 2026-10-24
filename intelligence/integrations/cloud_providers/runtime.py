from dataclasses import asdict
from hashlib import sha256
from time import perf_counter

from hdpi.contracts.loader import load_validator
from hdpi.contracts.validator import ContractValidator
from hdpi.generation.errors import (
    GenerationConfigurationError,
    GenerationContractError,
    GenerationTimeout,
    GenerationUnavailable,
)
from hdpi.profiles.registry import ProfileRegistry
from hdpi.services.assistance_runtime import AssistanceRuntime

from .contracts import CloudFailure, require
from .credentials import ServerCredential
from .http_client import BoundedHTTPSClient
from .providers import GeminiSelectionProvider, GroqSelectionProvider
from .quotas import QuotaGovernor


class AttemptProvider:
    """Per-request capture; no shared last-error state and no alternate provider."""

    def __init__(self, provider, governor):
        self.provider = provider
        self.governor = governor
        self.name = provider.name
        self.version = provider.version
        self.config_version = provider.config_version
        self.runtime_identity = provider.runtime_identity
        self.failure = None
        self.calls = 0

    def generate(self, request):
        try:
            self.governor.reserve(self.provider.route)
            self.calls += 1
            return self.provider.generate(request)
        except CloudFailure as failure:
            self.failure = failure
            self.governor.feedback(self.provider.route, failure)
            if failure.code == "GENERATION_TIMEOUT":
                raise GenerationTimeout("Cloud deadline exceeded") from None
            if failure.state in {"DISABLED", "REVOKED"}:
                raise GenerationConfigurationError(
                    "Pinned cloud route failed"
                ) from None
            raise GenerationUnavailable("Pinned cloud route unavailable") from None


class CloudAssistanceRuntime:
    def __init__(
        self,
        registry,
        *,
        client=None,
        environment=None,
        profiles=None,
        governor=None,
        observer=None
    ):
        self.registry = registry
        self.profiles = profiles or ProfileRegistry()
        self.governor = governor or QuotaGovernor()
        self.observer = observer
        self.client = client or BoundedHTTPSClient()
        # Unit receipts can exercise injected transports, never real sockets.
        if isinstance(self.client, BoundedHTTPSClient):
            require(not registry.approval["owner"].startswith("TEST_ONLY"))
        self.providers = {}
        self.request_contract = load_validator("assistance-cloud-request.schema.json")
        self.response_contract = load_validator("assistance-cloud-result.schema.json")
        for route in registry.routes.values():
            credential = ServerCredential(route.vendor, environment)
            credential.read()  # Reject missing/ambiguous secrets at startup.
            adapter = (
                GeminiSelectionProvider
                if route.vendor == "gemini"
                else GroqSelectionProvider
            )
            self.providers[route.id] = (adapter, route, credential)

    def assist(self, payload):
        ContractValidator._validate(self.request_contract, payload)
        route = self.registry.bind(payload)
        adapter, selected, credential = self.providers[route.id]
        provider = adapter(selected, client=self.client, credential=credential)
        attempt = AttemptProvider(provider, self.governor)
        engine = AssistanceRuntime(
            provider=attempt,
            policy=self.registry.policy,
            profile_id=self.registry.catalog["profileId"],
            profile_version=self.registry.catalog["profileVersion"],
            profiles=self.profiles,
        )
        request = {
            key: value for key, value in payload.items() if key != "routeBinding"
        }
        request["contractVersion"] = "0.1.0"
        started = perf_counter()

        try:
            response = engine.assist(request)
        except (GenerationConfigurationError, GenerationContractError):
            if attempt.failure is None:
                raise
            response = {
                "contractVersion": "0.1.0",
                "requestId": payload["requestId"],
                "status": "ERROR",
                "answer": None,
                "citations": [],
                "needsReview": True,
                "reasonCode": attempt.failure.code,
                "retrievalRuntime": {
                    "engine": "tfidf",
                    "engine_version": "0.1.0",
                    "config_version": "tfidf-config@0.1.0",
                    "corpus_version": "not-released",
                    "index_version": "not-released",
                },
                "generationRuntime": asdict(route.runtime),
            }

        elapsed = (perf_counter() - started) * 1000
        bucket = (
            "LT_100MS"
            if elapsed < 100
            else "LT_1S" if elapsed < 1000 else "LT_5S" if elapsed < 5000 else "GE_5S"
        )
        failure = attempt.failure
        if failure:
            response["reasonCode"] = failure.code
        response.update(
            contractVersion="0.2.0",
            routeBinding=payload["routeBinding"],
            routeFeedback={
                "state": failure.state if failure else "READY",
                "code": failure.code if failure else "OK",
                "cooldownSeconds": failure.cooldown if failure else 0,
                "providerCalls": attempt.calls,
                "latencyBucket": bucket,
            },
        )
        ContractValidator._validate(self.response_contract, response)

        if self.observer:
            event = {
                "event": "provider_attempt",
                "provider": route.vendor,
                "routeDigest": route.digest,
                "requestDigest": sha256(payload["requestId"].encode()).hexdigest(),
                "state": response["routeFeedback"]["state"],
                "code": response["routeFeedback"]["code"],
                "attemptSlotsReserved": attempt.calls,
                "latencyBucket": bucket,
                "observedModelDigest": provider.observed_model_digest,
                "inputTokens": provider.input_tokens,
                "outputTokens": provider.output_tokens,
                "usageCost": "NOT_VERIFIED",
            }

            try:
                self.observer(event)
            except Exception:
                pass  # A content-free metrics sink cannot change assistance.

        return response
