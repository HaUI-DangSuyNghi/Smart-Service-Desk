from typing import Protocol

from ..domain.models import TriageDecision, TriageRequest
from ..profiles.models import RuntimeProfile


class TriageEngine(Protocol):
    name: str
    build_version: str

    def triage(self, request: TriageRequest, profile: RuntimeProfile) -> TriageDecision: ...
