"""Prove the reusable Qwen observer works without the app or GPU libraries."""

import importlib.abc
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
if not sys.flags.isolated:
    sys.path[:0] = [
        str(ROOT / "intelligence/src"),
        str(ROOT / "intelligence/integrations"),
    ]


class DenyApplicationAndGpu(importlib.abc.MetaPathFinder):
    def find_spec(self, fullname, path=None, target=None):
        if fullname.split(".")[0] in (
            "servicedesk",
            "apps",
            "torch",
            "transformers",
            "peft",
        ):
            raise ImportError("Forbidden dependency in neutral Qwen proof")


sys.meta_path.insert(0, DenyApplicationAndGpu())

from hdpi.classification.protocols import ClassificationRequest  # noqa: E402
from local_classification.qwen_lora_runtime import QwenLoraRuntime  # noqa: E402


class FakeGenerator:
    ready = True

    def generate(self, messages, *, timeout_seconds):
        assert messages == [{"role": "user", "content": "Synthetic room"}]
        return "B"


config = {
    "policyDigest": "b" * 64,
    "allowedClasses": ("OPEN", "CLOSED", "HELD", "VACANT", "FULL"),
    "abstainId": "UNKNOWN",
    "letters": dict(zip("ABCDEF", ("OPEN", "CLOSED", "HELD", "VACANT", "FULL", None))),
    "messages": lambda fields: [{"role": "user", "content": fields["room"]}],
    "generation": {
        "maxInputTokens": 2047,
        "maxNewTokens": 4,
        "doSample": False,
        "seed": 42,
        "addGenerationPrompt": True,
        "skipSpecialTokens": True,
        "stripOutput": True,
    },
}
runtime = QwenLoraRuntime(
    model_digest="a" * 64, configuration=config, generator=FakeGenerator(), enabled=True
)
request = ClassificationRequest(
    fields=(("room", "Synthetic room"),),
    allowed_classes=config["allowedClasses"],
    policy_digest=config["policyDigest"],
)
result = runtime.observe(request)
assert result.class_id == "CLOSED" and not result.operational_authority
assert result.model_scores is None and result.evidence_status == "UNVERIFIED_BY_MODEL"
assert "servicedesk" not in sys.modules and "torch" not in sys.modules
print(
    json.dumps(
        {
            "secondDomain": "PASS",
            "applicationImports": False,
            "gpuImports": False,
            "weightsUsed": False,
            "authority": False,
        }
    )
)
