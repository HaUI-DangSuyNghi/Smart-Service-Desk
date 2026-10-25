"""App-owned dataset/scope authorization around the existing governed runtime."""

from copy import deepcopy
from dataclasses import asdict
from datetime import datetime, timezone
import os
from pathlib import Path
from math import ceil
from time import perf_counter, sleep
from uuid import uuid4

from _contracts import ROOT, DatasetError, digest, read_json, require, schema_check
from _dataset import sample_smoke
from _evaluation import evaluate_cases, implementation_identity, PROFILES
from _validation import validate_bundle


APPROVAL_FIELDS = {
    "schemaVersion",
    "status",
    "owner",
    "expiresAt",
    "dataClass",
    "datasetDigest",
    "manifestDigest",
    "scope",
    "provider",
    "model",
    "providerCatalogDigest",
    "maxRequests",
    "maxInputTokens",
    "maxOutputTokens",
    "maxCostMicros",
    "freeOnly",
}


def authorize_dataset(args, bundle, *, environment=None, now=None):
    environment = os.environ if environment is None else environment
    require(args.allow_external_egress is True, "EXTERNAL_EGRESS_NOT_ENABLED")
    require(args.data_class == "SYNTHETIC_DEV_ONLY", "DATA_CLASS_NOT_AUTHORIZED")
    require(
        environment.get("CLOUD_ENVIRONMENT") == "isolated-synthetic",
        "ENVIRONMENT_NOT_ISOLATED",
    )
    require(
        args.approval_file is not None and bool(args.approval_sha256),
        "DATASET_APPROVAL_REQUIRED",
    )
    path = Path(args.approval_file)
    require(
        not path.is_symlink() and not path.resolve().is_relative_to(ROOT),
        "APPROVAL_MUST_BE_EXTERNAL",
    )
    require(path.is_file() and 0 < path.stat().st_size <= 131072, "APPROVAL_SIZE")
    require(
        os.name == "nt" or path.stat().st_mode & 0o022 == 0,
        "APPROVAL_WRITABLE_BY_OTHERS",
    )
    raw = path.read_bytes()
    require(digest(raw) == args.approval_sha256, "APPROVAL_DIGEST")
    approval = read_json(path)
    require(
        type(approval) is dict and set(approval) == APPROVAL_FIELDS, "APPROVAL_SHAPE"
    )
    schema_check("cloud-approval", approval)
    require(
        approval["schemaVersion"] == "synthetic-dataset-egress@0.2.0"
        and approval["status"] == "APPROVED",
        "APPROVAL_NOT_ISSUED",
    )
    require(
        type(approval["owner"]) is str and bool(approval["owner"].strip()),
        "APPROVAL_OWNER",
    )
    try:
        expiry = datetime.fromisoformat(approval["expiresAt"].replace("Z", "+00:00"))
    except (ValueError, TypeError, AttributeError):
        raise DatasetError("APPROVAL_EXPIRY") from None
    require(
        expiry.tzinfo is not None and (now or datetime.now(timezone.utc)) < expiry,
        "APPROVAL_EXPIRED",
    )
    require(
        approval["dataClass"] == args.data_class and approval["scope"] == args.scope,
        "APPROVAL_SCOPE",
    )
    require(
        approval["provider"] == args.provider and args.provider in {"gemini", "groq"},
        "PROVIDER_NOT_AUTHORIZED",
    )
    require(
        approval["datasetDigest"] == digest(bundle["dataset"])
        and approval["manifestDigest"] == digest(bundle["manifest"]),
        "DATASET_NOT_APPROVED",
    )
    require(
        approval["providerCatalogDigest"] == environment.get("PROVIDER_CATALOG_SHA256"),
        "CATALOG_NOT_APPROVED",
    )
    require(
        approval["freeOnly"] is True
        and type(approval["maxCostMicros"]) is int
        and approval["maxCostMicros"] == 0
        and args.max_cost == "0",
        "PAID_USAGE_NOT_AUTHORIZED",
    )

    for argument, field in (
        ("max_requests", "maxRequests"),
        ("max_input_tokens", "maxInputTokens"),
        ("max_output_tokens", "maxOutputTokens"),
    ):
        limit = getattr(args, argument)
        require(
            type(limit) is int
            and type(approval[field]) is int
            and 0 < limit <= approval[field],
            "RUN_BUDGET_NOT_AUTHORIZED",
        )

    return approval


def request_payload(runtime, route, case):
    return {
        "contractVersion": "0.2.0",
        "requestId": str(uuid4()),
        "workspaceId": "synthetic-dataset-evaluation",
        "profileId": runtime.registry.catalog["profileId"],
        "profileVersion": runtime.registry.catalog["profileVersion"],
        "policyIdentity": runtime.registry.policy.identity,
        "question": case["question"],
        "documents": case["documents"],
        "routeBinding": {
            "routeId": route.id,
            "policyId": runtime.registry.catalog["policyId"],
            "dataClass": "synthetic-public-evaluation",
            "catalogDigest": runtime.registry.catalog_digest,
            "routeDigest": route.digest,
            "approvalDigest": runtime.registry.approval_digest,
            "attemptId": str(uuid4()),
            "expectedRuntime": asdict(route.runtime),
        },
    }


