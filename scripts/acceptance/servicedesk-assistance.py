"""Disposable PostgreSQL + real Core/Java/private Python synthetic acceptance.

JWT fixtures are used by MockMvc. This is not Keycloak/gateway/browser/model
acceptance and cannot authorize production. Only a newly owned container is
removed; no existing database, Docker volume or project is touched.
"""

import argparse
from contextlib import ExitStack
from hashlib import sha256
import json
import os
from pathlib import Path
import secrets
import subprocess
import sys
from threading import Thread
import time
from uuid import uuid4
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[2]
POSTGRES = "postgres:16.15-bookworm@sha256:0ea6700a3b4f0ae6ce746519073558aed4d88a79d8d07622a9a644946c7319c4"


def command(arguments, *, environment=None, check=True):
    return subprocess.run(
        arguments,
        env=environment,
        cwd=ROOT,
        check=check,
        capture_output=True,
        text=True,
    )


def start_server(stack, server):
    thread = Thread(target=server.serve_forever, daemon=True)
    thread.start()

    def stop():
        server.shutdown()
        server.server_close()
        thread.join(timeout=2)

    stack.callback(stop)
    return f"http://127.0.0.1:{server.server_address[1]}"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--maven", default="mvn")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument(
        "--tests", default="*Test,TicketAssistancePythonE2E,TicketTriagePythonE2E"
    )
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=False)

    sys.path.insert(0, str(ROOT / "intelligence/src"))
    from hdpi.assistance_http import make_server, reference_runtime
    from hdpi.http_service import make_server as triage_server

    os.environ["HDPI_PROFILE_ROOT"] = str(
        ROOT / "intelligence/src/apps/servicedesk/intelligence/profiles"
    )
    config = (
        ROOT
        / "intelligence/src/apps/servicedesk/intelligence/assistance-reference.json"
    )
    runtime = reference_runtime(config, sha256(config.read_bytes()).hexdigest())
    container = "ai06-acceptance-" + uuid4().hex[:16]
    password = secrets.token_hex(24)
    assistance_token = secrets.token_hex(32)
    triage_token = secrets.token_hex(32)
    environment = {**os.environ, "POSTGRES_PASSWORD": password}
    created = False
    summary = {
        "evidenceClass": "SYNTHETIC_TECHNICAL_EVIDENCE",
        "identityMode": "MockMvc JWT fixtures; actual Core, PostgreSQL and private Python HTTP",
        "productionDecision": "BLOCKED",
        "gatewayKeycloakBrowserRealModel": "NOT_VERIFIED",
        "result": "FAIL",
        "sourceRevision": command(["git", "rev-parse", "HEAD"]).stdout.strip(),
        "workingTreeDirty": bool(
            command(["git", "status", "--porcelain"]).stdout.strip()
        ),
        "postgresImage": POSTGRES,
    }

    try:
        command(
            [
                "docker",
                "run",
                "-d",
                "--name",
                container,
                "--label",
                "dx.acceptance=ai06-disposable",
                "--tmpfs",
                "/var/lib/postgresql/data",
                "-p",
                "127.0.0.1::5432",
                "-e",
                "POSTGRES_PASSWORD",
                "-e",
                "POSTGRES_USER=workflow",
                "-e",
                "POSTGRES_DB=file_storage",
                POSTGRES,
            ],
            environment=environment,
        )
        created = True

        for _ in range(45):
            ready = command(
                [
                    "docker",
                    "exec",
                    container,
                    "pg_isready",
                    "-U",
                    "workflow",
                    "-d",
                    "file_storage",
                ],
                check=False,
            )

            if ready.returncode == 0:
                break

            time.sleep(1)
        else:
            raise RuntimeError("Disposable PostgreSQL readiness failed")

        command(
            [
                "docker",
                "exec",
                container,
                "createdb",
                "-U",
                "workflow",
                "workflow_approval",
            ]
        )
        address = command(["docker", "port", container, "5432/tcp"]).stdout.strip()
        port = int(address.rsplit(":", 1)[1])

        with ExitStack() as stack:
            assistance_url = start_server(
                stack,
                make_server(
                    ("127.0.0.1", 0),
                    service_token=assistance_token,
                    runtime=runtime,
                ),
            )
            triage_url = start_server(
                stack,
                triage_server(
                    ("127.0.0.1", 0),
                    service_token=triage_token,
                ),
            )
            environment = {
                **os.environ,
                "FILE_TEST_JDBC_URL": f"jdbc:postgresql://127.0.0.1:{port}/file_storage",
                "FILE_TEST_DB_PASSWORD": password,
                "WORKFLOW_TEST_JDBC_URL": f"jdbc:postgresql://127.0.0.1:{port}/workflow_approval",
                "WORKFLOW_TEST_DB_PASSWORD": password,
                "INTELLIGENCE_ASSISTANCE_BASE_URL": assistance_url,
                "INTELLIGENCE_ASSISTANCE_TOKEN": assistance_token,
                "INTELLIGENCE_TRIAGE_BASE_URL": triage_url,
                "INTELLIGENCE_TRIAGE_TOKEN": triage_token,
            }

            with (args.output / "java-local.log").open("w", encoding="utf-8") as log:
                result = subprocess.run(
                    [
                        args.maven,
                        "-B",
                        "-ntp",
                        "-f",
                        str(ROOT / "backend/pom.xml"),
                        "-Dtest=" + args.tests,
                        "-Dspring.test.context.cache.maxSize=2",
                        "test",
                    ],
                    cwd=ROOT,
                    env=environment,
                    stdout=log,
                    stderr=subprocess.STDOUT,
                )

            if result.returncode:
                raise RuntimeError("Java acceptance failed; inspect private local log")

        reports = ROOT / "backend/target/surefire-reports"
        suites = []

        # Include only suites actually executed by this invocation, not stale
        # XML left by another test command in the shared target directory.
        import fnmatch

        patterns = args.tests.split(",")

        for path in sorted(reports.glob("TEST-*.xml")):
            suite = ET.parse(path).getroot()
            name = suite.attrib["name"]

            if not any(
                fnmatch.fnmatchcase(name.rsplit(".", 1)[-1], pattern)
                for pattern in patterns
            ):
                continue

            counts = {
                key: int(suite.get(key, 0))
                for key in ("tests", "failures", "errors", "skipped")
            }

            if counts["tests"] <= 0 or any(
                counts[key] for key in ("failures", "errors", "skipped")
            ):
                raise RuntimeError("Skipped, failed or empty acceptance suite")

            suites.append({"name": name, **counts})

        if not any(
            item["name"].endswith("TicketAssistancePythonE2E") for item in suites
        ):
            raise RuntimeError("Required assistance integration suite missing")

        summary.update(
            result="PASS", suites=suites, tests=sum(item["tests"] for item in suites)
        )
        print(
            f'PASS {summary["tests"]} Java tests; synthetic acceptance, production BLOCKED'
        )
    finally:
        if created:
            # The exact random name was created by this invocation. No host
            # filesystem paths, persistent volumes or preexisting projects.
            cleanup = command(["docker", "rm", "-f", container], check=False)
            summary["disposableContainerRemoved"] = cleanup.returncode == 0

        (args.output / "summary.json").write_text(
            json.dumps(summary, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )


if __name__ == "__main__":
    main()
