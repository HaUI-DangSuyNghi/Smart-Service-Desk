"""Verify approved model inventory offline; never downloads or starts inference."""

import argparse
from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest', required=True, type=Path)
    parser.add_argument('--directory', required=True, type=Path)
    parser.add_argument('--offline', action='store_true', required=True)
    parser.add_argument('--installed', action='store_true')
    args = parser.parse_args()

    if not args.installed:
        sys.path.insert(0, str(ROOT / 'intelligence/src'))

    from hdpi.generation.errors import GenerationConfigurationError
    from hdpi.generation.model_manifest import ModelManifest

    try:
        with args.manifest.open('rb') as stream:
            raw = stream.read(131073)

        manifest = ModelManifest.from_bytes(raw)
        manifest.verify_directory(args.directory)
    except (OSError, GenerationConfigurationError):
        print('BLOCKED model manifest, approvals or offline artifact verification failed')

        return 2

    print('PASS offline model inventory ' + manifest.digest)
    print('Receipt authenticity, serving-image execution and network policy require owner review')

    return 0


if __name__ == '__main__':
    raise SystemExit(main())