def provider_implementation_digest():
    import cloud_providers

    package = Path(cloud_providers.__file__).resolve().parent
    return digest(
        {
            path.relative_to(package).as_posix(): digest(path.read_bytes())
            for path in sorted(package.rglob("*.py"))
        }
    )


def build_cloud_runtime(args, *, installed):
    if not installed:
        import sys

        sys.path.insert(0, str(ROOT / "intelligence/integrations"))

    from cloud_providers.http_client import BoundedHTTPSClient
    from cloud_providers.registry import ProviderRegistry
    from cloud_providers.runtime import CloudAssistanceRuntime
    from hdpi.profiles.registry import ProfileRegistry

    require(
        os.environ.get("PROVIDER_MODE") == "synthetic-cloud-dev",
        "PROVIDER_MODE_DISABLED",
    )
    registry = ProviderRegistry(
        os.environ["PROVIDER_CATALOG_PATH"],
        os.environ["PROVIDER_CATALOG_SHA256"],
        os.environ["CLOUD_EGRESS_APPROVAL_FILE"],
        os.environ["CLOUD_EGRESS_APPROVAL_SHA256"],
        os.environ["CLOUD_ISOLATION_FILE"],
        os.environ.get("CLOUD_ENVIRONMENT"),
        repository_root=ROOT,
    )
    routes = [
        route for route in registry.routes.values() if route.vendor == args.provider
    ]
    require(len(routes) == 1, "EXACTLY_ONE_APPROVED_ROUTE_REQUIRED")
    runtime = CloudAssistanceRuntime(
        registry,
        client=BoundedHTTPSClient(proxy=os.environ.get("CLOUD_EGRESS_PROXY")),
        profiles=ProfileRegistry(PROFILES),
    )
    return runtime, routes[0]


