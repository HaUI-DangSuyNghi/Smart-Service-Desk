"""Synthetic safetensors/files exercise private staging and immutable identities."""

from hashlib import sha256
from pathlib import Path
import struct
import sys
import zipfile

import pytest

from hdpi.classification.protocols import ClassificationError

if not sys.flags.isolated:
    sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "integrations"))

from local_classification.qwen_artifacts import (  # noqa: E402
    BASE_REPOSITORY,
    SCHEMA,
    bundle_digest,
    canonical,
    file_digest,
    runtime_source_digest,
    safe_path,
    stage_adapter,
    verify_adapter,
    verify_bundle,
)


@pytest.fixture
def bundle(tmp_path):
    root = tmp_path / "bundle"
    adapter = root / "adapter"
    adapter.mkdir(parents=True)
    config = {
        "base_model_name_or_path": BASE_REPOSITORY,
        "peft_type": "LORA",
        "task_type": "CAUSAL_LM",
        "r": 8,
        "lora_alpha": 16,
        "bias": "none",
        "modules_to_save": None,
    }
    (adapter / "adapter_config.json").write_bytes(canonical(config))
    header, cursor = {}, 0
    for layer in range(32):
        for projection in ("q_proj", "k_proj", "v_proj"):
            for direction in "AB":
                name = f"base_model.model.model.language_model.layers.{layer}.self_attn.{projection}.lora_{direction}.weight"
                shape = [8, 1] if direction == "A" else [1, 8]
                header[name] = {
                    "dtype": "F32",
                    "shape": shape,
                    "data_offsets": [cursor, cursor + 32],
                }
                cursor += 32
    raw = canonical(header)
    (adapter / "adapter_model.safetensors").write_bytes(
        struct.pack("<Q", len(raw)) + raw + b"\0" * cursor
    )
    for name in (
        "chat_template.jinja",
        "processor_config.json",
        "README.md",
        "tokenizer.json",
        "tokenizer_config.json",
    ):
        (adapter / name).write_bytes(b"{}")
    base = root / "base"
    base.mkdir()
    (base / "config.json").write_bytes(
        canonical(
            {
                "model_type": "qwen3_5",
                "architectures": ["Qwen3_5ForConditionalGeneration"],
            }
        )
    )
    (base / "model.safetensors.index.json").write_bytes(
        canonical({"weight_map": {"x": "model.safetensors"}})
    )
    (base / "model.safetensors").write_bytes(b"SYNTHETIC_NOT_A_MODEL")
    receipt = {
        "schemaVersion": "ai07-qwen-base-download@0.1.0",
        "repository": BASE_REPOSITORY,
        "revision": "a" * 40,
        "files": {path.name: file_digest(path) for path in base.iterdir()},
    }
    (base / "source-receipt.json").write_bytes(canonical(receipt))
    (root / "dependencies.lock").write_bytes(b"setuptools==75.1.0\n")
    files = {
        path.relative_to(root).as_posix(): file_digest(path)
        for path in root.rglob("*")
        if path.is_file()
    }
    manifest = {
        "schema": SCHEMA,
        "baseRepository": BASE_REPOSITORY,
        "baseCommitSha": "a" * 40,
        "files": files,
        "filesDigest": sha256(canonical(files)).hexdigest(),
        "inferencePromptPolicyDigest": "b" * 64,
        "appGuardPolicyDigest": "c" * 64,
        "runtimeIdentity": "sha256:" + "d" * 64,
        "trainedOn": "MACHINE_SILVER_UNREVIEWED",
        "reviewedGold": False,
        "operationalAuthority": False,
        "release": "NO_GO",
        "trainingBaseRevision": "UNRECORDED",
    }
    manifest["runtimeSourceDigest"] = runtime_source_digest()
    (root / "serving-manifest.json").write_bytes(canonical(manifest))
    return root, manifest


def verify(root, manifest):
    return verify_bundle(
        root,
        expected_digest=bundle_digest(manifest),
        policy_digest="b" * 64,
        guard_digest="c" * 64,
        runtime_identity="sha256:" + "d" * 64,
    )


