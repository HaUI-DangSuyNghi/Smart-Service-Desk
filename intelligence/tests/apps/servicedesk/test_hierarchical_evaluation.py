import base64
from copy import deepcopy
from hashlib import sha256
import hmac
import json
from pathlib import Path
import time

import pytest
from jsonschema import Draft202012Validator

from servicedesk.intelligence.triage_ai07.hierarchical_evaluation import (
    evaluate_hierarchy,
    evaluate_reviewed_hierarchy,
)

ROOT = Path(__file__).resolve().parents[4]
RESOURCES = ROOT / "backend/src/main/resources/apps/servicedesk/intelligence"
CONTRACTS = ROOT / "contracts/apps/servicedesk/intelligence"
SCHEMA = json.loads((CONTRACTS / "ticket-assessment-v0.2.schema.json").read_bytes())
TAXONOMY = json.loads((RESOURCES / "taxonomy-v0.1.json").read_bytes())
MATRIX = json.loads((RESOURCES / "triage-policy.json").read_bytes())["matrix"]
FIXTURE = json.loads(
    (CONTRACTS / "examples/ticket-assessment-v0.2.fixture.json").read_bytes()
)
ARTIFACTS = {
    key: FIXTURE["result"]["provenance"][key]
    for key in ("taxonomyDigest", "assessmentPolicyDigest", "priorityPolicyDigest")
}


def cases():
    return [
        {
            "sampleId": "synthetic-one",
            "familyDigest": "c" * 64,
            "split": "DEV",
            "fields": FIXTURE["fields"],
            "labels": {
                "mainCategory": "NETWORK",
                "subCategory": "WIFI_CONNECTION",
                "impact": "I3",
                "urgency": "U4",
                "riskFlags": [],
                "needsClarification": False,
                "evidence": FIXTURE["result"]["evidence"],
            },
        }
    ], [
        {
            "caseId": "synthetic-one",
            "result": deepcopy(FIXTURE["result"]),
            "latencyMs": 10,
        }
    ]


def score(rows, predictions):
    return evaluate_hierarchy(
        rows, predictions, schema=SCHEMA, taxonomy=TAXONOMY, matrix=MATRIX
    )


def test_shared_java_python_fixture_offsets_and_policy_identity():
    Draft202012Validator.check_schema(SCHEMA)
    Draft202012Validator(SCHEMA).validate(FIXTURE["result"])
    for key, name in (
        ("taxonomyDigest", "taxonomy-v0.1.json"),
        ("assessmentPolicyDigest", "assessment-policy-v0.1.json"),
        ("priorityPolicyDigest", "triage-policy.json"),
    ):
        assert ARTIFACTS[key] == sha256((RESOURCES / name).read_bytes()).hexdigest()
    for span in FIXTURE["result"]["evidence"]:
        assert (
            FIXTURE["fields"][span["field"]][span["start"] : span["end"]]
            == span["quote"]
        )
    report = score(*cases())
    assert report["metrics"]["mainCategory"]["perClass"]["NETWORK"]["f1"] == 1
    assert report["metrics"]["subCategory"]["eligibleExactMatch"] == 1
    assert report["priority"]["matrixConsistency"] == 1
    assert report["evidence"]["literalValidity"] == 1
    assert report["operations"]["p95Ms"] == 10
    assert report["selectedModel"] is None
    assert report["productionDecision"] == "NO_GO"
    assert report["calibration"] == "NOT_AVAILABLE"


@pytest.mark.parametrize("child", TAXONOMY["children"], ids=lambda child: child["id"])
def test_schema_and_taxonomy_agree_for_every_parent_child(child):
    validator = Draft202012Validator(SCHEMA)
    for parent in TAXONOMY["mainCategories"]:
        result = deepcopy(FIXTURE["result"])
        result["classification"].update(mainCategory=parent, subCategory=child["id"])
        assert bool(list(validator.iter_errors(result))) == (parent != child["parent"])


@pytest.mark.parametrize(
    "mutation",
    [
        "parent",
        "confidence",
        "priority",
        "risk",
        "negative_offset",
        "extra",
        "trailing_null",
    ],
)
def test_schema_rejects_contract_violations(mutation):
    response = deepcopy(FIXTURE["result"])
    if mutation == "parent":
        response["classification"]["subCategory"] = "PASSWORD_RESET"
    elif mutation == "confidence":
        response["classification"]["confidence"] = 0.9
    elif mutation == "priority":
        response["assessment"]["priority"] = "LOW"
    elif mutation == "risk":
        response["assessment"]["risk"]["flags"] = ["UNKNOWN"]
    elif mutation == "negative_offset":
        response["evidence"][0]["start"] = -1
    elif mutation == "extra":
        response["modelOverride"] = "foreign"
    else:
        response["classification"]["mainCategory"] = None
    assert list(Draft202012Validator(SCHEMA).iter_errors(response))


def test_invalid_attempts_count_as_false_negatives_and_parent_violations():
    rows, predictions = cases()
    predictions[0]["result"]["classification"]["subCategory"] = "PASSWORD_RESET"
    report = score(rows, predictions)
    assert report["metrics"]["mainCategory"]["invalidRate"] == 1
    assert report["metrics"]["mainCategory"]["abstentionRate"] == 0
    assert report["metrics"]["mainCategory"]["perClass"]["NETWORK"]["recall"] == 0
    assert report["metrics"]["subCategory"]["parentChildViolationRate"] == 1


