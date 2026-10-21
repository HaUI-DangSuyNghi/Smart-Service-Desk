"""Provider boundary: generation receives data, never retrieval or authority."""

from typing import Protocol

from .models import GenerationDraft, GenerationRequest, GenerationRuntimeIdentity


class GenerationProvider(Protocol):
    name: str
    version: str
    config_version: str

    @property
    def runtime_identity(self) -> GenerationRuntimeIdentity: ...

    def generate(self, request: GenerationRequest) -> GenerationDraft: ...
