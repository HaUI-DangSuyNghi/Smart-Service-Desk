"""Verify engineering ledger completeness; independent production approvals stay mandatory."""

import argparse
from collections import Counter
from hashlib import sha256
import json
import math
from pathlib import Path
import statistics

from _bootstrap import ROOT
from local_runtime_support import rule_baseline, strict_json
from local_classification.diagnostics import REASON_CODES
from experiment_policies import POLICIES
from servicedesk.intelligence.triage_ai07.datasets import SOURCE_FILES, inventory


def expected_latency(values):
    values = sorted(values)
    return {
        "count": len(values),
        "p50Ms": round(statistics.median(values), 3) if values else None,
        "p95Ms": round(values[math.ceil(len(values) * 0.95) - 1], 3)
        if values
        else None,
    }


def verify_report(report, runtime_path):
    audit = inventory(ROOT)
    raw = (ROOT / SOURCE_FILES[1]).read_bytes()
    runtime_raw = Path(runtime_path).read_bytes()
    runtime = strict_json(runtime_raw, 262144)
    identity = report["runtimeIdentity"]
    if (
        report["contractVersion"]
        not in ("local-model-engineering@0.1.0", "local-model-engineering@0.2.0")
        or report["scope"] != "SYNTHETIC_DRAFT_NO_QUALITY_CLAIM"
        or report["split"] != "DEV"
        or report["officialQualityClaim"] is not False
        or report["operationalAuthority"] is not False
        or report["promotion"] != "BLOCKED_PENDING_REVIEW"
        or report["sources"] != audit
        or report["datasetDigest"] != sha256(raw).hexdigest()
        or identity["runtimeDigest"] != sha256(runtime_raw).hexdigest()
        or identity["candidates"] != runtime["candidates"]
        or report["deployment"]["ollamaImageRef"] != runtime["ollamaImage"]
    ):
        raise ValueError("Engineering source/runtime identity differs")

    cases = {}
    baseline_cases = []
    for line in raw.decode("utf-8").splitlines():
        item = json.loads(line)
        if item["split"] == "DEV":
            fields = {"title": item["title"], "description": item["description"]}
            baseline_cases.append({"caseId": item["id"], **fields})
            cases[item["id"]] = sha256(
                json.dumps(
                    fields, ensure_ascii=True, sort_keys=True, separators=(",", ":")
                ).encode()
            ).hexdigest()

    baseline = rule_baseline(baseline_cases)
    if (
        report["authoritativeRules"] != baseline
        or report["ruleProfile"] != "service-desk@0.3.0"
    ):
        raise ValueError("Authoritative rule snapshots differ")

    if (
        report["caseIds"] != list(cases)
        or type(report["repeats"]) is not int
        or not 1 <= report["repeats"] <= 3
    ):
        raise ValueError("Engineering split/cohort differs")

    candidates = tuple(report["summaries"])
    if not candidates or any(
        candidate not in POLICIES or candidate not in runtime["candidates"]
        for candidate in candidates
    ):
        raise ValueError("Unexpected comparison candidate")

    expected = Counter(
        (candidate, case, repeat)
        for candidate in candidates
        for case in cases
        for repeat in range(report["repeats"])
    )
    actual = Counter(
        (item["candidate"], item["caseId"], item["repeat"])
        for item in report["attempts"]
    )
    if actual != expected:
        raise ValueError("Incomplete or duplicated attempt denominator")

    warmups_per_load = report["warmupsPerLoad"]
    if type(warmups_per_load) is not int or not 0 <= warmups_per_load <= 10:
        raise ValueError("Invalid warmup count")

    expected_warmups = []
    previous = None
    for repeat in range(report["repeats"]):
        order = candidates if repeat % 2 == 0 else tuple(reversed(candidates))
        for candidate in order:
            if previous != candidate:
                expected_warmups.extend(
                    (candidate, next(iter(cases)), number)
                    for number in range(warmups_per_load)
                )
            previous = candidate

    actual_warmups = [
        (item["candidate"], item["caseId"], item["repeat"])
        for item in report["warmupAttempts"]
    ]
    if actual_warmups != expected_warmups:
        raise ValueError("Warmup ledger differs from model-load schedule")

    states = {"PROPOSE", "ABSTAIN", "TIMEOUT", "UNAVAILABLE", "INVALID", "REJECTED"}
    phased_attempts = [
        (item, phase)
        for phase, items in (
            ("DEV_ENGINEERING", report["attempts"]),
            ("WARMUP", report["warmupAttempts"]),
        )
        for item in items
    ]
    for item, phase in phased_attempts:
        if (
            item["candidate"] not in candidates
            or type(item["repeat"]) is not int
            or item["phase"] != phase
            or item["caseId"] not in cases
            or item["inputDigest"] != cases[item["caseId"]]
            or item["state"] not in states
            or type(item["wallMs"]) not in (int, float)
            or not math.isfinite(item["wallMs"])
            or item["wallMs"] < 0
            or item["authoritativeRuleDigest"]
            != baseline[item["caseId"]]["responseDigest"]
        ):
            raise ValueError("Invalid attempt receipt")

        if item["state"] not in ("PROPOSE", "ABSTAIN") and (
            report["contractVersion"] == "local-model-engineering@0.2.0"
            or "reasonCode" in item
        ):
            reason = item.get("reasonCode")
            if type(reason) is not str or reason not in REASON_CODES:
                raise ValueError("Failure receipt requires a bounded reason code")

        if "nativeElapsedMs" in item and (
            report["contractVersion"] == "local-model-engineering@0.1.0"
            and item["state"] not in ("PROPOSE", "ABSTAIN")
            or type(item["nativeElapsedMs"]) not in (int, float)
            or not math.isfinite(item["nativeElapsedMs"])
            or item["nativeElapsedMs"] < 0
        ):
            raise ValueError("Invalid native latency receipt")

        if item["state"] in ("PROPOSE", "ABSTAIN"):
            filename, adapter = POLICIES[item["candidate"]]
            policy_file = (
                ROOT
                / "intelligence/src/apps/servicedesk/intelligence/triage_ai07/experiments"
                / filename
            )
            candidate = runtime["candidates"][item["candidate"]]
            if (
                sha256(policy_file.read_bytes()).hexdigest()
                != candidate["policyDigest"]
                or candidate.get("adapter", adapter) != adapter
                or candidate.get("policyFile", filename) != filename
            ):
                raise ValueError("Application policy identity changed")
            allowed = json.loads(policy_file.read_bytes())["allowedClasses"]
            if (
                item["state"] == "PROPOSE"
                and item["classId"] not in allowed
                or item["state"] == "ABSTAIN"
                and item["classId"] is not None
            ):
                raise ValueError("Invalid candidate class receipt")
            if item["candidate"] == "gemma3-g1" and (
                item.get("evidenceStatus") != "UNVERIFIED_BY_MODEL"
                or item.get("scoreSemantics") != "NOT_AVAILABLE"
                or item.get("spanCount") != 0
            ):
                raise ValueError(
                    "Classification-only receipt cannot claim evidence or scores"
                )
            if item["placement"]["context_length"] != 2048:
                raise ValueError("Actual context differs")
            if identity["gpuRequired"] and item["placement"]["size_vram"] <= 0:
                raise ValueError("GPU-required attempt lacks GPU placement")

    failures = 0
    for candidate, summary in report["summaries"].items():
        attempts = [
            item for item in report["attempts"] if item["candidate"] == candidate
        ]
        counts = dict(Counter(item["state"] for item in attempts))
        if (
            summary["attempts"] != len(attempts)
            or summary["states"] != counts
            or summary["macroF1"] is not None
            or summary["calibratedAccuracy"] is not None
            or summary["allAttemptWallLatency"]
            != expected_latency([item["wallMs"] for item in attempts])
            or summary["validatedNativeLatency"]
            != expected_latency(
                [
                    item["nativeElapsedMs"]
                    for item in attempts
                    if "nativeElapsedMs" in item
                    and item["state"] in ("PROPOSE", "ABSTAIN")
                ]
            )
        ):
            raise ValueError("Summary differs from complete attempt ledger")
        failures += sum(
            number
            for state, number in counts.items()
            if state not in ("PROPOSE", "ABSTAIN")
        )

    return {
        "engineeringLedger": "COMPLETE" if failures == 0 else "COMPLETE_WITH_FAILURES",
        "failedAttempts": failures,
        "classificationQuality": "NOT_EVALUATED",
        "production": "NO_GO",
        "blockers": [
            "REVIEWED_TRAIN_DEV_LABELS",
            "DATA_RIGHTS",
            "INDEPENDENT_HOLDOUT",
            "MODEL_LICENSE_REVIEW",
            "HOSTED_CI",
            "INDEPENDENT_SECURITY_RELEASE_REVIEW",
        ],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reports", nargs="+", type=Path, required=True)
    parser.add_argument("--runtime-config", type=Path, required=True)
    parser.add_argument("--require-production", action="store_true")
    args = parser.parse_args()
    results = []

    for path in args.reports:
        if path.stat().st_size > 4 * 1024 * 1024:
            raise ValueError("Engineering report exceeds its limit")
        results.append(
            verify_report(
                strict_json(path.read_bytes(), 4 * 1024 * 1024), args.runtime_config
            )
        )

    print(json.dumps(results, sort_keys=True))
    if args.require_production:
        raise SystemExit(2)


if __name__ == "__main__":
    main()
