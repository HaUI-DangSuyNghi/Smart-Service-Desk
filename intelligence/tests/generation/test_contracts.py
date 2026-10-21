from dataclasses import FrozenInstanceError, asdict, fields, replace
from inspect import signature
from typing import get_type_hints

import pytest

from hdpi.domain.models import TrustedContext
from hdpi.generation.base import GenerationProvider
from hdpi.generation.errors import GenerationContractError
from hdpi.generation.models import (
    AuthorizedKnowledgeBundle,
    CitationRef,
    EvidenceExcerpt,
    GenerationDraft,
    GenerationRequest,
    GenerationRuntimeIdentity,
    GroundedClaim,
)


def test_bundle_is_immutable_canonical_and_digest_bound(bundle, verified_citations):
    reversed_bundle = AuthorizedKnowledgeBundle.from_verified_citations(
        **{key: getattr(bundle, key) for key in (
            'request_id', 'profile_id', 'profile_version', 'retrieval',
        )},
        citations=tuple(reversed(verified_citations)),
    )

    assert reversed_bundle == bundle
    assert reversed_bundle.digest == bundle.digest
    assert replace(bundle, citations=tuple(reversed(bundle.citations))) == bundle
    assert replace(bundle, request_id='different').digest != bundle.digest
    assert replace(bundle, needs_review=True).digest != bundle.digest

    with pytest.raises(FrozenInstanceError):
        bundle.citations[0].excerpt = 'changed'


@pytest.mark.parametrize('field,value', [
    ('source_checksum', 'invalid'),
    ('chunk_id', 'invalid'),
    ('source_version', ''),
    ('source_version', float('nan')),
    ('workspace_id', ''),
    ('metadata_id', 'local-authoring-id'),
    ('excerpt', ' '),
    ('excerpt', 'x' * 4097),
    ('local_citation_id', 'C0'),
])
def test_invalid_provenance_rejected(bundle, field, value):
    with pytest.raises(GenerationContractError):
        replace(bundle.citations[0], **{field: value})


@pytest.mark.parametrize('mutation', [
    'duplicate-alias', 'duplicate-evidence', 'mixed-workspace', 'conflicting-source',
    'missing-alias', 'mutable-container', 'oversized-bundle', 'oversized-count',
])
def test_inconsistent_or_unbounded_bundle_rejected(bundle, mutation):
    first, second = bundle.citations
    mutations = {
        'duplicate-alias': (first, replace(second, local_citation_id='C1')),
        'duplicate-evidence': (first, replace(first, local_citation_id='C2')),
        'mixed-workspace': (first, replace(second, workspace_id='foreign')),
        'conflicting-source': (
            first, replace(second, metadata_id=first.metadata_id, source_version='v2'),
        ),
        'missing-alias': (first, replace(second, local_citation_id='C3')),
        'mutable-container': list(bundle.citations),
        'oversized-bundle': tuple(
            replace(first, local_citation_id=f'C{index}', chunk_id=f'{index:064x}', excerpt='x' * 4096)
            for index in range(1, 5)
        ),
        'oversized-count': tuple(
            replace(first, local_citation_id=f'C{index}', chunk_id=f'{index:064x}')
            for index in range(1, 12)
        ),
    }

    with pytest.raises(GenerationContractError):
        replace(bundle, citations=mutations[mutation])


@pytest.mark.parametrize('value', [None, '', ' ', True, 1, float('inf'), float('nan'), {}])
def test_runtime_identity_rejects_invalid_metadata(bundle, value):
    with pytest.raises(GenerationContractError):
        replace(bundle.retrieval, engine_version=value)

    with pytest.raises(GenerationContractError):
        replace(bundle, retrieval=value)


def test_all_runtime_fields_are_explicit_nonempty_strings():
    runtime = GenerationRuntimeIdentity(
        provider='template', provider_version='1', model='none', model_revision='none',
        config_version='config@1', prompt_envelope_version='envelope@1',
        app_prompt_version='policy@1', output_schema_version='claims@1',
    )

    for field in fields(runtime):
        with pytest.raises(GenerationContractError):
            replace(runtime, **{field.name: float('nan')})


def test_provider_request_contains_only_data_and_local_aliases(bundle):
    request = GenerationRequest(
        question='What should I check?',
        policy_identity='synthetic-policy@1',
        policy_instructions='Quote the verified evidence.',
        output_schema_version='claims@1',
        evidence=tuple(
            EvidenceExcerpt(citation_id=item.local_citation_id, excerpt=item.excerpt)
            for item in bundle.citations
        ),
    )

    assert set(asdict(request)) == {
        'question', 'policy_identity', 'policy_instructions', 'output_schema_version', 'evidence',
    }
    assert set(asdict(request)['evidence'][0]) == {'citation_id', 'excerpt'}
    assert list(signature(GenerationProvider.generate).parameters) == ['self', 'request']
    assert get_type_hints(GenerationProvider.generate)['request'] is GenerationRequest

    for private_value in (
        bundle.citations[0].workspace_id, bundle.citations[0].metadata_id,
        bundle.citations[0].source_checksum, bundle.citations[0].chunk_id,
    ):
        assert private_value not in repr(request)

    with pytest.raises(TypeError):
        replace(request, trusted_context=TrustedContext(workspace_id='foreign'))

    with pytest.raises(GenerationContractError):
        replace(request, evidence=())

    with pytest.raises(GenerationContractError):
        replace(request, question='x' * 8193)


def test_claims_require_unique_citations_and_immutable_bounded_output():
    with pytest.raises(GenerationContractError):
        GroundedClaim(text='Unsupported', citations=())

    with pytest.raises(GenerationContractError):
        GroundedClaim(text='Duplicate', citations=(CitationRef('C1'), CitationRef('C1')))

    with pytest.raises(GenerationContractError):
        GenerationDraft(claims=[])

    claim = GroundedClaim(text='x' * 4096, citations=(CitationRef('C1'),))

    with pytest.raises(GenerationContractError):
        GenerationDraft(claims=(claim,) * 4)


def test_factory_rejects_unverified_or_mutable_input(bundle, verified_citations):
    for invalid in (list(verified_citations), bundle.citations, ({'excerpt': 'unverified'},)):
        with pytest.raises(GenerationContractError):
            AuthorizedKnowledgeBundle.from_verified_citations(
                request_id='test', profile_id='synthetic', profile_version='1',
                retrieval=bundle.retrieval, citations=invalid,
            )
