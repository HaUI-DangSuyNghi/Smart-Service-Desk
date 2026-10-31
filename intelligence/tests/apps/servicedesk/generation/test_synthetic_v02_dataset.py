from collections import Counter
import unicodedata

from _contracts import BASELINE_DIGEST, DATA, SEED, digest, load_inputs, read_json
from _validation import inspect_bundle


def test_400_case_release_preserves_historical_bytes_and_exact_allocation(
    pristine_bundle,
):
    values = load_inputs()
    cases = pristine_bundle["dataset"]["cases"]
    plan = values["plan"]

    assert digest((DATA / "dev-v0.1.json").read_bytes()) == BASELINE_DIGEST
    assert cases[:52] == values["baseline"]["cases"]
    assert len(cases[52:]) == 348
    assert [case["caseId"] for case in cases] == [
        f"GEN-SD-{index:04}" for index in range(1, 401)
    ]
    assert Counter(case["category"] for case in cases) == {
        row["id"]: row["total"] for row in plan["categories"]
    }
    assert len(plan["categories"]) == 30
    assert plan["seed"] == SEED


def test_independent_fact_patterns_and_explicit_metamorphic_pairs(pristine_bundle):
    report = inspect_bundle(pristine_bundle)
    assert report["factFamilies"] == 12
    assert report["factPatterns"] == 36
    assert report["templates"] == 60
    assert report["accidentalExactDuplicates"] == 0
    assert len(report["declaredExactOrderPairs"]) == 4
    assert report["nearDuplicateCandidates"]

    cases = {case["caseId"]: case for case in pristine_bundle["dataset"]["cases"]}
    removed = [
        row
        for row in pristine_bundle["curation"]["cases"]
        if row["mutationType"] == "evidence_removed"
    ]
    assert len(removed) == 8

    for row in removed:
        case = cases[row["caseId"]]
        parent = cases[row["metamorphicSiblingOf"]]
        assert case["question"] == parent["question"]
        assert parent["documents"] and case["documents"] == []
        assert parent["provisionalExpectedStatus"] == "SUCCESS"
        assert case["provisionalExpectedStatus"] == "INSUFFICIENT_EVIDENCE"


def test_unicode_and_near_limit_cases_exercise_actual_bounds(pristine_bundle):
    cases = pristine_bundle["dataset"]["cases"]
    boundary = [case for case in cases[52:] if case["category"] == "unicode_boundary"]
    assert len(boundary) == 8
    assert all(unicodedata.is_normalized("NFD", case["question"]) for case in boundary)
    assert any(
        not unicodedata.is_normalized("NFC", case["question"]) for case in boundary
    )

    for case in cases[52:]:
        assert 0 < len(case["question"]) <= 2000
        sizes = [len(source["content"].encode("utf-8")) for source in case["documents"]]
        assert sum(sizes) <= 32000

        if case["category"] == "near_limit_payload":
            assert 30000 <= sum(sizes) <= 32000


def test_smoke_is_exactly_one_case_per_category_and_keeps_review_pending(
    pristine_bundle,
):
    smoke = pristine_bundle["smoke"]
    cases = {case["caseId"]: case for case in pristine_bundle["dataset"]["cases"]}
    selected = [cases[identity] for identity in smoke["caseIds"]]
    assert len(selected) == 30
    assert len({case["category"] for case in selected}) == 30
    assert all(
        case["split"] == "DEV"
        and case["reviewDecision"] == "PENDING"
        and case["adjudication"] is None
        for case in cases.values()
    )
    assert pristine_bundle["curation"]["humanReview"] == "PENDING"
    assert pristine_bundle["dataset"]["rightsDecision"] == "PENDING"
    assert pristine_bundle["dataset"]["holdoutState"] == "NOT_LOCKED"
