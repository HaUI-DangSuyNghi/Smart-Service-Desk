"""Synthetic ledger contract tests. These fixtures are not real model/owner evidence."""

from copy import deepcopy
from hashlib import sha256
import importlib.util
import json
from pathlib import Path
import sys

import pytest


ROOT = Path(__file__).resolve().parents[4]
SCRIPTS = ROOT / "scripts/apps/servicedesk/ai07"
sys.path.insert(0, str(SCRIPTS))
SPEC = importlib.util.spec_from_file_location(
    "ai07_local_release", SCRIPTS / "validate-local-release.py"
)
release = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(release)


@pytest.fixture(scope="module")
def report_fixture(tmp_path_factory):
    from servicedesk.intelligence.triage_ai07.datasets import SOURCE_FILES, inventory
    from local_runtime_support import rule_baseline

    raw = (ROOT / SOURCE_FILES[1]).read_bytes()
    rows = []
    for line in raw.decode("utf-8").splitlines():
        item = json.loads(line)
        if item["split"] == "DEV":
            rows.append(
                {
                    "caseId": item["id"],
                    "title": item["title"],
                    "description": item["description"],
                }
            )
    baseline = rule_baseline(rows)
    policy = (
        ROOT
        / "intelligence/src/apps/servicedesk/intelligence/triage_ai07/experiments/tev1-policy-v0.1.json"
    )
    candidates = {"tev1": {"policyDigest": sha256(policy.read_bytes()).hexdigest()}}
    runtime = {"candidates": candidates, "ollamaImage": "synthetic-pinned-image"}
    runtime_file = tmp_path_factory.mktemp("local-release") / "runtime.json"
    runtime_file.write_bytes(json.dumps(runtime).encode())
    attempts = []
    for row in rows:
        fields = {"title": row["title"], "description": row["description"]}
        attempts.append(
            {
                "candidate": "tev1",
                "caseId": row["caseId"],
                "repeat": 0,
                "phase": "DEV_ENGINEERING",
                "state": "ABSTAIN",
                "classId": None,
                "inputDigest": sha256(
                    json.dumps(
                        fields, ensure_ascii=True, sort_keys=True, separators=(",", ":")
                    ).encode()
                ).hexdigest(),
                "authoritativeRuleDigest": baseline[row["caseId"]]["responseDigest"],
                "wallMs": 10.0,
                "nativeElapsedMs": 5.0,
                "placement": {"context_length": 2048, "size_vram": 0},
            }
        )
    report = {
        "contractVersion": "local-model-engineering@0.1.0",
        "scope": "SYNTHETIC_DRAFT_NO_QUALITY_CLAIM",
        "split": "DEV",
        "officialQualityClaim": False,
        "operationalAuthority": False,
        "promotion": "BLOCKED_PENDING_REVIEW",
        "sources": inventory(ROOT),
        "datasetDigest": sha256(raw).hexdigest(),
        "caseIds": [row["caseId"] for row in rows],
        "repeats": 1,
        "attempts": attempts,
        "warmupsPerLoad": 1,
        "warmupAttempts": [{**attempts[0], "phase": "WARMUP"}],
        "authoritativeRules": baseline,
        "ruleProfile": "service-desk@0.3.0",
        "runtimeIdentity": {
            "runtimeDigest": sha256(runtime_file.read_bytes()).hexdigest(),
            "candidates": candidates,
            "gpuRequired": False,
        },
        "deployment": {"ollamaImageRef": runtime["ollamaImage"]},
        "summaries": {
            "tev1": {
                "attempts": 48,
                "states": {"ABSTAIN": 48},
                "macroF1": None,
                "calibratedAccuracy": None,
                "allAttemptWallLatency": {"count": 48, "p50Ms": 10.0, "p95Ms": 10.0},
                "validatedNativeLatency": {"count": 48, "p50Ms": 5.0, "p95Ms": 5.0},
            }
        },
    }
    return report, runtime_file


def test_complete_draft_ledger_never_produces_production_approval(report_fixture):
    report, runtime_file = report_fixture
    result = release.verify_report(report, runtime_file)
    assert result["engineeringLedger"] == "COMPLETE"
    assert result["classificationQuality"] == "NOT_EVALUATED"
    assert result["production"] == "NO_GO"
    assert "INDEPENDENT_HOLDOUT" in result["blockers"]


