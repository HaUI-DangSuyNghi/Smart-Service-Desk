"""Inventory exact resolved JAR/POM notice evidence, without granting licenses."""
import csv
import hashlib
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[2]
REPORT = ROOT / "docs/publication"
CACHE = ROOT / ".local/m2"
NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def pom_path(group, artifact, version):
    return CACHE / group.replace(".", "/") / artifact / version / f"{artifact}-{version}.pom"


def licenses(path, seen=None):
    seen = set() if seen is None else seen
    if path in seen or not path.exists():
        return "UNKNOWN", "", ""
    seen.add(path)
    project = ET.parse(path)
    entries = project.findall("m:licenses/m:license", NS)
    if entries:
        names = "; ".join(entry.findtext("m:name", "UNNAMED", NS) for entry in entries)
        urls = "; ".join(entry.findtext("m:url", "", NS) for entry in entries)
        return names, urls, path.relative_to(CACHE).as_posix()
    parent = project.find("m:parent", NS)
    if parent is not None:
        coordinates = [parent.findtext("m:" + field, "", NS) for field in ("groupId", "artifactId", "version")]
        return licenses(pom_path(*coordinates), seen)
    return "UNKNOWN", "", ""


def main():
    metadata, notices = [], []
    for line in (REPORT / "java-resolved-dependencies.txt").read_text(encoding="utf-8").splitlines():
        coordinates = line.lstrip("| +-\\").split(":")
        if len(coordinates) < 5:
            continue
        group, artifact, kind = coordinates[:3]
        version, scope = coordinates[-2:]
        declared, urls, evidence = licenses(pom_path(group, artifact, version))
        directory = pom_path(group, artifact, version).parent
        classifier = "-" + coordinates[3] if len(coordinates) == 6 else ""
        jar = directory / f"{artifact}-{version}{classifier}.{kind}"
        names = []
        if jar.exists() and zipfile.is_zipfile(jar):
            with zipfile.ZipFile(jar) as archive:
                for name in archive.namelist():
                    if not name.endswith("/") and any(token in name.upper().split("/")[-1] for token in ("LICENSE", "NOTICE", "COPYING", "COPYRIGHT")):
                        names.append(name)
                        notices.append([group, artifact, version, name, hashlib.sha256(archive.read(name)).hexdigest()])
        metadata.append([group, artifact, version, scope, declared, urls, evidence, len(names), "METADATA_ONLY_REVIEW_REQUIRED" if names else "NO_BUNDLED_NOTICE_FOUND_REVIEW_UPSTREAM"])
    for filename, header, rows in [
        ("java-license-metadata.csv", ["group", "artifact", "version", "scope", "declared_license", "license_url", "pom_evidence", "jar_notice_entries", "status"], metadata),
        ("java-artifact-notices.csv", ["group", "artifact", "version", "jar_entry", "notice_sha256"], notices),
    ]:
        with (REPORT / filename).open("w", encoding="utf-8", newline="") as stream:
            writer = csv.writer(stream)
            writer.writerow(header)
            writer.writerows(rows)
    assert metadata and notices
    print(f"Resolved Java artifacts: {len(metadata)}; notice entries: {len(notices)}; UNKNOWN POM licenses: {sum(row[4] == 'UNKNOWN' for row in metadata)}")


if __name__ == "__main__":
    main()
