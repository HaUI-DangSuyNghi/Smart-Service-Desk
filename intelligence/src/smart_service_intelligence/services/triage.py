from dataclasses import dataclass
import platform
from time import perf_counter
import unicodedata

from ..contracts.validator import ContractValidator
from ..domain.errors import AuthorizationRejected, ContractValidationError, EngineUnavailable
from ..domain.models import TriageRequest, TrustedContext
from ..domain.trace import RunTrace
from ..engines.base import TriageEngine
from ..engines.rules.engine import RulesEngine
from ..profiles.registry import ProfileRegistry


@dataclass(frozen=True)
class TriageRun:
    response: dict
    trace: RunTrace


class TriageService:
    def __init__(self, *, registry=None, engine: TriageEngine | None = None, trace_sink=None):
        self.registry = registry or ProfileRegistry()
        self.engine = engine or RulesEngine()
        self.contract = ContractValidator()
        self.trace_sink = trace_sink

    def triage(self, payload, *, trusted_context: TrustedContext, profile_version: str) -> TriageRun:
        started = perf_counter()
        profile, response, error_type = None, None, None
        completed = False
        request_id, workspace_id = '', ''
        try:
            # A wire context is correlation data, never evidence of authorization.
            if not isinstance(trusted_context, TrustedContext):
                raise AuthorizationRejected('An explicit adapter-owned trusted context is required')
            workspace_id = trusted_context.workspace_id
            self.contract.request(payload)
            request_id = payload['requestId']
            if payload['context']['workspaceId'] != workspace_id:
                raise AuthorizationRejected('Request workspace differs from trusted context')
            request = TriageRequest(request_id, workspace_id, payload['profile'],
                                    payload['input']['title'], payload['input']['description'])
            profile = self.registry.get(request.profile_id, profile_version)
            envelope = {key: payload[key] for key in ('contractVersion', 'requestId', 'profile')}
            envelope['workspaceId'] = workspace_id
            try:
                decision = self.engine.triage(request, profile)
            except EngineUnavailable as error:
                error_type = type(error).__name__
                response = {**envelope, 'status': 'DEGRADED', 'result': None,
                            'evidence': [], 'explanation': None, 'runtime': None,
                            'needsReview': True, 'error': {'code': 'INTELLIGENCE_UNAVAILABLE'}}
            else:
                response = {
                    **envelope, 'status': 'SUCCESS',
                    'result': {'category': decision.category, 'priority': decision.priority,
                               'suggestedGroup': decision.suggested_group},
                    'evidence': [{'type': 'INPUT', 'field': e.field, 'text': e.text} for e in decision.evidence],
                    'explanation': {'matchedRules': list(decision.matched_rules), 'impact': decision.impact,
                                    'urgency': decision.urgency, 'reasonCode': decision.reason_code,
                                    'text': 'Gợi ý theo profile; người xử lý xác nhận quyết định cuối cùng.'},
                    'runtime': {'engine': self.engine.name, 'profileVersion': profile.identity,
                                'rulesVersion': profile.rules_version},
                    'needsReview': decision.needs_review, 'error': None,
                }
            # Validate even in release mode: a replaceable engine must not fabricate
            # literal evidence or add fields that shared contract v0.1 forbids.
            self.contract.response(response, payload)
            if response['status'] == 'SUCCESS' and len(response['evidence']) > 16:
                raise ContractValidationError('Evidence exceeds reviewed internal cap')
            completed = True
        except Exception as error:
            error_type = type(error).__name__
            raise
        finally:
            valid = completed
            trace = RunTrace(
                request_id=request_id, workspace_id=workspace_id,
                profile_version=profile.identity if profile else None,
                rules_version=profile.rules_version if profile else None,
                normalization_version=profile.normalization_version if profile else None,
                python_version=platform.python_version(), unicode_version=unicodedata.unidata_version,
                engine_name=self.engine.name, engine_build_version=self.engine.build_version,
                matched_rule_ids=tuple(response['explanation']['matchedRules']) if valid and response['explanation'] else (),
                status=response['status'] if valid else 'ERROR',
                needs_review=response['needsReview'] if valid else True,
                duration_ms=round((perf_counter() - started) * 1000, 3), error_type=error_type,
            )
            if self.trace_sink is not None:
                self.trace_sink(trace)
        return TriageRun(response, trace)
