"""These tests run without loading any Service Desk profile or authored fixture."""
from pathlib import Path
import pytest
import hdpi
from hdpi.domain.errors import EngineConfigurationError, ProfileNotFound
from hdpi.domain.models import TrustedContext
from hdpi.profiles.registry import ProfileRegistry
from hdpi.services.triage import TriageService

ROOT = Path(__file__).parent / 'fixtures/fake-domain'


def test_generic_runtime_has_no_implicit_app_or_bundled_profiles(monkeypatch):
    monkeypatch.delenv('HDPI_PROFILE_ROOT', raising=False)
    with pytest.raises(EngineConfigurationError):
        ProfileRegistry()
    assert not (Path(hdpi.__file__).parent / '_resources/profiles').exists()


def test_second_taxonomy_runs_with_existing_engine_and_no_other_profile(monkeypatch):
    monkeypatch.delenv('HDPI_PROFILE_ROOT', raising=False)
    registry = ProfileRegistry(ROOT)
    with pytest.raises(ProfileNotFound):
        registry.get('another-domain', '1.0')
    service = TriageService(registry=registry)
    payload = {'contractVersion': '0.1.0', 'requestId': 'second-domain', 'profile': 'fake-domain',
               'context': {'workspaceId': 'lab-a'}, 'input': {'title': 'Room A-101', 'description': 'Single request; can wait'}}
    run = service.triage(payload, trusted_context=TrustedContext(workspace_id='lab-a', subject='room-owner'), profile_version='1.0')
    assert run.response['status'] == 'SUCCESS'
    assert run.response['result'] == {'category': 'BOOKING', 'priority': 'NORMAL', 'suggestedGroup': 'FACILITIES'}
    assert run.response['runtime']['profileVersion'] == 'fake-domain@1.0'
