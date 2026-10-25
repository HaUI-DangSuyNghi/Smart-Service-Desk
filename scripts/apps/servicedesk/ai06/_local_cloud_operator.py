"""Local smoke launcher: explicit operator files, no approval issuance or retries."""

import argparse
from hashlib import sha256
import os
from pathlib import Path
import subprocess
import sys

from _contracts import ROOT, DatasetError, digest, read_json, require, write_json


ENV_NAMES = (
    "GEMINI_API_KEY",
    "GROQ_API_KEY",
    "GEMINI_API_KEY_FILE",
    "GROQ_API_KEY_FILE",
    "PROVIDER_MODE",
    "PROVIDER_CATALOG_PATH",
    "PROVIDER_CATALOG_SHA256",
    "CLOUD_EGRESS_APPROVAL_FILE",
    "CLOUD_EGRESS_APPROVAL_SHA256",
    "CLOUD_ISOLATION_FILE",
    "CLOUD_ENVIRONMENT",
    "CLOUD_EGRESS_PROXY",
    "HDPI_PROFILE_ROOT",
)


def load_simple_env(path):
    values = {}

    for raw in path.read_text("utf-8-sig").splitlines():
        line = raw.strip()

        if not line or line.startswith("#"):
            continue

        if line.startswith("export "):
            line = line[7:].lstrip()

        if "=" not in line:
            continue

        name, value = (part.strip() for part in line.split("=", 1))

        if name not in ENV_NAMES:
            continue

        require(name not in values, "DUPLICATE_ENV_NAME")

        if value and value[0] in {"'", '"'}:
            require(len(value) >= 2 and value[-1] == value[0], "INVALID_ENV_QUOTING")
            value = value[1:-1]

        values[name] = value

    return values


def validate_cases(path):
    cases = read_json(path)
    require(type(cases) is list and len(cases) == 2, "TWO_CASES_REQUIRED")
    require(
        {case["expectedStatus"] for case in cases}
        == {"SUCCESS", "INSUFFICIENT_EVIDENCE"},
        "SMOKE_STATUS_COVERAGE",
    )

    for case in cases:
        require(
            set(case) == {"question", "documents", "expectedStatus"}, "SMOKE_CASE_SHAPE"
        )
        require(
            type(case["question"]) is str and 0 < len(case["question"]) <= 2000,
            "SMOKE_QUESTION_BUDGET",
        )
        require(
            type(case["documents"]) is list and 1 <= len(case["documents"]) <= 10,
            "SMOKE_REQUIRES_EVIDENCE_FOR_MODEL_REFUSAL",
        )
        require(
            sum(len(doc["content"].encode("utf-8")) for doc in case["documents"])
            <= 32000,
            "SMOKE_EXCERPT_BUDGET",
        )

        for document in case["documents"]:
            require(
                document["checksum"]
                == sha256(document["content"].encode("utf-8")).hexdigest(),
                "SMOKE_SOURCE_CHECKSUM",
            )

    return cases


def operator_environment(directory, environment, cases_path, *, installed=False):
    directory = directory.resolve(strict=True)
    require(not directory.is_relative_to(ROOT), "OPERATOR_DIRECTORY_MUST_BE_EXTERNAL")
    require(
        not cases_path.resolve().is_relative_to(ROOT),
        "EXECUTION_CASES_MUST_BE_EXTERNAL",
    )
    catalog = directory / "catalog.json"
    approval = directory / "approval.json"
    isolation = directory / "isolation.json"
    environment = dict(environment)
    environment.update(
        {
            "PROVIDER_MODE": "synthetic-cloud-dev",
            "PROVIDER_CATALOG_PATH": str(catalog),
            "PROVIDER_CATALOG_SHA256": digest(catalog.read_bytes()),
            "CLOUD_EGRESS_APPROVAL_FILE": str(approval),
            "CLOUD_EGRESS_APPROVAL_SHA256": digest(approval.read_bytes()),
            "CLOUD_ISOLATION_FILE": str(isolation),
            "CLOUD_ENVIRONMENT": "isolated-synthetic",
            "HDPI_PROFILE_ROOT": str(
                ROOT / "intelligence/src/apps/servicedesk/intelligence/profiles"
            ),
        }
    )

    if not installed:
        sys.path[:0] = [
            str(ROOT / "intelligence/src"),
            str(ROOT / "intelligence/integrations"),
        ]

    from cloud_providers.registry import ProviderRegistry

    registry = ProviderRegistry(
        catalog,
        environment["PROVIDER_CATALOG_SHA256"],
        approval,
        environment["CLOUD_EGRESS_APPROVAL_SHA256"],
        isolation,
        environment["CLOUD_ENVIRONMENT"],
        repository_root=ROOT,
    )
    require(
        set(route.vendor for route in registry.routes.values()) == {"gemini", "groq"},
        "BOTH_PROVIDER_ROUTES_REQUIRED",
    )
    cases = validate_cases(cases_path)

    for case in cases:
        require(
            digest(case["question"].encode("utf-8"))
            in registry.approval["questionDigests"],
            "SMOKE_QUESTION_NOT_APPROVED",
        )
        require(
            all(
                doc["checksum"] in registry.approval["sourceDigests"]
                for doc in case["documents"]
            ),
            "SMOKE_SOURCE_NOT_APPROVED",
        )

    return environment


