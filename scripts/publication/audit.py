"""Read-only local Git/provenance audit; findings never include secret values."""
import collections
import csv
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
REPORT = ROOT / "docs/publication"
BASELINE = "ef047a31abfd8e35ce9b6155e3a06c224262269d"


def git(*args):
    return subprocess.check_output(["git", "-C", str(ROOT), *args])


def write_csv(name, header, rows):
    with (REPORT / name).open("w", newline="", encoding="utf-8") as stream:
        writer = csv.writer(stream)
        writer.writerow(header)
        writer.writerows(rows)


def audit():
    REPORT.mkdir(parents=True, exist_ok=True)
    commits = git("log", BASELINE, "--format=%H%x09%aI%x09%cI%x09%P%x09%s").decode().splitlines()
    write_csv("development-history.csv", ["commit", "author_time", "committer_time", "parents", "subject"], [line.split("\t", 4) for line in commits])
    refs = git("show-ref").decode().splitlines()
    objects = {}
    for line in git("rev-list", "--objects", "--all").decode().splitlines():
        parts = line.split(" ", 1)
        objects[parts[0]] = parts[1] if len(parts) > 1 else "[commit/tree]"
    patterns = {
        "private_key": re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----"),
        "github_token": re.compile(rb"\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,})\b"),
        "aws_access_key": re.compile(rb"\b(?:AKIA|ASIA)[A-Z0-9]{16}\b"),
        "google_api_key": re.compile(rb"\bAIza[A-Za-z0-9_-]{30,}\b"),
        "jwt_literal": re.compile(rb"\beyJ[A-Za-z0-9_-]{15,}\.[A-Za-z0-9_-]{15,}\.[A-Za-z0-9_-]{15,}"),
        "credential_assignment_review": re.compile(rb"(?i)(?:password|passwd|client_secret|api_key|access_token|secret)\s*[\"']?\s*[:=]\s*[\"'][^\"'\r\n]{8,}[\"']"),
    }
    findings, restricted = [], []
    blob_count = byte_count = binary_count = 0
    with tempfile.TemporaryFile() as source:
        source.write("".join(oid + "\n" for oid in objects).encode())
        source.seek(0)
        process = subprocess.Popen(["git", "-C", str(ROOT), "cat-file", "--batch"], stdin=source, stdout=subprocess.PIPE)
        for oid, name in objects.items():
            header = process.stdout.readline().decode().strip().split()
            size = int(header[2])
            data = process.stdout.read(size)
            assert len(data) == size and process.stdout.read(1) == b"\n"
            if header[1] != "blob":
                continue
            blob_count += 1
            byte_count += size
            if name.startswith("docs/references/") or name.endswith((".pdf", ".xlsx", ".jpg", ".png", ".safetensors", ".onnx", ".gguf")):
                restricted.append([oid, name, size, "REVIEW_OR_EXCLUDE_HISTORY"])
            if b"\0" in data:
                binary_count += 1
                continue
            for rule, pattern in patterns.items():
                for match in pattern.finditer(data):
                    findings.append([oid, name, data.count(b"\n", 0, match.start()) + 1, rule, "UNTRIAGED_NO_VALUE_RECORDED"])
        assert process.wait() == 0
    write_csv("secret-findings.csv", ["object", "representative_path", "line", "rule", "status"], findings)
    write_csv("historical-restricted-assets.csv", ["object", "representative_path", "bytes", "status"], restricted)
    (REPORT / "refs-snapshot.txt").write_text("\n".join(refs) + "\n", encoding="utf-8")
    summary = {"baseline": BASELINE, "scope": "All locally reachable refs at snapshot; no fetch; original repositories read-only", "refs": len(refs), "baseline_commits": len(commits), "local_all_ref_commits": len(git("rev-list", "--all").splitlines()), "objects": len(objects), "blobs": blob_count, "blob_bytes": byte_count, "binary_blobs_not_content_scanned": binary_count, "findings_by_rule": dict(collections.Counter(row[3] for row in findings)), "restricted_asset_blobs": len(restricted), "limitations": ["Patterns are not proof of absence; assignments include test/placeholders", "No private server-only refs, LFS payloads, untracked/runtime files, issue attachments or unknown secret formats", "Representative path only: objects can occur under multiple names", "No original history rewriting or asset deletion performed"]}
    (REPORT / "scan-summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    dependencies = []
    lock = json.loads((ROOT / "frontend/package-lock.json").read_text())
    for name, meta in lock["packages"].items():
        if name:
            dependencies.append(["npm", name, meta.get("version", ""), meta.get("resolved", ""), meta.get("license", "UNKNOWN"), "lock metadata; notice review pending"])
    for path in sorted((ROOT / "intelligence/docs/governance").glob("*resolution.json")) + sorted((ROOT / "intelligence/docs/governance").glob("*dependencies.json")):
        report = json.loads(path.read_text(encoding="utf-8"))
        for item in report.get("packages", []):
            dependencies.append(["python", item.get("name", ""), item.get("version", ""), item.get("source") or item.get("wheel", {}).get("url", ""), json.dumps(item.get("licenseMetadata") or item.get("licenseClassifiers") or "UNKNOWN"), str(path.relative_to(ROOT))])
    namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
    pom = ET.parse(ROOT / "backend/pom.xml")
    for item in pom.findall("m:dependencies/m:dependency", namespace):
        group = item.findtext("m:groupId", namespaces=namespace)
        name = item.findtext("m:artifactId", namespaces=namespace)
        dependencies.append(["java", group + ":" + name, item.findtext("m:version", "BOM_MANAGED_SEE_RESOLUTION", namespace), "Maven Central / backend/pom.xml", "UNKNOWN_REVIEW_UPSTREAM_POM_AND_NOTICES", "Direct declarations only; resolved Java report separate"])
    for path in ROOT.rglob("*"):
        if not path.is_file() or ".local" in path.parts or ".git" in path.parts:
            continue
        if path.name.startswith("Dockerfile") or "compose" in path.name and path.suffix in (".yaml", ".yml"):
            for line in path.read_text(encoding="utf-8").splitlines():
                match = re.match(r"\s*(?:FROM\s+|image:\s*)([^\s]+)", line)
                if match:
                    dependencies.append(["container", match[1], "See exact tag/digest", str(path.relative_to(ROOT)), "MIXED_IMAGE_PACKAGE_TERMS_REVIEW_REQUIRED", "Reference only, no image redistributed"])
    write_csv("dependency-inventory.csv", ["ecosystem", "component", "version", "origin", "license_evidence", "limitations"], dependencies)
    request = urllib.request.Request("https://api.github.com/repos/HaUI-DangSuyNghi/Smart-Service-Desk", headers={"User-Agent": "source-publication-readiness", "Accept": "application/vnd.github+json"})
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            metadata = json.load(response)
            result = {key: metadata.get(key) for key in ("full_name", "private", "visibility", "has_issues", "created_at", "default_branch")}
    except urllib.error.HTTPError as error:
        result = {"anonymous_http_status": error.code, "interpretation": "Not anonymously accessible; 404 does not independently prove privacy or Issues state"}
    except urllib.error.URLError as error:
        result = {"network_error": str(error.reason), "interpretation": "Remote metadata not verified"}
    (REPORT / "github-verification.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary, indent=2))
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    audit()