def test_matrix_audit_keeps_invalid_attempts_in_its_denominator():
    rows, predictions = cases()
    predictions[0]["result"]["assessment"]["priority"] = "LOW"
    report = score(rows, predictions)
    assert report["priority"]["matrixEligible"] == 1
    assert report["priority"]["matrixConsistency"] == 0
    assert report["priority"]["endToEndExactMatch"] == 0


def test_explicit_abstention_is_distinct_from_invalid_output():
    rows, predictions = cases()
    predictions[0]["result"]["classification"].update(
        decision="ABSTAIN",
        mainCategory=None,
        subCategory=None,
        subCategoryStatus="NOT_ASSESSED",
    )
    report = score(rows, predictions)
    assert report["metrics"]["mainCategory"]["coverage"] == 0
    assert report["metrics"]["mainCategory"]["abstentionRate"] == 1
    assert report["metrics"]["mainCategory"]["invalidRate"] == 0


def test_literal_evidence_is_checked_beyond_json_schema_and_unknown_support():
    rows, predictions = cases()
    predictions[0]["result"]["evidence"][0]["quote"] = "fabricated"
    rows[0]["labels"]["impact"] = None
    rows[0]["labels"]["subCategory"] = None
    report = score(rows, predictions)
    assert report["evidence"]["literalValidity"] < 1
    assert report["metrics"]["impact"]["unknownDetection"] == 0
    assert report["metrics"]["subCategory"]["ambiguityFalseAcceptance"] == 1


def signed_review(rows, *, split="DEV"):
    raw = b"\n".join(json.dumps(row, ensure_ascii=False).encode() for row in rows)
    provenance = FIXTURE["result"]["provenance"]
    bundle = ":".join(ARTIFACTS.values())
    manifest = {
        "purpose": "AI07_HIERARCHICAL_REVIEWED_EVALUATION",
        "status": "REVIEWED_AND_FROZEN",
        "issuer": "OWNED_SYNTHETIC_TEST_ONLY",
        "expiresEpochSecond": int(time.time()) + 600,
        "rowsDigest": sha256(raw).hexdigest(),
        "rightsReceipt": "d" * 64,
        "reviewerReceipts": ["e" * 64, "f" * 64],
        "adjudicationReceipt": "1" * 64,
        "rubricDigest": "2" * 64,
        "evaluationPlanDigest": "3" * 64,
        "candidateModelDigest": provenance["modelDigest"],
        "candidatePolicyDigest": provenance["modelPolicyDigest"],
        "assessmentPolicyBundleDigest": sha256(bundle.encode()).hexdigest(),
        "trainingFamilyDigests": [],
        "developmentFamilyDigests": [],
        "split": split,
    }
    return raw, manifest


def authenticate(rows_raw, manifest, predictions, *, corrupt=False):
    key = b"SYNTHETIC_REVIEW_KEY_FOR_TESTS_ONLY"
    payload = json.dumps(manifest).encode()
    envelope = json.dumps(
        {
            "payload": base64.b64encode(payload).decode(),
            "signature": "0" * 64
            if corrupt
            else hmac.new(key, payload, "sha256").hexdigest(),
        }
    ).encode()
    return evaluate_reviewed_hierarchy(
        envelope,
        rows_raw,
        predictions,
        trusted_key=key,
        schema=SCHEMA,
        taxonomy=TAXONOMY,
        matrix=MATRIX,
        artifact_digests=ARTIFACTS,
    )


def test_authenticated_review_never_automatically_promotes():
    rows, predictions = cases()
    raw, manifest = signed_review(rows)
    result = authenticate(raw, manifest, predictions)
    assert result["reviewFreeze"]["independentFamilies"] == 1
    assert result["productionDecision"] == "NO_GO"


@pytest.mark.parametrize(
    "mutation",
    [
        "signature",
        "rights",
        "reviewers",
        "expiry",
        "training",
        "holdout",
        "model",
        "policy",
        "row_bytes",
    ],
)
def test_review_freeze_fails_closed(mutation):
    rows, predictions = cases()
    if mutation == "holdout":
        rows[0]["split"] = "HOLDOUT"
    raw, manifest = signed_review(rows, split=rows[0]["split"])
    if mutation == "rights":
        manifest["rightsReceipt"] = None
    elif mutation == "reviewers":
        manifest["reviewerReceipts"] = ["e" * 64, "e" * 64]
    elif mutation == "expiry":
        manifest["expiresEpochSecond"] = 0
    elif mutation == "training":
        manifest["trainingFamilyDigests"] = [rows[0]["familyDigest"]]
    elif mutation == "holdout":
        manifest["developmentFamilyDigests"] = [rows[0]["familyDigest"]]
    elif mutation == "model":
        predictions[0]["result"]["provenance"]["modelDigest"] = "9" * 64
    elif mutation == "policy":
        predictions[0]["result"]["provenance"]["taxonomyDigest"] = "9" * 64
    elif mutation == "row_bytes":
        raw += b" "
    with pytest.raises((ValueError, TypeError)):
        authenticate(raw, manifest, predictions, corrupt=mutation == "signature")
