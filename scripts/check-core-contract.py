import json
import re
import sys
from pathlib import Path

import yaml
from openapi_schema_validator import OAS30Validator

root = Path(__file__).resolve().parent.parent
spec = yaml.safe_load((root / "contracts/openapi/core.yaml").read_text(encoding="utf-8"))
responses = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8-sig"))
assert responses, "No HTTP responses captured"
for response in responses:
    path = response["path"].split("?", 1)[0]
    routes = [route for route in spec["paths"] if re.fullmatch(re.sub(r"\{[^}]+\}", "[^/]+", route), path)]
    assert len(routes) == 1, f"Unknown or ambiguous route: {path}"
    operation = spec["paths"][routes[0]][response["method"].lower()]
    contract = operation["responses"][str(response["status"])]
    if "$ref" in contract:
        contract = spec["components"]["responses"][contract["$ref"].split("/")[-1]]
    media = "application/problem+json" if response["status"] >= 400 else "application/json"
    schema = dict(contract["content"][media]["schema"], components=spec["components"])
    errors = list(OAS30Validator(schema, format_checker=OAS30Validator.FORMAT_CHECKER).iter_errors(json.loads(response["bodyJson"])))
    assert not errors, f"{response['method']} {path}: {errors[0].message if errors else ''}"
print(f"PASS {len(responses)} real HTTP JSON responses match core.yaml")
