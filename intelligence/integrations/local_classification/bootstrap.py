"""Startup artifact verification and a cheap, fail-closed read-only integrity lease."""

from dataclasses import dataclass
from hashlib import sha256
import os
from pathlib import Path, PurePosixPath
import re

from hdpi.classification.protocols import require
from local_classification.ollama_transport import strict_json


def canonical_digest(value):
    import json

    return sha256(
        json.dumps(
            value, sort_keys=True, ensure_ascii=True, separators=(",", ":")
        ).encode("utf-8")
    ).hexdigest()


def _identity(path):
    value = path.stat()
    link = path.lstat()
    return (
        value.st_dev,
        value.st_ino,
        value.st_size,
        value.st_mtime_ns,
        value.st_ctime_ns,
        link.st_ino,
        link.st_mtime_ns,
        link.st_ctime_ns,
        os.readlink(path) if path.is_symlink() else None,
    )


@dataclass(frozen=True)
class ArtifactLease:
    snapshots: tuple

    @classmethod
    def verify(cls, root, files, *, require_readonly=True):
        root = Path(root).resolve(strict=True)
        require(root.is_dir() and type(files) is list and 1 <= len(files) <= 64)

        if require_readonly:
            require(hasattr(os, "statvfs"))
            require(bool(os.statvfs(root).f_flag & os.ST_RDONLY))

        snapshots, seen = [], set()

        for item in files:
            require(type(item) is dict and set(item) == {"path", "sha256", "bytes"})
            relative = PurePosixPath(item["path"])
            require(not relative.is_absolute() and str(relative) == item["path"])
            require(all(part not in ("", ".", "..") for part in relative.parts))
            require(re.fullmatch(r"[A-Za-z0-9_./:-]+", item["path"]) is not None)
            require(item["path"] not in seen)
            seen.add(item["path"])
            require(
                type(item["sha256"]) is str
                and re.fullmatch(r"[0-9a-f]{64}", item["sha256"])
            )
            require(type(item["bytes"]) is int and item["bytes"] > 0)
            path = root / item["path"]
            require(path.resolve(strict=True).is_relative_to(root))

            if path.is_symlink():
                # Only Ollama's content-addressed v2 manifest index may be a link.
                target = path.resolve(strict=True)
                require(relative.parts[0] == "manifests-v2")
                require(not PurePosixPath(os.readlink(path)).is_absolute())
                require(target == root / "blobs" / ("sha256-" + item["sha256"]))

            for parent in (path, *path.parents):
                if parent == root:
                    break
                require(
                    (parent == path or not parent.is_symlink())
                    and not parent.is_junction()
                )

            before = _identity(path)
            hasher = sha256()

            with path.open("rb") as stream:
                for block in iter(lambda: stream.read(4 * 1024 * 1024), b""):
                    hasher.update(block)

            require(before == _identity(path) and before[2] == item["bytes"])
            require(hasher.hexdigest() == item["sha256"])
            snapshots.append((path, before))

        return cls(tuple(snapshots))

    def check(self):
        for path, expected in self.snapshots:
            require(_identity(path) == expected)


def load_configuration(path):
    path = Path(path).resolve(strict=True)
    raw = path.read_bytes()
    value = strict_json(raw, 262144)
    require(
        type(value) is dict
        and set(value)
        == {
            "contractVersion",
            "mode",
            "ollamaVersion",
            "ollamaImage",
            "bridgeRevision",
            "contextTokens",
            "modelFiles",
            "candidates",
        }
    )
    require(value["contractVersion"] == "local-classification-runtime@0.1.0")
    require(value["mode"] == "SYNTHETIC_LAB_ONLY" and value["contextTokens"] == 2048)
    require(value["ollamaVersion"] == "0.40.8")
    require(type(value["ollamaImage"]) is str and "@sha256:" in value["ollamaImage"])
    require(
        type(value["bridgeRevision"]) is str
        and re.fullmatch(r"[0-9a-f]{64}", value["bridgeRevision"])
    )
    require(type(value["candidates"]) is dict and 1 <= len(value["candidates"]) <= 8)

    for key, candidate in value["candidates"].items():
        require(re.fullmatch(r"[a-z][a-z0-9_-]{0,31}", key) is not None)
        require(
            type(candidate) is dict
            and set(candidate)
            == {
                "adapter",
                "model",
                "apiDigest",
                "policyFile",
                "policyDigest",
                "tokenizerDigest",
                "modelArtifactDigest",
                "quantization",
                "sourceModelDigest",
                "licenseStatus",
            }
        )
        require(
            candidate["adapter"]
            in (
                "tev1-systemone@0.1.0",
                "gemma-chat-schema@0.1.0",
                "gemma-classify-only@0.2.0",
            )
        )
        require(
            type(candidate["model"]) is str
            and re.fullmatch(r"[a-z0-9_.-]+:[a-z0-9_.-]+", candidate["model"])
        )
        require(
            not candidate["model"].endswith(":latest")
            and ":cloud" not in candidate["model"]
        )
        require(candidate["licenseStatus"] == "PENDING_OWNER_REVIEW_LAB_ONLY")

        for field in (
            "apiDigest",
            "policyDigest",
            "tokenizerDigest",
            "modelArtifactDigest",
            "sourceModelDigest",
        ):
            require(
                type(candidate[field]) is str
                and re.fullmatch(r"[0-9a-f]{64}", candidate[field])
            )

        require(
            re.fullmatch(r"[a-z0-9_.-]+\.json", candidate["policyFile"]) is not None
        )

    return value, sha256(raw).hexdigest(), (path, _identity(path))


def load_policy(directory, candidate):
    root = Path(directory).resolve(strict=True)
    path = root / candidate["policyFile"]
    require(not path.is_symlink() and path.resolve(strict=True).is_relative_to(root))
    raw = path.read_bytes()
    require(sha256(raw).hexdigest() == candidate["policyDigest"])
    value = strict_json(raw, 16384)
    require(
        set(value)
        == {"experimentId", "allowedClasses", "inputFields", "policy", "dataScope"}
    )
    require(value["dataScope"] == "SYNTHETIC_DEV_ONLY")
    return value, (path, _identity(path))
