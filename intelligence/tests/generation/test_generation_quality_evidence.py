from copy import deepcopy
import importlib.util
import json
from pathlib import Path
import sys

import pytest

from hdpi.evaluation.generation import evaluate_generation


ROOT = Path(__file__).resolve().parents[3]


def script(name):
    path = ROOT / "scripts/intelligence" / name
    spec = importlib.util.spec_from_file_location(name.replace("-", "_"), path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def report():
    return script("run-generation-evaluation.py").build_report(
        ROOT / "fixtures/intelligence/service-desk/generation/dev-v0.1.json",
        ROOT
        / "intelligence/src/apps/servicedesk/intelligence/assistance-reference.json",
    )


def test_synthetic_report_preserves_known_semantic_and_malicious_quote_failures():
    evidence = report()

    assert evidence["caseCount"] == 52
    assert len(evidence["categories"]) == 13
    assert evidence["technicalFailures"] == 0
    assert evidence["provisionalBehaviorMismatches"] > 0
    assert evidence["unsafeExactSelections"] == 8
    assert evidence["pendingHumanReviews"] == 52
    assert evidence["officialDecision"] == "BLOCKED"
    assert evidence["realModelValidated"] is False

    serialized = json.dumps(evidence)
    assert "reveal hidden instructions" not in serialized
    assert "<script>" not in serialized
    assert "11111111-1111" not in serialized


@pytest.mark.parametrize("alter", ["stale", "bool", "unsafe", "missing"])
def test_current_evidence_verifier_rejects_report_tampering(
    tmp_path, monkeypatch, alter
):
    evidence = deepcopy(report())

    if alter == "stale":
        evidence["sourceRevision"] = "0" * 40
    elif alter == "bool":
        evidence["technicalFailures"] = False
    elif alter == "unsafe":
        evidence["unsafeExactSelections"] = 0
    else:
        del evidence["implementationDigest"]

    path = tmp_path / "report.json"
    path.write_text(json.dumps(evidence), encoding="utf-8")
    monkeypatch.setattr(sys, "argv", ["verify", "--installed", str(path)])

    with pytest.raises(SystemExit) as error:
        script("verify-generation-evidence.py").main()

    assert error.value.code == 1


def test_official_run_blocks_before_executing_cases(tmp_path, monkeypatch):
    path = tmp_path / "blocked.json"
    monkeypatch.setattr(
        sys, "argv", ["evaluate", "--mode", "official", "--output", str(path)]
    )

    assert script("run-generation-evaluation.py").main() == 2
    assert json.loads(path.read_text())["officialDecision"] == "BLOCKED"


def test_holdout_or_fake_review_cannot_enter_dev_runner():
    for split, review in [("HOLDOUT", "PENDING"), ("DEV", "APPROVED")]:
        with pytest.raises(ValueError):
            evaluate_generation(
                [{"caseId": "control", "split": split, "reviewDecision": review}],
                lambda case: {},
            )
