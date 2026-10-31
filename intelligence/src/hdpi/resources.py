"""Local resources work both in a source checkout and an installed wheel."""

from pathlib import Path
import os

from .domain.errors import EngineConfigurationError


def resource_root(name: str) -> Path:
    if name == 'profiles':
        configured = os.environ.get('HDPI_PROFILE_ROOT')
        if not configured or not Path(configured).is_dir():
            raise EngineConfigurationError('Explicit HDPI_PROFILE_ROOT or ProfileRegistry(root=...) required')
        return Path(configured).resolve()
    package = Path(__file__).resolve().parent / '_resources' / name
    if package.is_dir():
        return package
    project = Path(__file__).resolve().parents[2]
    source = project.parent / 'contracts/intelligence/v1'
    if not source.is_dir():
        raise EngineConfigurationError('Missing packaged resources: ' + name)
    return source
