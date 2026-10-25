"""Request-local retrieval over a snapshot supplied by an authenticated adapter.

The adapter owns Core authorization, rights and final freshness. This service
does not turn a serialized snapshot into proof of permission. No snapshot is
cached or reused across callers, and the generation provider sees aliases only.
"""

from dataclasses import asdict, replace
from itertools import zip_longest
from jsonschema import FormatChecker
from ..citations.base import CitationCandidate, VerifiedCitation
from ..citations.verifier import CoreCitationVerifier
from ..contracts.loader import load_validator
from ..contracts.validator import ContractValidator
from ..domain.errors import AuthorizationRejected
from ..domain.models import TrustedContext
from ..generation.errors import GenerationConfigurationError, GenerationContractError
from ..generation.models import AuthorizedKnowledgeBundle, RetrievalRuntimeIdentity
from ..profiles.registry import ProfileRegistry
from ..retrieval.chunking import chunk_document
from ..retrieval.index import build_index
from ..retrieval.models import AuthorizedDocument, PartitionKey, digest, scope_hash
from ..retrieval.registry import IndexRegistry
from .assist import GroundedAssistanceService
from .search import SearchService


class SnapshotSource:
    def __init__(self, context, partition, documents):
        self.context = context
        self.partition = partition
        self.documents = {item.metadata_id: item for item in documents}

    def resolve_partition(self, context, profile):
        self.partition.authorize(context)

        if context != self.context:
            raise AuthorizationRejected("Snapshot context changed")

        return self.partition

    def resolve(self, context, metadata_id):
        self.partition.authorize(context)

        if context != self.context or metadata_id not in self.documents:
            raise AuthorizationRejected("Source outside the request snapshot")

        return self.documents[metadata_id]


