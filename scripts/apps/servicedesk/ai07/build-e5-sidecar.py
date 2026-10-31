"""Build a verified, self-contained E5 CPU sidecar; explicit model download is opt-in.

Run after building the normal intelligence/Dockerfile image. No weights, predictions,
credentials or licensing approvals are copied to Git.
"""
import argparse
from hashlib import sha256
import json
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "scripts/apps/servicedesk/ai07"))
sys.path.insert(0, str(ROOT / "intelligence/src"))
from full_model_release import inspect, require
from e5_release import FILES, MODEL, REVISION, ARTIFACT_DIGEST, SCHEMA, verify_image
from hdpi.retrieval.dense.artifacts import FILES as SOURCE_FILES, ARTIFACT_DIGEST as SOURCE_DIGEST


def check_files(root):
    require(tuple(SOURCE_FILES) == FILES and SOURCE_DIGEST == ARTIFACT_DIGEST.removeprefix("sha256:"),
            "Repository E5 artifact pins changed; review the release contract")
    require(root.is_dir() and not root.is_symlink() and not root.is_junction(), "Model directory is missing")
    expected = {name for name, _, _ in FILES}
    actual = {p.relative_to(root).as_posix() for p in root.rglob("*") if p.is_file()}
    require(actual == expected, "E5 model context contains missing/unexpected files")
    inventory = {}
    for name, size, expected_hash in FILES:
        path = root / name
        require(not path.is_symlink() and not path.is_junction()
                and path.stat().st_size == size, "Invalid E5 artifact size/link")
        digest = sha256()
        with path.open("rb") as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(chunk)
        require(digest.hexdigest() == expected_hash, "E5 model artifact hash mismatch")
        inventory[name] = {"size": size, "sha256": expected_hash}
    return inventory


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model-directory", required=True, type=Path)
    parser.add_argument("--cpu-base-image", required=True, help="Verified base triage image ID or pinned alias")
    parser.add_argument("--tag", required=True, help="New local tag; manifest uses image ID, never mutable tag")
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--download", action="store_true", help="Explicitly provision Hugging Face pinned files")
    args = parser.parse_args()
    model = args.model_directory.resolve()
    output = args.output.resolve()
    require(output.is_relative_to((ROOT / ".local").resolve()) and not output.exists(),
            "Write a new E5 receipt under ignored .local only")
    require(model.is_relative_to((ROOT / ".local").resolve()), "Keep model assets under private .local")
    if args.download:
        from importlib.util import module_from_spec, spec_from_file_location
        spec = spec_from_file_location("pinned_e5_provision", ROOT / "scripts/intelligence/prepare-dense-model.py")
        module = module_from_spec(spec)
        spec.loader.exec_module(module)
        module.provision(model)
    files = check_files(model)
    base = inspect(args.cpu_base_image)
    require(base["Os"] == "linux" and base["Architecture"] == "amd64",
            "Only linux/amd64 CPU base image is supported")
    require(base["Config"]["User"] == "10001:10001", "Expected hardened CPU sidecar parent")
    require(re.fullmatch(r"[a-zA-Z0-9_.:-]+", args.tag) is not None, "Invalid Docker image tag")
    command = [
        "docker", "build", "--progress=plain", "--network=default",
        "--file", "intelligence/Dockerfile.e5",
        "--build-context", "e5-model=" + str(model),
        "--build-arg", "TRIAGE_BASE_IMAGE=" + base["Id"],
        "--build-arg", "TRIAGE_BASE_IMAGE_ID=" + base["Id"],
        "--tag", args.tag, ".",
    ]
    subprocess.run(command, cwd=ROOT, check=True)
    image = inspect(args.tag)
    receipt = {"schemaVersion": SCHEMA, "model": MODEL, "revision": REVISION,
               "artifactDigest": ARTIFACT_DIGEST, "imageId": image["Id"],
               "parentImageId": base["Id"], "files": files}
    verify_image(image["Id"], receipt)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("x", encoding="utf-8") as file:
        json.dump(receipt, file, sort_keys=True, indent=2)
        file.write("\n")
    print("E5_OFFLINE_IMAGE_PASS", image["Id"])
    print("E5 receipt:", output)


if __name__ == "__main__":
    main()
