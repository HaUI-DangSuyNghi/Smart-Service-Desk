from dataclasses import dataclass
from typing import Protocol

from ..domain.models import TrustedContext
from ..retrieval.models import AuthorizedDocument, Chunk


@dataclass(frozen=True)
class CitationCandidate:
    document: AuthorizedDocument
    chunk: Chunk


@dataclass(frozen=True)
class VerifiedCitation:
    workspace_id: str
    metadata_id: str
    source_version: str
    source_checksum: str
    chunk_id: str
    excerpt: str


class CoreSourceResolver(Protocol):
    """Authorized Core APIs only. None=removed; denial raises AuthorizationRejected;
    operational outage raises CorpusUnavailable. Bytes must match Core checksum.
    """
    def resolve(self, context: TrustedContext, metadata_id: str) -> AuthorizedDocument | None: ...


class CitationVerifier(Protocol):
    def verify(self, context: TrustedContext, candidate: CitationCandidate) -> VerifiedCitation | None: ...
