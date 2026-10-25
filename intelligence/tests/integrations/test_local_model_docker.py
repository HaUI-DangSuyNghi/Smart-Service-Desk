"""Deployment contracts only. Live GPU acceptance uses explicit operator scripts."""

from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[3]


def compose(name):
    return yaml.safe_load((ROOT / "infra/compose" / name).read_text("utf-8"))


def test_local_runtime_is_opt_in_and_does_not_publish_ports_or_mount_core():
    normal = yaml.safe_load((ROOT / "compose.yaml").read_text("utf-8"))
    assert not any(name.startswith("ai07-") for name in normal["services"])
    runtime = compose("compose.ai07-local-models.yaml")
    assert runtime["networks"]["ai07-inference-private"]["internal"] is True
    for service in runtime["services"].values():
        assert service["profiles"] == ["ai07-local"]
        assert "ports" not in service
        assert service["read_only"] is True
        assert service["cap_drop"] == ["ALL"]
        assert service["logging"] == {"driver": "none"}
        assert len(service["tmpfs"]) == 1 and service["tmpfs"][0].startswith("/tmp:")
        assert "no-new-privileges:true" in service["security_opt"]
        assert "ai07-ollama-models:/models:ro" in service["volumes"]
        assert all(
            "docker.sock" not in mount and "/var/lib/dx" not in mount
            for mount in service["volumes"]
        )
    bridge = runtime["services"]["ai07-classification-bridge"]
    assert bridge["network_mode"] == "service:ai07-ollama"
    assert "networks" not in bridge
    assert bridge["environment"]["AI07_LOCAL_ENABLED"] == "${AI07_LOCAL_ENABLED:-0}"
    assert (
        runtime["services"]["ai07-ollama"]["environment"]["OLLAMA_HOST"]
        == "127.0.0.1:11434"
    )


def test_cpu_and_gpu_are_explicit_and_model_volume_is_shared_with_provisioning():
    runtime = compose("compose.ai07-local-models.yaml")
    provision = compose("compose.ai07-local-provision.yaml")
    gpu = compose("compose.ai07-local-gpu.yaml")
    assert "deploy" not in runtime["services"]["ai07-ollama"]
    assert (
        runtime["services"]["ai07-classification-bridge"]["environment"][
            "AI07_GPU_REQUIRED"
        ]
        == "0"
    )
    assert (
        gpu["services"]["ai07-classification-bridge"]["environment"][
            "AI07_GPU_REQUIRED"
        ]
        == "1"
    )
    devices = gpu["services"]["ai07-ollama"]["deploy"]["resources"]["reservations"][
        "devices"
    ]
    assert devices == [{"driver": "nvidia", "count": 1, "capabilities": ["gpu"]}]
    for document in (runtime, provision):
        volume = document["volumes"]["ai07-ollama-models"]
        assert volume["external"] is True and volume["name"].startswith(
            "${AI07_MODEL_VOLUME:?"
        )
    assert (
        "ai07-ollama-models:/models"
        in provision["services"]["ai07-ollama-provision"]["volumes"]
    )


def test_images_are_pinned_and_bridge_image_has_only_code_and_generic_wheel():
    runtime = compose("compose.ai07-local-models.yaml")
    provision = compose("compose.ai07-local-provision.yaml")
    image = runtime["services"]["ai07-ollama"]["image"]
    assert "0.40.8@sha256:" in image and ":latest" not in image
    assert image == provision["services"]["ai07-ollama-provision"]["image"]
    dockerfile = (
        ROOT / "intelligence/integrations/local_classification/Dockerfile"
    ).read_text("utf-8")
    assert (
        "COPY wheels/*.whl" in dockerfile and "COPY local_classification/" in dockerfile
    )
    assert "COPY . " not in dockerfile and "model" not in dockerfile.casefold()
