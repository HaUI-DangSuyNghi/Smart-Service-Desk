"""Offline D2 kit: externally approved export, signed verification and local lifecycle.

Python 3.12+, Docker Compose v2 and OpenSSL are installed by the recipient before
going offline. The public trust key and expected manifest hash arrive separately.
No release command issues rights grants, signs approvals or removes data volumes.
"""

import argparse
import base64
import hmac
from datetime import datetime, timezone
import json
import os
from pathlib import Path, PurePosixPath
import re
import secrets
import shutil
import subprocess
import sys
import tarfile
import time

sys.dont_write_bytecode = True

# Exported kits carry this exact helper as an inventoried, signed source file.
HELPERS = Path(__file__).resolve().parent / "lib"
if not HELPERS.exists():
    HELPERS = Path(__file__).resolve().parent
sys.path.insert(0, str(HELPERS))
from full_model_release import (docker, file_digest, inspect, read_json, require,
                                safe_file, verify_approval, verify_receipt, write_new)
from e5_release import validate_receipt as verify_e5_receipt, verify_image as verify_e5_image

ROOT = Path(__file__).resolve().parents[4]
SERVICES = ("postgres", "keycloak", "platform", "gateway", "frontend", "intelligence", "ai07-qwen")
AUX_IMAGES = ("model-code", "e5-base")
STATIC_FILES = (
    "compose.yaml", "infra/compose/compose.triage.yaml", "infra/compose/compose.ai-d2-release.yaml",
    "infra/compose/compose.ai-d2-synthetic.yaml", "infra/postgres/init.sql",
    "infra/keycloak/realm-template.json", "infra/apisix/config.yaml", "infra/apisix/apisix.yaml",
    "scripts/setup.ps1", "scripts/apps/servicedesk/ai07/team_release.py", "scripts/ai-d2/runtime.env.example",
    "scripts/ai-d2/synthetic_acceptance.py",
    "scripts/ai-d2/Verify-Release.ps1", "scripts/ai-d2/Install-Team.ps1", "scripts/ai-d2/Start-Team.ps1",
    "scripts/ai-d2/Stop-Team.ps1", "scripts/ai-d2/Test-Team.ps1", "scripts/ai-d2/Export-Diagnostics.ps1",
    "scripts/ai-d2/Configure-Assisted.ps1",
    "scripts/ai-d2/release.schema.json", "scripts/ai-d2/e5_release.py",
    "docs/implementation/AI_D2_PORTABLE_DOCKER_VI.md",
)
PROFILE = "intelligence/src/apps/servicedesk/intelligence/profiles"
IMAGE_ENV = {"platform": "AI_D2_PLATFORM_IMAGE", "frontend": "AI_D2_FRONTEND_IMAGE", "gateway": "AI_D2_GATEWAY_IMAGE",
             "postgres": "AI_D2_POSTGRES_IMAGE", "keycloak": "AI_D2_KEYCLOAK_IMAGE", "intelligence": "AI_D2_TRIAGE_IMAGE",
             "ai07-qwen": "AI07_QWEN_DISTRIBUTION_IMAGE"}


def relative_name(value):
    require(type(value) is str and len(value) < 300 and "\\" not in value and ":" not in value)
    path = PurePosixPath(value)
    require(bool(path.parts) and not path.is_absolute() and path.as_posix() == value
            and all(part not in (".", "..", "") for part in path.parts), "Unsafe archive or inventory path")
    return value


def validate_archive(path):
    """Do not extract Docker archives onto the host; reject links/devices/traversal."""
    seen = set()
    with tarfile.open(path, "r|*") as archive:
        for member in archive:
            name = relative_name(member.name.rstrip("/"))
            require(name not in seen and (member.isfile() or member.isdir()), "Unsafe image archive member")
            seen.add(name)
    require("manifest.json" in seen or "index.json" in seen, "Not a Docker/OCI archive")


