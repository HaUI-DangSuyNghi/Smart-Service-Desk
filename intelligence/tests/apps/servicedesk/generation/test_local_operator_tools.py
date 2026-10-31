import importlib.util
from pathlib import Path

import pytest

from _contracts import ROOT, DatasetError, digest
from _local_cloud_operator import load_simple_env, reserve_batch


SPEC = importlib.util.spec_from_file_location(
    "ai06_mismatch_inspector",
    ROOT / "scripts/apps/servicedesk/ai06/inspect-ai06-mismatches.py",
)
inspector = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(inspector)


def example():
    cases = [
        {
            "caseId": "probe-1",
            "category": "probe",
            "provisionalExpectedStatus": "INSUFFICIENT_EVIDENCE",
            "question": "private-probe-question",
        },
        {
            "caseId": "probe-2",
            "category": "probe",
            "provisionalExpectedStatus": "SUCCESS",
            "question": "other-private-probe",
        },
    ]
    dataset = {"cases": cases}
    report = {
        "datasetDigest": digest(dataset),
        "sourceRevision": "0" * 40,
        "technicalFailures": 0,
        "provisionalBehaviorMismatches": 2,
        "results": [
            {
                "caseId": case["caseId"],
                "category": "probe",
                "technicalPass": True,
                "provisionalBehaviorMatch": False,
                "status": "SUCCESS" if index == 0 else "INSUFFICIENT_EVIDENCE",
            }
            for index, case in enumerate(cases)
        ],
    }
    return report, dataset


def test_triage_separates_semantic_and_recall_work_without_exposing_content():
    report, dataset = example()
    result = inspector.analyze(report, dataset)

    assert result["fixScopes"] == {
        "CANDIDATE_RECALL_REVIEW": 1,
        "SEMANTIC_SELECTION_REVIEW": 1,
    }
    assert "private-probe" not in repr(result)
    assert result["evidenceClass"] == "UNVERIFIED_INPUT_TRIAGE_NOT_RUNTIME_REPLAY"
    assert result["officialDecision"] == "BLOCKED"


@pytest.mark.parametrize("mutation", ["counter", "coverage", "digest", "flag"])
def test_triage_does_not_accept_inconsistent_or_boolean_counter_receipts(mutation):
    report, dataset = example()

    if mutation == "counter":
        report["technicalFailures"] = False
    elif mutation == "coverage":
        report["results"].pop()
    elif mutation == "digest":
        report["datasetDigest"] = "0" * 64
    else:
        report["results"][0]["provisionalBehaviorMatch"] = True

    with pytest.raises(DatasetError):
        inspector.analyze(report, dataset)


def test_one_operator_scope_cannot_replay_by_changing_output_path(tmp_path):
    cases = tmp_path / "cases.json"
    cases.write_bytes(b"[]\n")
    reserve_batch(tmp_path, cases)

    with pytest.raises(DatasetError, match="OPERATOR_SCOPE_ALREADY_RESERVED"):
        reserve_batch(tmp_path, cases)


def test_local_env_parser_does_not_execute_or_export_unrelated_names(tmp_path):
    env = tmp_path / "local.env"
    env.write_text(
        "GEMINI_API_KEY='literal-$(command)'\nVITE_SECRET=never-export\n",
        encoding="utf-8",
    )

    assert load_simple_env(env) == {"GEMINI_API_KEY": "literal-$(command)"}


def test_ambiguous_duplicate_env_configuration_fails_without_echoing_key(tmp_path):
    env = tmp_path / "local.env"
    env.write_text(
        "GEMINI_API_KEY=first-probe\nGEMINI_API_KEY=second-probe\n", encoding="utf-8"
    )

    with pytest.raises(DatasetError, match="^DUPLICATE_ENV_NAME$"):
        load_simple_env(env)
