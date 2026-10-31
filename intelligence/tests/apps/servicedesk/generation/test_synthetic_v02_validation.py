from copy import deepcopy

import pytest

from _contracts import DatasetError, digest, strict_json
from _validation import check_text, validate_bundle
import _validation


def refresh_case(synthetic_bundle, index):
    case = synthetic_bundle["dataset"]["cases"][index]
    synthetic_bundle["curation"]["cases"][index]["caseDigest"] = digest(case)
    synthetic_bundle["manifest"]["caseDigests"][case["caseId"]] = digest(case)
    synthetic_bundle["manifest"]["datasetDigest"] = digest(synthetic_bundle["dataset"])
    synthetic_bundle["manifest"]["curationDigest"] = digest(
        synthetic_bundle["curation"]
    )


@pytest.mark.parametrize(
    "change",
    [
        "duplicate-case",
        "checksum",
        "unknown-source",
        "approved-review",
        "locked-holdout",
        "approved-rights",
        "baseline-content",
        "different-uuid-duplicate",
        "long-question",
        "too-many-documents",
        "oversized-snapshot",
        "unknown-category",
        "source-count",
        "forged-human",
        "missing-unsafe",
        "oracle-support",
        "oracle-contradiction",
        "control",
        "nan-counter",
    ],
)
def test_invalid_mutations_fail_even_after_case_and_manifest_rehash(
    synthetic_bundle, change, monkeypatch
):
    # Exercise the validator layers independently of the release-history lock.
    monkeypatch.setattr(_validation, "frozen_release_digest", lambda: None)
    index = 52
    case = synthetic_bundle["dataset"]["cases"][index]

    if change == "duplicate-case":
        case["caseId"] = synthetic_bundle["dataset"]["cases"][0]["caseId"]
    elif change == "checksum":
        case["documents"][0]["checksum"] = "0" * 64
    elif change == "unknown-source":
        case["unsafeSourceIds"] = ["ffffffff-ffff-5fff-8fff-ffffffffffff"]
    elif change == "approved-review":
        case["reviewDecision"] = "APPROVED"
    elif change == "locked-holdout":
        synthetic_bundle["dataset"]["holdoutState"] = "LOCKED"
    elif change == "approved-rights":
        synthetic_bundle["dataset"]["rightsDecision"] = "APPROVED"
    elif change == "baseline-content":
        index = 0
        synthetic_bundle["dataset"]["cases"][0]["question"] += " changed"
    elif change == "different-uuid-duplicate":
        parent = synthetic_bundle["dataset"]["cases"][52]
        case = deepcopy(parent)
        case["caseId"] = synthetic_bundle["dataset"]["cases"][53]["caseId"]
        case["documents"][0]["metadataId"] = "ffffffff-ffff-5fff-8fff-ffffffffffff"
        index = 53
        synthetic_bundle["dataset"]["cases"][index] = case
        synthetic_bundle["curation"]["cases"][index]["oracleGraph"][
            "supportingSourceIds"
        ] = [case["documents"][0]["metadataId"]]
    elif change == "long-question":
        case["question"] = "x" * 2001
    elif change == "too-many-documents":
        case["documents"] *= 11
    elif change == "oversized-snapshot":
        index = next(
            position
            for position, row in enumerate(synthetic_bundle["dataset"]["cases"])
            if row["category"] == "near_limit_payload"
        )
        case = synthetic_bundle["dataset"]["cases"][index]
        extra = deepcopy(case["documents"][0])
        extra["metadataId"] = "ffffffff-ffff-5fff-8fff-ffffffffffff"
        case["documents"].append(extra)
        synthetic_bundle["curation"]["cases"][index]["sourceCount"] = 3
    elif change == "unknown-category":
        case["category"] = "unknown-app-category"
    elif change == "source-count":
        synthetic_bundle["curation"]["cases"][index]["sourceCount"] = 0
    elif change == "forged-human":
        synthetic_bundle["curation"]["humanApproved"] = True
    elif change == "missing-unsafe":
        index = next(
            position
            for position, row in enumerate(synthetic_bundle["dataset"]["cases"])
            if position >= 52 and row["unsafeSourceIds"]
        )
        synthetic_bundle["dataset"]["cases"][index]["unsafeSourceIds"] = []
    elif change == "oracle-support":
        synthetic_bundle["curation"]["cases"][index]["oracleGraph"][
            "requiredRelations"
        ] = ["fictional-unprovided-fact"]
    elif change == "oracle-contradiction":
        index = next(
            position
            for position, row in enumerate(synthetic_bundle["curation"]["cases"])
            if row["oracleGraph"]["contradictionPairs"]
        )
        graph = synthetic_bundle["curation"]["cases"][index]["oracleGraph"]
        graph["contradictionPairs"][0][1] = graph["contradictionPairs"][0][0]
    elif change == "control":
        case["question"] += "\x00"
    elif change == "nan-counter":
        synthetic_bundle["manifest"]["caseCount"] = False

    refresh_case(synthetic_bundle, index)

    expected_codes = {
        "duplicate-case": "CASE_IDS",
        "checksum": "SOURCE_CHECKSUM",
        "unknown-source": "UNSAFE_SOURCE_MEMBERSHIP",
        "approved-review": "SCHEMA_DATASET",
        "locked-holdout": "SCHEMA_DATASET",
        "approved-rights": "SCHEMA_DATASET",
        "baseline-content": "BASELINE_CASE_DRIFT",
        "different-uuid-duplicate": "ORACLE_UNSUPPORTED_CLAIM",
        "long-question": "SCHEMA_DATASET",
        "too-many-documents": "SCHEMA_DATASET",
        "oversized-snapshot": "SNAPSHOT_BUDGET",
        "unknown-category": "SCHEMA_DATASET",
        "source-count": "SOURCE_COUNT",
        "forged-human": "SCHEMA_CURATION",
        "missing-unsafe": "UNFLAGGED_UNSAFE_SOURCE",
        "oracle-support": "UNKNOWN_ORACLE_FACT",
        "oracle-contradiction": "ORACLE_CONTRADICTION_MEMBERSHIP",
        "control": "CONTROL_OR_SURROGATE",
        "nan-counter": "SCHEMA_MANIFEST",
    }

    with pytest.raises(DatasetError, match=expected_codes[change]):
        validate_bundle(synthetic_bundle)


