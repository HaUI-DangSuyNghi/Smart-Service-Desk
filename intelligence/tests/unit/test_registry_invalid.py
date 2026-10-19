import hashlib
import json
from pathlib import Path
import shutil

import pytest
import yaml

from hdpi.domain.errors import ProfileInvalid
from hdpi.profiles.registry import ProfileRegistry

ROOT = Path(__file__).resolve().parents[3] / 'intelligence/src/apps/servicedesk/intelligence/profiles'


@pytest.mark.parametrize('mutation', [
    lambda rules: rules['categoryRules']['CAT-NET-001'].update(regex='(a+)+$'),
    lambda rules: rules['impactRules']['I1'].update(positiveSignals=[]),
    lambda rules: rules['urgencyRules']['U2'].update(ruleId='CAT-NET-001'),
])
def test_invalid_profile_fails_during_registry_startup(tmp_path, mutation):
    root = tmp_path / 'profiles'
    shutil.copytree(ROOT, root)
    relative = 'service-desk/versions/0.2.0/rules.yaml'
    path = root / relative
    rules = yaml.safe_load(path.read_text(encoding='utf-8'))
    mutation(rules)
    path.write_text(yaml.safe_dump(rules, allow_unicode=True), encoding='utf-8', newline='\n')
    # Re-pin to ensure this test reaches semantic validation, not just the digest.
    lock_path = root / 'immutable-artifacts.json'
    lock = json.loads(lock_path.read_text())
    lock[relative] = hashlib.sha256(path.read_bytes()).hexdigest()
    lock_path.write_text(json.dumps(lock), encoding='utf-8')
    with pytest.raises(ProfileInvalid):
        ProfileRegistry(root)


@pytest.mark.parametrize('content', ['profiles: [', 'registryVersion: 1\nregistryVersion: 2', 'null', '[]'])
def test_malformed_registry_is_typed_failure(tmp_path, content):
    (tmp_path / 'registry.yaml').write_text(content)
    with pytest.raises(ProfileInvalid):
        ProfileRegistry(tmp_path)
