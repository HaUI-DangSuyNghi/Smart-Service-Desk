"""Bind an imported model volume, code image and Tier-3 policies to a lab-only runtime."""

import argparse
from hashlib import sha256
import json
import os
from pathlib import Path
import secrets
import subprocess

from _bootstrap import ROOT, write_new
from experiment_policies import POLICIES


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--provision", type=Path, required=True)
    parser.add_argument("--bridge", type=Path, required=True)
    parser.add_argument("--directory", type=Path, required=True)
    parser.add_argument("--enable-synthetic-lab", action="store_true")
    parser.add_argument("--gemma-lane", choices=("G0", "G1"), default="G0")
    parser.add_argument("--tev-study", choices=("T0", "T1", "T2", "all"), default="T0")
    args = parser.parse_args()
    directory = args.directory.resolve()

    if (
        not directory.is_relative_to((ROOT / ".local/ai07").resolve())
        or directory.exists()
    ):
        raise ValueError("Runtime attempt directory must be new and inside .local/ai07")

    provision = json.loads(args.provision.read_text("utf-8"))
    bridge = json.loads(args.bridge.read_text("utf-8"))
    if provision["native"]["version"] != {"version": "0.40.8"}:
        raise ValueError("Imported runtime version differs")

    policies = (
        ROOT / "intelligence/src/apps/servicedesk/intelligence/triage_ai07/experiments"
    )
    tags = {item["name"]: item for item in provision["native"]["tags"]["models"]}
    candidates = {}

    for name, alias, source_tag, adapter, weight_digest in (
        (
            "tev1",
            "ai07-tev1-0.8b:ctx2048",
            "tev1:0.8b-q8_0",
            "tev1-systemone@0.1.0",
            "7daa72d2ed4fa16f5120bc1bb89713531d0f511c360e9228905ba88c092f13b5",
        ),
        (
            "gemma3",
            "gemma3:1b",
            "gemma3:1b",
            "gemma-chat-schema@0.1.0",
            "2f8c3d137f2496dcb4019e17a50f2974b89c1be1a742db3c80cc86f91cafeeef",
        ),
    ):
        filename = ("tev1" if name == "tev1" else "gemma") + "-policy-v0.1.json"
        candidates[name] = {
            "adapter": adapter,
            "model": alias,
            "apiDigest": tags[alias]["digest"],
            "sourceModelDigest": tags[source_tag]["digest"],
            "policyFile": filename,
            "policyDigest": sha256((policies / filename).read_bytes()).hexdigest(),
            "tokenizerDigest": weight_digest,
            "modelArtifactDigest": weight_digest,
            "quantization": tags[alias]["details"]["quantization_level"],
            "licenseStatus": "PENDING_OWNER_REVIEW_LAB_ONLY",
        }

    if args.gemma_lane == "G1":
        filename, adapter = POLICIES["gemma3-g1"]
        candidates["gemma3-g1"] = {
            **candidates["gemma3"],
            "adapter": adapter,
            "policyFile": filename,
            "policyDigest": sha256((policies / filename).read_bytes()).hexdigest(),
        }

    study_candidates = {
        "T0": (),
        "T1": ("tev1-t1",),
        "T2": ("tev1-t2-rotate", "tev1-t2-reverse", "tev1-t2-interleave"),
        "all": ("tev1-t1", "tev1-t2-rotate", "tev1-t2-reverse", "tev1-t2-interleave"),
    }
    for name in study_candidates[args.tev_study]:
        filename, adapter = POLICIES[name]
        candidates[name] = {
            **candidates["tev1"],
            "adapter": adapter,
            "policyFile": filename,
            "policyDigest": sha256((policies / filename).read_bytes()).hexdigest(),
        }

    runtime = {
        "contractVersion": "local-classification-runtime@0.1.0",
        "mode": "SYNTHETIC_LAB_ONLY",
        "ollamaVersion": "0.40.8",
        "ollamaImage": provision["ollamaImage"],
        "bridgeRevision": bridge["bridgeRevision"],
        "contextTokens": 2048,
        "modelFiles": provision["files"],
        "candidates": candidates,
    }
    directory.mkdir(parents=True)
    write_new(directory / "runtime.json", runtime)
    token = directory / "bridge-token"
    with token.open("x", encoding="ascii") as stream:
        stream.write(secrets.token_urlsafe(32) + "\n")

    if os.name == "nt":
        sid = subprocess.run(
            [
                "powershell",
                "-NoProfile",
                "-Command",
                "[Security.Principal.WindowsIdentity]::GetCurrent().User.Value",
            ],
            check=True,
            capture_output=True,
            text=True,
        ).stdout.strip()
        subprocess.run(
            ["icacls", str(token), "/inheritance:r", "/grant:r", f"*{sid}:(F)"],
            check=True,
            capture_output=True,
        )
    else:
        token.chmod(0o600)

    values = {
        "AI07_BRIDGE_IMAGE": bridge["imageId"],
        "AI07_MODEL_VOLUME": provision["volume"],
        "AI07_RUNTIME_CONFIG": (directory / "runtime.json").as_posix(),
        "AI07_POLICY_DIRECTORY": policies.as_posix(),
        "AI07_AUTH_TOKEN_FILE": token.as_posix(),
        "AI07_LOCAL_ENABLED": "1" if args.enable_synthetic_lab else "0",
    }
    with (directory / "compose.env").open(
        "x", encoding="utf-8", newline="\n"
    ) as stream:
        for key, value in values.items():
            stream.write(key + "=" + value + "\n")

    print(
        "PASS lab-only runtime configured; secret remains in a private file; model promotion blocked"
    )


if __name__ == "__main__":
    main()