def run_cloud(args, bundle, inputs=None, *, runtime_factory=None, environment=None):
    validate_bundle(bundle, inputs)
    approval = authorize_dataset(args, bundle, environment=environment)
    interval = getattr(args, "minimum_interval_seconds", 0)
    require(type(interval) is int and 0 <= interval <= 60, "INVALID_REQUEST_INTERVAL")
    factory = runtime_factory or build_cloud_runtime

    if runtime_factory is None:
        require(
            not approval["owner"].startswith("TEST_ONLY"),
            "TEST_RECEIPT_CANNOT_ENABLE_NETWORK",
        )

    runtime, route = factory(args, installed=args.installed)
    require(
        route.vendor == args.provider and route.model == approval["model"],
        "MODEL_NOT_AUTHORIZED",
    )
    require(
        route.catalog_digest == approval["providerCatalogDigest"], "ROUTE_CATALOG_DRIFT"
    )
    events = []
    runtime.observer = events.append
    selected = deepcopy(bundle)

    if args.scope == "smoke":
        chosen = set(sample_smoke(bundle["dataset"], bundle["curation"])["caseIds"])
        selected["dataset"]["cases"] = [
            case for case in selected["dataset"]["cases"] if case["caseId"] in chosen
        ]

    counters = {
        "executedCases": 0,
        "requestSlotsReserved": 0,
        "providerAttemptSlots": 0,
        "confirmedStructuredResponses": 0,
        "notExecutedCases": 0,
        "rateLimitedCases": 0,
        "timeoutCases": 0,
        "reservedInputTokens": 0,
        "reservedOutputTokens": 0,
    }
    stop = None
    feedback_codes = {}
    latencies = []
    previous_started = None

    def execute(case):
        nonlocal stop, previous_started

        if stop:
            counters["notExecutedCases"] += 1
            raise DatasetError("CASE_NOT_EXECUTED")

        # Pace distinct planned cases only. A failed call is never retried, and
        # approval is rechecked after waiting, immediately before the next case.
        if interval and previous_started is not None:
            remaining = interval - (perf_counter() - previous_started)

            if remaining > 0:
                sleep(remaining)

        # Recheck the external receipt on every case; changed/expired approvals
        # suppress later egress instead of reusing a startup-only decision.
        try:
            authorize_dataset(args, bundle, environment=environment)
        except Exception:
            stop = "PARTIAL_AUTHORIZATION_REVOKED"
            counters["notExecutedCases"] += 1
            raise DatasetError("CASE_NOT_EXECUTED") from None
        input_reservation = route.max_input_bytes + 1024
        output_reservation = route.max_output_tokens

        if (
            counters["requestSlotsReserved"] >= args.max_requests
            or counters["reservedInputTokens"] + input_reservation
            > args.max_input_tokens
            or counters["reservedOutputTokens"] + output_reservation
            > args.max_output_tokens
        ):
            stop = "PARTIAL_LOCAL_BUDGET_EXHAUSTED"
            counters["notExecutedCases"] += 1
            raise DatasetError("CASE_NOT_EXECUTED")

        counters["reservedInputTokens"] += input_reservation
        counters["reservedOutputTokens"] += output_reservation
        counters["requestSlotsReserved"] += 1
        counters["executedCases"] += 1

        started = perf_counter()
        previous_started = started

        try:
            response = runtime.assist(request_payload(runtime, route, case))
        except Exception:
            stop = "PARTIAL_RUNTIME_UNAVAILABLE"
            raise DatasetError("RUNTIME_UNAVAILABLE") from None
        finally:
            latencies.append(round((perf_counter() - started) * 1000))

        feedback = response["routeFeedback"]
        feedback_codes[case["caseId"]] = feedback["code"]
        counters["providerAttemptSlots"] += feedback["providerCalls"]

        if response["generationRuntime"] != asdict(route.runtime):
            stop = "FAILED_RUNTIME_IDENTITY"
            raise DatasetError("RUNTIME_IDENTITY_DRIFT")

        if feedback["state"] == "RATE_LIMITED":
            counters["rateLimitedCases"] += 1
            stop = "PARTIAL_QUOTA_EXHAUSTED"
        elif feedback["state"] != "READY":
            counters["timeoutCases"] += int(feedback["code"] == "GENERATION_TIMEOUT")
            stop = (
                "PARTIAL_PROVIDER_UNAVAILABLE"
                if feedback["state"] == "UNAVAILABLE"
                else "FAILED_PROVIDER_CONTRACT"
            )

        response = {
            key: value
            for key, value in response.items()
            if key not in {"routeBinding", "routeFeedback"}
        }
        response["contractVersion"] = "0.1.0"
        return response

    report = evaluate_cases(selected, execute)
    counters["confirmedStructuredResponses"] = sum(
        event.get("observedModelDigest") is not None for event in events
    )
    complete = (
        counters["notExecutedCases"] == 0
        and report["technicalFailures"] == 0
        and stop is None
    )
    report.update(implementation_identity())
    report["providerImplementationDigest"] = provider_implementation_digest()
    report.update(counters)
    ordered_latencies = sorted(latencies)
    reported_usage = [
        event
        for event in events
        if type(event.get("inputTokens")) is int
        and type(event.get("outputTokens")) is int
    ]
    report["caseRuntimeLatencyMs"] = {
        "p50": (
            ordered_latencies[ceil(len(ordered_latencies) * 0.50) - 1]
            if ordered_latencies
            else None
        ),
        "p95": (
            ordered_latencies[ceil(len(ordered_latencies) * 0.95) - 1]
            if ordered_latencies
            else None
        ),
        "sampleCount": len(ordered_latencies),
        "timingClass": (
            "REMOTE_CASE_RUNTIME"
            if runtime_factory is None
            else "OFFLINE_CONTRACT_RUNTIME"
        ),
    }
    report["reportedTokenUsage"] = {
        "inputTokens": (
            sum(event["inputTokens"] for event in reported_usage)
            if reported_usage
            else None
        ),
        "outputTokens": (
            sum(event["outputTokens"] for event in reported_usage)
            if reported_usage
            else None
        ),
        "eventsWithUsage": len(reported_usage),
        "eventsWithUnknownUsage": len(events) - len(reported_usage),
        "cost": "NOT_RECONCILED_WITH_VENDOR_CONSOLE",
    }
    report.update(
        {
            "evidenceClass": "SYNTHETIC_DEV_ONLY",
            "datasetDigest": digest(bundle["dataset"]),
            "selectionDigest": digest(
                [case["caseId"] for case in selected["dataset"]["cases"]]
            ),
            "provider": route.vendor,
            "model": route.model,
            "modelRevision": "vendor-unpublished",
            "configurationDigest": route.catalog_digest,
            "approvalDigest": args.approval_sha256,
            "scope": args.scope,
            "minimumIntervalSeconds": interval,
            "totalPlannedCases": len(selected["dataset"]["cases"]),
            "result": (
                (
                    "CLOUD_DEV_VERIFIED"
                    if runtime_factory is None
                    else "OFFLINE_CLOUD_CONTRACT_VERIFIED"
                )
                if complete
                else stop or "FAILED_TECHNICAL_CHECKS"
            ),
            "realModelValidated": runtime_factory is None
            and complete
            and counters["confirmedStructuredResponses"] > 0,
            "realProviderRequests": "NOT_RECONCILED_WITH_VENDOR_CONSOLE",
            "providerUsageAndLatency": "SEE_CONTENT_FREE_ATTEMPT_EVENTS",
            "canaryEgressObserved": "NOT_MEASURED_SEPARATELY",
            "providerEvents": events,
            "feedbackCodes": feedback_codes,
            "officialDecision": "BLOCKED",
            "humanReview": "PENDING",
        }
    )
    return report
