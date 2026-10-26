from hashlib import sha256
import json
from pathlib import Path
import sys

import pytest

from hdpi.classification.protocols import ClassificationError
from hdpi.generation.errors import GenerationUnavailable


sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "integrations"))

from local_classification.runtime import LocalClassificationRuntime  # noqa: E402


class Native:
    def __init__(self, manifest_digest):
        self.version = "0.40.8"
        self.digest = manifest_digest
        self.context = 2048
        self.vram = 900
        self.active = False
        self.calls = []

    def exchange(self, method, path, payload=None):
        self.calls.append((path, payload))
        if path == "/api/version":
            return {"version": self.version}
        if path == "/api/tags":
            return {"models": [{"name": "pinned:vote", "digest": self.digest}]}
        if path == "/api/show":
            return {
                "details": {"quantization_level": "Q8_0"},
                "capabilities": ["decision"],
                "parameters": f"num_ctx {self.context}",
            }
        if path == "/api/ps":
            return {
                "models": []
                if not self.active
                else [
                    {
                        "name": "pinned:vote",
                        "size": 1000,
                        "size_vram": self.vram,
                        "context_length": self.context,
                    }
                ]
            }
        if path == "/v1/systemone":
            self.active = True
            return {
                "model": "pinned:vote",
                "answers": {
                    "decision": {
                        "type": "choice",
                        "choice": "A",
                        "probabilities": {"A": 0.7, "B": 0.2, "UNKNOWN": 0.1},
                    }
                },
                "usage": {"input_tokens": 20, "output_tokens": 1},
            }
        raise AssertionError("Unexpected native route")


@pytest.fixture
def setup_runtime(tmp_path):
    weights = tmp_path / "weights.fixture"
    native_manifest = tmp_path / "manifest.fixture"
    weights.write_bytes(b"synthetic weights, not a model")
    native_manifest.write_bytes(b"synthetic manifest")
    model_digest = sha256(weights.read_bytes()).hexdigest()
    api_digest = sha256(native_manifest.read_bytes()).hexdigest()
    policy = {
        "experimentId": "synthetic-vote@1",
        "allowedClasses": ["A", "B"],
        "inputFields": ["input"],
        "dataScope": "SYNTHETIC_DEV_ONLY",
        "policy": {
            "instructions": "Trusted policy",
            "criteria": {"A": "first", "B": "second", "UNKNOWN": "unknown"},
            "abstainId": "UNKNOWN",
        },
    }
    policy_file = tmp_path / "vote.json"
    policy_file.write_bytes(json.dumps(policy).encode())
    policy_digest = sha256(policy_file.read_bytes()).hexdigest()
    config = {
        "contractVersion": "local-classification-runtime@0.1.0",
        "mode": "SYNTHETIC_LAB_ONLY",
        "ollamaVersion": "0.40.8",
        "ollamaImage": "synthetic@sha256:" + "f" * 64,
        "bridgeRevision": "e" * 64,
        "contextTokens": 2048,
        "modelFiles": [
            {
                "path": file.name,
                "bytes": file.stat().st_size,
                "sha256": sha256(file.read_bytes()).hexdigest(),
            }
            for file in (weights, native_manifest)
        ],
        "candidates": {
            "vote": {
                "adapter": "tev1-systemone@0.1.0",
                "model": "pinned:vote",
                "apiDigest": api_digest,
                "policyFile": "vote.json",
                "policyDigest": policy_digest,
                "tokenizerDigest": model_digest,
                "modelArtifactDigest": model_digest,
                "quantization": "Q8_0",
                "sourceModelDigest": api_digest,
                "licenseStatus": "PENDING_OWNER_REVIEW_LAB_ONLY",
            }
        },
    }
    config_file = tmp_path / "runtime.json"
    config_file.write_bytes(json.dumps(config).encode())
    native = Native(api_digest)
    arguments = {
        "config_path": config_file,
        "policy_directory": tmp_path,
        "model_directory": tmp_path,
        "transport": native,
        "require_readonly": False,
        "gpu_required": True,
    }
    return arguments, native, weights, policy_digest


def test_disabled_does_not_open_artifacts_or_contact_native(setup_runtime):
    arguments, native, _, _ = setup_runtime
    with pytest.raises(ClassificationError):
        LocalClassificationRuntime(**arguments)
    assert native.calls == []


def test_inference_uses_startup_hash_lease_and_unverified_vote(
    setup_runtime, monkeypatch
):
    arguments, native, weights, policy_digest = setup_runtime
    runtime = LocalClassificationRuntime(**arguments, enabled=True)
    original_open = Path.open

    def guarded_open(path, *args, **kwargs):
        if path == weights:
            pytest.fail("model weights read on request hot path")
        return original_open(path, *args, **kwargs)

    monkeypatch.setattr(Path, "open", guarded_open)
    result = runtime.infer(
        "vote",
        {"input": "untrusted instructions"},
        runtime_digest=runtime.digest,
        policy_digest=policy_digest,
    )
    assert result["operationalAuthority"] is False
    assert result["observation"]["evidence_status"] == "UNVERIFIED_BY_MODEL"
    assert result["observation"]["input_spans"] == ()
    assert result["placement"]["context_length"] == 2048
    assert any(
        path == "/v1/systemone"
        and payload["state"] == {"input": "untrusted instructions"}
        for path, payload in native.calls
    )


