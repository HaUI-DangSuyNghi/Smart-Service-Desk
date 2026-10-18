"""Validate immutable authoring/runtime profile resources without running triage."""

from pathlib import Path
import hashlib
import json
import os
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'intelligence/src'))

from hdpi.profiles.validation import load_documents, read_yaml, require, verify_lock


def check_history(root, lock, baseline):
    """Changing both an old artifact and its digest cannot bypass the version gate."""
    def read_git(path):
        result = subprocess.run(['git', 'show', f'{baseline}:{path}'], cwd=ROOT, capture_output=True)
        return result.stdout if result.returncode == 0 else None

    # Reject bad refs rather than silently skipping the history gate.
    result = subprocess.run(['git', 'rev-parse', '--verify', baseline + '^{commit}'], cwd=ROOT, capture_output=True)
    require(result.returncode == 0, 'Immutable history baseline does not exist')
    prefix = 'apps/servicedesk/intelligence/profiles/'
    if read_git(prefix + 'immutable-artifacts.json') is None:
        prefix = 'intelligence/profiles/'  # Pre-recovery immutable history, not a runtime fallback.
    previous = read_git(prefix + 'immutable-artifacts.json')
    if previous is not None:
        old_lock = json.loads(previous)
        require(all(lock.get(path) == digest for path, digest in old_lock.items()), 'Reused immutable version or removed historical artifact')
    # Also protects the initial v0.1 artifacts before a lock existed on develop.
    for relative, digest in lock.items():
        historical = read_git(prefix + relative)
        if historical is not None:
            require(hashlib.sha256(historical).hexdigest() == digest, 'Historical version content changed: ' + relative)
    print('PASS immutable profile history against ' + baseline)


def main():
    root = ROOT / 'apps/servicedesk/intelligence/profiles'
    registry = read_yaml(root / 'registry.yaml')
    require(registry['registryVersion'] == '1', 'Unsupported registry version')
    lock = verify_lock(root)
    check_history(root, lock, os.environ.get('INTELLIGENCE_PROFILE_BASE_REF', 'HEAD'))
    count = 0
    for identity, entry in registry['profiles'].items():
        if entry['executable']:
            load_documents(root, entry['file'], identity)
            count += 1
    print(f'PASS {count} executable profile; immutable v0.1/v0.2 pins, signals, routing, precedence and matrix')


if __name__ == '__main__':
    main()
