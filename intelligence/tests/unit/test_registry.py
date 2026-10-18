import pytest

from hdpi_intelligence.domain.errors import ProfileNotFound, UnsupportedProfileVersion
from hdpi_intelligence.profiles.registry import ProfileRegistry


def test_exact_fail_closed_registry():
    registry = ProfileRegistry()
    profile = registry.get('service-desk', '0.2.0')
    assert registry.get('service-desk', '0.2.0') is profile
    with pytest.raises(TypeError):
        profile.rules['categoryRules']['CAT-NET-001']['category'] = 'ACCOUNT'
    with pytest.raises(ProfileNotFound):
        registry.get('unknown', '0.2.0')
    for version in ('0.1.0', '9.0.0', 'latest', '../../other'):
        with pytest.raises(UnsupportedProfileVersion):
            registry.get('service-desk', version)
