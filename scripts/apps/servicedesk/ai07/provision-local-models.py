"""Import the two already-downloaded models into an isolated, dedicated Docker volume."""

import argparse
from hashlib import sha256
import json
from pathlib import Path
import re
import subprocess

from _bootstrap import write_new


OLLAMA_IMAGE = "ollama/ollama:0.40.8@sha256:242721b8a8dbb71c38fdc0a07ed60ca78363f278d33fdf171a48728abf40743a"
PYTHON_IMAGE = "python:3.12.7-slim@sha256:60d9996b6a8a3689d36db740b49f4327be3be09a21122bd02fb8895abb38b50d"
MODEL_TAGS = ("tev1:0.8b-q8_0", "gemma3:1b")
CONTAINER = "ai07-model-provision-v14"


def docker(*args, input=None):
    return subprocess.run(
        ["docker", *args], input=input, check=True, capture_output=True, text=True
    ).stdout.strip()


def host_files(root):
    root = Path(root).resolve(strict=True)
    result = {}

    def include(relative, expected=None, expected_size=None):
        path = root / relative

        if path.is_symlink() or not path.resolve(strict=True).is_relative_to(root):
            raise ValueError("Model artifact escaped its source root")

        hasher = sha256()

        with path.open("rb") as stream:
            for block in iter(lambda: stream.read(4 * 1024 * 1024), b""):
                hasher.update(block)

        actual = hasher.hexdigest()

        if expected is not None and actual != expected:
            raise ValueError("Downloaded model artifact hash differs")
        if expected_size is not None and path.stat().st_size != expected_size:
            raise ValueError("Downloaded model artifact size differs")

        result[relative] = {
            "path": relative,
            "sha256": actual,
            "bytes": path.stat().st_size,
        }

        if relative.startswith("manifests/") or path.stat().st_size < 200000:
            try:
                value = json.loads(path.read_bytes())
            except (ValueError, UnicodeError):
                return

            if not isinstance(value, dict) or "layers" not in value:
                return

            for reference in (value["config"], *value["layers"]):
                digest = reference["digest"]

                if re.fullmatch(r"sha256:[0-9a-f]{64}", digest) is None:
                    raise ValueError("Model artifact reference is invalid")

                blob = "blobs/" + digest.replace(":", "-")

                if blob not in result:
                    include(blob, digest[7:], reference["size"])

    for tag in MODEL_TAGS:
        name, version = tag.split(":")
        include(f"manifests/registry.ollama.ai/library/{name}/{version}")

    return list(result.values())


def provision(source, volume):
    if not re.fullmatch(r"[a-z][a-z0-9-]{1,63}", volume):
        raise ValueError("Invalid model volume name")

    # Refuse reusing any existing volume or container; each receipt is a distinct attempt.
    existing = docker("volume", "ls", "--format", "{{.Name}}")
    if volume in existing.splitlines():
        raise ValueError("Refusing to overwrite an existing model volume")
    if CONTAINER in docker("ps", "-a", "--format", "{{.Names}}").splitlines():
        raise ValueError("Provisioning container already exists")

    files = host_files(source)
    docker("volume", "create", "--label", "ai07.scope=synthetic-lab", volume)
    docker(
        "run",
        "--detach",
        "--name",
        CONTAINER,
        "--network",
        "none",
        "--env",
        "OLLAMA_HOST=127.0.0.1:11434",
        "--env",
        "OLLAMA_MODELS=/models",
        "--env",
        "OLLAMA_NO_CLOUD=1",
        "--mount",
        f"type=volume,source={volume},target=/models",
        OLLAMA_IMAGE,
    )

    try:
        for item in files:
            target = "/models/" + item["path"]
            docker(
                "exec",
                CONTAINER,
                "mkdir",
                "-p",
                str(Path(target).parent).replace("\\", "/"),
            )
            docker(
                "cp",
                str(Path(source).resolve() / item["path"]),
                f"{CONTAINER}:{target}",
            )

        # System One has no per-request options. An explicit derived alias pins effective context.
        code = """
import http.client
import json
import time


def call(path, value=None):
    c = http.client.HTTPConnection("127.0.0.1", 11434, timeout=60)
    c.request(
        "GET" if value is None else "POST",
        path,
        None if value is None else json.dumps(value),
        {"Content-Type": "application/json"},
    )
    r = c.getresponse()
    raw = r.read(262145)
    if r.status != 200:
        raise RuntimeError("Provisioning API rejected")
    c.close()
    return json.loads(raw)


for _ in range(30):
    try:
        call("/api/version")
        break
    except OSError:
        time.sleep(1)

call(
    "/api/create",
    {
        "model": "ai07-tev1-0.8b:ctx2048",
        "from": "tev1:0.8b-q8_0",
        "parameters": {"num_ctx": 2048},
        "stream": False,
    },
)
print(json.dumps({"version": call("/api/version"), "tags": call("/api/tags")}))
"""
        native = json.loads(
            docker(
                "run",
                "--rm",
                "-i",
                "--network",
                f"container:{CONTAINER}",
                PYTHON_IMAGE,
                "python",
                "-",
                input=code,
            )
        )
    finally:
        docker("rm", "--force", CONTAINER)

    inspect_code = """
import hashlib
import json
import pathlib

root = pathlib.Path("/models")
files = []

for path in root.rglob("*"):
    if path.is_symlink():
        continue
    path.chmod(0o555 if path.is_dir() else 0o444)

root.chmod(0o555)

for path in sorted(root.rglob("*")):
    if not path.is_file():
        continue
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(4 * 1024 * 1024), b""):
            h.update(block)
    files.append(
        {
            "path": path.relative_to(root).as_posix(),
            "sha256": h.hexdigest(),
            "bytes": path.stat().st_size,
        }
    )

print(json.dumps(files))
"""
    verified = json.loads(
        docker(
            "run",
            "--rm",
            "-i",
            "--network",
            "none",
            "--mount",
            f"type=volume,source={volume},target=/models",
            PYTHON_IMAGE,
            "python",
            "-",
            input=inspect_code,
        )
    )
    actual = {item["path"]: item for item in verified}

    for expected in files:
        if actual.get(expected["path"]) != expected:
            raise ValueError("Docker model import differs from verified host bytes")

    return {
        "contractVersion": "local-model-provision@0.1.0",
        "volume": volume,
        "ollamaImage": OLLAMA_IMAGE,
        "native": native,
        "files": verified,
        "licenseStatus": "PENDING_OWNER_REVIEW_LAB_ONLY",
        "productionApproved": False,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--volume", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    receipt = provision(args.source, args.volume)
    write_new(args.output, receipt)
    print(
        "PASS verified offline model import; derived context alias explicit; production approval pending"
    )


if __name__ == "__main__":
    main()
