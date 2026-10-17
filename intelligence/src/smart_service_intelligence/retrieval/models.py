from dataclasses import dataclass, asdict
from hashlib import sha256
import json
import math
import re
from uuid import UUID

from ..domain.errors import AuthorizationRejected, CitationVerificationError, EngineConfigurationError
from ..domain.models import TrustedContext

CHUNK_POLICY = 'markdown-paragraph@0.1.0'
NORMALIZATION = 'text-normalization@0.1.0'
ENGINE = 'tfidf'
ENGINE_VERSION = '0.1.0'
ENGINE_CONFIG = 'tfidf-config@0.1.0'
NUMERICAL_RUNTIME = 'scikit-learn@1.6.1;numpy@2.2.6;scipy@1.15.3'
MAX_SOURCE_BYTES = 2 * 1024 * 1024
MAX_CORPUS_BYTES = 16 * 1024 * 1024
MAX_DOCUMENTS = 1000
MAX_CHUNKS = 4096
MAX_QUERY_CHARS = 8192


def digest(value) -> str:
    return sha256(json.dumps(value, ensure_ascii=False, sort_keys=True,
                             separators=(',', ':'), allow_nan=False).encode('utf-8')).hexdigest()


def scope_hash(context: TrustedContext) -> str:
    if not isinstance(context, TrustedContext) or not isinstance(context.auth_scope_fingerprint, str) or not context.auth_scope_fingerprint.strip():
        raise AuthorizationRejected('Adapter-owned access scope required')
    return sha256(context.auth_scope_fingerprint.encode('utf-8')).hexdigest()


@dataclass(frozen=True, kw_only=True)
class AuthorizedDocument:
    """Construct only from authorized Core metadata and exact downloaded bytes.

    Validation proves integrity/shape, never Core issuance or permission.
    """
    workspace_id: str
    metadata_id: str
    source_version: str
    source_checksum: str
    content: bytes
    title: str = ''
    media_type: str = 'text/markdown'

    def __post_init__(self):
        try:
            if str(UUID(self.metadata_id)) != self.metadata_id.lower():
                raise ValueError()
            if not self.workspace_id.strip() or not self.source_version.strip():
                raise ValueError()
            if not re.fullmatch('[a-f0-9]{64}', self.source_checksum):
                raise ValueError()
            if not isinstance(self.content, bytes) or len(self.content) > MAX_SOURCE_BYTES:
                raise ValueError()
            if sha256(self.content).hexdigest() != self.source_checksum:
                raise ValueError()
            if self.media_type not in ('text/plain', 'text/markdown'):
                raise ValueError()
            self.content.decode('utf-8')
        except (ValueError, TypeError, AttributeError) as error:
            raise CitationVerificationError('Invalid source provenance, bytes or format') from error

    @property
    def text(self):
        return self.content.decode('utf-8')


@dataclass(frozen=True)
class Chunk:
    source_id: str
    chunk_id: str
    ordinal: int
    original_text: str
    normalized_text: str
    start: int
    end: int


@dataclass(frozen=True)
class RankedHit:
    chunk: Chunk
    score: float

    def __post_init__(self):
        if isinstance(self.score, bool) or not math.isfinite(self.score) or self.score <= 0:
            raise CitationVerificationError('Ranking score must be finite and positive')


@dataclass(frozen=True, kw_only=True)
class PartitionKey:
    workspace_id: str
    profile_id: str
    profile_version: str
    access_scope_hash: str
    corpus_version: str
    chunk_policy: str = CHUNK_POLICY
    normalization: str = NORMALIZATION
    engine: str = ENGINE
    engine_version: str = ENGINE_VERSION
    engine_config: str = ENGINE_CONFIG

    def __post_init__(self):
        if any(not isinstance(v, str) or not v.strip() for v in asdict(self).values()):
            raise EngineConfigurationError('Partition versions must be explicit')
        if not re.fullmatch('[a-f0-9]{64}', self.access_scope_hash):
            raise EngineConfigurationError('Invalid scope digest')
        if (self.chunk_policy, self.normalization, self.engine, self.engine_version, self.engine_config) != (
                CHUNK_POLICY, NORMALIZATION, ENGINE, ENGINE_VERSION, ENGINE_CONFIG):
            raise EngineConfigurationError('Unsupported retrieval version/configuration')

    def authorize(self, context: TrustedContext):
        if context.workspace_id != self.workspace_id or scope_hash(context) != self.access_scope_hash:
            raise AuthorizationRejected('Foreign workspace or access scope partition')


@dataclass(frozen=True)
class IndexManifest:
    partition: PartitionKey
    index_version: str
    source_state_digest: str
    document_count: int
    chunk_count: int
    numerical_runtime: str = NUMERICAL_RUNTIME
    build_version: str = 'ai03@0.1.0'