def test_all_seven_files_and_bundle_identity(bundle, tmp_path):
    root, manifest = bundle
    expected = {
        key: value
        for key, value in manifest["files"].items()
        if key.startswith("adapter/")
    }
    assert verify_adapter(root, expected) == {"adapterFiles": 7, "tensors": 192}
    assert verify(root, manifest) == manifest
    stage = tmp_path / "new-stage"
    assert stage_adapter(root, stage, expected)["tensors"] == 192
    with pytest.raises(ClassificationError):
        stage_adapter(root, stage, expected)


@pytest.mark.parametrize(
    "key,value",
    [
        ("baseCommitSha", "main"),
        ("baseRepository", "other/model"),
        ("reviewedGold", True),
        ("release", "GO"),
        ("operationalAuthority", True),
        ("trainingBaseRevision", "invented"),
        ("inferencePromptPolicyDigest", "d" * 64),
        ("appGuardPolicyDigest", "d" * 64),
        ("runtimeIdentity", "wrong"),
        ("trainedOn", "GOLD"),
    ],
)
def test_even_rehashed_ineligible_manifest_is_rejected(bundle, key, value):
    root, manifest = bundle
    manifest[key] = value
    (root / "serving-manifest.json").write_bytes(canonical(manifest))
    with pytest.raises(ClassificationError):
        verify(root, manifest)


@pytest.mark.parametrize(
    "name",
    [
        "base/model.safetensors",
        "adapter/tokenizer.json",
        "adapter/chat_template.jinja",
        "adapter/adapter_model.safetensors",
        "dependencies.lock",
    ],
)
def test_tampered_bytes_are_rejected_before_loading(bundle, name):
    root, manifest = bundle
    (root / name).write_bytes(b"tampered")
    with pytest.raises(ClassificationError):
        verify(root, manifest)


@pytest.mark.parametrize(
    "name",
    [
        "../escape",
        "/absolute",
        "adapter/../escape",
        "adapter\\escape",
        "C:escape",
        "adapter//tokenizer.json",
    ],
)
def test_traversal_rejected(bundle, name):
    with pytest.raises((ClassificationError, FileNotFoundError)):
        safe_path(bundle[0], name)


def test_unlisted_executable_or_pickle_rejected(bundle):
    root, manifest = bundle
    (root / "base/evil.py").write_text("raise RuntimeError()", encoding="utf-8")
    with pytest.raises(ClassificationError):
        verify(root, manifest)


@pytest.mark.parametrize(
    "name",
    ["../escape", "/escape", "adapter/../escape", "C:/escape", "adapter\\escape"],
)
def test_zip_slip_is_rejected_before_extraction(bundle, tmp_path, name):
    archive = tmp_path / "source.zip"
    with zipfile.ZipFile(archive, "w") as stream:
        stream.writestr(name, b"x")
    if "\\" in name:
        archive.write_bytes(
            archive.read_bytes().replace(b"adapter/escape", b"adapter\\escape")
        )
    with pytest.raises(ClassificationError):
        stage_adapter(archive, tmp_path / "stage", bundle[1]["files"])


def test_zip_symlink_rejected(bundle, tmp_path):
    archive = tmp_path / "link.zip"
    item = zipfile.ZipInfo("adapter/adapter_config.json")
    item.create_system = 3
    item.external_attr = 0o120777 << 16
    with zipfile.ZipFile(archive, "w") as stream:
        stream.writestr(item, "../outside")
    with pytest.raises(ClassificationError):
        stage_adapter(archive, tmp_path / "stage", bundle[1]["files"])


def test_duplicate_manifest_keys_rejected(bundle):
    root, manifest = bundle
    raw = canonical(manifest)
    (root / "serving-manifest.json").write_bytes(raw[:-1] + b',"release":"GO"}')
    with pytest.raises(ClassificationError):
        verify(root, manifest)
