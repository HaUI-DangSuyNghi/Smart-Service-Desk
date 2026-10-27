"""Validate real MockMvc responses against the promoted OpenAPI subset."""
import argparse
import copy
from pathlib import Path

import yaml
from openapi_schema_validator import OAS30Validator
from openapi_spec_validator import OpenAPIV30SpecValidator
from referencing import Registry, Resource
from referencing.jsonschema import DRAFT4

ROOT = Path(__file__).resolve().parents[1]
HPDI = ROOT / "contracts/hpdi/openapi.yaml"
APP = ROOT / "contracts/openapi/servicedesk.yaml"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("evidence", type=Path)
    arguments = parser.parse_args()
    import json
    evidence = json.loads(arguments.evidence.read_text(encoding="utf-8-sig"))
    documents = {path.resolve().as_uri(): yaml.safe_load(path.read_text(encoding="utf-8"))
                 for path in (ROOT / "contracts").rglob("*.yaml")}
    hpdi, app = documents[HPDI.as_uri()], documents[APP.as_uri()]
    prefix = "/api/hpdi/v1/workspaces/{workspace}"
    paths = [prefix + "/queue"] + [prefix + "/tasks/{id}/" + action for action in ("start", "pause", "resume")]
    target = documents[(ROOT / "contracts/hpdi/openapi-target.yaml").as_uri()]
    assert all(path in hpdi["paths"] and path not in target["paths"] for path in paths)
    review = "/workspaces/{workspace}/tickets/{id}/review"
    assert review in app["paths"]
    assert review not in documents[(ROOT / "contracts/openapi/servicedesk-target.yaml").as_uri()]["paths"]
    subset = copy.deepcopy(hpdi)
    subset["paths"] = {path: hpdi["paths"][path] for path in paths}
    subset["components"]["schemas"] = {name: hpdi["components"]["schemas"][name] for name in
        ("QueueItem", "QueuePage", "WorkItem", "PageMeta", "Estimate", "Priority", "WorkState", "PendingReason", "Problem")}
    subset["components"]["responses"] = {name: hpdi["components"]["responses"][name] for name in
        ("BadRequest", "Unauthorized", "Forbidden", "NotFound", "Conflict")}
    subset["components"]["parameters"] = {name: hpdi["components"]["parameters"][name] for name in ("Workspace", "Page", "Size")}
    OpenAPIV30SpecValidator(subset, base_uri=HPDI.as_uri()).validate()
    OpenAPIV30SpecValidator(app, base_uri=APP.as_uri()).validate()
    registry = Registry().with_resources((location, Resource.from_contents(document, default_specification=DRAFT4))
        for location, document in documents.items())
    count = 0
    for name, value in evidence.items():
        document, location, schema = (app, APP, "Ticket") if name in ("created", "review") else (hpdi, HPDI, "QueuePage" if name == "queue" else "QueueItem")
        validator = OAS30Validator({"$ref": location.as_uri() + "#/components/schemas/" + schema},
            registry=registry, format_checker=OAS30Validator.FORMAT_CHECKER)
        validator.validate(value)
        invalid = copy.deepcopy(value)
        if name == "queue":
            invalid["loadMinutes"] = -1
        else:
            del invalid["id"]
        assert not validator.is_valid(invalid), "Schema must reject a broken " + name
        count += 2
    decision = OAS30Validator({"$ref": APP.as_uri() + "#/components/schemas/ReviewDecision"}, registry=registry)
    decision.validate({"category": "HARDWARE", "priority": "HIGH", "assignee": None, "reason": "Reviewed"})
    assert not decision.is_valid({"category": "HARDWARE", "priority": "HIGH", "assignee": None})
    print(f"PASS promoted paths and API subset validation; {count + 2} response/request positive and negative checks")
    print("Whole HPDI live/target validation is separate: known baseline errors are not waived by this subset check.")


if __name__ == "__main__":
    main()
