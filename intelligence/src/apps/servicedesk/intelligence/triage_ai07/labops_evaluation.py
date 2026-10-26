"""Full-denominator weak-reference comparison. Silver agreement is not accuracy."""

from collections import Counter
from hashlib import sha256
import math
import statistics

from hdpi.classification.protocols import require
from .dataset_profiles import bounded_json


STATES = {"PROPOSE", "ABSTAIN", "INVALID", "TIMEOUT", "UNAVAILABLE", "REJECTED"}


def timing(values):
    values = sorted(values)
    return {
        "count": len(values),
        "p50Ms": statistics.median(values) if values else None,
        "p95Ms": values[math.ceil(len(values) * 0.95) - 1] if values else None,
    }


def summarize(cases, attempts, *, repeats):
    expected = Counter(
        (case.case_id, repeat) for case in cases for repeat in range(repeats)
    )
    require(
        Counter((item["caseId"], item["repeatIndex"]) for item in attempts) == expected
    )
    by_id = {case.case_id: case for case in cases}
    agreed = accepted = labelled = accepted_on_abstain = 0
    for item in attempts:
        case = by_id[item["caseId"]]
        require(item["inputDigest"] == case.input_digest and item["state"] in STATES)
        require(
            type(item["wallLatencyMs"]) in (int, float)
            and math.isfinite(item["wallLatencyMs"])
            and item["wallLatencyMs"] >= 0
        )
        if item["state"] == "PROPOSE":
            require(
                item["proposedClassOrNull"]
                in ("ACCOUNT", "HARDWARE", "NETWORK", "OTHER", "SOFTWARE")
            )
            accepted += 1
            accepted_on_abstain += case.decision == "ABSTAIN"
        else:
            require(item["proposedClassOrNull"] is None)
        if case.decision == "PROPOSE":
            labelled += 1
            agreed += (
                item["state"] == "PROPOSE"
                and item["proposedClassOrNull"] == case.class_id
            )
    return {
        "uniqueCases": len(cases),
        "repetitions": repeats,
        "attempts": len(attempts),
        "states": dict(sorted(Counter(item["state"] for item in attempts).items())),
        "proxyAgreement": {
            "agreed": agreed,
            "silverLabelledAttempts": labelled,
            "rate": agreed / labelled if labelled else None,
        },
        "proposalsOnSilverAbstain": accepted_on_abstain,
        "proposalCoverage": accepted / len(attempts) if attempts else None,
        "allAttemptWallLatency": timing([item["wallLatencyMs"] for item in attempts]),
        "allAttemptNativeLatency": timing(
            [
                item["nativeLatencyMsOrNull"]
                for item in attempts
                if item.get("nativeLatencyMsOrNull") is not None
            ]
        ),
        "goldMacroF1": None,
        "officialQualityClaim": False,
        "qualityStatus": "PROXY_SILVER_ONLY",
    }


def report_for(dataset, *, split, candidate_id, attempts, repeats, identity):
    cases = dataset.partition(split)
    return {
        "schemaVersion": "ai07-labops-engineering@0.1.0",
        "datasetProfile": dataset.identity,
        "datasetDigest": dataset.dataset_digest,
        "split": split,
        "mode": "ENGINEERING_CHALLENGE_ONLY"
        if split == "CHALLENGE"
        else "ENGINEERING_SILVER",
        "candidateId": candidate_id,
        "identity": identity,
        "attempts": attempts,
        "summary": summarize(cases, attempts, repeats=repeats),
        "goldEligible": False,
        "rights": "PENDING_OWNER_CONFIRMATION",
        "officialQualityClaim": False,
        "selectedModel": None,
        "promotion": "NO_GO",
    }


def verify_report(dataset, report, *, runtime_raw=None):
    require(report["schemaVersion"] == "ai07-labops-engineering@0.1.0")
    require(
        report["datasetDigest"] == dataset.dataset_digest
        and report["datasetProfile"] == dataset.identity
    )
    require(report["goldEligible"] is False and report["officialQualityClaim"] is False)
    require(report["promotion"] == "NO_GO" and report["selectedModel"] is None)
    require(report["rights"] == "PENDING_OWNER_CONFIRMATION")
    identity = report["identity"]
    if report["candidateId"] in ("cpu-lexical", "cpu-embedding"):
        require(
            identity["role"] == "NEVER_PROMOTABLE"
            and identity["scope"] == "UNREVIEWED_SILVER_POC"
        )
        require(identity["datasetDigest"] == dataset.dataset_digest)
        require(
            identity["officialQualityClaim"] is False
            and identity["promotion"] == "NO_GO"
        )
    else:
        require(runtime_raw is not None)
        runtime = bounded_json(runtime_raw)
        require(
            runtime["mode"] == "ENGINEERING_LAB_ONLY"
            and runtime["engineeringDatasetDigest"] == dataset.dataset_digest
        )
        require(report["candidateId"] in runtime["candidates"])
        expected_identity = {
            **runtime["candidates"][report["candidateId"]],
            "bridgeRevision": runtime["bridgeRevision"],
            "providerVersion": runtime["ollamaVersion"],
            "runtimeDigest": sha256(runtime_raw).hexdigest(),
        }
        require(identity == expected_identity)
    require(
        report["mode"]
        == (
            "ENGINEERING_CHALLENGE_ONLY"
            if report["split"] == "CHALLENGE"
            else "ENGINEERING_SILVER"
        )
    )
    repeats = report["summary"]["repetitions"]
    require(type(repeats) is int and 1 <= repeats <= 3)
    for item in report["attempts"]:
        if item["state"] in ("INVALID", "TIMEOUT", "UNAVAILABLE", "REJECTED"):
            require(
                item["reasonCodeOrNull"]
                in {
                    "INVALID_JSON",
                    "INVALID_STATE_PAIR",
                    "INVALID_QUOTE_MISSING",
                    "INVALID_QUOTE_REPEATED",
                    "INVALID_CLASS",
                    "SCHEMA_MISMATCH",
                    "TRUNCATED",
                    "RUNTIME_IDENTITY",
                    "TRANSPORT",
                    "TIMEOUT",
                    "UNAVAILABLE",
                }
            )
        else:
            require(item["reasonCodeOrNull"] is None)
        require(item["goldEligible"] is False)
        if item.get("nativeLatencyMsOrNull") is not None:
            require(
                type(item["nativeLatencyMsOrNull"]) in (int, float)
                and math.isfinite(item["nativeLatencyMsOrNull"])
                and item["nativeLatencyMsOrNull"] >= 0
            )
    require(
        report["summary"]
        == summarize(
            dataset.partition(report["split"]), report["attempts"], repeats=repeats
        )
    )
    return {
        "engineeringLedger": "PASS",
        "officialQuality": "NOT_EVALUATED",
        "production": "NO_GO",
    }
