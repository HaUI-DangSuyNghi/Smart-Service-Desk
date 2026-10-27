"""Data-only release tests. Ephemeral fixture keys cannot approve real model delivery."""

import argparse
import base64
import hashlib
import hmac
import io
import json
from pathlib import Path
import subprocess
import tarfile
import tempfile
import time
import unittest

import team_release as release


class OfflineReleaseTest(unittest.TestCase):
    def archive(self, path, name="manifest.json", kind=tarfile.REGTYPE):
        with tarfile.open(path, "w") as archive:
            item = tarfile.TarInfo(name)
            item.type = kind
            item.linkname = "../../outside"
            item.size = 2 if kind == tarfile.REGTYPE else 0
            archive.addfile(item, io.BytesIO(b"[]") if item.size else None)

    def test_archive_rejects_host_escape_links_devices_and_duplicate_names(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "images.tar"
            self.archive(path)
            release.validate_archive(path)
            for name, kind in (("../escape", tarfile.REGTYPE), ("/absolute", tarfile.REGTYPE),
                               ("C:/drive", tarfile.REGTYPE), ("manifest.json", tarfile.SYMTYPE),
                               ("manifest.json", tarfile.LNKTYPE), ("manifest.json", tarfile.CHRTYPE)):
                self.archive(path, name, kind)
                with self.subTest(name=name, kind=kind), self.assertRaises(ValueError):
                    release.validate_archive(path)
            self.archive(path)
            with tarfile.open(path, "a") as archive:
                item = tarfile.TarInfo("manifest.json")
                item.size = 2
                archive.addfile(item, io.BytesIO(b"[]"))
            with self.assertRaises(ValueError):
                release.validate_archive(path)

    def sign(self, path, key):
        subprocess.run(["openssl", "dgst", "-sha256", "-sign", str(key), "-out", str(path.with_suffix(".sig")), str(path)],
                       check=True, capture_output=True)

    def test_actual_signed_inventory_detects_tamper_and_unlisted_secrets(self):
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            root = base / "kit"
            root.mkdir()
            key, public = base / "FIXTURE-ONLY.key", base / "FIXTURE-ONLY.pub"
            subprocess.run(["openssl", "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:2048", "-out", str(key)], check=True, capture_output=True)
            subprocess.run(["openssl", "pkey", "-in", str(key), "-pubout", "-out", str(public)], check=True, capture_output=True)
            self.archive(root / "images.tar")
            for name in release.STATIC_FILES:
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text("SYNTHETIC FIXTURE", encoding="utf-8")
            receipt = {"modelDigest": "a" * 64, "distributionImageId": "sha256:" + "b" * 64, "codeImageId": "sha256:" + "c" * 64}
            (root / "model-image.json").write_text(json.dumps(receipt), encoding="utf-8")
            approval = {"schemaVersion": "ai-d2-distribution-approval@1.0.0", "approved": True, "privateChannel": True,
                        "expiresEpochSecond": int(time.time()) + 60, "issuer": "FIXTURE_ONLY_NO_REAL_AUTHORITY",
                        "channel": "FIXTURE_OFFLINE", "modelDigest": "a" * 64}
            (root / "approval.json").write_text(json.dumps(approval), encoding="utf-8")
            self.sign(root / "approval.json", key)
            images = {name: {"configId": "sha256:" + "b" * 64, "os": "linux", "architecture": "amd64"} for name in release.SERVICES}
            images["model-code"] = {**images["ai07-qwen"], "configId": receipt["codeImageId"]}
            manifest = {"schemaVersion": "ai-d2-offline-release@1.0.0", "productionApproved": False,
                        "os": "linux", "architecture": "amd64", "modelDigest": receipt["modelDigest"],
                        "approvedChannel": "FIXTURE_OFFLINE", "images": images, "files": release.inventory(root)}
            (root / "release.json").write_text(json.dumps(manifest), encoding="utf-8")
            self.sign(root / "release.json", key)
            args = argparse.Namespace(root=root, public_key=public, manifest_sha256=release.file_digest(root / "release.json"))
            release.verify(args)
            extra = root / "unlisted-secret.key"
            extra.write_text("SYNTHETIC SECRET", encoding="ascii")
            with self.assertRaises(ValueError):
                release.verify(args)
            extra.unlink()
            (root / "images.tar").write_bytes(b"modified archive")
            with self.assertRaises(ValueError):
                release.verify(args)
            (root / "release.json").write_text("{}", encoding="utf-8")
            args.manifest_sha256 = release.file_digest(root / "release.json")
            with self.assertRaises(ValueError):
                release.verify(args)

    def test_assisted_activation_binds_real_grant_bytes_scope_policy_and_expiry(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "context").mkdir()
            activation = {"workspace": "lab-a", "modelDigest": "a" * 64, "policyDigest": "b" * 64,
                          "contextPolicyDigest": "c" * 64, "readinessPolicyDigest": "d" * 64,
                          "contextualPolicyDigest": "e" * 64, "assessmentPolicyDigest": "f" * 64, "rightsFiles": {}}
            key = "SYNTHETIC_FIXTURE_ONLY_" + "x" * 32
            for name in ("context.key", "classifier.key"):
                (root / name).write_text(key, encoding="ascii")
            grants = {
                "classifier.json": {"purpose": "AI07_ASSISTED_PROCESSING", "modelDigest": "a" * 64,
                                    "policyDigest": "b" * 64, "assessmentPolicyBundleDigest": "f" * 64},
                **{f"context/lab-a.{scope}.json": {"purpose": "AI08_ADVISORY_PROCESSING", "scope": scope,
                    "policyBundleDigest": "c" * 64, "readinessPolicyDigest": "d" * 64, "contextualPolicyDigest": "e" * 64}
                   for scope in ("OWN_TICKET", "WORKSPACE_REVIEW")}}
            for name, grant in grants.items():
                payload = json.dumps({**grant, "workspace": "lab-a", "expiresEpochSecond": int(time.time()) + 60,
                                      "issuer": "SYNTHETIC_FIXTURE_ONLY"}).encode()
                envelope = {"payload": base64.b64encode(payload).decode(), "signature": hmac.digest(key.encode(), payload, "sha256").hex()}
                (root / name).write_text(json.dumps(envelope), encoding="utf-8")
            activation["rightsFiles"] = {name: release.file_digest(path) for name, path in
                                         ((str(path.relative_to(root).as_posix()), path) for path in root.rglob("*") if path.is_file())}
            model = {"modelDigest": "a" * 64, "policyDigest": "b" * 64}
            release.validate_rights(root, activation, model)
            for field in ("modelDigest", "policyDigest", "contextPolicyDigest", "readinessPolicyDigest", "contextualPolicyDigest", "assessmentPolicyDigest"):
                with self.subTest(field=field), self.assertRaises(ValueError):
                    release.validate_rights(root, {**activation, field: "0" * 64}, model)
            (root / "context.key").write_text("recipient substituted key", encoding="ascii")
            with self.assertRaises(ValueError):
                release.validate_rights(root, activation, model)


if __name__ == "__main__":
    unittest.main()
