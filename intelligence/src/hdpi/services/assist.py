"""Grounded assistance from caller-verified knowledge; no retrieval or Core I/O."""

from dataclasses import dataclass
from time import perf_counter

from ..generation import validation as check
from ..generation.base import GenerationProvider
from ..generation.errors import (
    GenerationConfigurationError,
    GenerationContractError,
    GenerationTimeout,
    GenerationUnavailable,
)
from ..generation.models import (
    AuthorizedKnowledge,
    AuthorizedKnowledgeBundle,
    EvidenceExcerpt,
    GenerationRequest,
    GenerationRuntimeIdentity,
    GroundedClaim,
)
from ..generation.policy import GenerationPolicy


@dataclass(frozen=True)
class GroundedAnswer:
    text: str
    claims: tuple[GroundedClaim, ...]


@dataclass(frozen=True)
class GroundedAssistance:
    status: str
    answer: GroundedAnswer | None
    citations: tuple[AuthorizedKnowledge, ...]
    needs_review: bool
    reason_code: str
    runtime: GenerationRuntimeIdentity
    error_code: str | None = None


@dataclass(frozen=True)
class AssistanceTrace:
    request_id: str
    profile_id: str
    profile_version: str
    retrieval_engine: str
    retrieval_engine_version: str
    retrieval_config_version: str
    corpus_version: str
    index_version: str
    runtime: GenerationRuntimeIdentity
    knowledge_bundle_digest: str
    evidence_count: int
    claim_count: int
    citation_count: int
    status: str
    reason_code: str
    needs_review: bool
    generation_duration_ms: float
    total_duration_ms: float
    error_type: str | None


@dataclass(frozen=True)
class AssistanceRun:
    response: GroundedAssistance
    trace: AssistanceTrace


class GroundedAssistanceService:
    def __init__(self, *, provider: GenerationProvider, trace_sink=None):
        self.provider = provider
        self.trace_sink = trace_sink
        self.runtime = self._provider_identity()

    def _provider_identity(self):
        try:
            runtime = self.provider.runtime_identity
            valid = type(runtime) is GenerationRuntimeIdentity and (
                self.provider.name,
                self.provider.version,
                self.provider.config_version,
            ) == (
                runtime.provider,
                runtime.provider_version,
                runtime.config_version,
            )
        except Exception:
            raise GenerationConfigurationError('Invalid provider runtime identity') from None

        if not valid:
            raise GenerationConfigurationError('Provider runtime identity mismatch')

        return runtime

    def assist(
        self,
        question: str,
        *,
        bundle: AuthorizedKnowledgeBundle,
        policy: GenerationPolicy,
    ) -> AssistanceRun:
        check.text(question, maximum=check.MAX_QUESTION_CHARS)
        check.require(type(bundle) is AuthorizedKnowledgeBundle, 'Invalid authorized knowledge bundle')
        check.require(type(policy) is GenerationPolicy, 'Invalid generation policy')

        started = perf_counter()
        generation_ms = 0.0
        response = None
        error_type = None

        try:
            if self._provider_identity() != self.runtime:
                raise GenerationConfigurationError('Provider runtime changed during service lifetime')

            if policy.identity != self.runtime.app_prompt_version:
                raise GenerationConfigurationError('Application policy identity mismatch')

            if not bundle.citations:
                response = self._refusal('INSUFFICIENT_EVIDENCE')
            else:
                request = GenerationRequest(
                    question=question,
                    policy_identity=policy.identity,
                    policy_instructions=policy.instructions,
                    output_schema_version=self.runtime.output_schema_version,
                    evidence=tuple(
                        EvidenceExcerpt(citation_id=item.local_citation_id, excerpt=item.excerpt)
                        for item in bundle.citations[:policy.max_claims]
                    ),
                )

                generation_started = perf_counter()

                try:
                    draft = self.provider.generate(request)
                except GenerationTimeout:
                    error_type = 'GenerationTimeout'
                    response = self._degraded('GENERATION_TIMEOUT')
                except GenerationUnavailable:
                    error_type = 'GenerationUnavailable'
                    response = self._degraded('GENERATION_UNAVAILABLE')
                except GenerationConfigurationError:
                    raise GenerationConfigurationError('Provider configuration failed') from None
                except Exception:
                    raise GenerationContractError('Provider execution failed') from None
                finally:
                    generation_ms = round((perf_counter() - generation_started) * 1000, 3)

                if response is None:
                    # The response uses only claims accepted by this gate. No raw
                    # provider prose can bypass citation or grounding validation.
                    check.validate_grounded_draft(draft, request, max_claims=policy.max_claims)

                    if not draft.claims:
                        response = self._refusal('INSUFFICIENT_EVIDENCE')
                    else:
                        response = self._success(draft, bundle, policy)
        except Exception as error:
            error_type = type(error).__name__
            raise
        finally:
            trace = AssistanceTrace(
                request_id=bundle.request_id,
                profile_id=bundle.profile_id,
                profile_version=bundle.profile_version,
                retrieval_engine=bundle.retrieval.engine,
                retrieval_engine_version=bundle.retrieval.engine_version,
                retrieval_config_version=bundle.retrieval.config_version,
                corpus_version=bundle.retrieval.corpus_version,
                index_version=bundle.retrieval.index_version,
                runtime=self.runtime,
                knowledge_bundle_digest=bundle.digest,
                evidence_count=len(bundle.citations),
                claim_count=len(response.answer.claims) if response and response.answer else 0,
                citation_count=len(response.citations) if response else 0,
                status=response.status if response else 'ERROR',
                reason_code=response.reason_code if response else 'GENERATION_CONTRACT_FAILED',
                needs_review=response.needs_review if response else True,
                generation_duration_ms=generation_ms,
                total_duration_ms=round((perf_counter() - started) * 1000, 3),
                error_type=error_type,
            )

            if self.trace_sink is not None:
                try:
                    self.trace_sink(trace)
                except Exception:
                    # Diagnostic delivery is best effort. Do not log sink error
                    # content or replace the response/original safety failure.
                    pass

        return AssistanceRun(response=response, trace=trace)

    def _refusal(self, reason):
        return GroundedAssistance(
            status='INSUFFICIENT_EVIDENCE',
            answer=None,
            citations=(),
            needs_review=True,
            reason_code=reason,
            runtime=self.runtime,
        )

    def _degraded(self, reason):
        return GroundedAssistance(
            status='DEGRADED',
            answer=None,
            citations=(),
            needs_review=True,
            reason_code=reason,
            runtime=self.runtime,
            error_code=reason,
        )

    def _success(self, draft, bundle, policy):
        used_aliases = {
            reference.citation_id
            for claim in draft.claims
            for reference in claim.citations
        }
        citations = tuple(
            item for item in bundle.citations if item.local_citation_id in used_aliases
        )
        rendered = '\n'.join(
            f'{claim.text} [{", ".join(ref.citation_id for ref in claim.citations)}]'
            for claim in draft.claims
        )
        check.text(rendered, maximum=check.MAX_ANSWER_CHARS)

        return GroundedAssistance(
            status='SUCCESS',
            answer=GroundedAnswer(text=rendered, claims=draft.claims),
            citations=citations,
            needs_review=policy.needs_review or bundle.needs_review,
            reason_code='GROUNDED',
            runtime=self.runtime,
        )
