import json
import re
import sys
from pathlib import Path
from urllib.parse import urljoin

import yaml
from openapi_schema_validator import OAS30Validator
from referencing import Registry, Resource
from referencing.jsonschema import DRAFT4

root = Path(__file__).resolve().parent.parent
spec_path = Path(sys.argv[2]).resolve() if len(sys.argv) > 2 else root / "contracts/openapi/core.yaml"
spec = yaml.safe_load(spec_path.read_text(encoding="utf-8"))
registry = Registry().with_resources(
    (path.resolve().as_uri(), Resource.from_contents(yaml.safe_load(path.read_text(encoding="utf-8")),
                                                    default_specification=DRAFT4))
    for path in spec_path.parent.glob("*.yaml"))

responses = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8-sig"))
assert responses, "No HTTP responses captured"
for response in responses:
    path = response["path"].split("?", 1)[0]
    routes = [path] if path in spec["paths"] else [route for route in spec["paths"] if re.fullmatch(re.sub(r"\{[^}]+\}", "[^/]+", route), path)]
    assert len(routes) == 1, f"Unknown or ambiguous route: {path}"
    operation = spec["paths"][routes[0]][response["method"].lower()]
    contract = operation["responses"][str(response["status"])]
    if "$ref" in contract:
        pointer = urljoin(spec_path.as_uri(), contract["$ref"])
    else:
        route_pointer = routes[0].replace("~", "~0").replace("/", "~1")
        pointer = f"{spec_path.as_uri()}#/paths/{route_pointer}/{response['method'].lower()}/responses/{response['status']}"
    media = "application~1problem+json" if response["status"] >= 400 else "application~1json"
    schema = {"$ref": pointer + "/content/" + media + "/schema"}
    errors = list(OAS30Validator(schema, registry=registry, format_checker=OAS30Validator.FORMAT_CHECKER)
                  .iter_errors(json.loads(response["bodyJson"])))
    assert not errors, f"{response['method']} {path}: {errors[0].message if errors else ''}"
print(f"PASS {len(responses)} real HTTP JSON responses match {spec_path.name}")
