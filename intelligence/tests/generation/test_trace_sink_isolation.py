from dataclasses import asdict

import pytest

from hdpi.generation.deterministic import CitationBoundTemplateProvider
from hdpi.generation.errors import GenerationContractError, GenerationTimeout
from hdpi.generation.models import GenerationDraft
from hdpi.generation.policy import GenerationPolicy
from hdpi.services.assist import GroundedAssistanceService


@pytest.mark.parametrize('mode', ['success', 'refusal', 'timeout', 'contract'])
def test_sink_failure_never_replaces_result_or_original_failure(bundle, mode, caplog):
    policy = GenerationPolicy(
        identity='synthetic-policy@1', instructions='Quote supplied evidence.'
    )
    baseline = CitationBoundTemplateProvider(app_prompt_version=policy.identity)
    traces = []

    class ScriptedProvider:
        name = baseline.name
        version = baseline.version
        config_version = baseline.config_version
        runtime_identity = baseline.runtime_identity

        def generate(self, request):
            if mode == 'timeout':
                raise GenerationTimeout('DUMMY_CANARY private timeout')

            if mode == 'contract':
                raise GenerationContractError('DUMMY_CANARY private output')

            if mode == 'refusal':
                return GenerationDraft(claims=())

            return baseline.generate(request)

    def failing_sink(trace):
        traces.append(asdict(trace))

        raise RuntimeError('DUMMY_CANARY sink leaked prompt')

    service = GroundedAssistanceService(provider=ScriptedProvider(), trace_sink=failing_sink)

    if mode == 'contract':
        with pytest.raises(GenerationContractError) as caught:
            service.assist('DUMMY_CANARY question', bundle=bundle, policy=policy)

        assert 'DUMMY_CANARY' not in str(caught.value)
        assert traces[0]['status'] == 'ERROR'
    else:
        run = service.assist('DUMMY_CANARY question', bundle=bundle, policy=policy)
        expected = {'success': 'SUCCESS', 'refusal': 'INSUFFICIENT_EVIDENCE', 'timeout': 'DEGRADED'}

        assert run.response.status == expected[mode]
        assert traces[0]['status'] == expected[mode]

    assert len(traces) == 1
    assert 'DUMMY_CANARY' not in repr(traces)
    assert 'DUMMY_CANARY' not in caplog.text