def inventory(root):
    result = {}
    for path in sorted(root.rglob("*")):
        require(not path.is_symlink() and not path.is_junction(), "Linked release files prohibited")
        if path.is_file():
            name = path.relative_to(root).as_posix()
            result[name] = {"sha256": file_digest(path), "size": path.stat().st_size}
    return result


def archive_config_digests(path):
    """Docker engine IDs may be OCI indexes on Docker 29; read config digests separately."""
    result = {}
    with tarfile.open(path, "r") as archive:
        member = archive.getmember("manifest.json")
        require(member.isfile() and member.size <= 1024 * 1024)
        entries = json.loads(archive.extractfile(member).read())
        for entry in entries:
            config_name = relative_name(entry["Config"])
            config = archive.getmember(config_name)
            require(config.isfile() and config.size <= 2 * 1024 * 1024)
            digest = "sha256:" + __import__("hashlib").sha256(archive.extractfile(config).read()).hexdigest()
            for tag in entry.get("RepoTags") or []:
                require(tag not in result or result[tag] == digest)
                result[tag] = digest
    return result


def verify_files(root, manifest):
    require(manifest["schemaVersion"] == "ai-d2-offline-release@1.0.0" and manifest["productionApproved"] is False)
    require(manifest["os"] == "linux" and manifest["architecture"] == "amd64", "Unsupported release platform")
    files = manifest["files"]
    require(type(files) is dict and 1 <= len(files) <= 5000)
    require(set(manifest["images"]) == set(SERVICES) | set(AUX_IMAGES), "Incomplete offline image set")
    require("e5-image.json" in files, "Signed E5 model receipt missing")
    for name, item in files.items():
        relative_name(name)
        require(not name.startswith((".local/", ".env")) and "/.git/" not in name and "/.secrets/" not in name)
        require(type(item["size"]) is int and item["size"] >= 0 and re.fullmatch(r"[0-9a-f]{64}", item["sha256"]))
        path = safe_file(root, name)
        require(path.stat().st_size == item["size"] and file_digest(path) == item["sha256"], "Release file checksum mismatch")
    require("images.tar" in files and "model-image.json" in files and "approval.json" in files and "approval.sig" in files)
    require(set(STATIC_FILES) <= set(files), "Required offline lifecycle/config files missing")
    # Reject unlisted executable/config additions. Recipient-generated local state is separate.
    actual = {path.relative_to(root).as_posix() for path in root.rglob("*") if path.is_file()
              and not path.relative_to(root).as_posix().startswith(".local/")
              and path.relative_to(root).as_posix() not in (".env", "release.json", "release.sig")}
    require(actual == set(files), "Unexpected files in release")
    for item in manifest["images"].values():
        require(re.fullmatch(r"sha256:[0-9a-f]{64}", item["imageId"]))
        require(re.fullmatch(r"sha256:[0-9a-f]{64}", item["configDigest"]))
        require(re.fullmatch(r"ssd-ai-d2-release:[a-z0-9-]+-[0-9a-f]{16}", item["localRef"]))
        require(item["os"] == manifest["os"] and item["architecture"] == manifest["architecture"])
    validate_archive(root / "images.tar")
    configs = archive_config_digests(root / "images.tar")
    require(all(configs.get(item["localRef"]) == item["configDigest"] for item in manifest["images"].values()),
            "Archive config digest does not match the signed image mapping")


def signature(path, sig, key):
    result = subprocess.run(["openssl", "dgst", "-sha256", "-verify", str(key), "-signature", str(sig), str(path)], capture_output=True)
    require(result.returncode == 0, "Release signature invalid")


