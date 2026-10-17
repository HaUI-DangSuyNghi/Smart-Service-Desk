"""Run with python -I after installing the wheel; never import from source."""

from pathlib import Path

import smart_service_intelligence
from smart_service_intelligence.domain.models import TrustedContext
from smart_service_intelligence.resources import resource_root
from smart_service_intelligence.services.triage import TriageService


def main():
    location = Path(smart_service_intelligence.__file__).resolve()
    if 'site-packages' not in location.parts:
        raise RuntimeError('Verification must load the installed wheel')
    if '_resources' not in resource_root('profiles').parts or '_resources' not in resource_root('contracts').parts:
        raise RuntimeError('Wheel must contain profiles and shared contracts')
    payload = {'contractVersion': '0.1.0', 'requestId': 'installed-wheel', 'profile': 'service-desk',
               'context': {'workspaceId': 'local-wheel-check'},
               'input': {'title': 'Wi-Fi chập chờn', 'description': 'Một máy bị ảnh hưởng; có thể chờ.'}}
    run = TriageService().triage(payload, trusted_context=TrustedContext(workspace_id='local-wheel-check'),
                                profile_version='0.2.0')
    if run.response['result'] != {'category': 'NETWORK', 'priority': 'LOW', 'suggestedGroup': 'IT-NETWORK'}:
        raise RuntimeError('Installed package triage mismatch')
    print('PASS installed wheel: immutable resources, schema, triage and literal evidence')


if __name__ == '__main__':
    main()
