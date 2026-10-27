"""Run the opt-in Java/PostgreSQL/Flowable final pipeline against real embedded Qwen.

Only the built-in owned synthetic fixture is sent to the model. Rights and JWTs
in the JUnit harness are explicit test doubles, not authority for customer data.
"""

import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import secrets
import subprocess
import tempfile
import time

from team_release import ROOT
from full_model_release import docker, read_json, require, verify_receipt, write_new


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--image-receipt", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--build-image", default="maven:3.9.9-eclipse-temurin-17")
    args = parser.parse_args()
    require(not args.output.exists() and args.output.resolve().is_relative_to((ROOT / ".local").resolve()))
    receipt = read_json(args.image_receipt)
    verify_receipt(receipt)
    suffix = secrets.token_hex(6)
    name, network = "ssd-d2-real-model-" + suffix, "ssd-d2-real-network-" + suffix
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".synthetic-proof-", dir=args.output.parent) as temporary:
        token = Path(temporary) / "token"
        token.write_text(secrets.token_hex(32), encoding="ascii")
        docker("network", "create", "--internal", network)
        created = False
        try:
            docker("run", "-d", "--name", name, "--network", network, "--gpus", "all", "--read-only", "--cap-drop", "ALL",
                   "--security-opt", "no-new-privileges:true", "--memory", "6g", "--pids-limit", "96",
                   "--shm-size", "256m", "--tmpfs", "/tmp:rw,noexec,nosuid,size=256m",
                   "--mount", "type=bind,source=" + str(token.resolve()) + ",target=/run/secrets/classification-token,readonly",
                   "-e", "AI07_QWEN_ENABLED=1", "-e", "AI07_QWEN_TOKEN_FILE=/run/secrets/classification-token",
                   receipt["distributionImageId"], "--bundle", "/opt/model", "--model-digest", receipt["modelDigest"],
                   "--runtime-identity", receipt["codeImageId"], "--token-file", "/run/secrets/classification-token")
            created = True
            deadline = time.monotonic() + 240
            while time.monotonic() < deadline:
                ready = subprocess.run(["docker", "exec", name, "python", "-m", "local_classification.qwen_lora_health"], capture_output=True)
                if ready.returncode == 0:
                    break
                require(docker("inspect", name, "--format", "{{.State.Running}}").strip() == "true", "Model exited before readiness")
                time.sleep(3)
            else:
                raise RuntimeError("Real model readiness timed out")
            command = ["powershell", "-NoProfile", "-File", str(ROOT / "scripts/test-task-execution.ps1"), "-Profile", "full",
                       "-Test", "RealQwenIntakeProof", "-BuildImage", args.build_image, "-ClassificationContainer", name,
                       "-ClassificationTokenFile", str(token.resolve()), "-ClassificationModelDigest", receipt["modelDigest"],
                       "-ClassificationPolicyDigest", receipt["policyDigest"]]
            result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True)
            require(result.returncode == 0, "Real final pipeline failed; inspect private targeted Maven evidence")
            summary = json.loads((ROOT / ".local/task-execution-full-targeted/summary.json").read_text(encoding="utf-8-sig"))
            require(summary["tests"] == 1 and all(summary[key] == 0 for key in ("failures", "errors", "skipped")))
            write_new(args.output, {"schemaVersion": "ai-d2-real-qwen-intake-proof@1.0.0", "createdAt": datetime.now(timezone.utc).isoformat(),
                                    "distributionImageId": receipt["distributionImageId"], "modelDigest": receipt["modelDigest"],
                                    "policyDigest": receipt["policyDigest"], "network": "ISOLATED_INTERNAL_TEST_NETWORKS",
                                    "hostModelMounts": False, "classificationTransport": "REAL_JAVA_HTTP", "model": "REAL_QWEN_LORA_GPU",
                                    "database": "DISPOSABLE_POSTGRESQL", "workflow": "REAL_FLOWABLE",
                                    "processingRights": "SYNTHETIC_TEST_DOUBLES", "test": summary, "productionApproved": False})
            print("PASS real Qwen -> Java intake final -> persisted GET -> human review -> feedback; synthetic only")
        finally:
            if created:
                docker("rm", "-f", name)
            docker("network", "rm", network)


if __name__ == "__main__":
    main()