@pytest.mark.parametrize(
    "mutation",
    [
        "missing",
        "duplicate",
        "gold",
        "rules",
        "class",
        "latency",
        "nan",
        "missing_warmup",
        "wrong_phase",
        "negative_native",
    ],
)
def test_tampered_ledgers_cannot_hide_failures_or_claim_quality(
    report_fixture, mutation
):
    original, runtime_file = report_fixture
    report = deepcopy(original)
    if mutation == "missing":
        report["attempts"].pop()
    elif mutation == "duplicate":
        report["attempts"].append(deepcopy(report["attempts"][0]))
    elif mutation == "gold":
        report["summaries"]["tev1"]["macroF1"] = 1.0
    elif mutation == "rules":
        next(iter(report["authoritativeRules"].values()))["needsReview"] = (
            "model override"
        )
    elif mutation == "class":
        report["attempts"][0]["state"] = "PROPOSE"
        report["attempts"][0]["classId"] = "FOREIGN"
    elif mutation == "latency":
        report["summaries"]["tev1"]["allAttemptWallLatency"]["p95Ms"] = 1.0
    elif mutation == "nan":
        report["attempts"][0]["wallMs"] = float("nan")
    elif mutation == "missing_warmup":
        report["warmupAttempts"].clear()
    elif mutation == "wrong_phase":
        report["attempts"][0]["phase"] = "WARMUP"
    elif mutation == "negative_native":
        report["attempts"][0]["nativeElapsedMs"] = -5.0
        report["summaries"]["tev1"]["validatedNativeLatency"] = (
            release.expected_latency(
                [item["nativeElapsedMs"] for item in report["attempts"]]
            )
        )
    with pytest.raises(ValueError):
        release.verify_report(report, runtime_file)


def test_diagnostics_count_cases_and_preserve_missing_historical_reasons(
    report_fixture,
):
    from local_error_analysis import analyze_report

    original, _ = report_fixture
    report = deepcopy(original)
    report["repeats"] = 3
    report["attempts"] = [
        {**item, "repeat": repeat}
        for repeat in range(3)
        for item in original["attempts"]
    ]
    report["attempts"][0] = {
        **report["attempts"][0],
        "state": "INVALID",
        "rawCompletion": "PRIVATE_COMPLETION",
        "title": "PRIVATE_TITLE",
    }
    result = analyze_report(report)
    value = result["candidates"]["tev1"]
    assert value["attempts"] == 144
    assert value["uniqueCases"] == 48
    assert value["repeatDisagreementCases"] == 1
    assert value["stableAllAbstainCases"] == 47
    assert value["failureBuckets"] == {"MISSING_LEGACY_DIAGNOSTIC": 1}
    assert result["quality"]["macroF1"] is None
    assert result["release"] == "NO_GO"
    assert "PRIVATE_" not in json.dumps(result)
    assert result == analyze_report(report)


@pytest.mark.parametrize(
    "mutation",
    [
        "missing",
        "duplicate",
        "unknown_id",
        "missing_reason",
        "unsafe_reason",
        "class",
        "abstain_class",
        "latency",
        "authority",
        "holdout",
    ],
)
def test_diagnostics_reject_incomplete_or_unsafe_new_ledgers(report_fixture, mutation):
    from local_error_analysis import analyze_report

    original, _ = report_fixture
    report = deepcopy(original)
    report["contractVersion"] = "local-model-engineering@0.2.0"
    item = report["attempts"][0]
    if mutation == "missing":
        report["attempts"].pop()
    elif mutation == "duplicate":
        report["attempts"].append(deepcopy(item))
    elif mutation == "unknown_id":
        item["caseId"] = "PRODUCTION_CUSTOMER_ID"
    elif mutation == "missing_reason":
        item["state"] = "INVALID"
    elif mutation == "unsafe_reason":
        item.update(state="INVALID", reasonCode="PRIVATE_RAW_OUTPUT")
    elif mutation == "class":
        item.update(state="PROPOSE", classId="FOREIGN")
    elif mutation == "abstain_class":
        item["classId"] = "ACCOUNT"
    elif mutation == "latency":
        item["wallMs"] = float("nan")
    elif mutation == "authority":
        report["operationalAuthority"] = True
    elif mutation == "holdout":
        report["split"] = "HOLDOUT"
    with pytest.raises(ValueError):
        analyze_report(report)


def test_new_release_ledger_requires_failure_reason_including_warmups(report_fixture):
    original, runtime_file = report_fixture
    report = deepcopy(original)
    report["contractVersion"] = "local-model-engineering@0.2.0"
    report["warmupAttempts"][0]["state"] = "INVALID"
    report["warmupAttempts"][0].pop("nativeElapsedMs")
    with pytest.raises(ValueError, match="reason code"):
        release.verify_report(report, runtime_file)
    report["warmupAttempts"][0]["reasonCode"] = "INVALID_JSON"
    assert release.verify_report(report, runtime_file)["production"] == "NO_GO"


