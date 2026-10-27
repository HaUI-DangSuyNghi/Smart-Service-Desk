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
    parser.add_argument("--assessment-evidence", type=Path)
    parser.add_argument("--assessment-lifecycle", type=Path)
    parser.add_argument("--assignment-evidence", type=Path)
    arguments = parser.parse_args()
    import json
    evidence = json.loads(arguments.evidence.read_text(encoding="utf-8-sig"))
    documents = {path.resolve().as_uri(): yaml.safe_load(path.read_text(encoding="utf-8"))
                 for path in (ROOT / "contracts").rglob("*.yaml")}
    hpdi, app = documents[HPDI.as_uri()], documents[APP.as_uri()]
    ticket_validator = OAS30Validator({"$ref": APP.as_uri() + "#/components/schemas/Ticket"},
        registry=Registry().with_resources((uri, Resource(document, DRAFT4)) for uri, document in documents.items()))
    examples = app['paths']['/workspaces/{workspace}/tickets/{id}']['get']['responses']['200']['content']['application/json']['examples']
    for example in examples.values():
        ticket_validator.validate(example['value'])
    print('PASS live GET ticket examples: PENDING, READY, DEGRADED')
    if arguments.assessment_lifecycle:
        for state in ('pending', 'ready', 'degraded'):
            response = json.loads((arguments.assessment_lifecycle / (state + '.json')).read_text(encoding='utf-8'))
            ticket_validator.validate(response)
            assert response['assessment']['status'] == state.upper()
        print('PASS real Keycloak/Java/Python lifecycle responses against live OpenAPI: 3 checks')
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
    if arguments.assignment_evidence:
        assignment = json.loads(arguments.assignment_evidence.read_text(encoding="utf-8-sig"))
        validator = OAS30Validator({"$ref": HPDI.as_uri() + "#/components/schemas/AssignmentSuggestion"}, registry=registry)
        validator.validate(assignment)
        for field, value in (("taskId", None), ("estimateMinutes", 0), ("candidates", None), ("unknown", True)):
            invalid = copy.deepcopy(assignment)
            invalid[field] = value
            assert not validator.is_valid(invalid), "Suggestion schema must reject " + field
        print("PASS assignment suggestion HTTP contract: real response and four negative checks")
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
    if arguments.assessment_evidence:
        ticket = json.loads(arguments.assessment_evidence.read_text(encoding="utf-8-sig"))
        validator = OAS30Validator({"$ref": APP.as_uri() + "#/components/schemas/Ticket"}, registry=registry)
        validator.validate(ticket)
        assessment = ticket["assessment"]
        assert assessment["status"] == "READY" and assessment["result"] is not None
        for status in ("PENDING", "DEGRADED"):
            invalid = copy.deepcopy(ticket)
            invalid["assessment"]["status"] = status
            assert not validator.is_valid(invalid), "Unfinished assessment cannot release a result"
            invalid["assessment"]["result"] = None
            validator.validate(invalid)
        for mutation in ("missing-result", "fake-confidence", "missing-version", "unknown-status"):
            invalid = copy.deepcopy(ticket)
            if mutation == "missing-result":
                invalid["assessment"]["result"] = None
            elif mutation == "fake-confidence":
                invalid["assessment"]["result"]["confidence"] = 0.9
            elif mutation == "missing-version":
                del invalid["assessment"]["version"]
            else:
                invalid["assessment"]["status"] = "UNKNOWN"
            assert not validator.is_valid(invalid), "Schema must reject " + mutation
        print("PASS background assessment contract: 9 positive/negative checks against real READY HTTP evidence")


if __name__ == "__main__":
    main()
