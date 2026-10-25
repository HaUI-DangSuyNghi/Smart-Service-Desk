"""Versioned candidate contracts; labels and interpretation belong to the caller."""

from dataclasses import dataclass, field
import re
from typing import Protocol


class ClassificationError(ValueError):
    """Safe contract failure without raw inference output."""


def require(condition):
    if not condition:
        raise ClassificationError("Classification contract failed")


def digest(value):
    return type(value) is str and re.fullmatch(r"[0-9a-f]{64}", value) is not None


@dataclass(frozen=True, kw_only=True)
class ClassificationRequest:
    fields: tuple[tuple[str, str], ...] = field(repr=False)
    allowed_classes: tuple[str, ...]
    policy_digest: str
    max_input_chars: int = 8192

    def __post_init__(self):
        require(type(self.fields) is tuple and 1 <= len(self.fields) <= 8)
        require(all(type(pair) is tuple and len(pair) == 2 for pair in self.fields))
        require(
            all(
                type(key) is str
                and re.fullmatch(r"[a-z][a-z_]{0,31}", key)
                and type(text) is str
                for key, text in self.fields
            )
        )
        require(len({key for key, _ in self.fields}) == len(self.fields))
        require(type(self.max_input_chars) is int and 1 <= self.max_input_chars <= 8192)
        require(0 < sum(len(text) for _, text in self.fields) <= self.max_input_chars)
        require(
            type(self.allowed_classes) is tuple and 2 <= len(self.allowed_classes) <= 64
        )
        require(
            all(
                type(value) is str and re.fullmatch(r"[A-Za-z0-9_-]{1,64}", value)
                for value in self.allowed_classes
            )
        )
        require(len(set(self.allowed_classes)) == len(self.allowed_classes))
        require(digest(self.policy_digest))


@dataclass(frozen=True, kw_only=True)
class InputSpan:
    """Python Unicode code-point offsets; start inclusive, end exclusive."""

    field: str
    start: int
    end: int
    text: str = field(repr=False)


@dataclass(frozen=True, kw_only=True)
class ClassificationProposal:
    contract_version: str
    decision: str
    class_id: str | None
    spans: tuple[InputSpan, ...] = ()
    score: float | None = None
    score_semantics: str | None = None


class ClassificationCandidate(Protocol):
    def predict(self, request: ClassificationRequest) -> ClassificationProposal: ...
