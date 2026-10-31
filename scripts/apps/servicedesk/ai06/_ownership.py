"""App-owned guard for generic source, new shared tooling and built artifacts.

Legacy shared tooling is measured against the frozen Git tree, never granted a
new debt exception. This AST/string guard complements independent code review.
"""

import ast
from collections import Counter
from email.parser import BytesParser
from functools import lru_cache
import json
from pathlib import Path
import re
import subprocess
import zipfile

from _contracts import BASELINE_REVISION, DATA, ROOT, read_json


APP_SCRIPTS = "scripts/apps/servicedesk/"
APP_TESTS = "intelligence/tests/apps/servicedesk/"
BYPASS_FLAGS = {
    "skip-approval",
    "bypass-egress",
    "allow-unapproved",
    "skip-egress-check",
}


@lru_cache(maxsize=1)
def owned_literals():
    contract = read_json(
        ROOT / "intelligence/src/apps/servicedesk/intelligence/boundary-semantics.json"
    )
    categories = read_json(DATA / "generation-plan-v0.2.json")["categories"]
    return tuple(contract["ownedLiterals"]), {item["id"] for item in categories}


def constant_string(node):
    if isinstance(node, ast.Constant) and type(node.value) is str:
        return node.value

    if isinstance(node, ast.BinOp) and isinstance(node.op, ast.Add):
        left, right = constant_string(node.left), constant_string(node.right)

        if left is not None and right is not None:
            return left + right

    return None


def semantic_violations(source, *, generic=True):
    try:
        tree = ast.parse(source)
    except SyntaxError:
        return Counter({("INVALID_PYTHON", "syntax"): 1})

    literals, categories = owned_literals()
    found = Counter()
    parents = {child: parent for parent in ast.walk(tree) for child in ast.iter_child_nodes(parent)}

    for node in ast.walk(tree):
        if isinstance(node, (ast.Import, ast.ImportFrom)):
            names = (
                [item.name for item in node.names]
                if isinstance(node, ast.Import)
                else [node.module or ""]
            )

            for name in names:
                if generic and (name == "apps" or name.startswith("apps.")):
                    found[("UPSTREAM_APP_IMPORT", name)] += 1

                if not generic and (
                    name == "core"
                    or name.startswith("core.")
                    or name.startswith("hdpi.")
                    and any(
                        part.startswith("_") or part == "internal"
                        for part in name.split(".")
                    )
                ):
                    found[("PRIVATE_OR_CORE_IMPORT", name)] += 1

        value = constant_string(node)

        if generic and value is not None:
            folded = value.casefold().replace("\\", "/")

            if (
                any(re.search(r"(?<![a-z0-9-])" + re.escape(literal.casefold()), folded) for literal in literals)
                or value in categories
            ):
                found[("APP_SEMANTIC_LITERAL", value)] += 1

            if (
                (folded == "apps" and isinstance(parents.get(node), (ast.Call, ast.BinOp)))
                or folded.startswith("apps.")
                or any(
                    part in folded
                    for part in (
                        "/apps/",
                        "source-facts-v0.2",
                        "templates-v0.2",
                        "curation-v0.2",
                        "dev-v0.2.json",
                    )
                )
            ):
                found[("APP_RESOURCE_DEPENDENCY", value)] += 1

        if (
            not generic
            and isinstance(node, ast.Call)
            and isinstance(node.func, ast.Attribute)
            and node.func.attr == "add_argument"
        ):
            flags = [constant_string(argument) for argument in node.args]

            for flag in flags:
                if flag and flag.lstrip("-") in BYPASS_FLAGS:
                    found[("EGRESS_BYPASS_FLAG", flag)] += 1

    return found


@lru_cache(maxsize=512)
def historical_source(relative):
    result = subprocess.run(
        ["git", "show", BASELINE_REVISION + ":" + relative],
        cwd=ROOT,
        capture_output=True,
    )

    if result.returncode:
        return None

    return result.stdout.decode("utf-8")


def audit_delta(relative, source, previous=None):
    app_owned = relative.startswith((APP_SCRIPTS, APP_TESTS))
    current = semantic_violations(source, generic=not app_owned)

    if app_owned:
        return current

    if relative.startswith(
        ("intelligence/src/hdpi/", "intelligence/integrations/cloud_providers/")
    ):
        return current

    if previous is None:
        return current

    return current - semantic_violations(previous)


