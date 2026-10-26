"""Deterministic, content-free DEV diagnostics with unique-case denominators."""

from collections import Counter, defaultdict
from hashlib import sha256
import json
import math

from _bootstrap import ROOT
from experiment_policies import POLICIES
from local_runtime_support import strict_json
from local_classification.diagnostics import REASON_CODES
from servicedesk.intelligence.triage_ai07.datasets import SOURCE_FILES


VALID_STATES = frozenset({"PROPOSE", "ABSTAIN"})
STATES = VALID_STATES | {"INVALID", "TIMEOUT", "UNAVAILABLE", "REJECTED"}
LEGACY_MISSING = "MISSING_LEGACY_DIAGNOSTIC"
EXPERIMENTS = (
    ROOT / "intelligence/src/apps/servicedesk/intelligence/triage_ai07/experiments"
)


def wilson(successes, total):
    """95% interval for a case-level binary event, never independent repeats."""
    if total == 0:
        return None

    z = 1.959963984540054
    rate = successes / total
    denominator = 1 + z * z / total
    center = (rate + z * z / (2 * total)) / denominator
    margin = (
        z
        * math.sqrt(rate * (1 - rate) / total + z * z / (4 * total * total))
        / denominator
    )
    return [round(max(0, center - margin), 6), round(min(1, center + margin), 6)]


def dev_metadata():
    raw = (ROOT / SOURCE_FILES[1]).read_bytes()
    rows = {}

    for line in raw.decode("utf-8").splitlines():
        item = json.loads(line)

        if item["split"] == "DEV":
            rows[item["id"]] = {
                key: item[key] for key in ("scenario", "scenarioFamily")
            }

    return rows, sha256(raw).hexdigest()


def checked_attempts(report):
    """Reject incomplete cohorts and unknown IDs before emitting any identifiers."""
    version = report["contractVersion"]
    if (
        version
        not in ("local-model-engineering@0.1.0", "local-model-engineering@0.2.0")
        or report["split"] != "DEV"
        or report["scope"] != "SYNTHETIC_DRAFT_NO_QUALITY_CLAIM"
        or report["officialQualityClaim"] is not False
        or report["operationalAuthority"] is not False
        or report["promotion"] != "BLOCKED_PENDING_REVIEW"
    ):
        raise ValueError("Only non-authoritative synthetic DEV ledgers are accepted")

    metadata, dataset_digest = dev_metadata()
    cases, repeats = report["caseIds"], report["repeats"]
    if (
        report["datasetDigest"] != dataset_digest
        or type(cases) is not list
        or not cases
        or any(type(case) is not str or case not in metadata for case in cases)
        or len(cases) != len(set(cases))
        or type(repeats) is not int
        or not 1 <= repeats <= 3
    ):
        raise ValueError("DEV dataset/cohort identity differs")

    candidates = tuple(report["summaries"])
    if not candidates or any(candidate not in POLICIES for candidate in candidates):
        raise ValueError("Unknown study candidate")

    allowed_classes = {
        name: json.loads((EXPERIMENTS / POLICIES[name][0]).read_bytes())[
            "allowedClasses"
        ]
        for name in candidates
    }
    expected = Counter(
        (name, case, repeat)
        for name in candidates
        for case in cases
        for repeat in range(repeats)
    )
    actual = Counter()

    for phase, attempts in (
        ("DEV_ENGINEERING", report["attempts"]),
        ("WARMUP", report["warmupAttempts"]),
    ):
        for item in attempts:
            name, case, repeat = item["candidate"], item["caseId"], item["repeat"]
            state = item["state"]
            if (
                name not in candidates
                or case not in cases
                or type(repeat) is not int
                or not 0 <= repeat < (repeats if phase == "DEV_ENGINEERING" else 10)
                or item["phase"] != phase
                or state not in STATES
            ):
                raise ValueError("Unexpected attempt identity/state")

            for key in ("wallMs", "nativeElapsedMs"):
                if key in item and (
                    type(item[key]) not in (int, float)
                    or not math.isfinite(item[key])
                    or item[key] < 0
                ):
                    raise ValueError("Invalid attempt timing")

            if "wallMs" not in item:
                raise ValueError("Missing all-attempt wall latency")
            if state == "PROPOSE" and item.get("classId") not in allowed_classes[name]:
                raise ValueError("Unknown class in receipt")
            if state == "ABSTAIN" and item.get("classId") is not None:
                raise ValueError("Invalid abstention state")
            if state not in VALID_STATES and (
                version == "local-model-engineering@0.2.0" or "reasonCode" in item
            ):
                reason = item.get("reasonCode")
                if type(reason) is not str or reason not in REASON_CODES:
                    raise ValueError("Missing or unsafe failure reason")
            if phase == "DEV_ENGINEERING":
                actual[(name, case, repeat)] += 1

    if actual != expected:
        raise ValueError("Incomplete or duplicated measured-attempt denominator")

    warmup_count = report["warmupsPerLoad"]
    if type(warmup_count) is not int or not 0 <= warmup_count <= 10:
        raise ValueError("Invalid warmup resource limit")

    expected_warmups = []
    previous = None
    for repeat in range(repeats):
        order = candidates if repeat % 2 == 0 else tuple(reversed(candidates))
        for candidate in order:
            if candidate != previous:
                expected_warmups.extend(
                    (candidate, cases[0], number) for number in range(warmup_count)
                )
            previous = candidate
    actual_warmups = [
        (item["candidate"], item["caseId"], item["repeat"])
        for item in report["warmupAttempts"]
    ]
    if actual_warmups != expected_warmups:
        raise ValueError("Incomplete or reordered warmup denominator")

    return metadata


