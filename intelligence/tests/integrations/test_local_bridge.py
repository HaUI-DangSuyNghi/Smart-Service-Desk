from hashlib import sha256
import http.client
import json
import os
from pathlib import Path
import sys
from threading import Thread

import pytest

from hdpi.classification.protocols import ClassificationError


sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "integrations"))

from local_classification.bootstrap import ArtifactLease  # noqa: E402
from local_classification.bridge_api import BridgeServer  # noqa: E402


class Runtime:
    def __init__(self):
        self.calls = []

    def identity(self):
        return {"runtimeDigest": "a" * 64, "operationalAuthority": False}

    def infer(self, candidate_id, fields, **identity):
        self.calls.append((candidate_id, fields, identity))
        return {"operationalAuthority": False, "state": "observed"}


@pytest.fixture
def bridge():
    runtime = Runtime()
    server = BridgeServer(("127.0.0.1", 0), runtime=runtime, token="a" * 43)
    thread = Thread(target=server.serve_forever, daemon=True)
    thread.start()

    try:
        yield server, runtime
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=2)


def call(bridge, path, *, payload=None, token="a" * 43):
    server, _ = bridge
    connection = http.client.HTTPConnection(*server.server_address, timeout=3)
    headers = {"Content-Type": "application/json"}

    if token is not None:
        headers["Authorization"] = "Bearer " + token

    connection.request("GET" if payload is None else "POST", path, payload, headers)
    response = connection.getresponse()
    status, body, cache = (
        response.status,
        json.loads(response.read()),
        response.getheader("Cache-Control"),
    )
    connection.close()
    return status, body, cache


@pytest.mark.parametrize("token", [None, "wrong", "a" * 200])
def test_authentication_precedes_runtime_access(bridge, token):
    assert call(bridge, "/v1/identity", token=token)[0] == 403
    assert call(bridge, "/v1/observe", payload=b"{}", token=token)[0] == 403
    assert bridge[1].calls == []


def test_bounded_private_routes_and_no_store(bridge):
    status, body, cache = call(bridge, "/v1/identity")
    assert (
        status == 200 and body["operationalAuthority"] is False and cache == "no-store"
    )

    for path in ("/api/pull", "/api/chat", "/api/delete", "/v1/observe?model=override"):
        assert call(bridge, path, payload=b"{}")[0] == 404

    assert bridge[1].calls == []


@pytest.mark.parametrize(
    "body",
    [
        b"null",
        b"[]",
        b"{}",
        b"{} {}",
        b'{"x":1,"x":2}',
        b'{"candidateId":"a","model":"override"}',
        b"x" * 16385,
    ],
    ids=["null", "array", "empty", "trailing", "duplicate", "override", "oversized"],
)
def test_malformed_bodies_do_not_reach_inference(bridge, body):
    assert call(bridge, "/v1/observe", payload=body)[0] == 400
    assert bridge[1].calls == []


def test_bridge_passes_only_bounded_trusted_identity_and_input(bridge):
    body = {
        "candidateId": "candidate",
        "runtimeDigest": "a" * 64,
        "policyDigest": "b" * 64,
        "dataScope": "SYNTHETIC_DEV_ONLY",
        "input": {"input": "untrusted state"},
    }
    assert call(bridge, "/v1/observe", payload=json.dumps(body))[0] == 200
    assert len(bridge[1].calls) == 1
    body["subject"] = "foreign actor"
    assert call(bridge, "/v1/observe", payload=json.dumps(body))[0] == 400
    assert len(bridge[1].calls) == 1


def test_artifact_lease_hashes_at_startup_and_detects_mutation_without_rehash(
    tmp_path, monkeypatch
):
    content = b"synthetic model artifact"
    path = tmp_path / "model.fixture"
    path.write_bytes(content)
    files = [
        {
            "path": path.name,
            "bytes": len(content),
            "sha256": sha256(content).hexdigest(),
        }
    ]
    lease = ArtifactLease.verify(tmp_path, files, require_readonly=False)
    monkeypatch.setattr(
        "local_classification.bootstrap.sha256", lambda: pytest.fail("hot-path rehash")
    )
    lease.check()
    path.write_bytes(b"changed model artifact length")

    with pytest.raises(ClassificationError):
        lease.check()


def test_artifact_hash_and_path_escape_rejected(tmp_path):
    path = tmp_path / "model.fixture"
    path.write_bytes(b"synthetic")

    with pytest.raises(ClassificationError):
        ArtifactLease.verify(
            tmp_path,
            [{"path": path.name, "bytes": 9, "sha256": "0" * 64}],
            require_readonly=False,
        )

    with pytest.raises(ClassificationError):
        ArtifactLease.verify(
            tmp_path,
            [{"path": "../outside", "bytes": 1, "sha256": "0" * 64}],
            require_readonly=False,
        )


@pytest.mark.skipif(
    os.name == "nt",
    reason="Native Ollama index is verified on Linux container filesystems",
)
def test_native_manifest_index_link_is_content_addressed_and_lease_bound(tmp_path):
    content = b"synthetic manifest, no model"
    digest = sha256(content).hexdigest()
    blob = tmp_path / "blobs" / ("sha256-" + digest)
    blob.parent.mkdir()
    blob.write_bytes(content)
    link = tmp_path / "manifests-v2/registry/opaque/version"
    link.parent.mkdir(parents=True)
    link.symlink_to(os.path.relpath(blob, link.parent))
    item = {
        "path": link.relative_to(tmp_path).as_posix(),
        "sha256": digest,
        "bytes": len(content),
    }
    lease = ArtifactLease.verify(tmp_path, [item], require_readonly=False)
    lease.check()
    link.unlink()
    link.symlink_to("../wrong")
    with pytest.raises((ClassificationError, FileNotFoundError)):
        lease.check()
    arbitrary = tmp_path / "weights.fixture"
    arbitrary.symlink_to(blob)
    with pytest.raises(ClassificationError):
        ArtifactLease.verify(
            tmp_path, [{**item, "path": arbitrary.name}], require_readonly=False
        )
