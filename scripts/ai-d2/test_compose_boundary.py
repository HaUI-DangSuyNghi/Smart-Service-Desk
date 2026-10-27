"""Resolve Compose with synthetic values; never load the developer's .env."""

import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

import team_release as release


class ComposeBoundaryTest(unittest.TestCase):
    def test_private_model_network_secret_files_and_synthetic_fail_closed(self):
        with tempfile.TemporaryDirectory() as temporary:
            env_file = Path(temporary) / "fixture.env"
            values = {name: "SYNTHETIC_NOT_A_REAL_SECRET" for name in (
                "POSTGRES_PASSWORD", "KC_ADMIN_PASSWORD", "DIRECTORY_CLIENT_SECRET", "BACKGROUND_CLIENT_SECRET", "INTELLIGENCE_TRIAGE_TOKEN")}
            values.update({value: "sha256:" + "a" * 64 for value in release.IMAGE_ENV.values()})
            values.update({"AI07_QWEN_CODE_IMAGE_ID": "sha256:" + "b" * 64, "AI07_QWEN_MODEL_DIGEST": "c" * 64,
                           "AI07_QWEN_POLICY_DIGEST": "d" * 64, "AI07_QWEN_TOKEN_FILE": str(Path(temporary) / "token"),
                           "AI_D2_RIGHTS_DIRECTORY": str(Path(temporary) / "rights")})
            env_file.write_text("\n".join(f"{key}={value}" for key, value in values.items()), encoding="ascii")
            environment = {key: value for key, value in os.environ.items() if key not in values
                           and not key.startswith(("AI07_", "AI_D2_", "INTELLIGENCE_", "SERVICE_DESK_", "COMPOSE_"))}
            command = ["docker", "compose", "--project-directory", str(release.ROOT), "--env-file", str(env_file)]
            for name in ("compose.yaml", "infra/compose/compose.triage.yaml", "infra/compose/compose.ai-d2-release.yaml", "infra/compose/compose.ai-d2-synthetic.yaml"):
                command += ["-f", str(release.ROOT / name)]
            command += ["--profile", "intelligence", "--profile", "ai-d2-qwen", "config", "--format", "json"]
            result = subprocess.run(command, env=environment, capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, "Compose synthetic config failed")
            config = json.loads(result.stdout)
            services = config["services"]
            model = services["ai07-qwen"]
            self.assertEqual(set(model["networks"]), {"model-private"})
            self.assertTrue(config["networks"]["model-private"]["internal"])
            self.assertFalse(model.get("ports"))
            self.assertFalse(model.get("volumes"))
            self.assertTrue(model["read_only"])
            self.assertEqual(model["user"], "10001:10001")
            self.assertEqual(model["cap_drop"], ["ALL"])
            peers = {name for name, service in services.items() if "model-private" in service.get("networks", {})}
            self.assertEqual(peers, {"ai07-qwen", "platform"})
            backend = services["platform"]["environment"]
            self.assertEqual(backend["INTELLIGENCE_CLASSIFICATION_BASE_URL"], "http://intelligence-classification:8003")
            self.assertEqual(backend["INTELLIGENCE_CLASSIFICATION_TOKEN"], "")
            self.assertEqual(backend["INTELLIGENCE_CLASSIFICATION_TOKEN_FILE"], "/run/secrets/classification-token")
            self.assertEqual(backend["SERVICE_DESK_CLASSIFIER_MODE"], "DISABLED")
            self.assertEqual(backend["SERVICE_DESK_CONTEXT_MODE"], "DISABLED")
            self.assertEqual(backend["SERVICE_DESK_INTAKE_TRUE_WAIT"], "true")
            for service in services.values():
                self.assertEqual(service["pull_policy"], "never")


if __name__ == "__main__":
    unittest.main()
