"""Release boundary tests use data-only fixtures; never model weights or issuer keys."""

import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from full_model_release import read_json, safe_file, verify_ancestry, verify_approval


class ReleaseBoundaryTest(unittest.TestCase):
    def test_rejects_duplicate_json_keys(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "manifest.json"
            path.write_text('{"approved":false,"approved":true}', encoding="utf-8")
            with self.assertRaises(ValueError):
                read_json(path)

    def test_safe_files_reject_traversal_aliases_and_absolute_paths(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "fixture.txt").write_text("synthetic", encoding="utf-8")
            self.assertEqual(safe_file(root, "fixture.txt"), root / "fixture.txt")
            for relative in ("../fixture.txt", "/fixture.txt", "./fixture.txt", "C:/fixture.txt", "a\\fixture.txt", "", "a//fixture.txt"):
                with self.subTest(relative=relative), self.assertRaises((ValueError, FileNotFoundError)):
                    safe_file(root, relative)

    def test_requires_exact_base_layers_platform_and_nonroot(self):
        code = {"RootFS": {"Layers": ["base-a", "base-b"]}, "Os": "linux", "Architecture": "amd64"}
        full = {"RootFS": {"Layers": ["base-a", "base-b", "model"]}, "Os": "linux", "Architecture": "amd64", "Config": {"User": "10001:10001"}}
        verify_ancestry(code, full)
        for layers in (["base-a", "base-b"], ["other", "base-b", "model"], []):
            with self.subTest(layers=layers), self.assertRaises(ValueError):
                verify_ancestry(code, {**full, "RootFS": {"Layers": layers}})
        for change in ({"Os": "windows"}, {"Architecture": "arm64"}, {"Config": {"User": "root"}}):
            with self.subTest(change=change), self.assertRaises(ValueError):
                verify_ancestry(code, {**full, **change})

    def test_bad_signature_never_accepts_claimed_approval(self):
        with patch("full_model_release.subprocess.run") as run:
            run.return_value.returncode = 1
            with self.assertRaises(ValueError):
                verify_approval("approval.json", "signature", "external-public-key")

    def test_expired_or_public_approval_cannot_distribute(self):
        base = {"schemaVersion": "ai-d2-distribution-approval@1.0.0", "approved": True, "privateChannel": True,
                "expiresEpochSecond": 9999999999, "issuer": "synthetic-independent-issuer", "modelDigest": "a" * 64}
        with patch("full_model_release.subprocess.run") as run, patch("full_model_release.read_json") as read:
            run.return_value.returncode = 0
            for change in ({"expiresEpochSecond": 0}, {"approved": False}, {"privateChannel": False}, {"modelDigest": "latest"}):
                read.return_value = {**base, **change}
                with self.subTest(change=change), self.assertRaises(ValueError):
                    verify_approval("approval.json", "signature", "external-public-key")


if __name__ == "__main__":
    unittest.main()
