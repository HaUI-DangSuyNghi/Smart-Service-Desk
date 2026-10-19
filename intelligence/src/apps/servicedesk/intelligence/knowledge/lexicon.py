"""Controlled domain terms are data; they never change runtime normalization."""
from hdpi.evaluation.dataset import require
from hdpi.normalization.v1 import NormalizerV1

from .contracts import review_consistency, validate_schema


def validate_lexicon(value, root):
    validate_schema(value, 'lexicon', root)
    ids, owners = set(), {}
    normalize = NormalizerV1().normalize
    for concept in value['concepts']:
        identifier = concept['conceptId']
        require(identifier not in ids, 'Duplicate concept identity')
        ids.add(identifier)
        review_consistency(concept, concept['reviewStatus'])
        # Runtime normalization merges wi-fi/wi fi/wifi deliberately. Check raw
        # NFC/casefold spelling uniqueness here, not runtime equivalence.
        own = set()
        for term in [concept['preferredTerm'], *concept['variants']]:
            spelling = term.strip().casefold()
            require(bool(spelling) and spelling not in own, 'Duplicate alias in concept')
            own.add(spelling)
            normalized = normalize(term).value
            require(normalized not in owners or owners[normalized] == identifier, 'Alias has conflicting concept ownership')
            owners[normalized] = identifier
    require(next(c for c in value['concepts'] if c['conceptId'] == 'broad-network')['ambiguity'] == 'high',
            'Broad network vocabulary must not expand automatically')
    return {concept['conceptId']: concept for concept in value['concepts']}


def phrases(metadata, concepts, *, allow_draft=False):
    result = []
    for identifier in metadata['conceptIds']:
        require(identifier in concepts, 'Unknown lexicon concept')
        concept = concepts[identifier]
        require(allow_draft or concept['reviewStatus'] == 'REVIEWED', 'Unreviewed lexicon cannot be published')
        require(concept['ambiguity'] != 'high', 'Ambiguous broad term cannot expand into projection')
        result.extend([concept['preferredTerm'], *concept['variants']])
    return tuple(dict.fromkeys(result))
