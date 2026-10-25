from copy import deepcopy
from hashlib import sha256
import json
from pathlib import Path

import pytest
import yaml

from hdpi.classification.protocols import ClassificationProposal
from servicedesk.intelligence.triage_ai07.datasets import inventory, worksheets
from servicedesk.intelligence.triage_ai07.hybrid import HybridTriagePolicy
from servicedesk.intelligence.triage_ai07.evaluation import evaluate, verify


ROOT = Path(__file__).resolve().parents[4]


class Candidate:
    def __init__(self, result=None, error=None):
        self.result = result
        self.error = error
        self.calls = 0

    def predict(self, request):
        self.calls += 1
        assert len(request.allowed_classes) == 5

        if self.error:
            raise self.error

        return self.result


@pytest.mark.parametrize("category", ["ACCOUNT", "NETWORK"])
def test_challenger_never_changes_rules_or_review_flags(category):
    rule = {
        "status": "SUCCESS",
        "result": {
            "category": "NETWORK",
            "priority": "CRITICAL",
            "suggestedGroup": "IT-NETWORK",
        },
        "needsReview": False,
    }
    before = deepcopy(rule)
    proposal = ClassificationProposal(
        contract_version="0.1.0", decision="PROPOSE", class_id=category
    )
    challenger = Candidate(result=proposal)
    result, observation = HybridTriagePolicy(
        candidate=challenger, enabled=True
    ).compare(
        rule,
        title="wifi",
        description="test",
    )
    assert result == before == rule
    assert observation.agreement == (category == "NETWORK")
    assert challenger.calls == 1


def test_challenger_disabled_failure_and_foreign_taxonomy():
    rule = {"status": "DEGRADED", "result": None, "needsReview": True}
    candidate = Candidate(error=RuntimeError("sensitive detail"))
    result, observation = HybridTriagePolicy(candidate=candidate).compare(
        rule, title="x", description="y"
    )
    assert result == rule and observation.state == "DISABLED" and candidate.calls == 0

    result, observation = HybridTriagePolicy(candidate=candidate, enabled=True).compare(
        rule, title="x", description="y"
    )
    assert result == rule and observation.state == "DEGRADED"
    assert "sensitive" not in repr(observation)

    candidate = Candidate(
        result=ClassificationProposal(
            contract_version="0.1.0", decision="PROPOSE", class_id="foreign"
        )
    )
    assert (
        HybridTriagePolicy(candidate=candidate, enabled=True)
        .compare(rule, title="x", description="y")[1]
        .state
        == "DEGRADED"
    )


def test_java_projection_policy_is_exactly_derived_from_frozen_ai02():
    profiles = (
        ROOT / "intelligence/src/apps/servicedesk/intelligence/profiles/service-desk"
    )
    rules = yaml.safe_load((profiles / "versions/0.3.0/rules.yaml").read_text("utf-8"))
    taxonomy = yaml.safe_load((profiles / "taxonomy.yaml").read_text("utf-8"))
    policy = yaml.safe_load((profiles / "priority-policy.yaml").read_text("utf-8"))
    java = json.loads(
        (
            ROOT
            / "backend/src/main/resources/apps/servicedesk/intelligence/triage-policy.json"
        ).read_text("utf-8")
    )

    assert java["matrix"] == policy["matrix"]
    assert java["groups"] == {
        key: item["suggestedGroup"] for key, item in taxonomy["categories"].items()
    }
    assert java["categoryRules"] == {
        item["category"]: key for key, item in rules["categoryRules"].items()
    }

    for section in ("impactRules", "urgencyRules"):
        assert java[section] == {
            key: item["ruleId"] for key, item in rules[section].items()
        }

    assert java["priorityRules"] == rules["priorityRuleIds"]


def test_inventory_and_worksheets_preserve_draft_sources():
    path = ROOT / "fixtures/intelligence/service-desk/tickets/tickets-v0.2.jsonl"
    before = sha256(path.read_bytes()).hexdigest()
    audit = inventory(ROOT)
    rows = worksheets(ROOT)

    assert audit["promotion"] == "BLOCKED_PENDING_REVIEW"
    assert len(rows) == 48
    assert all(row["category"] == "" and row["reviewStatus"] == "DRAFT" for row in rows)
    assert sha256(path.read_bytes()).hexdigest() == before


def test_acceptance_recomputes_results_and_never_grants_model_promotion():
    report = evaluate(ROOT)
    assert report["attempts"] == 48
    assert sum(report["statuses"].values()) == 48
    assert report["macroF1"] is None
    assert verify(ROOT, report)["engineeringControls"] == "PASS"

    with pytest.raises(ValueError, match="Promotion blocked"):
        verify(ROOT, report, require_promotion=True)

    report["receipts"][0]["responseDigest"] = "0" * 64

    with pytest.raises(ValueError, match="recomputed"):
        verify(ROOT, report)
