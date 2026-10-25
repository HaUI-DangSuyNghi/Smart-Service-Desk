import importlib.util
from pathlib import Path
import zipfile

import pytest

SCRIPT = Path(__file__).resolve().parents[3] / 'scripts/intelligence/install-built-wheel.py'
spec = importlib.util.spec_from_file_location('wheel_installer', SCRIPT)
installer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(installer)


def wheel(path, *, version='0.3.0', name='smart-service-intelligence'):
    with zipfile.ZipFile(path, 'w') as archive:
        archive.writestr('test.dist-info/METADATA', f'Name: {name}\nVersion: {version}\n')


def test_wheel_discovery_requires_exactly_one_artifact(tmp_path):
    with pytest.raises(ValueError):
        installer.discover_wheel(tmp_path, '0.3.0')
    path = tmp_path / 'one.whl'
    wheel(path)
    assert installer.discover_wheel(tmp_path, '0.3.0') == path.resolve()
    wheel(tmp_path / 'stale.whl', version='0.2.0')
    with pytest.raises(ValueError):
        installer.discover_wheel(tmp_path, '0.3.0')


@pytest.mark.parametrize('values', [{'version': '0.2.0'}, {'name': 'another-package'}])
def test_wheel_metadata_matches_project_version(tmp_path, values):
    wheel(tmp_path / 'one.whl', **values)
    with pytest.raises(ValueError):
        installer.discover_wheel(tmp_path, '0.3.0')


@pytest.mark.parametrize('path', ['fixtures/intelligence/service-desk/dataset.yaml',
    'hdpi/_resources/profiles/fake/profile.yaml', 'hdpi/evaluation/fixtures/gold.json',
    'hdpi/evaluation/baseline.json', 'apps/example/policy.py'])
def test_wheel_rejects_bundled_application_evidence(tmp_path, path):
    artifact = tmp_path / 'one.whl'
    wheel(artifact)
    with zipfile.ZipFile(artifact, 'a') as archive:
        archive.writestr(path, 'synthetic mutation')
    with pytest.raises(ValueError, match='bundled'):
        installer.discover_wheel(tmp_path, '0.3.0')


@pytest.mark.parametrize('path', [
    'local_classification/sidecar_protocol.py',
    'cloud_providers/vendor.py',
    'hdpi/classification/model.joblib',
    'hdpi/classification/weights.gguf',
    'hdpi/classification/model.safetensors',
])
def test_wheel_rejects_classification_integrations_and_weights(tmp_path, path):
    artifact = tmp_path / 'one.whl'
    wheel(artifact)

    with zipfile.ZipFile(artifact, 'a') as archive:
        archive.writestr(path, 'synthetic mutation')

    with pytest.raises(ValueError, match='bundled'):
        installer.discover_wheel(tmp_path, '0.3.0')
