"""Application-owned policy values; generic code supplies no business semantics."""

from dataclasses import dataclass
import unicodedata

from . import validation as check


@dataclass(frozen=True, kw_only=True)
class GenerationPolicy:
    identity: str
    instructions: str
    max_claims: int = check.MAX_CLAIMS
    needs_review: bool = True
    evidence_mode: str = "ranked"
    max_evidence: int | None = None
    blocked_source_markers: tuple[str, ...] = ()

    def __post_init__(self):
        check.text(self.identity)
        check.text(self.instructions, maximum=4096)
        check.require(
            type(self.max_claims) is int and 1 <= self.max_claims <= check.MAX_CLAIMS,
            "Invalid policy claim limit",
        )
        check.require(type(self.needs_review) is bool, "Invalid policy review state")
        check.require(
            self.evidence_mode in ("ranked", "source-balanced@0.1.0"),
            "Invalid evidence selection mode",
        )
        check.require(
            self.max_evidence is None
            or (
                type(self.max_evidence) is int
                and 1 <= self.max_evidence <= check.MAX_EVIDENCE
            ),
            "Invalid evidence input limit",
        )
        check.require(
            type(self.blocked_source_markers) in (tuple, list)
            and len(self.blocked_source_markers) <= 32,
            "Invalid source marker policy",
        )

        for marker in self.blocked_source_markers:
            check.text(marker, maximum=128)

        object.__setattr__(
            self, "blocked_source_markers", tuple(self.blocked_source_markers)
        )

    def blocks_source(self, text):
        def normalized(value):
            return " ".join(unicodedata.normalize("NFKC", value).casefold().split())

        source = normalized(text)
        return any(
            normalized(marker) in source for marker in self.blocked_source_markers
        )
