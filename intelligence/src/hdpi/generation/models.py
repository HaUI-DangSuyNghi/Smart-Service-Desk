"""Immutable internal contracts, separate from any public HTTP response.

Bundle validation proves shape and consistency, never authorization or freshness.
Only a trusted caller may construct bundles from currently verified citations.
The provider-facing request deliberately excludes the bundle and its provenance.
"""

from dataclasses import asdict, dataclass
from hashlib import sha256
import json

from ..citations.base import VerifiedCitation
from . import validation as check


@dataclass(frozen=True, kw_only=True)
class RetrievalRuntimeIdentity:
    engine: str
    engine_version: str
    config_version: str
    corpus_version: str
    index_version: str

    def __post_init__(self):
        for value in asdict(self).values():
            check.text(value)


@dataclass(frozen=True, kw_only=True)
class GenerationRuntimeIdentity:
    provider: str
    provider_version: str
    model: str
    model_revision: str
    config_version: str
    prompt_envelope_version: str
    app_prompt_version: str
    output_schema_version: str

    def __post_init__(self):
        for value in asdict(self).values():
            check.text(value)


@dataclass(frozen=True, kw_only=True)
class AuthorizedKnowledge:
    local_citation_id: str
    workspace_id: str
    metadata_id: str
    source_version: str
    source_checksum: str
    chunk_id: str
    excerpt: str

    def __post_init__(self):
        check.citation_id(self.local_citation_id)
        check.text(self.workspace_id)
        check.metadata_id(self.metadata_id)
        check.text(self.source_version)
        check.checksum(self.source_checksum)
        check.checksum(self.chunk_id)
        check.text(self.excerpt, maximum=check.MAX_EXCERPT_CHARS)


@dataclass(frozen=True, kw_only=True)
class AuthorizedKnowledgeBundle:
    request_id: str
    profile_id: str
    profile_version: str
    retrieval: RetrievalRuntimeIdentity
    citations: tuple[AuthorizedKnowledge, ...]
    needs_review: bool = False

    def __post_init__(self):
        for value in (self.request_id, self.profile_id, self.profile_version):
            check.text(value)

        check.require(type(self.retrieval) is RetrievalRuntimeIdentity, 'Invalid retrieval identity')
        check.require(type(self.needs_review) is bool, 'Invalid review state')
        check.bounded_tuple(self.citations, AuthorizedKnowledge, maximum=check.MAX_EVIDENCE)

        ordered = tuple(sorted(self.citations, key=lambda item: int(item.local_citation_id[1:])))
        aliases = tuple(item.local_citation_id for item in ordered)
        expected = tuple(f'C{index + 1}' for index in range(len(ordered)))
        check.require(aliases == expected, 'Citation aliases must be unique and contiguous')

        identities = {(item.metadata_id, item.chunk_id) for item in ordered}
        check.require(len(identities) == len(ordered), 'Duplicate evidence identity')
        check.require(len({item.workspace_id for item in ordered}) <= 1, 'Mixed workspace evidence')
        check.require(
            sum(len(item.excerpt) for item in ordered) <= check.MAX_BUNDLE_CHARS,
            'Evidence exceeds the bundle budget',
        )

        source_states = {}
        for item in ordered:
            state = (item.source_version, item.source_checksum)
            previous = source_states.setdefault(item.metadata_id, state)
            check.require(previous == state, 'Conflicting source versions in evidence')

        object.__setattr__(self, 'citations', ordered)

    @classmethod
    def from_verified_citations(cls, *, citations, **identity):
        """Trusted-call boundary; a VerifiedCitation object alone is not authority.

        Assign aliases after canonical provenance sorting, independent of input
        order. Do not pass authoring documents or unverified retrieval candidates.
        """
        check.bounded_tuple(citations, VerifiedCitation, maximum=check.MAX_EVIDENCE)

        # Validate fields before sorting so malformed values fail consistently.
        evidence = tuple(
            AuthorizedKnowledge(local_citation_id=f'C{index + 1}', **asdict(item))
            for index, item in enumerate(citations)
        )
        ordered = sorted(
            evidence,
            key=lambda item: (
                item.workspace_id,
                item.metadata_id,
                item.source_version,
                item.source_checksum,
                item.chunk_id,
            ),
        )
        canonical = tuple(
            AuthorizedKnowledge(**{**asdict(item), 'local_citation_id': f'C{index + 1}'})
            for index, item in enumerate(ordered)
        )

        return cls(citations=canonical, **identity)

    @property
    def digest(self):
        encoded = json.dumps(
            asdict(self), ensure_ascii=False, sort_keys=True, separators=(',', ':'), allow_nan=False,
        ).encode('utf-8')

        return sha256(encoded).hexdigest()


@dataclass(frozen=True)
class CitationRef:
    citation_id: str

    def __post_init__(self):
        check.citation_id(self.citation_id)


@dataclass(frozen=True, kw_only=True)
class EvidenceExcerpt:
    citation_id: str
    excerpt: str

    def __post_init__(self):
        check.citation_id(self.citation_id)
        check.text(self.excerpt, maximum=check.MAX_EXCERPT_CHARS)


@dataclass(frozen=True, kw_only=True)
class GenerationRequest:
    """Data-only provider view: no context, IDs, credentials, or source resolver."""

    question: str
    policy_identity: str
    policy_instructions: str
    output_schema_version: str
    evidence: tuple[EvidenceExcerpt, ...]

    def __post_init__(self):
        check.text(self.question, maximum=check.MAX_QUESTION_CHARS)
        check.text(self.policy_identity)
        check.text(self.policy_instructions, maximum=4096)
        check.text(self.output_schema_version)
        check.bounded_tuple(
            self.evidence, EvidenceExcerpt, maximum=check.MAX_EVIDENCE, allow_empty=False,
        )
        aliases = tuple(item.citation_id for item in self.evidence)
        check.require(
            aliases == tuple(f'C{index + 1}' for index in range(len(aliases))),
            'Provider evidence must use canonical aliases',
        )
        check.require(
            sum(len(item.excerpt) for item in self.evidence) <= check.MAX_BUNDLE_CHARS,
            'Provider evidence exceeds the bundle budget',
        )


@dataclass(frozen=True, kw_only=True)
class GroundedClaim:
    text: str
    citations: tuple[CitationRef, ...]

    def __post_init__(self):
        check.text(self.text, maximum=check.MAX_EXCERPT_CHARS)
        check.bounded_tuple(
            self.citations, CitationRef, maximum=check.MAX_EVIDENCE, allow_empty=False,
        )
        check.require(len(set(self.citations)) == len(self.citations), 'Duplicate claim citation')


@dataclass(frozen=True, kw_only=True)
class GenerationDraft:
    """An empty claim tuple is a refusal; no unchecked free-form answer field."""

    claims: tuple[GroundedClaim, ...]

    def __post_init__(self):
        check.bounded_tuple(self.claims, GroundedClaim, maximum=check.MAX_CLAIMS)
        check.require(
            sum(len(item.text) for item in self.claims) <= check.MAX_ANSWER_CHARS,
            'Draft exceeds the answer budget',
        )
