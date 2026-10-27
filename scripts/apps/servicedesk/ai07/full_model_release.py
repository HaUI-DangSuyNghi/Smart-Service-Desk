"""Private D2 release helpers. Distribution identities are external to model bytes."""

from hashlib import sha256
import json
from pathlib import Path, PurePosixPath
import re
import subprocess
import time

APP_FILES = (
    "intelligence/triage_ai07/__init__.py",
    "intelligence/triage_ai07/qwen_policy.py",
    "intelligence/triage_ai07/guarded_decision.py",
    "intelligence/triage_ai07/qwen35-lora-policy-v0.1.json",
    "intelligence/triage_ai07/qwen35-letter-map-v0.1.json",
    "intelligence/triage_ai07/guard-policy-v0.1.json",
)
IMAGE_ID = re.compile(r"sha256:[0-9a-f]{64}")
DIGEST = re.compile(r"[0-9a-f]{64}")


def require(condition, message="Release integrity check failed"):
    if not condition:
        raise ValueError(message)


def file_digest(path):
    result = sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            result.update(block)
    return result.hexdigest()


def unique_pairs(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, "Duplicate JSON key")
        result[key] = value
    return result


def read_json(path):
    path = Path(path)
    require(not path.is_symlink() and not path.is_junction() and path.stat().st_size <= 1024 * 1024)
    return json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=unique_pairs)


def write_new(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("x", encoding="utf-8", newline="\n") as stream:
        json.dump(value, stream, sort_keys=True, indent=2)
        stream.write("\n")


def safe_file(root, relative):
    require(type(relative) is str and "\\" not in relative and ":" not in relative)
    item = PurePosixPath(relative)
    require(not item.is_absolute() and item.as_posix() == relative and bool(item.parts))
    require(all(part not in (".", "..", "") for part in item.parts))
    root = Path(root).resolve(strict=True)
    path = root / relative
    require(path.resolve(strict=True).is_relative_to(root))
    for parent in (path, *path.parents):
        if parent == root:
            break
        require(not parent.is_symlink() and not parent.is_junction())
    require(path.is_file())
    return path


def docker(*arguments):
    result = subprocess.run(["docker", *map(str, arguments)], capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError("Docker operation failed: " + str(arguments[0]))
    return result.stdout


def inspect(image):
    value = json.loads(docker("image", "inspect", image))[0]
    require(IMAGE_ID.fullmatch(value["Id"]))
    return value


def verify_ancestry(code, wrapper):
    base = code["RootFS"]["Layers"]
    layers = wrapper["RootFS"]["Layers"]
    require(bool(base) and len(layers) > len(base) and layers[:len(base)] == base,
            "Wrapper does not extend the inspected code image")
    require((code["Os"], code["Architecture"]) == (wrapper["Os"], wrapper["Architecture"]), "Image platform changed")
    require(wrapper["Config"]["User"] == "10001:10001", "Non-root model user required")


def verify_embedded(image, model, policy, guard, code_id):
    # The immutable code image's verifier checks all model bytes and its own runtime
    # source digest. No host code/model mounts and no network can substitute files.
    script = (
        "from local_classification.qwen_artifacts import verify_bundle; "
        "from servicedesk.intelligence.triage_ai07.qwen_policy import POLICY_DIGEST; "
        "from servicedesk.intelligence.triage_ai07.guarded_decision import POLICY_DIGEST as G; "
        "import sys; "
        "assert POLICY_DIGEST==sys.argv[2] and G==sys.argv[3]; "
        "verify_bundle('/opt/model',expected_digest=sys.argv[1],policy_digest=POLICY_DIGEST,"
        "guard_digest=G,runtime_identity=sys.argv[4]); print('EMBEDDED_BUNDLE_VERIFIED')"
    )
    output = docker("run", "--rm", "--network", "none", "--read-only", "--cap-drop", "ALL",
                    "--security-opt", "no-new-privileges:true", "--entrypoint", "python", image,
                    "-c", script, model, policy, guard, code_id)
    require(output.strip() == "EMBEDDED_BUNDLE_VERIFIED")


def verify_receipt(receipt, *, code_ref=None, distribution_ref=None):
    require(receipt["schemaVersion"] == "ai07-full-model-image@1.0.0")
    require(receipt["productionApproved"] is False and receipt["trainingBaseRevision"] == "UNRECORDED")
    for key in ("codeImageId", "distributionImageId"):
        require(IMAGE_ID.fullmatch(receipt[key]))
    for key in ("modelDigest", "policyDigest", "guardDigest"):
        require(DIGEST.fullmatch(receipt[key]))
    # A signed offline archive binds config digests to local import aliases. Docker
    # 29 containerd IDs can be OCI index digests; classic engines expose config IDs.
    code, full = inspect(code_ref or receipt["codeImageId"]), inspect(distribution_ref or receipt["distributionImageId"])
    verify_ancestry(code, full)
    labels = full["Config"]["Labels"]
    for name, key in (("code-image-id", "codeImageId"), ("model-digest", "modelDigest"), ("policy-digest", "policyDigest")):
        require(labels.get("vn.haui.ssd." + name) == receipt[key], "Distribution label mismatch")
    verify_embedded(full["Id"], receipt["modelDigest"], receipt["policyDigest"],
                    receipt["guardDigest"], receipt["codeImageId"])
    return full


def verify_approval(path, signature, public_key, openssl="openssl"):
    # Signatures are issued outside this tool. Onboarding never generates an issuer key.
    result = subprocess.run([openssl, "dgst", "-sha256", "-verify", str(public_key),
                             "-signature", str(signature), str(path)], capture_output=True)
    require(result.returncode == 0, "External distribution approval signature is invalid")
    approval = read_json(path)
    require(approval["schemaVersion"] == "ai-d2-distribution-approval@1.0.0")
    require(approval["approved"] is True and approval["privateChannel"] is True)
    require(type(approval["expiresEpochSecond"]) is int and approval["expiresEpochSecond"] > time.time())
    require(bool(approval["issuer"]) and DIGEST.fullmatch(approval["modelDigest"]))
    return approval
