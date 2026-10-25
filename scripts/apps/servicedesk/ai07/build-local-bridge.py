"""Build a separate code-only image. No app policy, model bytes or operator secrets."""

import argparse
from hashlib import sha256
from pathlib import Path
import shutil
import subprocess
import sys

from _bootstrap import ROOT, write_new


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    output = Path(args.output).resolve()
    if not output.is_relative_to((ROOT / ".local/ai07").resolve()) or output.exists():
        raise ValueError("Build evidence must be new and inside .local/ai07")
    context = output.parent / ("bridge-build-" + output.stem)

    if context.exists():
        raise ValueError("Bridge build context already exists; preserve prior attempt")

    context.mkdir(parents=True)
    integration = ROOT / "intelligence/integrations/local_classification"
    files = sorted((ROOT / "intelligence/src/hdpi").rglob("*.py"))
    files += sorted(integration.rglob("*.py"))
    files.append(integration / "Dockerfile")
    hasher = sha256()

    for path in files:
        hasher.update(path.relative_to(ROOT).as_posix().encode())
        hasher.update(path.read_bytes())

    revision = hasher.hexdigest()
    (context / "bridge-revision").write_text(revision + "\n", encoding="ascii")
    target = context / "local_classification"
    target.mkdir()

    for path in integration.rglob("*.py"):
        destination = target / path.relative_to(integration)
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(path, destination)

    shutil.copyfile(integration / "Dockerfile", context / "Dockerfile")
    (context / ".dockerignore").write_text(
        "**/__pycache__/\n*.json\n*.gguf\n*.joblib\n*.env\n", encoding="ascii"
    )
    subprocess.run(
        [
            sys.executable,
            "-m",
            "build",
            "--wheel",
            "--no-isolation",
            "--outdir",
            str(context / "wheels"),
            str(ROOT / "intelligence"),
        ],
        check=True,
    )
    tag = "ai07-classification-bridge:" + revision[:12]
    subprocess.run(["docker", "build", "--tag", tag, str(context)], check=True)
    image_id = subprocess.run(
        ["docker", "image", "inspect", tag, "--format", "{{.Id}}"],
        capture_output=True,
        check=True,
        text=True,
    ).stdout.strip()
    write_new(
        args.output,
        {
            "contractVersion": "local-bridge-build@0.1.0",
            "bridgeRevision": revision,
            "imageId": image_id,
            "localTag": tag,
            "productionApproved": False,
        },
    )
    print("PASS code-only bridge image built; approval/signing remain separate")


if __name__ == "__main__":
    main()