def test_content_duplicate_with_new_uuid_is_rejected_independently(pristine_bundle):
    first = deepcopy(pristine_bundle["dataset"]["cases"][52])
    second = deepcopy(first)
    second["caseId"] = "GEN-SD-0401"
    second["documents"][0]["metadataId"] = "ffffffff-ffff-5fff-8fff-ffffffffffff"
    metadata = [
        deepcopy(pristine_bundle["curation"]["cases"][52]) for unused in range(2)
    ]
    metadata[1]["metamorphicSiblingOf"] = None

    with pytest.raises(DatasetError, match="ACCIDENTAL_EXACT_DUPLICATE"):
        _validation.duplicate_report([first, second], metadata, 900000)


def test_release_lock_rejects_reassignment_even_with_all_local_hashes_updated(
    synthetic_bundle, monkeypatch
):
    frozen = digest(synthetic_bundle["dataset"])
    monkeypatch.setattr(_validation, "frozen_release_digest", lambda: frozen)
    synthetic_bundle["dataset"]["cases"][52]["question"] += " changed"
    refresh_case(synthetic_bundle, 52)

    with pytest.raises(DatasetError, match="FROZEN_DATASET_REVISION"):
        _validation.inspect_bundle(synthetic_bundle)


@pytest.mark.parametrize(
    "raw", [b'{"a":1,"a":2}', b'{"a":NaN}', b'{"a":1e999}', b"{} trailing", b"\xff"]
)
def test_strict_json_rejects_duplicates_non_finite_and_invalid_text(raw):
    with pytest.raises(DatasetError):
        strict_json(raw)


@pytest.mark.parametrize(
    "text",
    [
        "sk-syntheticcredentialpattern123456789",
        "gsk_syntheticcredentialpattern123456789",
        "AIzaSyntheticCredentialPattern123456789",
        "-----BEGIN PRIVATE KEY-----",
        "aaaaaaaaaaaa.bbbbbbbbbbbb.cccccccccccc",
        "API_KEY=synthetic-forbidden-value",
        "reader@unapproved.example",
        "https://unapproved.example/demo",
        "127.0.0.1",
        "+84 912 345 678",
        "STUDENT12345678",
        "A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6",
    ],
    ids=[f"risk-{index}" for index in range(12)],
)
def test_secret_and_personal_identifier_patterns_are_never_waived_by_attack_category(
    text,
):
    with pytest.raises(DatasetError):
        check_text(text, "data_exfiltration_canary", source=True, unsafe=True)


def test_reserved_dummy_markers_require_exact_declared_scope():
    text = "SYNTHETIC_CANARY_DO_NOT_DISCLOSE https://example.invalid/demo user@example.test 192.0.2.10"
    check_text(text, "data_exfiltration_canary", source=True, unsafe=True)

    with pytest.raises(DatasetError):
        check_text(text, "relevant", source=True, unsafe=False)
