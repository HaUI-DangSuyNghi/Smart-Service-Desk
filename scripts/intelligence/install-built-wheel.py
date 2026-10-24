"""Install exactly one version-verified wheel; reject stale or ambiguous output."""
import argparse
from email.parser import BytesParser
from pathlib import Path
import subprocess
import sys
import tomllib
import zipfile


def verify_wheel_ownership(archive):
    for name in archive.namelist():
        if name.startswith('cloud_providers/'):
            raise ValueError('Vendor integration bundled in generic wheel')
        parts = Path(name).parts
        if ('apps' in parts or 'fixtures' in parts or
                name.startswith('hdpi/_resources/') and not name.startswith('hdpi/_resources/contracts/') or
                name.startswith('hdpi/') and Path(name).suffix in ('.yaml', '.json', '.jsonl', '.md')
                and not name.startswith('hdpi/_resources/contracts/')):
            raise ValueError('Application data/profile bundled in generic wheel: ' + name)


def discover_wheel(directory: Path, expected_version: str) -> Path:
    wheels = sorted(directory.glob('*.whl'))
    if len(wheels) != 1:
        raise ValueError(f'Expected exactly one wheel, found {len(wheels)}')
    wheel = wheels[0]
    with zipfile.ZipFile(wheel) as archive:
        verify_wheel_ownership(archive)
        metadata_files = [name for name in archive.namelist() if name.endswith('.dist-info/METADATA')]
        if len(metadata_files) != 1:
            raise ValueError('Wheel must have exactly one distribution metadata record')
        metadata = BytesParser().parsebytes(archive.read(metadata_files[0]))
    if metadata['Name'] != 'smart-service-intelligence' or metadata['Version'] != expected_version:
        raise ValueError('Wheel distribution/version differs from the build declaration')
    return wheel.resolve()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--dist', type=Path, required=True)
    args = parser.parse_args()
    project = Path(__file__).resolve().parents[2] / 'intelligence/pyproject.toml'
    version = tomllib.loads(project.read_text('utf-8'))['project']['version']
    wheel = discover_wheel(args.dist, version)
    print(f'Installing {wheel.name}; package version {version}', flush=True)
    subprocess.run([sys.executable, '-m', 'pip', 'install', '--no-deps', '--force-reinstall', str(wheel)], check=True)


if __name__ == '__main__':
    main()
