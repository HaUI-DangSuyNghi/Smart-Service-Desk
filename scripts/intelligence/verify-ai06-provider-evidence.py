"""Verify current selector identities and outcomes, including mutually stale reports."""

import argparse
import importlib.util
from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = Path(__file__).with_name('ai06-provider-evidence.py')
SPEC = importlib.util.spec_from_file_location('ai06_provider_evidence', SCRIPT)
evidence = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(evidence)


def verify(first, second):
    from hdpi.generation.model_manifest import fingerprint
    from hdpi.generation.selection_codec import strict_json

    current = evidence.build_report()

    for path in (first, second):
        with path.open('rb') as stream:
            report = strict_json(stream.read(262145), maximum=262144)

        if fingerprint(report) != fingerprint(current):
            raise ValueError('Provider evidence differs from current identities, cases or outcomes')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('first', type=Path)
    parser.add_argument('second', type=Path)
    parser.add_argument('--installed', action='store_true')
    args = parser.parse_args()

    if not args.installed:
        sys.path.insert(0, str(ROOT / 'intelligence/src'))

    verify(args.first, args.second)
    print('PASS current AI06 selector identities and 40 synthetic outcomes')


if __name__ == '__main__':
    main()
