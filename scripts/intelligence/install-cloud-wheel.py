"""Install and verify the optional vendor wheel separately from generic hdpi."""

import argparse
from email.parser import BytesParser
from pathlib import Path
import subprocess
import sys
import tomllib
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dist", type=Path, required=True)
    args = parser.parse_args()
    wheels = list(args.dist.glob("*.whl"))

    if len(wheels) != 1:
        raise ValueError("Expected exactly one cloud integration wheel")

    project = (
        Path(__file__).resolve().parents[2] / "intelligence/integrations/pyproject.toml"
    )
    expected = tomllib.loads(project.read_text("utf-8"))["project"]["version"]

    with zipfile.ZipFile(wheels[0]) as archive:
        names = archive.namelist()

        if any(
            not name.startswith(
                (
                    "cloud_providers/",
                    "local_classification/",
                    "smart_service_cloud_integration-",
                )
            )
            for name in names
        ):
            raise ValueError("Unexpected cloud wheel ownership")

        if any(
            name.startswith(("cloud_providers/", "local_classification/"))
            and not name.endswith(".py")
            for name in names
        ):
            raise ValueError("Integration wheels must contain code only")

        metadata_names = [
            name for name in names if name.endswith(".dist-info/METADATA")
        ]

        if len(metadata_names) != 1:
            raise ValueError("Invalid distribution metadata")

        metadata = BytesParser().parsebytes(archive.read(metadata_names[0]))

        if (
            metadata["Name"] != "smart-service-cloud-integration"
            or metadata["Version"] != expected
        ):
            raise ValueError("Cloud wheel identity mismatch")

    subprocess.run(
        [
            sys.executable,
            "-m",
            "pip",
            "install",
            "--no-deps",
            "--force-reinstall",
            str(wheels[0].resolve()),
        ],
        check=True,
    )

    subprocess.run(
        [
            sys.executable,
            "-I",
            "-c",
            "from pathlib import Path; import cloud_providers; assert 'site-packages' in Path(cloud_providers.__file__).parts",
        ],
        check=True,
    )
    print("PASS separate cloud wheel installation and import ownership")


if __name__ == "__main__":
    main()
