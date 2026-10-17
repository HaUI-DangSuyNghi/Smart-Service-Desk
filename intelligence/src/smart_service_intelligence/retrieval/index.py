from dataclasses import asdict, dataclass

from ..domain.errors import AuthorizationRejected, EngineConfigurationError
from .chunking import chunk_document
from .models import (AuthorizedDocument, IndexManifest, MAX_CORPUS_BYTES, MAX_DOCUMENTS,
                     NUMERICAL_RUNTIME, PartitionKey, digest, scope_hash)
from .tfidf.engine import LexicalIndex, fit_chunks


@dataclass(frozen=True)
class TfidfIndex:
    manifest: IndexManifest
    documents: tuple[AuthorizedDocument, ...]
    lexical: LexicalIndex

    @property
    def chunks(self):
        return self.lexical.chunks


def build_index(documents, partition: PartitionKey) -> TfidfIndex:
    documents = tuple(documents)
    if any(not isinstance(d, AuthorizedDocument) for d in documents):
        raise EngineConfigurationError('Runtime index requires authorized Core documents')
    documents = tuple(sorted(documents, key=lambda d: d.metadata_id))
    if len(documents) > MAX_DOCUMENTS or sum(len(d.content) for d in documents) > MAX_CORPUS_BYTES:
        raise EngineConfigurationError('Corpus exceeds internal resource limits')
    if len({d.metadata_id for d in documents}) != len(documents):
        raise EngineConfigurationError('Duplicate runtime source identity')
    for doc in documents:
        if not isinstance(doc, AuthorizedDocument):
            raise EngineConfigurationError('Runtime index requires authorized Core documents')
        if doc.workspace_id != partition.workspace_id:
            raise AuthorizationRejected('Source belongs to another workspace')
    chunks = tuple(c for doc in documents for c in chunk_document(doc))
    lexical = fit_chunks(chunks)
    # Bind every persisted property, as well as source bytes through their checksum.
    source_digest = digest([{k: v for k, v in asdict(d).items() if k != 'content'} for d in documents])
    index_version = digest([asdict(partition), source_digest, NUMERICAL_RUNTIME,
                            [asdict(c) for c in chunks], 'ai03@0.1.0'])
    manifest = IndexManifest(partition, index_version, source_digest, len(documents), len(chunks))
    return TfidfIndex(manifest, documents, lexical)


def build_authorized_index(*, source, context, profile):
    """Adapter integration helper; authorize and choose scope before loading bytes."""
    scope_hash(context)
    key = source.resolve_partition(context, profile)
    key.authorize(context)
    if (key.profile_id, key.profile_version, key.normalization) != (
            profile.profile_id, profile.identity.split('@')[-1], profile.normalization_version):
        raise EngineConfigurationError('Authorized corpus does not match exact profile')
    return build_index(source.load_documents(context, profile), key)
