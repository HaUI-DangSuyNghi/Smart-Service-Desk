"""Entry point for external Service Desk authoring tools, including wheel checks."""

import argparse
import sys
from pathlib import Path


def main():
    options = argparse.ArgumentParser(add_help=False)
    options.add_argument('--installed', action='store_true')
    arguments, _ = options.parse_known_args()
    root = Path(__file__).resolve().parents[2]

    if not arguments.installed:
        sys.path.insert(0, str(root / 'intelligence/src'))
    sys.path.insert(0, str(root / 'intelligence/src/apps'))

    import hdpi

    if arguments.installed and 'site-packages' not in Path(hdpi.__file__).resolve().parts:
        raise RuntimeError('Installed evaluation must use the generic wheel')

    from servicedesk.intelligence.knowledge.cli import main as run

    return run()


if __name__ == '__main__':
    raise SystemExit(main())
