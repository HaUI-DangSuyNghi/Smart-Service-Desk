"""Build a code-only GPU image in an allowlisted context; no private artifacts."""

import argparse
from hashlib import sha256
from pathlib import Path
import shutil
import subprocess
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from _bootstrap import ROOT, write_new  # noqa: E402


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    output = args.output.resolve()
    if not output.is_relative_to((ROOT / ".local/ai07").resolve()) or output.exists():
        parser.error("Use a new evidence path inside .local/ai07")
    context = output.parent / ("qwen-image-" + output.stem)
    context.mkdir(parents=True, exist_ok=False)
    shutil.copyfile(
        ROOT / "intelligence/requirements-qwen-linux.lock",
        context / "requirements-qwen-linux.lock",
    )
    shutil.copyfile(
        ROOT / "intelligence/integrations/local_classification/Dockerfile.qwen",
        context / "Dockerfile",
    )
    for project in ("intelligence", "intelligence/integrations"):
        subprocess.run(
            [
                sys.executable,
                "-m",
                "build",
                "--wheel",
                "--no-isolation",
                "--outdir",
                str(context / "wheels"),
                str(ROOT / project),
            ],
            check=True,
        )
    files = sorted(path for path in context.rglob("*") if path.is_file())
    if any(path.suffix not in (".whl", ".lock", "") for path in files):
        raise ValueError("Unexpected build context file")
    identity = sha256()
    for path in files:
        identity.update(path.relative_to(context).as_posix().encode())
        identity.update(path.read_bytes())
    tag = "ai07-qwen-lora:" + identity.hexdigest()[:12]
    subprocess.run(["docker", "build", "--tag", tag, str(context)], check=True)
    image_id = subprocess.run(
        ["docker", "image", "inspect", tag, "--format", "{{.Id}}"],
        capture_output=True,
        text=True,
        check=True,
    ).stdout.strip()
    write_new(
        output,
        {
            "schemaVersion": "ai07-qwen-code-image@0.1.0",
            "imageId": image_id,
            "localTag": tag,
            "buildContextDigest": identity.hexdigest(),
            "containsWeights": False,
            "containsDataset": False,
            "productionApproved": False,
        },
    )
    print("PASS code-only Qwen GPU image built")


if __name__ == "__main__":
    main()