@pytest.mark.parametrize("mutation", ["version", "alias", "artifact", "context", "gpu"])
def test_runtime_identity_and_resource_failures_reject_proposal(
    setup_runtime, mutation
):
    arguments, native, weights, policy_digest = setup_runtime
    runtime = LocalClassificationRuntime(**arguments, enabled=True)
    if mutation == "version":
        native.version = "changed"
    elif mutation == "alias":
        native.digest = "0" * 64
    elif mutation == "artifact":
        weights.write_bytes(b"changed artifact")
    elif mutation == "context":
        native.context = 8192
    elif mutation == "gpu":
        native.vram = 0
    with pytest.raises(ClassificationError):
        runtime.infer(
            "vote",
            {"input": "alpha"},
            runtime_digest=runtime.digest,
            policy_digest=policy_digest,
        )


def test_context_must_be_verified_before_model_inference(setup_runtime):
    arguments, native, _, _ = setup_runtime
    native.context = 8192
    with pytest.raises(ClassificationError):
        LocalClassificationRuntime(**arguments, enabled=True)
    assert not any(path == "/v1/systemone" for path, _ in native.calls)


def test_foreign_fields_wrong_policy_and_capacity_do_not_call_model(setup_runtime):
    arguments, native, _, policy_digest = setup_runtime
    runtime = LocalClassificationRuntime(**arguments, enabled=True)
    for fields, digest in (
        ({"input": "x", "workspace_id": "foreign"}, policy_digest),
        ({"input": "x"}, "0" * 64),
    ):
        with pytest.raises(ClassificationError):
            runtime.infer(
                "vote", fields, runtime_digest=runtime.digest, policy_digest=digest
            )
    runtime._slot.acquire()
    try:
        with pytest.raises(GenerationUnavailable):
            runtime.infer(
                "vote",
                {"input": "x"},
                runtime_digest=runtime.digest,
                policy_digest=policy_digest,
            )
    finally:
        runtime._slot.release()
    assert not any(path == "/v1/systemone" for path, _ in native.calls)


@pytest.mark.parametrize(
    "adapter_version", ["gemma-classify-only@0.2.0", "gemma-classify-only@0.3.0"]
)
@pytest.mark.parametrize("invalid", [False, True])
def test_g1_runtime_routes_by_pinned_adapter_and_never_calls_g0(
    setup_runtime, invalid, adapter_version
):
    from local_classification.adapters.gemma_classify_only import output_schema
    from local_classification.diagnostics import ObservationRejected

    arguments, native, _, _ = setup_runtime
    policy_path = arguments["policy_directory"] / "vote.json"
    policy = json.loads(policy_path.read_bytes())
    policy["policy"] = {
        "instructions": "Trusted synthetic policy",
        "abstainId": "UNKNOWN",
        "outputSchema": output_schema(("A", "B")),
    }
    policy_path.write_bytes(json.dumps(policy).encode())
    digest = sha256(policy_path.read_bytes()).hexdigest()
    config_path = arguments["config_path"]
    config = json.loads(config_path.read_bytes())
    config["candidates"]["vote"].update(
        adapter=adapter_version,
        policyDigest=digest,
    )
    config_path.write_bytes(json.dumps(config).encode())
    original_exchange = native.exchange

    def exchange(method, path, payload=None):
        if path == "/api/show":
            return {
                "details": {"quantization_level": "Q8_0"},
                "capabilities": ["completion"],
            }
        if path == "/api/chat":
            native.active = True
            native.calls.append((path, payload))
            return {
                "model": "pinned:vote",
                "created_at": "fixture",
                "done": True,
                "done_reason": "stop",
                "total_duration": 1,
                "load_duration": 0,
                "prompt_eval_count": 10,
                "eval_count": 20,
                "message": {
                    "role": "assistant",
                    "content": json.dumps(
                        {
                            "decision": "ABSTAIN" if invalid else "PROPOSE",
                            "classId": "A",
                        }
                    ),
                },
            }
        return original_exchange(method, path, payload)

    native.exchange = exchange
    runtime = LocalClassificationRuntime(**arguments, enabled=True)
    if invalid:
        with pytest.raises(ObservationRejected) as failure:
            runtime.infer(
                "vote",
                {"input": "alpha"},
                runtime_digest=runtime.digest,
                policy_digest=digest,
            )
        assert failure.value.reason_code == "INVALID_STATE_PAIR"
    else:
        result = runtime.infer(
            "vote",
            {"input": "alpha"},
            runtime_digest=runtime.digest,
            policy_digest=digest,
        )
        assert result["observation"]["class_id"] == "A"
        assert result["observation"]["evidence_status"] == "UNVERIFIED_BY_MODEL"
        assert result["observation"]["model_scores"] is None
    assert not any(path == "/v1/systemone" for path, _ in native.calls)
    assert runtime._slot.acquire(blocking=False)
    runtime._slot.release()