class AssistanceRuntime:
    def __init__(self, *, provider, policy, profile_id, profile_version, profiles=None):
        self.generation = GroundedAssistanceService(provider=provider)
        self.policy = policy
        self.profile_id = profile_id
        self.profile_version = profile_version
        self.profiles = profiles or ProfileRegistry()
        self.profiles.get(profile_id, profile_version)
        self.contract = load_validator("assistance-request.schema.json").evolve(
            format_checker=FormatChecker(),
        )
        self.response_contract = load_validator("assistance-result.schema.json")

        if self.generation.runtime.app_prompt_version != policy.identity:
            raise GenerationConfigurationError("Policy identity mismatch")

        if policy.max_claims > 3:
            raise GenerationConfigurationError(
                "Private assistance permits at most three claims"
            )

    def assist(self, payload):
        ContractValidator._validate(self.contract, payload)

        if (
            payload["profileId"] != self.profile_id
            or payload["profileVersion"] != self.profile_version
            or payload["policyIdentity"] != self.policy.identity
        ):
            raise GenerationConfigurationError("Unconfigured assistance identity")

        documents = tuple(
            AuthorizedDocument(
                workspace_id=payload["workspaceId"],
                metadata_id=item["metadataId"],
                source_version=item["version"],
                source_checksum=item["checksum"],
                content=item["content"].encode("utf-8"),
                media_type=item["mediaType"],
            )
            for item in payload["documents"]
        )

        if len({item.metadata_id for item in documents}) != len(documents):
            raise GenerationContractError("Duplicate snapshot source")

        if sum(len(item.content) for item in documents) > 32000:
            raise GenerationContractError("Snapshot exceeds content budget")

        state = digest(
            sorted(
                (item.metadata_id, item.source_version, item.source_checksum)
                for item in documents
            )
        )
        context = TrustedContext(
            workspace_id=payload["workspaceId"],
            auth_scope_fingerprint=digest([payload["requestId"], state]),
        )
        partition = PartitionKey(
            workspace_id=context.workspace_id,
            profile_id=self.profile_id,
            profile_version=self.profile_version,
            access_scope_hash=scope_hash(context),
            corpus_version="adapter-snapshot@0.1.0:" + state,
        )

        source = SnapshotSource(context, partition, documents)
        if self.policy.evidence_mode == "source-balanced@0.1.0":
            bundle = self._source_balanced_bundle(
                payload, context, partition, source, documents
            )
        else:
            bundle = self._ranked_bundle(payload, context, partition, source, documents)

        if any(self.policy.blocks_source(document.text) for document in documents):
            # Withhold the whole candidate set. Dropping only the flagged source
            # could hide a contradiction or let a partial safe fragment escape.
            bundle = replace(bundle, citations=())

        result = self.generation.assist(
            payload["question"],
            bundle=bundle,
            policy=self.policy,
        ).response

        citations = [
            {
                "citationId": item.local_citation_id,
                "metadataId": item.metadata_id,
                "sourceVersion": item.source_version,
                "sourceChecksum": item.source_checksum,
                "excerpt": item.excerpt,
            }
            for item in result.citations
        ]
        answer = (
            "\n".join(f'{item["excerpt"]} [{item["citationId"]}]' for item in citations)
            if citations
            else None
        )

        # This wire permits one exact quotation per selected alias. Preserve the
        # foundation validator and reject unsupported claim arrangements too.
        if (result.answer.text if result.answer else None) != answer:
            raise GenerationContractError("Unsupported quotation arrangement")

        response = {
            "contractVersion": "0.1.0",
            "requestId": payload["requestId"],
            "status": result.status,
            "answer": answer,
            "citations": citations,
            "needsReview": True,
            "reasonCode": result.reason_code,
            "retrievalRuntime": asdict(bundle.retrieval),
            "generationRuntime": asdict(result.runtime),
        }

        ContractValidator._validate(self.response_contract, response)
        return response

    def _ranked_bundle(self, payload, context, partition, source, documents):
        indexes = IndexRegistry()
        index = build_index(documents, partition)
        indexes.register(index)

        search = SearchService(
            corpus_source=source,
            indexes=indexes,
            citation_verifier=CoreCitationVerifier(source),
            profiles=self.profiles,
        ).search(
            {
                "contractVersion": "0.1.0",
                "requestId": payload["requestId"],
                "profile": self.profile_id,
                "context": {"workspaceId": context.workspace_id},
                "query": payload["question"],
                "topK": self.policy.max_evidence or self.policy.max_claims,
            },
            trusted_context=context,
            profile_version=self.profile_version,
        )

        return AuthorizedKnowledgeBundle.from_verified_citations(
            request_id=payload["requestId"],
            profile_id=self.profile_id,
            profile_version=self.profile_version,
            retrieval=RetrievalRuntimeIdentity(
                engine=search.trace.engine,
                engine_version=search.trace.engine_version,
                config_version=search.trace.engine_config_version,
                corpus_version=search.trace.corpus_version,
                index_version=search.trace.index_version,
            ),
            citations=tuple(
                VerifiedCitation(
                    workspace_id=item["workspaceId"],
                    metadata_id=item["metadataId"],
                    source_version=item["sourceVersion"],
                    source_checksum=item["sourceChecksum"],
                    chunk_id=item["chunkId"],
                    excerpt=item["excerpt"],
                )
                for item in search.response["results"]
            ),
            needs_review=True,
        )

    def _source_balanced_bundle(self, payload, context, partition, source, documents):
        """Offer bounded chunks from every authorized source to the selector.

        No keyword match is required. A model must decide answerability; inclusion
        is not a claim of relevance. The partition scopes the resolver only; this
        path builds no lexical index and reports its own retrieval identity.
        """
        partition.authorize(context)
        verifier = CoreCitationVerifier(source)
        groups = [
            tuple(
                CitationCandidate(document, chunk) for chunk in chunk_document(document)
            )
            for document in sorted(documents, key=lambda item: item.metadata_id)
        ]
        citations = []

        for row in zip_longest(*groups):
            for candidate in row:
                if candidate is None:
                    continue

                citation = verifier.verify(context, candidate)

                if citation is None:
                    raise GenerationContractError(
                        "Snapshot source changed during selection"
                    )

                citations.append(citation)

                if len(citations) == 10:
                    break

            if len(citations) == 10:
                break

        identity = digest(
            [
                self.policy.evidence_mode,
                partition.corpus_version,
                [
                    (item.metadata_id, item.source_checksum, item.chunk_id)
                    for item in citations
                ],
            ]
        )

        return AuthorizedKnowledgeBundle.from_verified_citations(
            request_id=payload["requestId"],
            profile_id=self.profile_id,
            profile_version=self.profile_version,
            retrieval=RetrievalRuntimeIdentity(
                engine="snapshot",
                engine_version="0.1.0",
                config_version=self.policy.evidence_mode,
                corpus_version=partition.corpus_version,
                index_version="snapshot@0.1.0:" + identity,
            ),
            citations=tuple(citations),
            needs_review=True,
        )
