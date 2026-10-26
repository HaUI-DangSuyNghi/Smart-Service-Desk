"""Public, byte-pinned license texts stay complete and match shipped versions."""

from hashlib import sha256
import json
from pathlib import Path

import pytest


ROOT = Path(__file__).resolve().parents[3]


def test_base_license_is_complete_original_text_with_provenance():
    folder = ROOT / "licenses/models/qwen35-08b"
    provenance = json.loads((folder / "provenance.json").read_bytes())
    raw = (folder / provenance["licenseFile"]).read_bytes()
    assert len(raw) > 10000 and b"Apache License" in raw and b"Version 2.0" in raw
    assert sha256(raw).hexdigest() == provenance["licenseSha256"]
    assert provenance["scope"] == "BASE_MODEL_ONLY"
    assert provenance["repository"] == "unsloth/Qwen3.5-0.8B"
    assert provenance["revision"] in provenance["source"]


@pytest.mark.parametrize("distribution", ["tokenizers", "cuda-toolkit"])
def test_upstream_supplement_matches_locked_version_and_original_bytes(distribution):
    folder = ROOT / "licenses/runtime-supplements"
    manifest = json.loads((folder / "provenance.json").read_bytes())
    item = next(row for row in manifest["files"] if row["distribution"] == distribution)
    lock = (
        (ROOT / "intelligence/requirements-qwen-linux.lock")
        .read_text("ascii")
        .splitlines()
    )
    assert f"{distribution}=={item['version']}" in lock
    raw = (folder / item["file"]).read_bytes()
    assert 10000 < len(raw) < 2 * 1024 * 1024
    assert sha256(raw).hexdigest() == item["sha256"]
    assert item["source"].startswith("https://")
