from ..domain.errors import AuthorizationRejected, CitationVerificationError
from ..retrieval.chunking import chunk_document
from ..retrieval.models import scope_hash
from .base import CitationCandidate, CoreSourceResolver, VerifiedCitation


class CoreCitationVerifier:
    """Verify against an authorized, current Core source resolver, never the index.

    Removed/changed source => stale candidate; denied access => hard authorization
    error; temporary resolver outage => CorpusUnavailable supplied by the adapter.
    """

    def __init__(self, resolver: CoreSourceResolver):
        self.resolver = resolver

    def verify(self, context, candidate: CitationCandidate) -> VerifiedCitation | None:
        scope_hash(context)
        document, chunk = candidate.document, candidate.chunk
        if document.workspace_id != context.workspace_id:
            raise AuthorizationRejected('Candidate belongs to another workspace')
        current = self.resolver.resolve(context, document.metadata_id)
        if current is None:
            return None
        if current.workspace_id != context.workspace_id or current.metadata_id != document.metadata_id:
            raise CitationVerificationError('Core resolver returned a foreign source')
        if (current.source_version, current.source_checksum) != (document.source_version, document.source_checksum):
            return None
        chunks = chunk_document(current)
        if chunk.ordinal < 0 or chunk.ordinal >= len(chunks) or chunks[chunk.ordinal] != chunk:
            raise CitationVerificationError('Candidate is not an exact chunk of the authoritative source')
        return VerifiedCitation(current.workspace_id, current.metadata_id, current.source_version,
                                current.source_checksum, chunk.chunk_id, chunk.original_text)
