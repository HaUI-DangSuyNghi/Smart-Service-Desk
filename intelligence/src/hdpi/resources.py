"""Local resources work both in a source checkout and an installed wheel."""

from pathlib import Path

from .domain.errors import EngineConfigurationError


def resource_root(name: str) -> Path:
    package = Path(__file__).resolve().parent / '_resources' / name
    if package.is_dir():
        return package
    project = Path(__file__).resolve().parents[2]
    source = project / 'profiles' if name == 'profiles' else project.parent / 'contracts/intelligence/v1'
    if not source.is_dir():
        raise EngineConfigurationError('Missing packaged resources: ' + name)
    return source
