"""Offline, eager-loaded PEFT provider with one killable inference process."""

import importlib.metadata
import multiprocessing
import os
from pathlib import Path
from threading import Lock
from time import monotonic

from hdpi.classification.protocols import digest, require
from hdpi.generation.errors import GenerationTimeout, GenerationUnavailable
from local_classification.adapters.qwen35_lora_letter import decode_letter
from local_classification.qwen_artifacts import verify_bundle


def _load_model(root):
    # Set before importing any HF libraries. No request may trigger a download.
    os.environ.update(
        HF_HUB_OFFLINE="1",
        TRANSFORMERS_OFFLINE="1",
        HF_HUB_DISABLE_TELEMETRY="1",
        DO_NOT_TRACK="1",
    )
    import torch
    from peft import PeftModel, get_peft_model_state_dict
    from safetensors import safe_open
    from transformers import AutoTokenizer, Qwen3_5ForConditionalGeneration

    require(torch.cuda.is_available())
    torch.manual_seed(42)
    torch.cuda.manual_seed_all(42)
    torch.backends.cuda.matmul.allow_tf32 = False
    tokenizer = AutoTokenizer.from_pretrained(
        str(root / "adapter"), local_files_only=True, trust_remote_code=False
    )
    require(
        tokenizer.chat_template
        == (root / "adapter/chat_template.jinja").read_text("utf-8")
    )
    base = Qwen3_5ForConditionalGeneration.from_pretrained(
        str(root / "base"),
        local_files_only=True,
        trust_remote_code=False,
        use_safetensors=True,
        dtype=torch.float16,
        device_map={"": "cuda:0"},
        attn_implementation="eager",
    )
    model = PeftModel.from_pretrained(
        base,
        str(root / "adapter"),
        local_files_only=True,
        is_trainable=False,
        autocast_adapter_dtype=True,
    )
    loaded = get_peft_model_state_dict(model, save_embedding_layers=False)
    with safe_open(
        str(root / "adapter/adapter_model.safetensors"), framework="pt", device="cpu"
    ) as checkpoint:
        require(set(loaded) == set(checkpoint.keys()))
        for name, tensor in loaded.items():
            require(
                torch.equal(tensor.detach().cpu().float(), checkpoint.get_tensor(name))
            )
    model.eval()
    return model, tokenizer


def _generate(model, tokenizer, messages, generation):
    import torch

    inputs = tokenizer.apply_chat_template(
        messages, tokenize=True, add_generation_prompt=True, return_tensors="pt"
    )
    if not isinstance(inputs, dict) and not hasattr(inputs, "keys"):
        inputs = {"input_ids": inputs}
    inputs = {name: value.to(model.device) for name, value in inputs.items()}
    length = inputs["input_ids"].shape[1]
    require(length <= generation["maxInputTokens"])
    torch.cuda.reset_peak_memory_stats()
    started = monotonic()
    with torch.inference_mode():
        output = model.generate(
            **inputs,
            max_new_tokens=generation["maxNewTokens"],
            do_sample=False,
            pad_token_id=tokenizer.eos_token_id,
        )
    torch.cuda.synchronize()
    completion = tokenizer.decode(output[0][length:], skip_special_tokens=True)
    return completion, {
        "inputTokens": length,
        "outputTokens": int(output.shape[1] - length),
        "inferenceMs": round((monotonic() - started) * 1000, 3),
        "peakAllocatedBytes": torch.cuda.max_memory_allocated(),
        "peakReservedBytes": torch.cuda.max_memory_reserved(),
    }


def verify_dependencies(root):
    lines = (Path(root) / "dependencies.lock").read_text("ascii").splitlines()
    require(1 <= len(lines) <= 128)
    for line in lines:
        if not line or line.startswith("#"):
            continue
        if line == "--extra-index-url https://download.pytorch.org/whl/cu128":
            continue
        name, version = line.split("==")
        require(importlib.metadata.version(name) == version)