def verify(args):
    root = args.root.resolve(strict=True)
    require(re.fullmatch(r"[0-9a-f]{64}", args.manifest_sha256), "Independent manifest hash required")
    manifest_path = safe_file(root, "release.json")
    require(file_digest(manifest_path) == args.manifest_sha256, "Release manifest identity mismatch")
    signature(manifest_path, safe_file(root, "release.sig"), args.public_key.resolve(strict=True))
    manifest = read_json(manifest_path)
    verify_files(root, manifest)
    approval = verify_approval(root / "approval.json", root / "approval.sig", args.public_key)
    receipt = read_json(root / "model-image.json")
    e5 = verify_e5_receipt(read_json(root / "e5-image.json"))
    require(e5["imageId"] == manifest["images"]["intelligence"]["imageId"]
            and e5["parentImageId"] == manifest["images"]["e5-base"]["imageId"], "E5 image identity mismatch")
    require(approval["modelDigest"] == receipt["modelDigest"] == manifest["modelDigest"])
    require(approval["channel"] == manifest["approvedChannel"])
    require(receipt["distributionImageId"] == manifest["images"]["ai07-qwen"]["imageId"])
    require(receipt["codeImageId"] == manifest["images"]["model-code"]["imageId"])
    return root, manifest


def export(args):
    plan = read_json(args.plan)
    receipt = read_json(args.image_receipt)
    verify_receipt(receipt)
    approval = verify_approval(args.approval, args.approval_signature, args.public_key)
    e5 = verify_e5_receipt(read_json(args.e5_receipt))
    require(approval["modelDigest"] == receipt["modelDigest"] and approval["channel"] == args.channel)
    require(set(plan["images"]) == set(SERVICES), "Pin all seven services in the export plan")
    require(plan["images"]["intelligence"] == e5["imageId"], "CPU intelligence must be the E5-enabled image")
    root = args.output.resolve()
    require(root.is_relative_to((ROOT / ".local").resolve()) and not root.exists(), "Use a new private .local export directory")
    root.mkdir(parents=True)
    images = {}
    for service, pin in {**plan["images"], "model-code": receipt["codeImageId"], "e5-base": e5["parentImageId"]}.items():
        require(re.fullmatch(r"sha256:[0-9a-f]{64}", pin), "Exact Docker image IDs required; tags are not immutable identities")
        actual = inspect(pin)
        require(actual["Id"] == pin and actual["Os"] == "linux" and actual["Architecture"] == "amd64")
        reference = "ssd-ai-d2-release:" + service + "-" + pin.removeprefix("sha256:")[:16]
        docker("tag", pin, reference)
        require(inspect(reference)["Id"] == pin)
        images[service] = {"imageId": pin, "localRef": reference, "sourceDescriptor": actual.get("Descriptor"),
                           "ociRefs": actual.get("RepoDigests", []), "os": actual["Os"], "architecture": actual["Architecture"]}
    require(images["ai07-qwen"]["imageId"] == receipt["distributionImageId"])
    verify_e5_image(images["intelligence"]["imageId"], e5)
    for name in STATIC_FILES:
        source = safe_file(ROOT, name)
        target = root / name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)
    profile_root = ROOT / PROFILE
    require(profile_root.is_dir(), "Synthetic profile missing")
    for path in profile_root.rglob("*"):
        if path.is_file():
            require(path.suffix in (".json", ".yaml", ".yml", ".md"), "Unexpected profile file")
            name = path.relative_to(ROOT).as_posix()
            source = safe_file(ROOT, name)
            target = root / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, target)
    helper_target = root / "scripts/ai-d2/lib"
    helper_target.mkdir()
    shutil.copyfile(HELPERS / "full_model_release.py", helper_target / "full_model_release.py")
    for source, target in ((args.image_receipt, "model-image.json"), (args.e5_receipt, "e5-image.json"), (args.approval, "approval.json"), (args.approval_signature, "approval.sig")):
        shutil.copyfile(source, root / target)
    # Export original runtime notices and dependency inventory from the pinned model image.
    license_script = ROOT / "scripts/apps/servicedesk/ai07/export-qwen-licenses.py"
    container = "ssd-d2-licenses-" + secrets.token_hex(6)
    docker("create", "--name", container, "--network", "none", "--entrypoint", "python", receipt["distributionImageId"],
           "/tmp/export-notices.py", "--output-root", "/tmp/notices", "--image-identity", receipt["distributionImageId"])
    try:
        docker("cp", license_script, container + ":/tmp/export-notices.py")
        docker("start", "--attach", container)
        require(json.loads(docker("inspect", container))[0]["State"]["ExitCode"] == 0, "License export failed")
        docker("cp", container + ":/tmp/notices", root / "runtime-notices")
    finally:
        docker("rm", container)
    shutil.copytree(ROOT / "licenses/models", root / "licenses/models")
    shutil.copytree(ROOT / "licenses/runtime-supplements", root / "licenses/runtime-supplements")
    docker("save", "--output", root / "images.tar", *sorted({item["localRef"] for item in images.values()}))
    validate_archive(root / "images.tar")
    configs = archive_config_digests(root / "images.tar")
    for item in images.values():
        require(item["localRef"] in configs, "Image archive is missing an exact config mapping")
        item["configDigest"] = configs[item["localRef"]]
    manifest = {"schemaVersion": "ai-d2-offline-release@1.0.0", "createdAt": datetime.now(timezone.utc).isoformat(),
                "sourceCommit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
                "worktreeDirty": bool(subprocess.check_output(["git", "status", "--porcelain"], cwd=ROOT, text=True).strip()),
                "os": "linux", "architecture": "amd64", "images": images, "modelDigest": receipt["modelDigest"],
                "approvedChannel": args.channel, "productionApproved": False, "files": inventory(root)}
    write_new(root / "release.json", manifest)
    print("EXPORTED_UNSIGNED; external issuer must sign release.json and deliver its SHA-256 and trust key separately")
    print("manifest SHA-256: " + file_digest(root / "release.json"))


