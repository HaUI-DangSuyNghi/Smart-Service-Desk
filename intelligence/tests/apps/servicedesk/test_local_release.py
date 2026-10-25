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
