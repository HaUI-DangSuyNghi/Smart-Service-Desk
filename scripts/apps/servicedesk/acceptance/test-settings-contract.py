"""Validate settings HTTP evidence and require valid whole-document OpenAPI."""
import argparse
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
from urllib.parse import urljoin

import yaml
from openapi_schema_validator import OAS30Validator
from openapi_spec_validator import OpenAPIV30SpecValidator
from referencing import Registry, Resource
from referencing.jsonschema import DRAFT4

ROOT = Path(__file__).resolve().parents[4]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("evidence", type=Path)
    parser.add_argument("--baseline", default="7dfb2be8b36f0d1d1fdc6abc9cab45a987fe8bea")
    args = parser.parse_args()
    documents = {path.resolve().as_uri(): yaml.safe_load(path.read_text(encoding="utf-8"))
                 for path in (ROOT / "contracts").rglob("*.yaml")}
    registry = Registry().with_resources((uri, Resource(document, DRAFT4)) for uri, document in documents.items())
    responses = json.loads(args.evidence.read_text(encoding="utf-8-sig"))
    validated = 0
    for name, live_name, target_name, route, schema in (
        ("work", "hpdi/openapi.yaml", "hpdi/openapi-target.yaml", "/api/hpdi/v1/settings/work", "WorkSettings"),
        ("servicedesk", "openapi/servicedesk.yaml", "openapi/servicedesk-target.yaml", "/settings", "ServiceDeskSettings"),
    ):
        live = (ROOT / "contracts" / live_name).as_uri()
        target = (ROOT / "contracts" / target_name).as_uri()
        assert documents[live]["components"]["schemas"][schema] == documents[target]["components"]["schemas"][schema]
        assert documents[target]["paths"][route]["$ref"] == Path(live_name).name + "#/paths/" + route.replace("~", "~0").replace("/", "~1")
        selected = [response for response in responses if response["path"] ==
                    (route if name == "work" else "/api/apps/servicedesk/v1/settings")]
        if not selected:
            assert name == "servicedesk", "Missing work HTTP evidence"
            print("NOT_APPLICABLE Service Desk absent from Second-domain profile")
            continue
        assert {200, 400, 401, 403, 409, 503} <= {response["status"] for response in selected}
        for response in selected:
            if response["status"] == 500:
                print("EXPECTED 500: injected audit failure; not claimed as Problem-schema compliance")
                continue
            operation = documents[live]["paths"][route][response["method"].lower()]
            status = str(response["status"])
            media = "application~1problem+json" if response["status"] >= 400 else "application~1json"
            pointer = route.replace("~", "~0").replace("/", "~1")
            contract = operation["responses"][status]
            location = urljoin(live, contract["$ref"]) if "$ref" in contract else f"{live}#/paths/{pointer}/{response['method'].lower()}/responses/{status}"
            validator = OAS30Validator({"$ref": f"{location}/content/{media}/schema"},
                                       registry=registry, format_checker=OAS30Validator.FORMAT_CHECKER)
            validator.validate(json.loads(response["bodyJson"]))
            validated += 1
    print(f"PASS settings live/target schema parity and {validated} HTTP response schemas")
    relatives = ("hpdi/openapi.yaml", "hpdi/openapi-target.yaml", "openapi/servicedesk.yaml", "openapi/servicedesk-target.yaml", "openapi/core.yaml", "openapi/core-target.yaml")
    def errors(path):
        document = yaml.safe_load(path.read_text(encoding="utf-8"))
        return sorted((str(error.json_path), error.message) for error in
                      OpenAPIV30SpecValidator(document, base_uri=path.as_uri()).iter_errors())
    with tempfile.TemporaryDirectory(prefix="settings-spec-") as temporary:
        baseline = Path(temporary) / "contracts"
        shutil.copytree(ROOT / "contracts", baseline)
        for relative in relatives:
            (baseline / relative).write_bytes(subprocess.check_output(["git", "show", args.baseline + ":contracts/" + relative], cwd=ROOT))
        for relative in relatives:
            before, after = errors(baseline / relative), errors(ROOT / "contracts" / relative)
            assert not after, f"Whole-document OpenAPI failure: {relative}: {after}"
            print("PASS whole-document OpenAPI", relative, f"(resolved {len(before)} baseline diagnostics)")


if __name__ == "__main__":
    main()
