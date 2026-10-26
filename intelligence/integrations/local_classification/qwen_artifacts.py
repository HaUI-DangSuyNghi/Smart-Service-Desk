"""Bounded, data-only artifact verification. No pickle, remote code or model import."""

from hashlib import sha256
import json
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import struct
import zipfile

import hdpi
import local_classification

from hdpi.classification.protocols import digest, require
from local_classification.ollama_transport import strict_json


SCHEMA = "ai07-private-qwen-serving-manifest@0.1.0"
BASE_REPOSITORY = "unsloth/Qwen3.5-0.8B"
MAX_FILE = 4 * 1024**3


def canonical(value):
    return json.dumps(
        value, ensure_ascii=True, sort_keys=True, separators=(",", ":")
    ).encode()


def file_digest(path):
    hasher = sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            hasher.update(block)
    return hasher.hexdigest()


def safe_path(root, relative):
    require(type(relative) is str and "\\" not in relative and ":" not in relative)
    item = PurePosixPath(relative)
    require(not item.is_absolute() and item.as_posix() == relative)
    require(all(part not in ("", ".", "..") for part in item.parts))
    root = Path(root).resolve(strict=True)
    path = root / relative
    require(path.resolve(strict=True).is_relative_to(root))
    for ancestor in (path, *path.parents):
        if ancestor == root:
            break
        require(not ancestor.is_symlink() and not ancestor.is_junction())
    require(path.is_file() and 0 < path.stat().st_size <= MAX_FILE)
    return path


def verify_files(root, expected):
    require(type(expected) is dict and 1 <= len(expected) <= 64)
    for relative, expected_digest in expected.items():
        require(digest(expected_digest))
        require(file_digest(safe_path(root, relative)) == expected_digest)
    return sha256(canonical(expected)).hexdigest()


def verify_adapter(root, expected):
    verify_files(root, expected)
    config = strict_json(
        safe_path(root, "adapter/adapter_config.json").read_bytes(), 16384
    )
    require(config["base_model_name_or_path"] == BASE_REPOSITORY)
    require(config["peft_type"] == "LORA" and config["task_type"] == "CAUSAL_LM")
    require(config["r"] == 8 and config["lora_alpha"] == 16)
    require(config["bias"] == "none" and config["modules_to_save"] is None)
    path = safe_path(root, "adapter/adapter_model.safetensors")
    with path.open("rb") as stream:
        size = struct.unpack("<Q", stream.read(8))[0]
        require(0 < size <= 1024 * 1024)
        header = strict_json(stream.read(size), 1024 * 1024)
    tensors = {key: value for key, value in header.items() if key != "__metadata__"}
    require(len(tensors) == 192)
    intervals = []
    for name, value in tensors.items():
        require(
            re.fullmatch(
                r"base_model\.model\.model\.language_model\.layers\.\d+\.(?:mlp|self_attn)\.[a-z_]+\.lora_[AB]\.weight",
                name,
            )
        )
        shape = value["shape"]
        require(value["dtype"] == "F32" and len(shape) == 2)
        require(all(type(dim) is int and dim > 0 for dim in shape))
        require(shape[0 if ".lora_A." in name else 1] == 8)
        start, end = value["data_offsets"]
        require(type(start) is int and type(end) is int and 0 <= start < end)
        require(end - start == 4 * shape[0] * shape[1])
        intervals.append((start, end))
    cursor = 0
    for start, end in sorted(intervals):
        require(start == cursor)
        cursor = end
    require(cursor + 8 + size == path.stat().st_size)
    return {"adapterFiles": len(expected), "tensors": len(tensors)}


def stage_adapter(source, destination, expected):
    """Accept the original private directory or a ZIP, never overwrite a prior stage."""
    destination = Path(destination)
    require(not destination.exists())
    destination.mkdir(parents=True)
    source = Path(source)
    if source.is_dir():
        verify_adapter(source, expected)
        for name in expected:
            target = destination / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(safe_path(source, name), target)
    else:
        with zipfile.ZipFile(source) as archive:
            seen = set()
            total = 0
            for entry in archive.infolist():
                name = entry.orig_filename
                item = PurePosixPath(name)
                require(not item.is_absolute() and "\\" not in name and ":" not in name)
                require(name == entry.filename)
                require(all(part not in ("", ".", "..") for part in item.parts))
                require(name not in seen)
                seen.add(name)
                require(not stat.S_ISLNK(entry.external_attr >> 16))
                require(not entry.flag_bits & 1)
                total += entry.file_size
                require(total <= 128 * 1024 * 1024)
                require(entry.file_size <= 64 * 1024 * 1024)
                require(entry.file_size <= max(1, entry.compress_size) * 250)
                if name in expected:
                    target = destination / name
                    target.parent.mkdir(parents=True, exist_ok=True)
                    with target.open("xb") as stream:
                        stream.write(archive.read(entry))
    return verify_adapter(destination, expected)


