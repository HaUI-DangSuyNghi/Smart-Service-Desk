from dataclasses import dataclass


@dataclass(frozen=True)
class RunTrace:
    request_id: str
    workspace_id: str
    profile_version: str | None
    rules_version: str | None
    normalization_version: str | None
    python_version: str
    unicode_version: str
    engine_name: str
    engine_build_version: str
    matched_rule_ids: tuple[str, ...]
    status: str
    needs_review: bool
    duration_ms: float
    error_type: str | None
