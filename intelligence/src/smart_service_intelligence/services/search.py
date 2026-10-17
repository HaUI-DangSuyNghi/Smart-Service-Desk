from dataclasses import dataclass
import math
from time import perf_counter

from ..citations.base import CitationCandidate, CitationVerifier, VerifiedCitation
from ..contracts.search import SearchContractValidator
from ..domain.errors import (AuthorizationRejected, CitationVerificationError, ContractValidationError,
                             CorpusUnavailable, EngineConfigurationError, EngineUnavailable)
from ..domain.models import TrustedContext
from ..normalization.v1 import NormalizerV1
from ..profiles.registry import ProfileRegistry
from ..retrieval.base import AuthorizedCorpusSource, SearchEngine
from ..retrieval.models import MAX_QUERY_CHARS, scope_hash
from ..retrieval.registry import IndexRegistry
from ..retrieval.tfidf.engine import TfidfSearchEngine


@dataclass(frozen=True)
class SearchTrace:
    request_id: str
    profile_version: str | None
    engine: str
    engine_version: str
    engine_config_version: str
    corpus_version: str | None
    index_version: str | None
    chunk_policy_version: str | None
    normalization_version: str | None
    candidate_count: int
    verified_hit_count: int
    stale_hit_count: int
    status: str
    duration_ms: float
    error_type: str | None


@dataclass(frozen=True)
class SearchRun:
    response: dict
    trace: SearchTrace


class SearchService:
    def __init__(self, *, corpus_source: AuthorizedCorpusSource, indexes: IndexRegistry,
                 citation_verifier: CitationVerifier, engine: SearchEngine | None = None,
                 profiles=None, trace_sink=None, allow_operational_degraded=False):
        self.source, self.indexes, self.verifier = corpus_source, indexes, citation_verifier
        self.engine = engine or TfidfSearchEngine()
        self.profiles = profiles or ProfileRegistry()
        self.contract = SearchContractValidator()
        self.trace_sink = trace_sink
        self.allow_operational_degraded = allow_operational_degraded

    def search(self, payload, *, trusted_context: TrustedContext, profile_version: str) -> SearchRun:
        started = perf_counter()
        profile, manifest, response, error_type = None, None, None, None
        request_id, candidate_count, stale_count = '', 0, 0
        completed = False
        try:
            if not isinstance(trusted_context, TrustedContext):
                raise AuthorizationRejected('Explicit adapter-owned context required')
            self.contract.request(payload)
            request_id = payload['requestId']
            if payload['context']['workspaceId'] != trusted_context.workspace_id:
                raise AuthorizationRejected('Request workspace differs from trusted context')
            scope_hash(trusted_context)
            if len(payload['query']) > MAX_QUERY_CHARS:
                raise ContractValidationError('Query exceeds internal resource limit')
            profile = self.profiles.get(payload['profile'], profile_version)
            envelope = {k: payload[k] for k in ('contractVersion', 'requestId', 'profile')}
            envelope['workspaceId'] = trusted_context.workspace_id
            try:
                # Adapter MUST reauthorize current scope before any registry/scoring access.
                key = self.source.resolve_partition(trusted_context, profile)
                key.authorize(trusted_context)
                if (key.profile_id, key.profile_version) != (profile.profile_id, profile_version):
                    raise EngineConfigurationError('Partition profile mismatch')
                if key.normalization != profile.normalization_version:
                    raise EngineConfigurationError('Profile normalization mismatch')
                if (self.engine.name, self.engine.version, self.engine.config_version) != (
                        key.engine, key.engine_version, key.engine_config):
                    raise EngineConfigurationError('Engine does not match exact index configuration')
                index = self.indexes.get(key, trusted_context)
                manifest = index.manifest
                query = NormalizerV1().normalize(payload['query']).value
                hits = tuple(self.engine.search(query, index, payload['topK']))
                candidate_count = len(hits)
                if len(hits) > payload['topK']:
                    raise CitationVerificationError('Engine exceeded candidate cap')
                chunks = {c.chunk_id: c for c in index.chunks}
                documents = {d.metadata_id: d for d in index.documents}
                results, seen = [], set()
                for hit in hits:
                    chunk = hit.chunk
                    if (chunks.get(chunk.chunk_id) != chunk or chunk.chunk_id in seen or
                            isinstance(hit.score, bool) or not math.isfinite(hit.score) or hit.score <= 0):
                        raise CitationVerificationError('Engine returned invalid or foreign candidate')
                    seen.add(chunk.chunk_id)
                    document = documents.get(chunk.source_id)
                    if document is None:
                        raise CitationVerificationError('Candidate lacks runtime provenance')
                    citation = self.verifier.verify(trusted_context, CitationCandidate(document, chunk))
                    if citation is None:
                        stale_count += 1
                        continue
                    expected = VerifiedCitation(document.workspace_id, document.metadata_id,
                                                document.source_version, document.source_checksum,
                                                chunk.chunk_id, chunk.original_text)
                    if citation != expected:
                        raise CitationVerificationError('Verifier changed citation identity or excerpt')
                    results.append({'workspaceId': citation.workspace_id, 'metadataId': citation.metadata_id,
                                    'sourceVersion': citation.source_version, 'sourceChecksum': citation.source_checksum,
                                    'chunkId': citation.chunk_id, 'score': hit.score, 'excerpt': citation.excerpt})
                response = {**envelope, 'status': 'SUCCESS', 'results': results,
                            'runtime': {'engine': key.engine, 'engineVersion': key.engine_version,
                                        'profileVersion': profile.identity, 'corpusVersion': key.corpus_version,
                                        'indexVersion': manifest.index_version},
                            'needsReview': not results or stale_count > 0, 'error': None}
            except (CorpusUnavailable, EngineUnavailable) as error:
                if not self.allow_operational_degraded:
                    raise
                error_type = type(error).__name__
                response = {**envelope, 'status': 'DEGRADED', 'results': [], 'runtime': None,
                            'needsReview': True, 'error': {'code': 'INTELLIGENCE_UNAVAILABLE'}}
            self.contract.response(response, payload)
            completed = True
        except Exception as error:
            error_type = type(error).__name__
            raise
        finally:
            key = manifest.partition if manifest else None
            trace = SearchTrace(request_id, profile.identity if profile else None,
                                self.engine.name, self.engine.version, self.engine.config_version,
                                key.corpus_version if key else None, manifest.index_version if manifest else None,
                                key.chunk_policy if key else None, key.normalization if key else None,
                                candidate_count, len(response['results']) if completed else 0, stale_count,
                                response['status'] if completed else 'ERROR',
                                round((perf_counter() - started)*1000, 3), error_type)
            if self.trace_sink is not None:
                self.trace_sink(trace)
        return SearchRun(response, trace)