def installed(root):
    state = read_json(safe_file(root, ".local/team-install.json"))
    require(re.fullmatch(r"ssd-d2-[0-9a-f]{12}", state["project"]))
    return state


def validate_rights(directory, activation, model):
    """Verification only; the recipient must supply all issuer-generated bytes."""
    workspace = activation["workspace"]
    names = {"context.key", "classifier.key", "classifier.json", "context/lab-a.OWN_TICKET.json", "context/lab-a.WORKSPACE_REVIEW.json"}
    require(set(activation["rightsFiles"]) == names, "Activation must bind every grant and verification key")
    for name in names:
        require(file_digest(safe_file(directory, name)) == activation["rightsFiles"][name], "Issuer rights bytes changed")
    require(workspace == "lab-a", "This kit's reviewed identity template supports lab-a assisted processing only")
    require(activation["modelDigest"] == model["modelDigest"] and activation["policyDigest"] == model["policyDigest"])
    for name in ("contextPolicyDigest", "readinessPolicyDigest", "contextualPolicyDigest", "assessmentPolicyDigest"):
        require(re.fullmatch(r"[0-9a-f]{64}", activation[name]))
    for kind, key_name in (("context", "context.key"), ("classifier", "classifier.key")):
        key_path = safe_file(directory, key_name)
        require(key_path.stat().st_size <= 8192)
        key = key_path.read_text(encoding="ascii").rstrip()
        require(len(key) >= 32 and all(33 <= ord(char) <= 126 for char in key))
        for scope in (("OWN_TICKET", "WORKSPACE_REVIEW") if kind == "context" else (None,)):
            name = f"context/{workspace}.{scope}.json" if scope else "classifier.json"
            envelope = read_json(safe_file(directory, name))
            require(set(envelope) == {"payload", "signature"})
            payload = base64.b64decode(envelope["payload"], validate=True)
            require(hmac.compare_digest(hmac.digest(key.encode("ascii"), payload, "sha256").hex(), envelope["signature"]))
            grant = json.loads(payload, object_pairs_hook=__import__("full_model_release").unique_pairs)
            require(grant["workspace"] == workspace and grant["expiresEpochSecond"] > time.time() and bool(grant["issuer"]))
            if scope:
                require(grant["purpose"] == "AI08_ADVISORY_PROCESSING" and grant["scope"] == scope)
                for field, expected in (("policyBundleDigest", "contextPolicyDigest"), ("readinessPolicyDigest", "readinessPolicyDigest"),
                                        ("contextualPolicyDigest", "contextualPolicyDigest")):
                    require(grant[field] == activation[expected])
            else:
                require(grant["purpose"] == "AI07_ASSISTED_PROCESSING")
                require(grant["modelDigest"] == model["modelDigest"] and grant["policyDigest"] == model["policyDigest"])
                require(grant["assessmentPolicyBundleDigest"] == activation["assessmentPolicyDigest"])


