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
                         registry=registry)
classification = dict(category="OTHER", priority="MEDIUM", basePriority="MEDIUM",
                      escalated=False, group="manual", source="COORDINATOR", impact=None,
                      urgency=None, reviewedBy="synthetic-coordinator",
                      reviewedAt="2026-10-30T17:47:46Z", priorityDefaulted=False)
assert validator.is_valid(classification)
assert not validator.is_valid({**classification, "priorityDefaulted": "false"})
assert not validator.is_valid({**classification, "unknown": True})
assert not validator.is_valid({**classification, "source": "UNKNOWN"})
print("PASS classification response and strict rejection checks")
