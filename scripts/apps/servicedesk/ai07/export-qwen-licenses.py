"""Export original third-party notices from the pinned image, without importing models."""

import argparse
from hashlib import sha256
from importlib.metadata import distributions
import json
from pathlib import Path
import re
import shutil


def copy_notice(source, target):
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, target)
    return sha256(target.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-root", type=Path, required=True)
    parser.add_argument("--image-identity", required=True)
    args = parser.parse_args()
    target = args.output_root
    target.mkdir(parents=True, exist_ok=False)
    inventory = []
    for distribution in sorted(
        distributions(), key=lambda item: item.metadata["Name"].lower()
    ):
        name = distribution.metadata["Name"]
        directory = re.sub(r"[^A-Za-z0-9_.-]", "_", name)
        notices = []
        for relative in distribution.files or ():
            parts = relative.parts
            basename = relative.name.lower()
            if not (
                any(part.lower() in ("licenses", "licences") for part in parts)
                or basename.startswith(
                    ("license", "licence", "copying", "copyright", "notice", "eula")
                )
            ):
                continue
            source = Path(distribution.locate_file(relative))
            if not source.is_file():
                continue
            filename = f"{len(notices):03d}-{source.name}"
            destination = target / directory / filename
            notices.append(
                {
                    "originalPath": str(relative),
                    "file": destination.relative_to(target).as_posix(),
                    "sha256": copy_notice(source, destination),
                }
            )
        own_code = name in (
            "smart-service-intelligence",
            "smart-service-cloud-integration",
        )
        inventory.append(
            {
                "distribution": name,
                "version": distribution.version,
                "licenseExpression": distribution.metadata.get("License-Expression"),
                "licenseClassifiers": [
                    value
                    for value in distribution.metadata.get_all("Classifier", [])
                    if value.startswith("License ::")
                ],
                "notices": notices,
                "scope": "PROJECT_CODE_NO_NEW_LICENSE_ASSIGNED"
                if own_code
                else "THIRD_PARTY",
                "noticeStatus": "BUNDLED_NOTICES_EXPORTED"
                if notices
                else "NO_LICENSE_FILE_IN_DISTRIBUTION",
            }
        )
    platform_notices = []
    sources = [
        Path("/usr/local/LICENSE"),
        Path("/usr/local/lib/python3.12/LICENSE.txt"),
    ]
    sources += sorted(Path("/usr/share/doc").glob("*/copyright"))
    sources += sorted(Path("/usr/share/common-licenses").glob("*"))
    for source in sources:
        if source.is_file():
            destination = target / "platform" / source.relative_to("/")
            platform_notices.append(
                {
                    "originalPath": str(source),
                    "file": destination.relative_to(target).as_posix(),
                    "sha256": copy_notice(source, destination),
                }
            )
    report = {
        "schemaVersion": "ai07-runtime-license-inventory@0.1.0",
        "imageIdentity": args.image_identity,
        "distributions": inventory,
        "platformNotices": platform_notices,
        "scope": "ORIGINAL_LICENSE_FILES_PRESERVED_NO_RELICENSING",
    }
    with (target / "manifest.json").open("x", encoding="utf-8", newline="\n") as stream:
        json.dump(report, stream, ensure_ascii=True, indent=2)
        stream.write("\n")
    print(
        f"Exported original notices for {len(inventory)} distributions and {len(platform_notices)} platform files"
    )


if __name__ == "__main__":
    main()