def test_t2_policy_changes_only_class_order_and_has_distinct_identity():
    from experiment_policies import POLICIES
    from local_error_analysis import EXPERIMENTS

    baseline = json.loads((EXPERIMENTS / POLICIES["tev1"][0]).read_bytes())
    digests = set()
    for name in ("tev1-t2-rotate", "tev1-t2-reverse", "tev1-t2-interleave"):
        raw = (EXPERIMENTS / POLICIES[name][0]).read_bytes()
        value = json.loads(raw)
        assert set(value["allowedClasses"]) == set(baseline["allowedClasses"])
        assert value["allowedClasses"] != baseline["allowedClasses"]
        assert value["policy"]["instructions"] == baseline["policy"]["instructions"]
        assert value["policy"]["criteria"] == baseline["policy"]["criteria"]
        assert tuple(value["policy"]["criteria"]) == (
            *value["allowedClasses"],
            "UNDETERMINED",
        )
        digests.add(sha256(raw).hexdigest())
    assert len(digests) == 3


def test_worker_failure_keeps_only_bounded_reason_and_telemetry(monkeypatch):
    from types import SimpleNamespace
    import local_runtime_support as support

    failure = {
        "status": 502,
        "reasonCode": "INVALID_STATE_PAIR",
        "diagnostics": {"nativeElapsedMs": 12.5, "completion": "PRIVATE_COMPLETION"},
        "prompt": "PRIVATE_PROMPT",
    }
    monkeypatch.setattr(
        support.subprocess,
        "run",
        lambda *args, **kwargs: SimpleNamespace(
            returncode=2,
            stderr=json.dumps(failure).encode(),
            stdout=b"",
        ),
    )
    with pytest.raises(support.WorkerRejected) as error:
        support.worker(
            "synthetic-container",
            candidate="gemma3-g1",
            fields={"input": "PRIVATE_INPUT"},
        )
    assert error.value.status == 502
    assert error.value.reason_code == "INVALID_STATE_PAIR"
    assert error.value.telemetry == {"nativeElapsedMs": 12.5}
    assert "PRIVATE_" not in str(error.value)


def _choice_report(original, candidate):
    from experiment_policies import POLICIES
    from local_error_analysis import EXPERIMENTS

    report = deepcopy(original)
    filename, adapter = POLICIES[candidate]
    report["summaries"] = {candidate: report["summaries"]["tev1"]}
    report["runtimeIdentity"]["candidates"] = {
        candidate: {
            "policyFile": filename,
            "policyDigest": sha256((EXPERIMENTS / filename).read_bytes()).hexdigest(),
            "adapter": adapter,
            "modelArtifactDigest": "a" * 64,
            "tokenizerDigest": "a" * 64,
            "apiDigest": "b" * 64,
        }
    }
    for item in (*report["attempts"], *report["warmupAttempts"]):
        item["candidate"] = candidate
    return report


@pytest.mark.parametrize(
    "mutation", [None, "input", "model", "policy", "missing", "duplicate", "serving"]
)
def test_choice_stability_is_paired_and_identity_bound(report_fixture, mutation):
    spec = importlib.util.spec_from_file_location(
        "ai07_choice_stability", SCRIPTS / "evaluate-choice-stability.py"
    )
    choice = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(choice)
    original, _ = report_fixture
    control = _choice_report(original, "tev1")
    challenger = _choice_report(original, "tev1-t2-rotate")
    challenger["attempts"][0].update(state="PROPOSE", classId="ACCOUNT")
    if mutation == "input":
        challenger["attempts"][0]["inputDigest"] = "f" * 64
    elif mutation == "model":
        challenger["runtimeIdentity"]["candidates"]["tev1-t2-rotate"]["apiDigest"] = (
            "f" * 64
        )
    elif mutation == "policy":
        challenger["runtimeIdentity"]["candidates"]["tev1-t2-rotate"][
            "policyDigest"
        ] = "f" * 64
    elif mutation == "missing":
        challenger["attempts"].pop()
    elif mutation == "serving":
        challenger["runtimeIdentity"]["contextTokens"] = 8192
    reports = [control, challenger]
    if mutation == "duplicate":
        reports.append(deepcopy(challenger))
    if mutation:
        with pytest.raises(ValueError):
            choice.compare_reports(reports)
    else:
        result = choice.compare_reports(reports)
        comparison = result["comparisons"]["tev1-t2-rotate"]
        assert comparison["uniqueCases"] == 48
        assert comparison["pairedAttempts"] == 48
        assert comparison["changedCases"] == 1
        assert result["quality"]["macroF1"] is None
        assert result["release"] == "NO_GO"