def activation_file(root, expected_manifest, key):
    path = safe_file(root, ".local/assisted-activation.json")
    signature(path, safe_file(root, ".local/assisted-activation.sig"), key)
    activation = read_json(path)
    require(activation["schemaVersion"] == "ai-d2-assisted-activation@1.0.0" and activation["approved"] is True
            and activation["realTicketProcessingApproved"] is True and activation["productionApproved"] is False
            and activation["releaseManifestSha256"] == expected_manifest and activation["expiresEpochSecond"] > time.time())
    validate_rights(root / ".local/rights", activation, read_json(root / "model-image.json"))
    return activation


def configure_assisted(args, root):
    require(installed(root)["mode"] == "DEMO_SYNTHETIC", "Activate from the default demo installation")
    signature(args.activation, args.activation_signature, args.public_key)
    value = read_json(args.activation)
    require(value["schemaVersion"] == "ai-d2-assisted-activation@1.0.0" and value["approved"] is True
            and value["realTicketProcessingApproved"] is True and value["productionApproved"] is False
            and value["releaseManifestSha256"] == args.manifest_sha256 and value["expiresEpochSecond"] > time.time())
    validate_rights(args.rights_directory, value, read_json(root / "model-image.json"))
    target = root / ".local/rights"
    names = ("context.key", "classifier.key", "classifier.json", "context/lab-a.OWN_TICKET.json", "context/lab-a.WORKSPACE_REVIEW.json")
    # Copy no unrelated files from the issuer directory. Never export these local files.
    for name in names:
        destination = target / name
        destination.parent.mkdir(parents=True, exist_ok=True)
        require(not destination.exists(), "Existing rights preserved; use reviewed grant rotation procedure")
        shutil.copyfile(safe_file(args.rights_directory, name), destination)
    shutil.copyfile(args.activation, root / ".local/assisted-activation.json")
    shutil.copyfile(args.activation_signature, root / ".local/assisted-activation.sig")
    settings = {"AI_D2_MODE": "AUTHORIZED_ASSISTED", "AI07_CLASSIFIER_MODE": "ASSISTED",
                "SERVICE_DESK_CONTEXT_ENABLED": "true", "SERVICE_DESK_CONTEXT_MODE": "ASSISTED",
                "SERVICE_DESK_CONTEXT_POLICY_BUNDLE_DIGEST": value["contextPolicyDigest"],
                "SERVICE_DESK_CONTEXTUAL_ASSESSMENT_ENABLED": "true",
                "SERVICE_DESK_CONTEXTUAL_ASSESSMENT_POLICY_DIGEST": value["contextualPolicyDigest"],
                "AI_D2_CLASSIFIER_KEY_CONTAINER_PATH": "/run/ai-rights/classifier.key",
                "AI_D2_CONTEXT_KEY_CONTAINER_PATH": "/run/ai-rights/context.key"}
    lines = (root / ".env").read_text(encoding="utf-8").splitlines()
    lines = [line for line in lines if line.partition("=")[0] not in settings]
    (root / ".env").write_text("\n".join(lines + [f"{key}={value}" for key, value in settings.items()]) + "\n", encoding="utf-8")
    state = installed(root)
    state["mode"] = "AUTHORIZED_ASSISTED"
    (root / ".local/team-install.json").write_text(json.dumps(state, indent=2), encoding="utf-8")
    print("CONFIGURED AUTHORIZED_ASSISTED; backend independently verifies current grants on every protected operation")


