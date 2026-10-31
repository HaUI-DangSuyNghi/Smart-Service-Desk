import json
import os
from pathlib import Path
import secrets
import subprocess
import tempfile


root = Path(__file__).resolve().parents[2]
production = (root / "infra/apisix/config.yaml").read_text(encoding="utf-8")
acceptance = (root / "scripts/acceptance/apisix-single-worker.yaml").read_text(encoding="utf-8")
assert acceptance == production.rstrip() + "\nnginx_config:\n  worker_processes: 1\n"
environment = os.environ.copy()
environment.update({key: secrets.token_hex(32) for key in (
    "POSTGRES_PASSWORD", "KC_ADMIN_PASSWORD", "DIRECTORY_CLIENT_SECRET",
    "INTELLIGENCE_TRIAGE_TOKEN", "BACKGROUND_CLIENT_SECRET",
)})
environment.update(INT01_PROJECT="int01-config-check", INT01_SOURCE_SHA="config-check",
                   INT01_KEYCLOAK_PORT="18081", INT01_GATEWAY_PORT="19080")
with tempfile.TemporaryDirectory() as directory:
    env_file = Path(directory) / "empty.env"
    env_file.touch()
    command = ["docker", "compose", "--env-file", str(env_file), "-p", "int01-config-check",
               "-f", "compose.yaml", "-f", "infra/compose/compose.triage.yaml",
               "-f", "scripts/acceptance/integration-compose.yaml", "--profile", "intelligence",
               "config", "--format", "json"]
    result = subprocess.run(command, cwd=root, env=environment, capture_output=True, text=True, check=True)
    config = json.loads(result.stdout)
services = config["services"]
assert services["intelligence"]["entrypoint"] == ["python", "-m", "hdpi.http_service", "--host", "0.0.0.0"]
mount, = [mount for mount in services["gateway"]["volumes"]
          if mount["target"] == "/usr/local/apisix/conf/config.yaml"]
assert Path(mount["source"]).resolve() == root / "scripts/acceptance/apisix-single-worker.yaml"
assert mount["read_only"]
for service, port in (("gateway", "19080"), ("keycloak", "18081")):
    binding, = services[service]["ports"]
    assert binding["host_ip"] == "127.0.0.1" and str(binding["published"]) == port
for service in ("postgres", "platform", "intelligence"):
    assert not services[service].get("ports")
for volume in config["volumes"].values():
    assert not volume.get("external") and volume["name"].startswith("int01-config-check_")
assert services["postgres"]["image"] == (
    "postgres:16.15-bookworm@sha256:0ea6700a3b4f0ae6ce746519073558aed4d88a79d8d07622a9a644946c7319c4"
)
print("PASS acceptance config: one worker, hdpi entrypoint, loopback/private ports, isolated volumes, unchanged DB pin")
