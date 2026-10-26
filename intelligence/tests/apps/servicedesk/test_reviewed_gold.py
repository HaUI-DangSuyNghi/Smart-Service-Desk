"""Test-only signatures exercise a gate; they are not real owner/reviewer approvals."""

from hashlib import sha256
import base64
import hmac
import json
import time

import pytest

from servicedesk.intelligence.triage_ai07.dataset_profiles import CLASSES
from servicedesk.intelligence.triage_ai07.reviewed_gold import evaluate_reviewed


KEY = b"SYNTHETIC_TEST_KEY_NEVER_AN_OWNER_KEY_0000"


def fixture(mutation=None):
    rows, predictions = [], []
    for index, label in enumerate(CLASSES):
        for variant in range(2):
            identifier = f"OWNED-{index}-{variant}"
            identity = sha256(identifier.encode()).hexdigest()
            rows.append(
                {
                    "sampleId": identifier,
                    "familyDigest": identity,
                    "inputDigest": identity,
                    "decision": "PROPOSE",
                    "classId": label,
                }
            )
            predictions.append(
                {
                    "caseId": identifier,
                    "inputDigest": identity,
                    "state": "PROPOSE",
                    "classId": label,
                    "modelDigest": "f" * 64,
                    "policyDigest": "0" * 64,
                }
            )
    raw = b"".join((json.dumps(row) + "\n").encode() for row in rows)
    manifest = {
        "purpose": "AI07_REVIEWED_CLASSIFICATION_EVALUATION",
        "status": "REVIEWED_AND_FROZEN",
        "issuer": "SYNTHETIC_TEST_ONLY",
        "expiresEpochSecond": int(time.time()) + 3600,
        "rowsDigest": sha256(raw).hexdigest(),
        "rightsReceipt": "a" * 64,
        "reviewerReceipts": ["b" * 64, "c" * 64],
        "rubricDigest": "d" * 64,
        "labelObjective": "initial_receiving_team_by_observed_symptom",
        "trainingFamilyDigests": [],
        "split": "DEV",
        "evaluationPlanDigest": "e" * 64,
        "candidateModelDigest": "f" * 64,
        "candidatePolicyDigest": "0" * 64,
    }
    if mutation == "expiry":
        manifest["expiresEpochSecond"] = 0
    elif mutation == "rights":
        manifest["rightsReceipt"] = "PENDING"
    elif mutation == "reviewers":
        manifest["reviewerReceipts"] = ["b" * 64, "b" * 64]
    elif mutation == "family":
        manifest["trainingFamilyDigests"] = [rows[0]["familyDigest"]]
    elif mutation == "holdout":
        manifest["split"] = "HOLDOUT"
    elif mutation == "abstain":
        predictions[0].update(state="ABSTAIN", classId=None)
    payload = json.dumps(manifest).encode()
    envelope = json.dumps(
        {
            "payload": base64.b64encode(payload).decode(),
            "signature": hmac.new(KEY, payload, "sha256").hexdigest(),
        }
    ).encode()
    if mutation == "source":
        raw += b"\n"
    return (
        envelope,
        raw,
        b"".join((json.dumps(row) + "\n").encode() for row in predictions),
    )


@pytest.mark.parametrize(
    "mutation",
    ["expiry", "rights", "reviewers", "family", "holdout", "source", "signature"],
)
def test_forged_or_insufficient_review_never_becomes_quality(mutation):
    values = fixture(mutation)
    with pytest.raises(ValueError):
        evaluate_reviewed(
            *values, trusted_key=KEY if mutation != "signature" else b"x" * 40
        )


def test_reviewed_metric_accounts_for_abstention_and_cannot_promote():
    perfect = evaluate_reviewed(*fixture(), trusted_key=KEY)
    abstained = evaluate_reviewed(*fixture("abstain"), trusted_key=KEY)
    assert perfect["macroF1"] == 1.0
    assert abstained["macroF1"] < 1.0 and abstained["coverage"] == 0.9
    assert abstained["uniqueCases"] == 10
    assert abstained["promotion"] == "NO_GO" and abstained["selectedModel"] is None