def install(args, root, manifest):
    require(not (root / ".env").exists() and not (root / ".local/team-install.json").exists(), "Already installed; preserve existing credentials and volumes")
    # Host toolchain must already be present; installation performs no pip/npm/apt requests.
    docker("compose", "version")
    require(json.loads(docker("info", "--format", "{{json .OSType}}")) == "linux", "Linux Docker engine required")
    docker("load", "--input", root / "images.tar")
    imported = {}
    for name, item in manifest["images"].items():
        actual = inspect(item["localRef"])
        require(actual["Os"] == item["os"] and actual["Architecture"] == item["architecture"])
        imported[name] = actual["Id"]
    verify_receipt(read_json(root / "model-image.json"), code_ref=manifest["images"]["model-code"]["localRef"],
                   distribution_ref=manifest["images"]["ai07-qwen"]["localRef"])
    verify_e5_image(manifest["images"]["intelligence"]["localRef"],
                    read_json(root / "e5-image.json"))
    # Generate local demo users/realm using the existing project's setup logic.
    result = subprocess.run(["powershell", "-NoProfile", "-File", str(root / "scripts/setup.ps1")], cwd=root, capture_output=True)
    require(result.returncode == 0, "Local secrets/realm preparation failed")
    local = root / ".local"
    (local / "rights/context").mkdir(parents=True, exist_ok=True)
    token = local / "classification-token"
    token.write_text(secrets.token_hex(32), encoding="ascii")
    receipt = read_json(root / "model-image.json")
    values = {IMAGE_ENV[name]: imported[name] for name in SERVICES}
    values.update({"AI07_QWEN_CODE_IMAGE_ID": receipt["codeImageId"], "AI07_QWEN_MODEL_DIGEST": receipt["modelDigest"],
                   "AI07_QWEN_POLICY_DIGEST": receipt["policyDigest"], "AI07_QWEN_TOKEN_FILE": "./.local/classification-token",
                   "AI_D2_RIGHTS_DIRECTORY": "./.local/rights", "SERVICE_DESK_INTAKE_NEW_TICKETS": "true",
                   "SERVICE_DESK_INTAKE_TRUE_WAIT": "true", "AI_D2_MODE": "DEMO_SYNTHETIC"})
    with (root / ".env").open("a", encoding="utf-8") as stream:
        for key, value in values.items():
            stream.write(f"\n{key}={value}")
        stream.write("\n")
    write_new(local / "team-install.json", {"project": "ssd-d2-" + secrets.token_hex(6), "manifestSha256": args.manifest_sha256, "images": imported,
                                            "mode": "DEMO_SYNTHETIC", "productionApproved": False})
    print("INSTALLED DEMO_SYNTHETIC; no real-ticket processing grants issued; existing stacks preserved")