def audit_repository(root=ROOT):
    # The frozen tree must exist. A shallow/incorrect checkout is not a PASS.
    subprocess.run(
        ["git", "cat-file", "-e", BASELINE_REVISION + "^{commit}"],
        cwd=ROOT,
        check=True,
        capture_output=True,
    )
    failures = []
    inspected = 0
    legacy = 0
    roots = (
        "intelligence/src/hdpi",
        "intelligence/integrations/cloud_providers",
        "scripts",
        "intelligence/tests",
    )

    for prefix in roots:
        for path in sorted((root / prefix).rglob("*.py")):
            relative = path.relative_to(root).as_posix()
            previous = historical_source(relative)
            source = path.read_text("utf-8")
            owned = relative.startswith((APP_SCRIPTS, APP_TESTS))
            shared = (
                relative.startswith(("scripts/intelligence/", "intelligence/tests/"))
                and not owned
            )
            generic = relative.startswith(
                ("intelligence/src/hdpi/", "intelligence/integrations/cloud_providers/")
            )

            if not (owned or shared or generic or previous is None):
                continue

            inspected += 1
            observed = audit_delta(relative, source, previous)

            if shared and previous is not None and semantic_violations(previous):
                legacy += 1

            for (rule, _), count in observed.items():
                failures.append({"path": relative, "rule": rule, "count": count})

    return {
        "result": "FAIL" if failures else "PASS",
        "inspectedFiles": inspected,
        "legacySharedModulesMeasuredAgainstFrozenTree": legacy,
        "baselineRevision": BASELINE_REVISION,
        "scope": "FULL_GENERIC_SOURCE_PLUS_NEW_OR_ADDED_SHARED_DEPENDENCIES_AND_APP_PUBLIC_IMPORTS",
        "limitations": "AST/string evidence is not proof against arbitrary encoded or runtime-computed dependencies; independent review required.",
        "violations": failures,
    }


def resource_violations(value):
    literals, categories = owned_literals()
    failures = []

    if isinstance(value, str):
        folded = value.casefold()

        if (
            any(re.search(r"(?<![a-z0-9-])" + re.escape(literal.casefold()) + r"(?![a-z0-9-])", folded) for literal in literals)
            or value in categories
        ):
            failures.append("APP_RESOURCE_LITERAL")
    elif isinstance(value, dict):
        for key, item in value.items():
            failures.extend(resource_violations(key))
            failures.extend(resource_violations(item))
    elif isinstance(value, list):
        for item in value:
            failures.extend(resource_violations(item))

    return failures


def audit_wheel(path):
    failures = []

    with zipfile.ZipFile(path) as archive:
        names = set(archive.namelist())
        contracts = {
            "hdpi/_resources/contracts/" + item.name: item.read_bytes()
            for item in (ROOT / "contracts/intelligence/v1").iterdir()
            if item.is_file()
        }
        required = {"hdpi/__init__.py"} | contracts.keys()
        metadata_names = [
            name for name in names if name.endswith(".dist-info/METADATA")
        ]

        if not required <= names or len(metadata_names) != 1:
            failures.append(
                {"path": "distribution", "rule": "MISSING_GENERIC_WHEEL_CONTENT"}
            )
        else:
            metadata = BytesParser().parsebytes(archive.read(metadata_names[0]))

            if (
                metadata["Name"] != "smart-service-intelligence"
                or metadata["Version"] != "0.3.0"
            ):
                failures.append(
                    {"path": "distribution", "rule": "WRONG_GENERIC_DISTRIBUTION"}
                )

        for member in archive.infolist():
            name = member.filename
            parts = Path(name).parts

            if any(part in {"apps", "fixtures", "config"} for part in parts) or any(
                marker in name
                for marker in (
                    "source-facts",
                    "templates-v0.2",
                    "curation-v0.2",
                    "generation-plan",
                    "dev-v0.2",
                )
            ):
                failures.append({"path": name, "rule": "APP_WHEEL_MEMBER"})

            if not name.startswith("hdpi/"):
                continue

            if member.file_size > 2 * 1024 * 1024:
                failures.append(
                    {"path": name, "rule": "UNEXPECTED_WHEEL_RESOURCE_SIZE"}
                )
                continue

            raw = archive.read(name)

            if name in contracts:
                if raw != contracts[name]:
                    failures.append({"path": name, "rule": "CONTRACT_RESOURCE_DRIFT"})

                if name.endswith(".json"):
                    for rule in resource_violations(json.loads(raw)):
                        failures.append({"path": name, "rule": rule})
            elif name.endswith(".py"):
                for (rule, _), count in semantic_violations(
                    raw.decode("utf-8")
                ).items():
                    failures.append({"path": name, "rule": rule, "count": count})
            elif name.endswith(".json"):
                if not name.startswith(
                    "hdpi/_resources/contracts/"
                ) or not name.endswith(".schema.json"):
                    failures.append({"path": name, "rule": "NON_CONTRACT_RESOURCE"})

                for rule in resource_violations(json.loads(raw)):
                    failures.append({"path": name, "rule": rule})
            elif not name.endswith("/") and name != "hdpi/py.typed":
                failures.append({"path": name, "rule": "NON_CONTRACT_RESOURCE"})

    return {"result": "FAIL" if failures else "PASS", "violations": failures}
