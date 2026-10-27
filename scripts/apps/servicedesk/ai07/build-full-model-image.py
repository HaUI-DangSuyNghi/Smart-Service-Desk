"""Build an embedded-model private image; never publish or issue processing grants."""

import argparse
from datetime import datetime, timezone
from pathlib import Path
import shutil
import subprocess
import sys
import uuid

sys.path.insert(0, str(Path(__file__).resolve().parent))
from _bootstrap import ROOT
from full_model_release import APP_FILES, docker, inspect, read_json, require, verify_ancestry, verify_embedded, write_new

sys.path.insert(0, str(ROOT / "intelligence/integrations"))
from local_classification.qwen_artifacts import safe_path, verify_bundle
from servicedesk.intelligence.triage_ai07.qwen_policy import POLICY_DIGEST
from servicedesk.intelligence.triage_ai07.guarded_decision import POLICY_DIGEST as GUARD_DIGEST


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bundle", type=Path, required=True)
    parser.add_argument("--model-digest", required=True)
    parser.add_argument("--code-image", required=True, help="Exact local image ID, or a ref whose inspected ID is independently pinned")
    parser.add_argument("--expected-code-image-id", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    require(args.output.resolve().is_relative_to((ROOT / ".local/ai07").resolve()) and not args.output.exists())
    code = inspect(args.code_image)
    require(code["Id"] == args.expected_code_image_id, "Code image identity drift")
    # Dockerfile FROM interprets sha256:<engine-image-id> as a registry tag. Use a local
    # alias named by that ID, then verify ancestry independently after the build.
    code_alias = "ssd-ai07-code-pinned:" + code["Id"].removeprefix("sha256:")
    docker("tag", code["Id"], code_alias)
    require(inspect(code_alias)["Id"] == code["Id"])
    manifest = verify_bundle(args.bundle, expected_digest=args.model_digest, policy_digest=POLICY_DIGEST,
                             guard_digest=GUARD_DIGEST, runtime_identity=code["Id"])
    context = ROOT / ".local/ai07/d2" / ("full-build-" + uuid.uuid4().hex)
    context.mkdir(parents=True)
    (context / "model").mkdir()
    for relative in sorted(set(manifest["files"]) | {"serving-manifest.json"}):
        target = context / "model" / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(safe_path(args.bundle, relative), target)
    for relative in APP_FILES:
        source = ROOT / "intelligence/src/apps/servicedesk" / relative
        require(not source.is_symlink() and not source.is_junction())
        target = context / "apps/servicedesk" / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)
    shutil.copytree(ROOT / "licenses/models", context / "licenses/models")
    shutil.copytree(ROOT / "licenses/runtime-supplements", context / "licenses/runtime-supplements")
    shutil.copyfile(ROOT / "infra/docker/ai07-qwen-full/Dockerfile", context / "Dockerfile")
    tag = "ssd-ai07-qwen-full:" + args.model_digest[:16]
    docker("build", "--pull=false", "--network=none", "--tag", tag,
           "--build-arg", "CODE_IMAGE_REF=" + code_alias, "--build-arg", "CODE_IMAGE_ID=" + code["Id"],
           "--build-arg", "MODEL_DIGEST=" + args.model_digest, "--build-arg", "POLICY_DIGEST=" + POLICY_DIGEST, context)
    full = inspect(tag)
    verify_ancestry(code, full)
    verify_embedded(full["Id"], args.model_digest, POLICY_DIGEST, GUARD_DIGEST, code["Id"])
    write_new(args.output, {
        "schemaVersion": "ai07-full-model-image@1.0.0",
        "createdAt": datetime.now(timezone.utc).isoformat(),
        "sourceCommit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
        "worktreeDirty": bool(subprocess.check_output(["git", "status", "--porcelain"], cwd=ROOT, text=True).strip()),
        "codeImageId": code["Id"], "modelDigest": args.model_digest, "policyDigest": POLICY_DIGEST, "guardDigest": GUARD_DIGEST,
        "distributionImageId": full["Id"], "distributionImageRef": None, "localTag": tag,
        "os": full["Os"], "architecture": full["Architecture"], "containsWeights": True,
        "trainingBaseRevision": "UNRECORDED", "servingBaseRevision": manifest["baseCommitSha"],
        "integrity": "PASS_OFFLINE_NO_MOUNTS", "inference": "NOT_RUN", "distributionApproved": False,
        "productionApproved": False, "scope": "PRIVATE_LOCAL_ENGINEERING",
    })
    print("PASS embedded-model image integrity; inference NOT_RUN; distribution NOT_APPROVED; production NO_GO")


if __name__ == "__main__":
    main()
