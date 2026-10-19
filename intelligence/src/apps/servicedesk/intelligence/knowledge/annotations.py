"""Validate human-only relevance and release locks; blank cells stay blank."""

from collections import Counter

from hdpi.evaluation.dataset import require


def annotation_agreement(pairs):
    require(bool(pairs), 'Cannot measure agreement without reviewer votes')
    require(
        all(type(vote) is int and vote in (0, 1, 2) for pair in pairs for vote in pair),
        'Invalid annotation agreement grade',
    )
    size = len(pairs)
    raw = sum(a == b for a, b in pairs) / size
    first, second = Counter(a for a, _ in pairs), Counter(b for _, b in pairs)
    observed = sum((a - b) ** 2 / 4 for a, b in pairs) / size
    expected = (
        sum(first[a] * second[b] * (a - b) ** 2 / 4 for a in range(3) for b in range(3)) / size**2
    )
    return {
        'rawAgreement': raw,
        'weightedCohenKappa': None if expected == 0 else 1 - observed / expected,
        'judgmentPairCount': size,
    }


def validate_judgments(qrels, queries, artifacts, *, require_reviewed=False):
    known = set(artifacts)
    by_id = {q['id']: q for q in queries}
    ids = [case['queryId'] for case in qrels['cases']]
    require(
        len(ids) == len(set(ids)) and set(ids) == set(by_id), 'Orphan or duplicate query judgments'
    )
    pending, pairs = 0, []
    holdout_reviewers = None
    for case in qrels['cases']:
        query = by_id[case['queryId']]
        cells = case['judgments']
        identifiers = [cell['artifactId'] for cell in cells]
        require(
            len(identifiers) == len(set(identifiers)) and set(identifiers) == known,
            'Incomplete or unknown artifact judgments',
        )
        primary = 0
        for cell in cells:
            reviews = cell['reviews']
            reviewers = [r['reviewer'].strip().casefold() for r in reviews]
            require(all(reviewers), 'Empty reviewer identity')
            require(len(set(reviewers)) == len(reviewers), 'Duplicate reviewer identity')
            if cell['grade'] is None:
                require(
                    cell['reason'] is None and not reviews and cell['adjudication'] is None,
                    'Blank cell carries invented review',
                )
                pending += 1
                continue
            required = 2 if query['split'] == 'HOLDOUT' else 1
            require(
                bool(cell['reason']) and len(reviews) == required,
                'Final qrel lacks required independent human reviews',
            )
            require(
                query['reviewStatus'] == 'REVIEWED' and query['provenance']['humanReviewed'],
                'Unreviewed query cannot be gold',
            )
            if query['split'] == 'HOLDOUT':
                ordered = tuple(sorted(reviewers))
                holdout_reviewers = holdout_reviewers or ordered
                require(
                    holdout_reviewers == ordered,
                    'Holdout agreement requires a consistent independent reviewer pair',
                )
                votes = {r['reviewer'].strip().casefold(): r['grade'] for r in reviews}
                pairs.append(tuple(votes[r] for r in ordered))
            grades = {r['grade'] for r in reviews}
            if len(grades) > 1:
                adjudication = cell['adjudication']
                require(
                    adjudication is not None and adjudication['grade'] == cell['grade'],
                    'Unadjudicated reviewer disagreement',
                )
                require(
                    adjudication['reviewer'].strip().casefold() not in reviewers,
                    'Adjudicator must be independent of the votes',
                )
            else:
                require(
                    cell['grade'] == next(iter(grades)),
                    'Final grade differs from reviewer consensus',
                )
                require(cell['adjudication'] is None, 'Unnecessary adjudication record')
            primary += cell['grade'] == 2
        if not any(cell['grade'] is None for cell in cells):
            require(primary > 0, 'Reviewed query has no primary answer')
    if require_reviewed or qrels['status'] == 'REVIEWED':
        require(pending == 0 and qrels['status'] == 'REVIEWED', 'Human qrels are incomplete')
    return {
        'pendingJudgmentCount': pending,
        'totalJudgmentCount': len(queries) * len(known),
        'agreement': annotation_agreement(pairs) if pairs else None,
    }
