from typing import Protocol, Sequence


class EmbeddingProvider(Protocol):
    """Local model boundary; identity is required before any vectors are accepted."""
    name: str
    model_version: str
    artifact_digest: str
    dimensions: int

    def encode_documents(self, texts: Sequence[str]) -> Sequence[Sequence[float]]: ...

    def encode_query(self, text: str) -> Sequence[float]: ...