def reserve_batch(directory, cases_path):
    # A crash consumes this scope. Changing --output cannot replay the same approval.
    path = directory / "execution-ledger.json"

    try:
        with path.open("x", encoding="utf-8", newline="\n") as stream:
            import json

            json.dump(
                {
                    "status": "RESERVED_NO_AUTOMATIC_REPLAY",
                    "maximumInferenceAttempts": 4,
                    "casesDigest": digest(cases_path.read_bytes()),
                },
                stream,
                sort_keys=True,
            )
            stream.write("\n")
    except FileExistsError:
        raise DatasetError("OPERATOR_SCOPE_ALREADY_RESERVED") from None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=ROOT)
    parser.add_argument("--env-file", type=Path, default=ROOT / ".env")
    parser.add_argument("--operator-dir", type=Path)
    parser.add_argument("--cases", type=Path)
    parser.add_argument(
        "--output", type=Path, default=ROOT / ".local/ai06-smoke-direct.json"
    )
    parser.add_argument("--installed", action="store_true")
    parser.add_argument("--execute", action="store_true")
    args = parser.parse_args()

    try:
        require(args.repo.resolve() == ROOT, "WRONG_REPOSITORY")
        cases = args.cases or (
            args.operator_dir / "smoke-cases.json"
            if args.operator_dir
            else ROOT / ".local/ai06_smoke_2cases.json"
        )
        validate_cases(cases)
        environment = os.environ.copy()
        environment.update(load_simple_env(args.env_file))
        print("Fixtures: 2 cases, UTF-8 checksums OK")
        print(
            "Secret sources present:",
            sum(
                bool(environment.get(name))
                for name in (
                    "GEMINI_API_KEY",
                    "GROQ_API_KEY",
                    "GEMINI_API_KEY_FILE",
                    "GROQ_API_KEY_FILE",
                )
            ),
        )

        if args.operator_dir:
            environment = operator_environment(
                args.operator_dir, environment, cases, installed=args.installed
            )

        if not args.execute:
            print("DRY_RUN_ONLY [ZERO_INFERENCE_REQUESTS]")
            return 0

        require(args.operator_dir is not None, "EXTERNAL_OPERATOR_DIRECTORY_REQUIRED")
        require(not args.output.exists(), "EXISTING_RECEIPT_PRESERVED")
        reserve_batch(args.operator_dir.resolve(), cases)
        command = [
            sys.executable,
            "-I",
            str(ROOT / "scripts/intelligence/cloud-api-smoke.py"),
            "--real-api",
            "--cases",
            str(cases.resolve()),
            "--output",
            str(args.output.resolve()),
        ]

        if args.installed:
            command.append("--installed")

        return subprocess.run(
            command, cwd=ROOT, env=environment, check=False
        ).returncode
    except Exception as error:
        print(
            "BLOCKED ["
            + (
                str(error)
                if isinstance(error, DatasetError)
                else "INVALID_OR_UNAPPROVED_LOCAL_CONFIGURATION"
            )
            + "]"
        )
        return 2
