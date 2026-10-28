"""Check promoted R12 live/target schemas and captured statistics responses."""
import copy
import json
from pathlib import Path
import subprocess
import sys

import yaml
from openapi_schema_validator import OAS30Validator
from openapi_spec_validator import OpenAPIV30SpecValidator
from referencing import Registry, Resource
from referencing.jsonschema import DRAFT4

ROOT = Path(__file__).resolve().parents[1]
LIVE = ROOT / "contracts/openapi/servicedesk.yaml"
TARGET = ROOT / "contracts/openapi/servicedesk-target.yaml"
documents = {path.resolve().as_uri(): yaml.safe_load(path.read_text(encoding="utf-8"))
             for path in (ROOT / "contracts").rglob("*.yaml")}
registry = Registry().with_resources((uri, Resource.from_contents(document, default_specification=DRAFT4))
                                     for uri, document in documents.items())
for path in (LIVE, TARGET):
    OpenAPIV30SpecValidator(documents[path.as_uri()], base_uri=path.as_uri()).validate()
validators = [OAS30Validator({"$ref": path.as_uri() + "#/components/schemas/" + schema}, registry=registry)
              for path, schema in ((LIVE, "TicketStatistics"), (TARGET, "TicketStatisticsV2"))]
schema = documents[LIVE.as_uri()]["components"]["schemas"]["TicketStatistics"]
baseline = yaml.safe_load(subprocess.check_output([
    "git", "show", "bd458261ecad994ba1b6356f200a95ef625e6b05:contracts/openapi/servicedesk.yaml"], cwd=ROOT, text=True, encoding="utf-8"))
legacy = baseline["components"]["schemas"]["TicketStatistics"]
assert set(legacy["required"]) <= set(schema["required"])
for field, definition in legacy["properties"].items():
    assert definition.get("type") == schema["properties"][field].get("type"), field
    assert definition.get("format") == schema["properties"][field].get("format"), field
sample = {field: 0 for field in schema["properties"] if field not in ("workspace", "visibility")}
sample.update(workspace="lab-a", visibility="own")
for validator in validators:
    validator.validate(sample)
    for field in schema["required"]:
        invalid = copy.deepcopy(sample)
        del invalid[field]
        assert not validator.is_valid(invalid), field
    for field in ("awaitingReview", "assigned", "inProgress", "pending", "reopened", "closed", "overdue", "intake", "following"):
        invalid = dict(sample, **{field: -1})
        assert not validator.is_valid(invalid), field
    assert not validator.is_valid(dict(sample, futureUnknownField=0))
for filename in sys.argv[1:]:
    for response in json.loads(Path(filename).read_text(encoding="utf-8-sig")):
        for validator in validators:
            validator.validate(response)
        OAS30Validator(legacy).validate({field: response[field] for field in legacy["properties"]})
        assert response["closed"] == response["completed"]
        assert response["total"] == sum(response[field] for field in (
            "intake", "following", "awaitingReview", "assigned", "inProgress", "pending", "reopened",
            "awaitingConfirmation", "closed", "unclassified"))
        assert response["awaitingHandling"] == sum(response[field] for field in (
            "awaitingReview", "assigned", "inProgress", "pending", "reopened"))
        assert 0 <= response["overdue"] <= response["total"]
print("PASS R12 live/target schemas, required fields, negative/unknown-field rejection and response invariants")
