from dataclasses import dataclass

from .errors import AuthorizationRejected


@dataclass(frozen=True, kw_only=True)
class TrustedContext:
    """Only the authorized adapter (or local CLI/tests) constructs this object.

    It is not an authenticator. Never deserialize this from a client payload;
    a public adapter must validate JWT/membership before invoking this library.
    """

    workspace_id: str
    subject: str | None = None
    auth_scope_fingerprint: str | None = None

    def __post_init__(self):
        if not isinstance(self.workspace_id, str) or not self.workspace_id.strip():
            raise AuthorizationRejected('Missing trusted workspace')


@dataclass(frozen=True)
class TriageRequest:
    request_id: str
    workspace_id: str
    profile_id: str
    title: str
    description: str


@dataclass(frozen=True)
class InputEvidence:
    field: str
    text: str


@dataclass(frozen=True)
class TriageDecision:
    category: str
    priority: str
    suggested_group: str
    impact: str | None
    urgency: str | None
    reason_code: str
    needs_review: bool
    matched_rules: tuple[str, ...]
    evidence: tuple[InputEvidence, ...]
