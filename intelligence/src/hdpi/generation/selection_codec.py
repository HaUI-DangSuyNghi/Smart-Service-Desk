"""Untrusted selector decoding and exact quotation mapping; never render model prose."""

import json

from .errors import GenerationContractError
from .models import CitationRef, GenerationDraft, GroundedClaim


def _check_nesting(raw):
    depth = 0
    quoted = False
    escaped = False

    for byte in raw:
        if quoted:
            if escaped:
                escaped = False
            elif byte == 92:
                escaped = True
            elif byte == 34:
                quoted = False
        elif byte == 34:
            quoted = True
        elif byte in (91, 123):
            depth += 1

            if depth > 16:
                raise ValueError
        elif byte in (93, 125):
            depth -= 1


def strict_json(raw: bytes, *, maximum: int):
    def pairs(items):
        result = {}

        for key, value in items:
            if key in result:
                raise ValueError

            result[key] = value

        return result

    def reject(_):
        raise ValueError

    try:
        if type(raw) is not bytes or not 0 < len(raw) <= maximum:
            raise ValueError

        _check_nesting(raw)

        return json.loads(
            raw.decode('utf-8'),
            object_pairs_hook=pairs,
            parse_constant=reject,
            parse_float=reject,
        )
    except (ValueError, UnicodeError, RecursionError):
        raise GenerationContractError('Invalid bounded JSON response') from None


def selection_draft(value, request):
    if type(value) is not dict or set(value) != {'decision', 'citation_ids'}:
        raise GenerationContractError('Invalid selector fields')

    decision, aliases = value['decision'], value['citation_ids']
    valid = {item.citation_id for item in request.evidence}

    if (
        type(decision) is not str
        or decision not in ('SELECT', 'REFUSE')
        or type(aliases) is not list
        or len(aliases) > len(valid)
        or any(type(alias) is not str for alias in aliases)
    ):
        raise GenerationContractError('Invalid selector shape')

    if (
        len(set(aliases)) != len(aliases)
        or not set(aliases) <= valid
        or (decision == 'SELECT') != bool(aliases)
    ):
        raise GenerationContractError('Invalid selector aliases or decision')

    return GenerationDraft(
        claims=tuple(
            GroundedClaim(text=item.excerpt, citations=(CitationRef(item.citation_id),))
            for item in request.evidence
            if item.citation_id in aliases
        )
    )


def selection_schema(request):
    return {
        'type': 'object',
        'additionalProperties': False,
        'required': ['decision', 'citation_ids'],
        'properties': {
            'decision': {'enum': ['SELECT', 'REFUSE']},
            'citation_ids': {
                'type': 'array',
                'uniqueItems': True,
                'maxItems': len(request.evidence),
                'items': {'enum': [item.citation_id for item in request.evidence]},
            },
        },
    }
