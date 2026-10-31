"""Read-only, hash-bound inventory and label worksheets. No implicit label promotion."""

from hashlib import sha256
import json
from pathlib import Path
import subprocess


SOURCE_COMMIT = "f94f0ef102cbc7d5737ca05270b0affab1fde1a6"
SOURCE_FILES = (
    "fixtures/intelligence/service-desk/tickets/tickets-v0.1.jsonl",
    "fixtures/intelligence/service-desk/tickets/tickets-v0.2.jsonl",
    "fixtures/intelligence/service-desk/knowledge/manifest.json",
    "fixtures/intelligence/service-desk/knowledge/manifest-v0.2.json",
    "fixtures/intelligence/service-desk/retrieval/gold-v0.1.json",
    "fixtures/intelligence/service-desk/retrieval/qrels-v0.2.json",
    "fixtures/intelligence/service-desk/generation/dev-v0.1.json",
    "fixtures/intelligence/service-desk/generation/dev-v0.2.json",
)


def inventory(root):
    root = Path(root).resolve()
    entries = []
    legacy_paths = subprocess.run(
        [
            "git",
            "ls-tree",
            "-r",
            "--name-only",
            SOURCE_COMMIT,
            "--",
            "fixtures/intelligence/service-desk/knowledge/sources",
            "fixtures/intelligence/service-desk/knowledge/curated",
            "intelligence/src/apps/servicedesk/intelligence/profiles",
        ],
        cwd=root,
        check=True,
        capture_output=True,
        text=True,
    ).stdout.splitlines()

    for relative in (*SOURCE_FILES, *legacy_paths):
        path = root / relative
        raw = path.read_bytes()
        baseline = subprocess.run(
            ["git", "show", f"{SOURCE_COMMIT}:{relative}"],
            cwd=root,
            check=True,
            capture_output=True,
        ).stdout

        if raw != baseline:
            raise ValueError("Immutable source differs from pinned baseline")

        document = None

        if path.suffix == ".jsonl":
            document = [
                json.loads(line)
                for line in raw.decode("utf-8").splitlines()
                if line.strip()
            ]
        elif path.suffix == ".json":
            document = json.loads(raw)
        count = len(document) if isinstance(document, list) else None

        if isinstance(document, dict):
            for key in (
                "cases",
                "sources",
                "queries",
                "qrels",
                "documents",
                "artifacts",
            ):
                if isinstance(document.get(key), (list, dict)):
                    count = len(document[key])
                    break

        entries.append(
            {
                "path": relative,
                "sha256": sha256(raw).hexdigest(),
                "bytes": len(raw),
                "topLevelCount": count,
            }
        )

    return {
        "schemaVersion": "0.1.0",
        "sourceCommit": SOURCE_COMMIT,
        "files": entries,
        "labels": "DRAFT_NOT_GOLD",
        "rights": "PENDING",
        "labops": "NOT_IMPORTED_PENDING_RIGHTS",
        "promotion": "BLOCKED_PENDING_REVIEW",
    }


def worksheets(root):
    # Audit all immutable inputs before deriving any rows. Never copy provisional labels.
    audit = inventory(root)
    path = Path(root) / SOURCE_FILES[1]
    source = json.loads(path.read_text("utf-8").splitlines()[0])

    if source.get("split") != "DEV":
        raise ValueError("Only explicit DEV worksheets are supported")

    rows = []

    for line in path.read_text("utf-8").splitlines():
        item = json.loads(line)

        if item.get("split") == "HOLDOUT":
            continue

        if item.get("split") != "DEV":
            raise ValueError("Unknown split")

        rows.append(
            {
                "sampleId": item["id"],
                "family": item["scenarioFamily"],
                "inputDigest": sha256(
                    (item["title"] + "\n" + item["description"]).encode("utf-8")
                ).hexdigest(),
                "category": "",
                "impact": "",
                "urgency": "",
                "reviewer": "",
                "reviewStatus": "DRAFT",
                "sourceDigest": audit["files"][1]["sha256"],
            }
        )

    return rows
