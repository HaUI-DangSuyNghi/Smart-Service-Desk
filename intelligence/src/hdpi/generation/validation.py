"""Bounded, strict primitives shared by the internal generation contracts."""

import re
from uuid import UUID

from .errors import GenerationContractError


MAX_EVIDENCE = 10
MAX_EXCERPT_CHARS = 4096
MAX_BUNDLE_CHARS = 16000
MAX_QUESTION_CHARS = 8192
MAX_CLAIMS = 10
MAX_ANSWER_CHARS = 16000


def require(condition, message):
    if not condition:
        raise GenerationContractError(message)


def text(value, *, maximum=256):
    require(
        type(value) is str and bool(value.strip()) and len(value) <= maximum,
        'Expected bounded, nonempty text',
    )


def checksum(value):
    require(
        type(value) is str and re.fullmatch(r'[a-f0-9]{64}', value) is not None,
        'Expected a SHA-256 digest',
    )


def metadata_id(value):
    try:
        valid = type(value) is str and str(UUID(value)) == value
    except ValueError:
        valid = False

    require(valid, 'Expected a canonical metadata UUID')


def citation_id(value):
    require(
        type(value) is str and re.fullmatch(r'C[1-9][0-9]*', value) is not None,
        'Expected a local citation alias',
    )


def bounded_tuple(value, item_type, *, maximum, allow_empty=True):
    require(type(value) is tuple, 'Expected an immutable tuple')
    require(len(value) <= maximum, 'Collection exceeds its resource budget')
    require(allow_empty or bool(value), 'Evidence or claims are required')
    require(all(type(item) is item_type for item in value), 'Unexpected collection item')


def validate_grounded_draft(draft, request, *, max_claims):
    """Foundation gate: only exact, fully cited evidence quotations may pass.

    Citation membership alone cannot prove entailment. Future paraphrase support
    requires a separately evaluated grounding policy, not a relaxed alias check.
    """
    from .errors import GroundingViolation
    from .models import GenerationDraft

    require(type(draft) is GenerationDraft, 'Provider returned an invalid draft')
    require(len(draft.claims) <= max_claims, 'Provider exceeded the policy claim limit')

    evidence = {item.citation_id: item.excerpt for item in request.evidence}

    for claim in draft.claims:
        for reference in claim.citations:
            excerpt = evidence.get(reference.citation_id)

            if excerpt is None:
                raise GroundingViolation('Provider referenced unknown evidence')

            if claim.text != excerpt:
                raise GroundingViolation('Claim is not an exact quotation of cited evidence')
