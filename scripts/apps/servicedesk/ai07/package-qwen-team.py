"""Export a private, portable Docker test kit. Never publish weights or issue grants."""

import argparse
import gzip
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from _bootstrap import ROOT  # noqa: E402

if not sys.flags.isolated:
    sys.path.insert(0, str(ROOT / "intelligence/integrations"))

from hdpi.classification.protocols import require  # noqa: E402
from local_classification.qwen_artifacts import file_digest, verify_bundle  # noqa: E402
from servicedesk.intelligence.triage_ai07.guarded_decision import (
    POLICY_DIGEST as GUARD_DIGEST,
)  # noqa: E402
from servicedesk.intelligence.triage_ai07.qwen_policy import POLICY_DIGEST  # noqa: E402


def copy_model_file(source, target):
    # Frozen weights can share local disk blocks; copying the kit to another machine
    # still produces ordinary files. Metadata stays independently editable.
    if Path(source).suffix == ".safetensors":
        try:
            os.link(source, target)
            return target
        except OSError:
            pass
    return shutil.copyfile(source, target)


def export_image(image, target):
    process = subprocess.Popen(
        ["docker", "save", image], stdout=subprocess.PIPE, stderr=subprocess.PIPE
    )
    try:
        with gzip.open(target, "wb", compresslevel=1) as stream:
            shutil.copyfileobj(process.stdout, stream, 1024 * 1024)
        process.stdout.close()
        process.stderr.read()
        if process.wait() != 0:
            raise RuntimeError("Docker image export failed")
    except Exception:
        process.kill()
        process.wait()
        raise


def export_licenses(image, destination):
    script = Path(__file__).with_name("export-qwen-licenses.py").resolve()
    subprocess.run(
        [
            "docker",
            "run",
            "--rm",
            "--network",
            "none",
            "--read-only",
            "--user",
            "0:0",
            "--cap-drop",
            "ALL",
            "--security-opt",
            "no-new-privileges:true",
            "--entrypoint",
            "python",
            "--mount",
            f"type=bind,source={script},target=/run/export-licenses.py,readonly",
            "--mount",
            f"type=bind,source={destination},target=/export",
            image,
            "/run/export-licenses.py",
            "--output-root",
            "/export/licenses/runtime",
            "--image-identity",
            image,
        ],
        check=True,
    )
    shutil.copytree(ROOT / "licenses/models", destination / "licenses/models")
    supplements = ROOT / "licenses/runtime-supplements"
    supplemental_manifest = json.loads((supplements / "provenance.json").read_bytes())
    runtime_manifest = json.loads(
        (destination / "licenses/runtime/manifest.json").read_bytes()
    )
    versions = {
        item["distribution"]: item["version"]
        for item in runtime_manifest["distributions"]
    }
    for item in supplemental_manifest["files"]:
        require(versions[item["distribution"]] == item["version"])
        require(file_digest(supplements / item["file"]) == item["sha256"])
    shutil.copytree(supplements, destination / "licenses/runtime-supplements")
    notice = (
        "# Giấy phép bên thứ ba\n\n"
        "Base Qwen: xem toàn văn `licenses/models/qwen35-08b/LICENSE` và provenance.\n\n"
        "Thư viện/CPython/Debian: xem toàn văn trong `licenses/runtime/` và manifest.\n\n"
        "Hai wheel không kèm file license được bổ sung từ đúng phiên bản nguồn tại `licenses/runtime-supplements/`.\n\n"
        "Các file được giữ nguyên; không gán một license chung cho repo, dataset hoặc adapter.\n"
    )
    (destination / "THIRD_PARTY_NOTICES.md").write_text(
        notice, encoding="utf-8", newline="\n"
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bundle", type=Path, required=True)
    parser.add_argument("--model-digest", required=True)
    parser.add_argument("--image-receipt", type=Path, required=True)
    parser.add_argument("--directory", type=Path, required=True)
    parser.add_argument(
        "--omit-image-tar",
        action="store_true",
        help="Local smoke only; recipient must already have the exact image",
    )
    args = parser.parse_args()
    require(not args.directory.exists())
    image = json.loads(args.image_receipt.read_bytes())["imageId"]
    verify_bundle(
        args.bundle,
        expected_digest=args.model_digest,
        policy_digest=POLICY_DIGEST,
        guard_digest=GUARD_DIGEST,
        runtime_identity=image,
    )
    # Package contains trained private weights; require Git exclusion for any repo path.
    destination = args.directory.resolve()
    if destination.is_relative_to(ROOT):
        ignored = subprocess.run(
            ["git", "check-ignore", "--quiet", str(destination)], cwd=ROOT
        )
        require(ignored.returncode == 0)
    destination.mkdir(parents=True)
    shutil.copytree(args.bundle, destination / "model", copy_function=copy_model_file)
    source = ROOT / "intelligence/src/apps/servicedesk"
    app = destination / "apps/servicedesk"
    relative_files = [
        "intelligence/triage_ai07/__init__.py",
        "intelligence/triage_ai07/qwen_policy.py",
        "intelligence/triage_ai07/guarded_decision.py",
        "intelligence/triage_ai07/qwen35-lora-policy-v0.1.json",
        "intelligence/triage_ai07/qwen35-letter-map-v0.1.json",
        "intelligence/triage_ai07/guard-policy-v0.1.json",
    ]
    for relative in relative_files:
        target = app / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source / relative, target)
    shutil.copyfile(
        ROOT / "infra/compose/compose.ai07-qwen-lora.yaml", destination / "compose.yaml"
    )
    for path in (Path(__file__).parent / "qwen-team").iterdir():
        if path.is_file():
            shutil.copyfile(path, destination / path.name)
    export_licenses(image, destination)
    if not args.omit_image_tar:
        export_image(image, destination / "qwen-image.tar.gz")
    files = {
        path.relative_to(destination).as_posix(): file_digest(path)
        for path in destination.rglob("*")
        if path.is_file()
    }
    package = {
        "schemaVersion": "ai07-qwen-team-package@0.1.0",
        "imageId": image,
        "modelDigest": args.model_digest,
        "policyDigest": POLICY_DIGEST,
        "files": files,
        "imageTarIncluded": not args.omit_image_tar,
        "thirdPartyLicensesIncluded": True,
        "dataIncluded": False,
        "secretsIncluded": False,
        "scope": "LOCAL_SYNTHETIC_TEST_ONLY",
        "rights": "PENDING_OWNER_CONFIRMATION",
        "selectedModel": None,
        "release": "NO_GO",
    }
    with (destination / "package.json").open(
        "x", encoding="utf-8", newline="\n"
    ) as stream:
        json.dump(package, stream, indent=2)
        stream.write("\n")
    print(
        "PASS private Docker test package exported; no dataset, secret or processing grant"
    )


if __name__ == "__main__":
    main()
