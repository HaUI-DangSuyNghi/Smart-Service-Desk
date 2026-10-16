"""Validate immutable authoring/runtime profile resources without running triage."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'intelligence/src'))

from smart_service_intelligence.profiles.validation import load_documents, read_yaml, require, verify_lock


def main():
    root = ROOT / 'intelligence/profiles'
    registry = read_yaml(root / 'registry.yaml')
    require(registry['registryVersion'] == '1', 'Unsupported registry version')
    verify_lock(root)
    count = 0
    for identity, entry in registry['profiles'].items():
        if entry['executable']:
            load_documents(root, entry['file'], identity)
            count += 1
    print(f'PASS {count} executable profile; immutable v0.1/v0.2 pins, signals, routing, precedence and matrix')


if __name__ == '__main__':
    main()