def _worker(connection, root, verification, warmup_messages, generation):
    """Only safe status/letters leave the child; library exceptions never leave it."""
    try:
        verify_bundle(root, **verification)
        verify_dependencies(root)
        started = monotonic()
        model, tokenizer = _load_model(root)
        _generate(model, tokenizer, warmup_messages, generation)
        verify_bundle(root, **verification)
        import torch

        connection.send(
            (
                "READY",
                {
                    "coldLoadWarmupMs": round((monotonic() - started) * 1000, 3),
                    "gpu": torch.cuda.get_device_name(0),
                    "gpuMemoryBytes": torch.cuda.get_device_properties(0).total_memory,
                    "cuda": torch.version.cuda,
                },
            )
        )
        while True:
            messages = connection.recv()
            if messages is None:
                return
            try:
                completion, metrics = _generate(model, tokenizer, messages, generation)
                connection.send(("OK", completion, metrics))
            except Exception:
                connection.send(("REJECTED",))
    except Exception:
        try:
            connection.send(("UNAVAILABLE",))
        except (OSError, EOFError):
            pass
    finally:
        connection.close()


# GPU laptop rảnh thì hạ xung (P8), lần đầu sau đó có thể quá 3 giây; backend chờ 10 giây
INFERENCE_TIMEOUT_SECONDS = 8.0


class ProcessGenerator:
    """Hard timeout terminates the only GPU worker; restart requires fresh warmup."""

    def __init__(
        self, root, *, verification, warmup_messages, generation, startup_seconds=180
    ):
        context = multiprocessing.get_context("spawn")
        self.connection, child = context.Pipe()
        self.process = context.Process(
            target=_worker,
            args=(child, Path(root), verification, warmup_messages, generation),
            daemon=True,
        )
        self.ready = False
        self.process.start()
        child.close()
        try:
            require(self.connection.poll(startup_seconds))
            response = self.connection.recv()
            require(response[0] == "READY")
            self.metrics = response[1]
            self.ready = True
        except Exception:
            self.close()
            raise GenerationUnavailable("Qwen startup unavailable") from None

    def generate(self, messages, *, timeout_seconds):
        if not self.ready or not self.process.is_alive():
            raise GenerationUnavailable("Qwen not ready")
        try:
            self.connection.send(messages)
            if not self.connection.poll(timeout_seconds):
                self.close()
                raise GenerationTimeout("Qwen inference deadline")
            value = self.connection.recv()
            if value[0] != "OK":
                raise GenerationUnavailable("Qwen input or generation rejected")
            self.last_metrics = value[2]
            return value[1]
        except (EOFError, OSError):
            self.close()
            raise GenerationUnavailable("Qwen worker unavailable") from None

    def close(self):
        self.ready = False
        if self.process.is_alive():
            self.process.terminate()
        self.process.join(timeout=0.15)
        if self.process.is_alive():
            self.process.kill()
            self.process.join(timeout=0.15)
        self.connection.close()


class QwenLoraRuntime:
    def __init__(self, *, model_digest, configuration, generator, enabled=False):
        require(enabled is True)
        require(digest(model_digest) and digest(configuration["policyDigest"]))
        require(
            configuration["generation"]
            == {
                "maxInputTokens": 2047,
                "maxNewTokens": 4,
                "doSample": False,
                "seed": 42,
                "addGenerationPrompt": True,
                "skipSpecialTokens": True,
                "stripOutput": True,
            }
        )
        self.model_digest = model_digest
        self.configuration = configuration
        self.generator = generator
        self.slot = Lock()

    @property
    def ready(self):
        return self.generator.ready

    def observe(self, request):
        require(request.policy_digest == self.configuration["policyDigest"])
        require(request.allowed_classes == tuple(self.configuration["allowedClasses"]))
        messages = self.configuration["messages"](dict(request.fields))
        if not self.ready or not self.slot.acquire(blocking=False):
            raise GenerationUnavailable("Qwen busy or not ready")
        try:
            raw = self.generator.generate(messages, timeout_seconds=INFERENCE_TIMEOUT_SECONDS)
            return decode_letter(
                raw,
                request,
                letters=self.configuration["letters"],
                abstain_id=self.configuration["abstainId"],
            )
        finally:
            self.slot.release()

    def close(self):
        self.generator.close()
