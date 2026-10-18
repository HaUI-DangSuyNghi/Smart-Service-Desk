"""These tests run without loading any Service Desk profile or authored fixture."""
from pathlib import Path
import pytest
import hdpi_intelligence
from hdpi_intelligence.domain.errors import EngineConfigurationError, ProfileNotFound
from hdpi_intelligence.domain.models import TrustedContext
from hdpi_intelligence.profiles.registry import ProfileRegistry
from hdpi_intelligence.services.triage import TriageService

ROOT = Path(__file__).resolve().parents[3] / 'apps/roomrequest/intelligence/profiles'


def test_generic_runtime_has_no_implicit_app_or_bundled_profiles(monkeypatch):
    monkeypatch.delenv('HDPI_PROFILE_ROOT', raising=False)
    with pytest.raises(EngineConfigurationError):
        ProfileRegistry()
    assert not (Path(hdpi_intelligence.__file__).parent / '_resources/profiles').exists()


def test_second_taxonomy_runs_with_existing_engine_and_no_other_profile(monkeypatch):
    monkeypatch.delenv('HDPI_PROFILE_ROOT', raising=False)
    registry = ProfileRegistry(ROOT)
    with pytest.raises(ProfileNotFound):
        registry.get('service-desk', '0.2.0')
    service = TriageService(registry=registry)
    payload = {'contractVersion': '0.1.0', 'requestId': 'second-domain', 'profile': 'room-request',
               'context': {'workspaceId': 'lab-a'}, 'input': {'title': 'Room A-101', 'description': 'Single request; can wait'}}
    run = service.triage(payload, trusted_context=TrustedContext(workspace_id='lab-a', subject='room-owner'), profile_version='0.2.0')
    assert run.response['status'] == 'SUCCESS'
    assert run.response['result'] == {'category': 'BOOKING', 'priority': 'NORMAL', 'suggestedGroup': 'FACILITIES'}
    assert run.response['runtime']['profileVersion'] == 'room-request@0.2.0'
