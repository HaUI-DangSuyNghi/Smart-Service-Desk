"""Local source-only candidate. No license grant or publication."""
import csv
import hashlib
import io
import json
from pathlib import Path
import subprocess
import sys
import zipfile

BASELINE = "ef047a31abfd8e35ce9b6155e3a06c224262269d"
ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / ".local/public-source"
REPORT = ROOT / "docs/publication"


def exclusion(path):
    if path.startswith("docs/references/"):
        return "Third-party reference rights unverified"
    if path.startswith("fixtures/") and not path.endswith(".schema.json"):
        return "Fixture/data rights pending owner approval"
    if path == "frontend/public/landing/banner-phong-may.jpg":
        return "Photo rights unverified"
    if path.startswith(("task/", "docs/audits/", "docs/implementation/evidence/", "docs/security/")) or "/evidence/" in path:
        return "Internal evidence: publication/privacy review pending"
    if path.endswith((".jsonl", ".safetensors", ".onnx", ".gguf", ".pkl", ".joblib", ".zip", ".tar", ".gz")):
        return "Data/model/archive payload not approved"
    return ""


def prepare():
    if OUT.exists():
        raise SystemExit(f"Refusing to overwrite candidate: {OUT}")
    REPORT.mkdir(parents=True, exist_ok=True)
    raw = subprocess.check_output(["git", "-C", str(ROOT), "archive", "--format=zip", BASELINE])
    rows = []
    OUT.mkdir(parents=True)
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        for entry in archive.infolist():
            if entry.is_dir():
                continue
            path = entry.filename
            data = archive.read(entry)
            reason = exclusion(path)
            rows.append([path, hashlib.sha256(data).hexdigest(), "EXCLUDE" if reason else "INCLUDE_CONDITIONAL", reason or "Owner approval and final content/secret review required"])
            if not reason:
                target = OUT / path
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(data)
    hero = OUT / "frontend/src/platform/shell/landing/sections/Hero.tsx"
    content = hero.read_text(encoding="utf-8")
    old = '"url(\'/landing/banner-phong-may.jpg\')"'
    assert old in content
    hero.write_text(content.replace(old, '"linear-gradient(135deg, #152a31, #466b63)"'), encoding="utf-8")
    for name in ("README.md", "CHANGELOG.md"):
        (OUT / name).write_bytes((ROOT / name).read_bytes())
    for path in public_documents():
        target = OUT / "docs/publication" / path.name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(path.read_bytes())
    with (REPORT / "source-manifest.csv").open("w", newline="", encoding="utf-8") as stream:
        writer = csv.writer(stream)
        writer.writerow(["baseline_path", "baseline_sha256", "decision", "reason"])
        writer.writerows(rows)
    (OUT / "docs/publication/source-manifest.csv").write_bytes((REPORT / "source-manifest.csv").read_bytes())
    files = sorted(path for path in OUT.rglob("*") if path.is_file())
    finalize(files, rows)


def public_documents():
    return sorted(REPORT.glob("*.md")) + [REPORT / name for name in ("dependency-inventory.csv", "java-resolved-dependencies.txt", "java-license-metadata.csv", "java-artifact-notices.csv")]


def finalize(files, rows):
    manifest = {path.relative_to(OUT).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest() for path in files}
    provenance = {"baseline": BASELINE, "status": "PRIVATE_CANDIDATE_NOT_LICENSED_NOT_APPROVED", "files": manifest, "transformations": ["Replace uncleared photo with CSS gradient", "Overlay prepared README, CHANGELOG and publication docs"], "history": "Fresh repository; no commits/remotes; original untouched"}
    (OUT / "PUBLICATION_SOURCE.json").write_text(json.dumps(provenance, indent=2) + "\n", encoding="utf-8")
    if not (OUT / ".git").exists():
        subprocess.run(["git", "init", "--initial-branch=publication-candidate", str(OUT)], check=True)
    bundle_path = ROOT / ".local/smart-service-desk-v0.1.0-candidate-source.zip"
    with zipfile.ZipFile(bundle_path, "w", zipfile.ZIP_DEFLATED) as bundle:
        for path in files + [OUT / "PUBLICATION_SOURCE.json"]:
            bundle.write(path, "smart-service-desk-v0.1.0/" + path.relative_to(OUT).as_posix())
    print(json.dumps({"candidate": str(OUT), "included": len(files), "excluded": sum(row[2] == "EXCLUDE" for row in rows), "zip_sha256": hashlib.sha256(bundle_path.read_bytes()).hexdigest()}, indent=2))


def refresh_documents():
    provenance = json.loads((OUT / "PUBLICATION_SOURCE.json").read_text(encoding="utf-8"))
    names = set(provenance["files"])
    for path in public_documents():
        name = "docs/publication/" + path.name
        (OUT / name).write_bytes(path.read_bytes())
        names.add(name)
    for name in ("README.md", "CHANGELOG.md"):
        (OUT / name).write_bytes((ROOT / name).read_bytes())
    with (REPORT / "source-manifest.csv").open(encoding="utf-8", newline="") as stream:
        rows = list(csv.reader(stream))[1:]
    finalize([OUT / name for name in sorted(names)], rows)


def check():
    provenance = json.loads((OUT / "PUBLICATION_SOURCE.json").read_text(encoding="utf-8"))
    for name, digest in provenance["files"].items():
        assert not exclusion(name), name
        assert hashlib.sha256((OUT / name).read_bytes()).hexdigest() == digest, name
    assert not (OUT / "LICENSE").exists()
    assert not (OUT / "docs/references").exists()
    assert not (OUT / ".env").exists()
    assert "banner-phong-may.jpg" not in (OUT / "frontend/src/platform/shell/landing/sections/Hero.tsx").read_text(encoding="utf-8")
    assert not subprocess.check_output(["git", "-C", str(OUT), "remote"]).strip()
    bundle_path = ROOT / ".local/smart-service-desk-v0.1.0-candidate-source.zip"
    with zipfile.ZipFile(bundle_path) as bundle:
        expected = set(provenance["files"]) | {"PUBLICATION_SOURCE.json"}
        assert {name.removeprefix("smart-service-desk-v0.1.0/") for name in bundle.namelist()} == expected
        for name in provenance["files"]:
            assert hashlib.sha256(bundle.read("smart-service-desk-v0.1.0/" + name)).hexdigest() == provenance["files"][name]
    print("PASS: checksums, exclusions, photo replacement, no license grant, no remote")


if __name__ == "__main__":
    if "--check" in sys.argv:
        check()
    elif "--refresh-docs" in sys.argv:
        refresh_documents()
    else:
        prepare()
