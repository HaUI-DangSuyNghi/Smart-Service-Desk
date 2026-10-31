from pathlib import Path

import yaml
from openapi_schema_validator import OAS30Validator
from referencing import Registry, Resource
from referencing.jsonschema import DRAFT4

root = Path(__file__).resolve().parents[2]
path = root / "contracts/openapi/servicedesk.yaml"
registry = Registry().with_resources(
    (source.as_uri(), Resource.from_contents(yaml.safe_load(source.read_text(encoding="utf-8")),
                                            default_specification=DRAFT4))
    for source in (root / "contracts").rglob("*.yaml")
)
validator = OAS30Validator({"$ref": path.as_uri() + "#/components/schemas/Classification"},
                         registry=registry, format_checker=OAS30Validator.FORMAT_CHECKER)
classification = dict(category="OTHER", priority="MEDIUM", basePriority="MEDIUM",
                      escalated=False, group="manual", source="COORDINATOR", impact=None,
                      urgency=None, reviewedBy="synthetic-coordinator",
                      reviewedAt="2026-10-30T17:47:46Z", priorityDefaulted=False)
assert validator.is_valid(classification)
unresolved = {**classification, "category": None, "source": "AI", "reviewedBy": None,
              "priorityDefaulted": True}
assert validator.is_valid(unresolved)
for source in ("AI", "COORDINATOR", "AGENT"):
    for category in ("HARDWARE", "NETWORK", "SOFTWARE", "ACCOUNT", "OTHER"):
        assert validator.is_valid({**classification, "source": source, "category": category})
    assert not validator.is_valid({**classification, "source": source, "category": "UNKNOWN"})
for source in ("COORDINATOR", "AGENT"):
    assert not validator.is_valid({**unresolved, "source": source})
for key in ("category", "source"):
    assert not validator.is_valid({name: value for name, value in unresolved.items() if name != key})
assert not validator.is_valid({**unresolved, "priority": "UNKNOWN"})
assert not validator.is_valid({**unresolved, "reviewedAt": "not-a-date"})
assert not validator.is_valid({**classification, "priorityDefaulted": "false"})
assert not validator.is_valid({**classification, "unknown": True})
assert not validator.is_valid({**classification, "source": "UNKNOWN"})
decision = OAS30Validator({"$ref": path.as_uri() + "#/components/schemas/ReviewDecision"}, registry=registry)
assert decision.is_valid(dict(category="OTHER", priority="MEDIUM", assignee=None, reason="Synthetic review"))
assert not decision.is_valid(dict(category=None, priority="MEDIUM", assignee=None, reason="Synthetic review"))
print("PASS AI unresolved category, all reviewed categories, strict rejection and non-null review input")