def _counts(items):
    return dict(sorted(Counter(items).items()))


def _latency(values):
    values = sorted(values)
    return {
        "count": len(values),
        **{
            f"p{percentile}Ms": round(
                values[math.ceil(len(values) * percentile / 100) - 1], 3
            )
            if values
            else None
            for percentile in (50, 95, 99)
        },
    }


def _case_summary(items):
    outcomes = {(item["state"], item.get("classId")) for item in items}
    disagreement = [
        item["disagreesWithRules"]
        for item in items
        if type(item.get("disagreesWithRules")) is bool
    ]
    return {
        "attempts": len(items),
        "states": _counts(item["state"] for item in items),
        "classDistribution": _counts(
            item["classId"] for item in items if item["state"] == "PROPOSE"
        ),
        "repeatDisagreement": len(outcomes) > 1 if len(items) > 1 else None,
        "ruleComparisons": len(disagreement),
        "modelVsRuleDisagreements": sum(disagreement),
        "failureBuckets": _counts(
            item.get("reasonCode", LEGACY_MISSING)
            for item in items
            if item["state"] not in VALID_STATES
        ),
    }


def _token_totals(attempts):
    result = {}
    for key in ("nativeInputTokens", "nativeOutputTokens", "nativeCachedInputTokens"):
        observed = [
            item[key]
            for item in attempts
            if type(item.get(key)) is int and 0 <= item[key] <= 2048
        ]
        result[key] = {
            "observedAttempts": len(observed),
            "total": sum(observed) if observed else None,
        }
    return result


def analyze_report(report):
    metadata = checked_attempts(report)
    summaries = {}

    for candidate in sorted(report["summaries"]):
        attempts = [
            item for item in report["attempts"] if item["candidate"] == candidate
        ]
        warmups = [
            item for item in report["warmupAttempts"] if item["candidate"] == candidate
        ]
        grouped = defaultdict(list)

        for item in attempts:
            grouped[item["caseId"]].append(item)

        per_case = {case: _case_summary(grouped[case]) for case in sorted(grouped)}
        repeated = [value for value in per_case.values() if value["attempts"] > 1]
        unstable = sum(value["repeatDisagreement"] for value in repeated)
        abstaining = sum("ABSTAIN" in value["states"] for value in per_case.values())
        slices = {}

        for field in ("scenario", "scenarioFamily"):
            slices[field] = {
                label: {
                    "uniqueCases": len({item["caseId"] for item in subset}),
                    **_case_summary(subset),
                }
                for label in sorted({metadata[case][field] for case in grouped})
                if (
                    subset := [
                        item
                        for item in attempts
                        if metadata[item["caseId"]][field] == label
                    ]
                )
            }
            for value in slices[field].values():
                # Different cases in a slice are not repeats of one ticket.
                value.pop("repeatDisagreement")

        summaries[candidate] = {
            **_case_summary(attempts),
            "uniqueCases": len(grouped),
            "repetitionsPerCase": report["repeats"],
            "casesWithAbstention": abstaining,
            "stableAllAbstainCases": sum(
                value["states"].get("ABSTAIN") == value["attempts"]
                for value in per_case.values()
            ),
            "caseAbstentionWilson95": wilson(abstaining, len(grouped)),
            "repeatEligibleCases": len(repeated),
            "repeatDisagreementCases": unstable,
            "repeatDisagreementRate": unstable / len(repeated) if repeated else None,
            "repeatDisagreementWilson95": wilson(unstable, len(repeated)),
            "allAttemptWallLatency": _latency([item["wallMs"] for item in attempts]),
            "allAttemptNativeLatency": _latency(
                [
                    item["nativeElapsedMs"]
                    for item in attempts
                    if "nativeElapsedMs" in item
                ]
            ),
            "validatedNativeLatency": _latency(
                [
                    item["nativeElapsedMs"]
                    for item in attempts
                    if item["state"] in VALID_STATES and "nativeElapsedMs" in item
                ]
            ),
            "loadedBefore": _counts(
                str(item["loadedBefore"])
                for item in attempts
                if type(item.get("loadedBefore")) is bool
            ),
            "stopReasons": _counts(
                item["stopReason"]
                for item in attempts
                if item.get("stopReason") in ("stop", "length")
            ),
            "tokenTotals": _token_totals(attempts),
            "warmups": _case_summary(warmups),
            "perCase": per_case,
            "slices": slices,
        }
        summaries[candidate].pop("repeatDisagreement")

    return {
        "schemaVersion": "ai07-local-diagnostics@0.1.0",
        "datasetDigest": report["datasetDigest"],
        "split": "DEV_DEBUG",
        "uniqueCases": len(report["caseIds"]),
        "measuredAttempts": len(report["attempts"]),
        "warmupAttempts": len(report["warmupAttempts"]),
        "candidates": summaries,
        "intervalUnit": "UNIQUE_CASE_NOT_REPEATED_ATTEMPT",
        "sliceSemantics": "ANALYSIS_ONLY_NOT_GOLD",
        "quality": {"status": "NOT_EVALUATED", "macroF1": None},
        "release": "NO_GO",
        "operationalAuthority": False,
    }


def read_report(path):
    with path.open("rb") as stream:
        raw = stream.read(4 * 1024 * 1024 + 1)

    return strict_json(raw, 4 * 1024 * 1024), sha256(raw).hexdigest()
