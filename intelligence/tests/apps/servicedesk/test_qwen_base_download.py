"""Provisioning rejects incomplete or wrongly ranged public weights before staging."""

import importlib.util
from pathlib import Path

import pytest


@pytest.fixture
def provisioner():
    script = (
        Path(__file__).resolve().parents[4]
        / "scripts/apps/servicedesk/ai07/prepare-qwen-base.py"
    )
    spec = importlib.util.spec_from_file_location("qwen_base_provisioner", script)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class Response:
    status = 206

    def __init__(self, raw, content_range):
        self.raw = raw
        self.headers = {"Content-Range": content_range}

    def read(self, length):
        return self.raw[:length]

    def __enter__(self):
        return self

    def __exit__(self, *args):
        pass


def test_resume_verifies_range_and_appends_without_losing_prefix(
    provisioner, monkeypatch, tmp_path
):
    path = tmp_path / "weight"
    path.write_bytes(b"prefix")
    seen = []

    def open_request(request, **kwargs):
        seen.append(request.get_header("Range"))
        return Response(b"suffix", "bytes 6-11/12")

    monkeypatch.setattr(provisioner.urllib.request, "urlopen", open_request)
    provisioner.download_file(
        "https://example.invalid/weight", path, size=12, resume=True
    )
    assert path.read_bytes() == b"prefixsuffix"
    assert seen == ["bytes=6-11"]


@pytest.mark.parametrize(
    "raw,content_range",
    [
        (b"short", "bytes 6-11/12"),
        (b"suffix", "bytes 0-5/12"),
        (b"suffix-extra", "bytes 6-11/12"),
    ],
)
def test_partial_or_wrong_range_does_not_corrupt_existing_prefix(
    provisioner, monkeypatch, tmp_path, raw, content_range
):
    path = tmp_path / "weight"
    path.write_bytes(b"prefix")
    monkeypatch.setattr(
        provisioner.urllib.request,
        "urlopen",
        lambda *args, **kwargs: Response(raw, content_range),
    )
    with pytest.raises(ValueError):
        provisioner.download_file(
            "https://example.invalid/weight", path, size=12, resume=True
        )
    assert path.read_bytes() == b"prefix"


def test_existing_download_needs_explicit_resume(provisioner, tmp_path):
    path = tmp_path / "weight"
    path.write_bytes(b"prefix")
    with pytest.raises(ValueError):
        provisioner.download_file("https://example.invalid/weight", path, size=12)
