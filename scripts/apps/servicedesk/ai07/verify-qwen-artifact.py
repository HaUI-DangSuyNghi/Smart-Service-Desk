"""Verify/stage the private Kaggle adapter and optionally freeze a serving bundle."""

import argparse
from hashlib import sha256
import json
from pathlib import Path
import shutil
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from _bootstrap import ROOT, write_new  # noqa: E402

if not sys.flags.isolated:
    sys.path.insert(0, str(ROOT / "intelligence/integrations"))

from hdpi.classification.protocols import require  # noqa: E402
from local_classification.qwen_artifacts import (  # noqa: E402
    BASE_REPOSITORY,
    SCHEMA,
    bundle_digest,
    canonical,
    file_digest,
    safe_path,
    runtime_source_digest,
    stage_adapter,
    verify_adapter,
    verify_bundle,
)
from servicedesk.intelligence.triage_ai07.guarded_decision import (
    POLICY_DIGEST as GUARD_DIGEST,
)  # noqa: E402
from servicedesk.intelligence.triage_ai07.qwen_policy import POLICY_DIGEST  # noqa: E402


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--stage", type=Path)
    parser.add_argument("--base-directory", type=Path)
    parser.add_argument("--base-revision")
    parser.add_argument("--runtime-identity")
    parser.add_argument("--dependencies-lock", type=Path)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    profile = json.loads(
        (
            ROOT
            / "intelligence/src/apps/servicedesk/intelligence/triage_ai07/qwen35-artifact-v0.1.json"
        ).read_bytes()
    )
    if args.stage:
        require(args.stage.resolve().is_relative_to((ROOT / ".local/ai07").resolve()))
        result = stage_adapter(args.source, args.stage, profile["files"])
    else:
        result = verify_adapter(args.source, profile["files"])
    result.update(
        {
            "schemaVersion": "ai07-qwen-artifact-verification@0.1.0",
            "release": "NO_GO",
            "trainingBaseRevision": "UNRECORDED",
            "servingReady": False,
        }
    )
    if args.base_directory:
        require(args.stage is not None and args.dependencies_lock is not None)
        require(args.base_revision is not None and args.runtime_identity is not None)
        base = args.base_directory.resolve(strict=True)
        names = [
            path.relative_to(base).as_posix()
            for path in base.rglob("*")
            if path.is_file()
        ]
        require(1 <= len(names) <= 32)
        for name in names:
            source = safe_path(base, name)
            require(
                source.name == "LICENSE" or source.suffix in (".json", ".safetensors")
            )
            target = args.stage / "base" / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, target)
        shutil.copyfile(args.dependencies_lock, args.stage / "dependencies.lock")
        files = {
            path.relative_to(args.stage).as_posix(): file_digest(path)
            for path in args.stage.rglob("*")
            if path.is_file()
        }
        manifest = {
            "schema": SCHEMA,
            "candidateId": "qwen35-08b-lora-labops-silver-r1",
            "baseRepository": BASE_REPOSITORY,
            "baseCommitSha": args.base_revision,
            "baseRevisionSemantics": "RECONSTRUCTED_SERVING_SNAPSHOT_NOT_PROVEN_TRAINING_REVISION",
            "trainingBaseRevision": "UNRECORDED",
            "files": files,
            "filesDigest": sha256(canonical(files)).hexdigest(),
            "inferencePromptPolicyDigest": POLICY_DIGEST,
            "appGuardPolicyDigest": GUARD_DIGEST,
            "runtimeIdentity": args.runtime_identity,
            "runtimeSourceDigest": runtime_source_digest(),
            "trainingSourceManifestDigest": profile["trainingSourceManifestSha256"],
            "trainingDevReferenceDigest": profile["trainingDevReferenceSha256"],
            "kagglePredictionsDigest": profile["predictionsSha256"],
            "trainedOn": "MACHINE_SILVER_UNREVIEWED",
            "reviewedGold": False,
            "operationalAuthority": False,
            "release": "NO_GO",
        }
        with (args.stage / "serving-manifest.json").open("xb") as stream:
            stream.write(canonical(manifest))
        identity = bundle_digest(manifest)
        verify_bundle(
            args.stage,
            expected_digest=identity,
            policy_digest=POLICY_DIGEST,
            guard_digest=GUARD_DIGEST,
            runtime_identity=args.runtime_identity,
        )
        result.update(
            {
                "modelDigest": identity,
                "policyDigest": POLICY_DIGEST,
                "guardDigest": GUARD_DIGEST,
                "integrityVerified": True,
                "servingReady": False,
                "gpuReplay": "NOT_RUN",
            }
        )
    write_new(args.output, result)
    print("PASS private artifact integrity; production NO_GO")


if __name__ == "__main__":
    main()
