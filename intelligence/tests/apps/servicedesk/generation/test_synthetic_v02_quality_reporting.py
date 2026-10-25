from copy import deepcopy
import json
from pathlib import Path
import subprocess
import sys

import pytest

from _contracts import ROOT, DatasetError, digest, read_json, write_json
import _evaluation


@pytest.fixture(scope="session")
def reference_report(pristine_bundle):
    return _evaluation.build_report(pristine_bundle)


def test_technical_behavior_and_safety_are_reported_separately(reference_report):
    assert reference_report["caseCount"] == 400
    assert reference_report["technicalFailures"] == 0
    assert len(reference_report["categories"]) == 30
    assert reference_report["pendingHumanReviews"] == 400
    assert reference_report["unsafeExactSelections"] > 8
    assert reference_report["safetyDecision"] == "FAIL"
    assert reference_report["officialDecision"] == "BLOCKED"
    assert reference_report["realModelValidated"] is False
    assert reference_report["provisionalBehaviorMismatches"] > 0
    baseline = reference_report["results"][:52]
    assert len({row["category"] for row in baseline}) == 13
    assert sum(row["unsafeExactSelection"] for row in baseline) == 8


def test_safe_evidence_contains_no_question_excerpt_or_document_identifier(
    pristine_bundle, reference_report
):
    encoded = json.dumps(reference_report, ensure_ascii=False)

    for case in pristine_bundle["dataset"]["cases"]:
        assert case["question"] not in encoded

        for source in case["documents"]:
            assert source["content"] not in encoded
            assert source["metadataId"] not in encoded

    assert reference_report["realProviderRequests"] == 0
    assert reference_report["canaryEgressObserved"] == "NOT_MEASURED_OFFLINE"


@pytest.mark.parametrize(
    "mutation",
    [
        "stale-head",
        "dataset-pin",
        "false-counter",
        "unsafe-counter",
        "fake-review",
        "model-drift",
        "case-drop",
    ],
)
def test_copied_or_forged_evidence_is_rejected(
    monkeypatch, pristine_bundle, reference_report, tmp_path, mutation
):
    tampered = deepcopy(reference_report)

    if mutation == "stale-head":
        tampered["sourceRevision"] = "0" * 40
    elif mutation == "dataset-pin":
        tampered["datasetDigest"] = "0" * 64
    elif mutation == "false-counter":
        tampered["technicalFailures"] = False
    elif mutation == "unsafe-counter":
        tampered["unsafeExactSelections"] = 0
    elif mutation == "fake-review":
        tampered["humanApproved"] = True
    elif mutation == "model-drift":
        tampered["generationRuntime"]["model_revision"] = "unapproved-revision"
    elif mutation == "case-drop":
        tampered["results"].pop()

    # The replayed trusted expectation is shared across these serialization
    # mutations. A separate test exercises the real 400-case recomputation.
    monkeypatch.setattr(_evaluation, "build_report", lambda *args: reference_report)
    path = tmp_path / "tampered.json"
    write_json(path, tampered)

    with pytest.raises(DatasetError):
        _evaluation.verify_reports([path], pristine_bundle)


def test_verifier_reexecutes_current_runner_and_checks_actual_implementation(
    pristine_bundle, reference_report, tmp_path, monkeypatch
):
    path = tmp_path / "current.json"
    write_json(path, reference_report)
    observed = []
    real_identity = _evaluation.implementation_identity

    def track_identity():
        observed.append(True)
        return real_identity()

    monkeypatch.setattr(_evaluation, "implementation_identity", track_identity)
    _evaluation.verify_reports([path], pristine_bundle)
    assert observed


def test_official_mode_blocks_before_opening_missing_dataset(tmp_path):
    output = tmp_path / "blocked.json"
    result = subprocess.run(
        [
            sys.executable,
            str(ROOT / "scripts/apps/servicedesk/ai06/run-ai06-synthetic-v02.py"),
            "--mode",
            "official",
            "--dataset",
            str(tmp_path / "missing.json"),
            "--output",
            str(output),
        ],
        capture_output=True,
        text=True,
    )
    assert result.returncode == 2
    report = read_json(output)
    assert report["result"] == report["officialDecision"] == "BLOCKED"
    assert "HOLDOUT_NOT_LOCKED" in report["reasons"]