def bundle_digest(manifest):
    return sha256(b"ai07-qwen-serving-v1\0" + canonical(manifest)).hexdigest()


def runtime_source_digest():
    files = {}
    for package in (hdpi, local_classification):
        root = Path(package.__file__).parent
        for path in sorted(root.rglob("*.py")):
            files[package.__name__ + "/" + path.relative_to(root).as_posix()] = (
                file_digest(path)
            )
    return sha256(canonical(files)).hexdigest()


def verify_bundle(
    root, *, expected_digest, policy_digest, guard_digest, runtime_identity
):
    manifest = strict_json(safe_path(root, "serving-manifest.json").read_bytes(), 65536)
    require(manifest["schema"] == SCHEMA and bundle_digest(manifest) == expected_digest)
    require(manifest["baseRepository"] == BASE_REPOSITORY)
    require(re.fullmatch(r"[0-9a-f]{40}", manifest["baseCommitSha"]) is not None)
    require(manifest["inferencePromptPolicyDigest"] == policy_digest)
    require(manifest["appGuardPolicyDigest"] == guard_digest)
    require(manifest["runtimeIdentity"] == runtime_identity)
    require(
        re.fullmatch(r"(?:sha256:|host:)[0-9a-f]{64}", runtime_identity) is not None
    )
    require(manifest["runtimeSourceDigest"] == runtime_source_digest())
    require(manifest["trainedOn"] == "MACHINE_SILVER_UNREVIEWED")
    require(
        manifest["reviewedGold"] is False and manifest["operationalAuthority"] is False
    )
    require(
        manifest["release"] == "NO_GO"
        and manifest["trainingBaseRevision"] == "UNRECORDED"
    )
    files = manifest["files"]
    require(verify_files(root, files) == manifest["filesDigest"])
    require(
        all(
            name.startswith(("base/", "adapter/")) or name == "dependencies.lock"
            for name in files
        )
    )
    require(not any(name.endswith((".bin", ".pt", ".pkl", ".py")) for name in files))
    require(
        {
            "base/config.json",
            "base/model.safetensors.index.json",
            "base/source-receipt.json",
            "adapter/tokenizer.json",
            "adapter/tokenizer_config.json",
            "adapter/chat_template.jinja",
            "dependencies.lock",
        }
        <= set(files)
    )
    verify_adapter(
        root,
        {name: value for name, value in files.items() if name.startswith("adapter/")},
    )
    config = strict_json(safe_path(root, "base/config.json").read_bytes(), 65536)
    receipt = strict_json(
        safe_path(root, "base/source-receipt.json").read_bytes(), 65536
    )
    require(receipt["schemaVersion"] == "ai07-qwen-base-download@0.1.0")
    require(
        receipt["repository"] == BASE_REPOSITORY
        and receipt["revision"] == manifest["baseCommitSha"]
    )
    require(
        receipt["files"]
        == {
            name.removeprefix("base/"): value
            for name, value in files.items()
            if name.startswith("base/") and name != "base/source-receipt.json"
        }
    )
    require(config["model_type"] == "qwen3_5")
    require(config["architectures"] == ["Qwen3_5ForConditionalGeneration"])
    index = strict_json(
        safe_path(root, "base/model.safetensors.index.json").read_bytes(), 1024 * 1024
    )
    require(
        all(
            "base/" + name in files and name.endswith(".safetensors")
            for name in index["weight_map"].values()
        )
    )
    # Extra files could be discovered by Transformers/PEFT; reject them before import.
    actual = {
        path.relative_to(root).as_posix()
        for path in Path(root).rglob("*")
        if path.is_file()
    }
    require(actual == set(files) | {"serving-manifest.json"})
    return manifest