def compose(root, manifest, *arguments):
    state = installed(root)
    require(state["manifestSha256"] == file_digest(root / "release.json"), "Installed release changed")
    require(state["mode"] in ("DEMO_SYNTHETIC", "AUTHORIZED_ASSISTED"))
    command = ["docker", "compose", "--project-directory", str(root), "--env-file", str(root / ".env"), "-p", state["project"]]
    overlays = ["compose.yaml", "infra/compose/compose.triage.yaml", "infra/compose/compose.ai-d2-release.yaml"]
    if state["mode"] == "DEMO_SYNTHETIC":
        overlays.append("infra/compose/compose.ai-d2-synthetic.yaml")
    for name in overlays:
        command += ["-f", str(root / name)]
    # Isolate the environment from inherited app flags and image substitutions.
    environment = {key: value for key, value in os.environ.items() if key not in
                   {"POSTGRES_PASSWORD", "KC_ADMIN_PASSWORD", "DEMO_PASSWORD", "DIRECTORY_CLIENT_SECRET", "BACKGROUND_CLIENT_SECRET"}
                   and not key.startswith(("SERVICE_DESK_", "AI_D2_", "AI07_", "INTELLIGENCE_", "COMPOSE_", "VITE_"))}
    for name in SERVICES:
        item = manifest["images"][name]
        require(inspect(item["localRef"])["Id"] == state["images"][name], "Imported image alias changed after installation")
        environment[IMAGE_ENV[name]] = state["images"][name]
    command += ["--profile", "intelligence", "--profile", "ai-d2-qwen", *arguments]
    result = subprocess.run(command, cwd=root, env=environment, capture_output=True, text=True)
    require(result.returncode == 0, "Compose action failed; inspect local service health without exporting secrets")
    return result.stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="action", required=True)
    out = commands.add_parser("export")
    for name in ("plan", "image-receipt", "e5-receipt", "approval", "approval-signature", "public-key", "output"):
        out.add_argument("--" + name, type=Path, required=True)
    out.add_argument("--channel", required=True)
    for action in ("verify", "install", "configure-assisted", "start", "stop", "test", "diagnostics"):
        command = commands.add_parser(action)
        command.add_argument("--root", type=Path, required=True)
        command.add_argument("--public-key", type=Path, required=action != "stop")
        command.add_argument("--manifest-sha256", required=action != "stop")
        if action == "configure-assisted":
            for name in ("activation", "activation-signature", "rights-directory"):
                command.add_argument("--" + name, type=Path, required=True)
    args = parser.parse_args()
    if args.action == "export":
        export(args)
        return
    if args.action == "stop":
        # Stop remains available after approval expiry or archive corruption.
        state = installed(args.root.resolve(strict=True))
        ids = docker("ps", "--quiet", "--filter", "label=com.docker.compose.project=" + state["project"]).split()
        require(all(re.fullmatch(r"[0-9a-f]{12,64}", value) for value in ids))
        if ids:
            docker("stop", *ids)
        print("STOPPED exact installed project; volumes and credentials preserved")
        return
    root, manifest = verify(args)
    if args.action in ("start", "test") and installed(root)["mode"] == "AUTHORIZED_ASSISTED":
        activation_file(root, args.manifest_sha256, args.public_key)
    if args.action == "install":
        install(args, root, manifest)
    elif args.action == "configure-assisted":
        configure_assisted(args, root)
    elif args.action == "start":
        compose(root, manifest, "up", "--detach", "--no-build", "--pull", "never", "--wait", "--wait-timeout", "300")
        print("STARTED " + installed(root)["mode"] + "; localhost ports 5173/8081/9080 must be available")
    elif args.action in ("test", "diagnostics"):
        output = compose(root, manifest, "ps", "--format", "json").strip()
        try:
            rows = json.loads(output)
            if isinstance(rows, dict):
                rows = [rows]
        except json.JSONDecodeError:
            rows = [json.loads(line) for line in output.splitlines() if line.strip()]
        health = [{"service": row.get("Service"), "state": row.get("State"), "health": row.get("Health")} for row in rows]
        good = {row["service"] for row in health if row["state"] == "running" and row["health"] in ("healthy", "")}
        report = {"schemaVersion": "ai-d2-diagnostics@1.0.0", "manifestSha256": args.manifest_sha256, "mode": installed(root)["mode"],
                  "services": health, "allServicesHealthy": set(SERVICES) <= good, "functionalAcceptance": "NOT_RUN",
                  "productionApproved": False}
        acceptance_failure = False
        if args.action == "test" and report["allServicesHealthy"]:
            try:
                from synthetic_acceptance import run
                report["functionalAcceptance"] = run(root, report["mode"])
            except Exception:
                report["functionalAcceptance"] = "FAIL_SYNTHETIC_HTTP_REDACTED"
                acceptance_failure = True
        output = root / ".local" / ("diagnostics-" + secrets.token_hex(6) + ".json")
        write_new(output, report)
        if args.action == "test":
            require(report["allServicesHealthy"], "Service readiness failed; diagnostics written locally")
            require(not acceptance_failure, "Synthetic functional acceptance failed; redacted diagnostics written locally")
        print("REDACTED_LOCAL_REPORT " + str(output))
    else:
        print("VERIFIED signed inventory/archive/approval; imported runtime and recipient acceptance not implied")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError, OSError, RuntimeError, subprocess.SubprocessError):
        sys.exit("D2 action failed; check signed inventory, toolchain and local deployment inputs. No secret values printed.")
