"""Reference extraction provider. This is not a production language model."""

from dataclasses import dataclass
from typing import ClassVar

from . import validation as check
from .errors import GenerationConfigurationError
from .models import (
    CitationRef,
    GenerationDraft,
    GenerationRequest,
    GenerationRuntimeIdentity,
    GroundedClaim,
)


@dataclass(frozen=True, kw_only=True)
class CitationBoundTemplateProvider:
    app_prompt_version: str

    name: ClassVar[str] = 'citation-bound-template'
    version: ClassVar[str] = '0.1.0'
    config_version: ClassVar[str] = 'exact-excerpts@0.1.0'

    def __post_init__(self):
        check.text(self.app_prompt_version)

    @property
    def runtime_identity(self):
        return GenerationRuntimeIdentity(
            provider=self.name,
            provider_version=self.version,
            model='none',
            model_revision='none',
            config_version=self.config_version,
            prompt_envelope_version='data-only@0.1.0',
            app_prompt_version=self.app_prompt_version,
            output_schema_version='grounded-claims@0.1.0',
        )

    def generate(self, request: GenerationRequest) -> GenerationDraft:
        check.require(type(request) is GenerationRequest, 'Invalid provider request')

        if (
            request.policy_identity != self.app_prompt_version
            or request.output_schema_version != self.runtime_identity.output_schema_version
        ):
            raise GenerationConfigurationError('Provider request identity mismatch')

        return GenerationDraft(
            claims=tuple(
                GroundedClaim(
                    text=item.excerpt,
                    citations=(CitationRef(item.citation_id),),
                )
                for item in request.evidence
            ),
        )
