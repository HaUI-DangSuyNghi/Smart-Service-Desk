from typing import Protocol, Sequence

from ..domain.models import TrustedContext
from ..profiles.models import RuntimeProfile
from .models import AuthorizedDocument, Chunk, IndexManifest, PartitionKey, RankedHit


class SearchIndex(Protocol):
    manifest: IndexManifest
    documents: tuple[AuthorizedDocument, ...]
    chunks: tuple[Chunk, ...]


class SearchEngine(Protocol):
    name: str
    version: str
    config_version: str

    def search(self, query: str, index: SearchIndex, top_k: int) -> Sequence[RankedHit]: ...


class AuthorizedCorpusSource(Protocol):
    """Adapter reauthorizes current membership AND file scope on every call.

    Return an exact key only after authorization; rotate scope when access changes.
    Never construct scope from wire fields. Production adapter is a BE gate.
    """

    def resolve_partition(self, context: TrustedContext, profile: RuntimeProfile) -> PartitionKey: ...

    def load_documents(self, context: TrustedContext, profile: RuntimeProfile) -> Sequence[AuthorizedDocument]: ...
