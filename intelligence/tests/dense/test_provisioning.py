from hashlib import sha256
import importlib.util
from pathlib import Path

import pytest

from hdpi.retrieval.dense import artifacts

SCRIPT = Path(__file__).resolve().parents[3] / 'scripts/intelligence/prepare-dense-model.py'
spec = importlib.util.spec_from_file_location('dense_provision', SCRIPT)
provisioning = importlib.util.module_from_spec(spec)
spec.loader.exec_module(provisioning)


def test_offline_provisioning_never_downloads_and_checks_checksum(monkeypatch, tmp_path):
    raw = b'synthetic-model'
    monkeypatch.setattr(artifacts, 'FILES', (('model.onnx', len(raw), sha256(raw).hexdigest()),))
    def forbidden(*args, **kwargs):
        pytest.fail('Offline provision attempted download')
    monkeypatch.setattr(provisioning, 'urlopen', forbidden)
    with pytest.raises(ValueError, match='Missing or corrupt'):
        provisioning.provision(tmp_path, offline=True)
    path = tmp_path / 'model.onnx'
    path.write_bytes(raw)
    provisioning.provision(tmp_path, offline=True)
    path.write_bytes(b'corrupt-model!!')
    with pytest.raises(ValueError):
        provisioning.provision(tmp_path, offline=True)
