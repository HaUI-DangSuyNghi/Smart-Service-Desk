"""Application-owned policy values; generic code supplies no business semantics."""

from dataclasses import dataclass

from . import validation as check


@dataclass(frozen=True, kw_only=True)
class GenerationPolicy:
    identity: str
    instructions: str
    max_claims: int = check.MAX_CLAIMS
    needs_review: bool = True

    def __post_init__(self):
        check.text(self.identity)
        check.text(self.instructions, maximum=4096)
        check.require(
            type(self.max_claims) is int and 1 <= self.max_claims <= check.MAX_CLAIMS,
            'Invalid policy claim limit',
        )
        check.require(type(self.needs_review) is bool, 'Invalid policy review state')
