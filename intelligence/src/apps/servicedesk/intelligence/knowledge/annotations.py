"""Validate human-only relevance, agreement and release locks.

The application owns relevance semantics. Generic HDPI code never knows Service Desk
rationale codes. Blank cells deliberately remain blank until a human reviewer acts.
"""

from collections import Counter

from hdpi.evaluation.dataset import require

ANNOTATION_POLICY_VERSION = '0.2.0'

PRIMARY_CODES = frozenset(
    {
        'PRIMARY_DIRECT_ANSWER',
        'PRIMARY_ROOT_CAUSE',
        'PRIMARY_INSUFFICIENT_INFO',
        'PRIMARY_SECURITY_BREACH',
        'PRIMARY_ROUTING_NON_IT',
        'PRIMARY_ESCALATION_TRIGGER',
    }
)
SUPPORT_CODES = frozenset(
    {
        'SUPPORT_POLICY_PRIORITY',
        'SUPPORT_SECURITY_HYGIENE',
        'SUPPORT_ESCALATION_PATH',
        'SUPPORT_TRIAGE_HANDOFF',
        'SUPPORT_SECONDARY_CHECK',
        'SUPPORT_RELATED_POLICY',
    }
)
HARD_NEGATIVE_CODES = frozenset(
    {
        'HARD_NEGATIVE_LEXICAL_TRAP',
        'HARD_NEGATIVE_NEGATIVE_EVIDENCE',
        'HARD_NEGATIVE_SPECULATIVE',
    }
)
IRRELEVANT_CODE = 'IRRELEVANT_DOMAIN'
RATIONALE_CODES = PRIMARY_CODES | SUPPORT_CODES | HARD_NEGATIVE_CODES | {IRRELEVANT_CODE}


def validate_rationale(grade, code):
    require(code in RATIONALE_CODES, 'Unknown relevance rationale code')
    if grade == 2:
        require(code in PRIMARY_CODES, 'Grade 2 requires a PRIMARY_* rationale')
    elif grade == 1:
        require(code in SUPPORT_CODES, 'Grade 1 requires a SUPPORT_* rationale')
    else:
        require(
            code == IRRELEVANT_CODE or code in HARD_NEGATIVE_CODES,
            'Grade 0 requires IRRELEVANT_DOMAIN or HARD_NEGATIVE_* rationale',
        )


def _weighted_kappa(pairs, *, quadratic):
    size = len(pairs)
    first, second = Counter(a for a, _ in pairs), Counter(b for _, b in pairs)

    def weight(a, b):
        delta = abs(a - b) / 2
        return delta * delta if quadratic else delta

    observed = sum(weight(a, b) for a, b in pairs) / size
    expected = (
        sum(first[a] * second[b] * weight(a, b) for a in range(3) for b in range(3))
        / size**2
    )
    return None if expected == 0 else 1 - observed / expected


def _ordinal_distance(first, second, marginals):
    if first == second:
        return 0.0
    low, high = sorted((first, second))
    cumulative = sum(marginals[value] for value in range(low, high + 1))
    cumulative -= (marginals[low] + marginals[high]) / 2
    return cumulative**2


def _krippendorff_alpha_ordinal(pairs):
    """Ordinal alpha for two complete raters using Krippendorff's ordinal metric."""
    marginals = Counter(vote for pair in pairs for vote in pair)
    total = sum(marginals.values())
    observed = sum(_ordinal_distance(a, b, marginals) for a, b in pairs) / len(pairs)
    expected_numerator = sum(
        marginals[a] * marginals[b] * _ordinal_distance(a, b, marginals)
        for a in range(3)
        for b in range(3)
        if a != b
    )
    expected = expected_numerator / (total * (total - 1)) if total > 1 else 0
    return None if expected == 0 else 1 - observed / expected


def _positive_specific_agreement(pairs):
    both = sum(a > 0 and b > 0 for a, b in pairs)
    first_only = sum(a > 0 and b == 0 for a, b in pairs)
    second_only = sum(a == 0 and b > 0 for a, b in pairs)
    denominator = 2 * both + first_only + second_only
    return None if denominator == 0 else 2 * both / denominator


def conflict_severity(first, second):
    values = {first, second}
    if values == {0, 2}:
        return 'CRITICAL_2_0'
    if values == {0, 1}:
        return 'MODERATE_1_0'
    if values == {1, 2}:
        return 'MINOR_2_1'
    return None


def annotation_agreement(pairs):
    require(bool(pairs), 'Cannot measure agreement without reviewer votes')
    require(
        all(
            isinstance(pair, (tuple, list))
            and len(pair) == 2
            and all(type(vote) is int and vote in (0, 1, 2) for vote in pair)
            for pair in pairs
        ),
        'Invalid annotation agreement grade',
    )
    size = len(pairs)
    raw = sum(a == b for a, b in pairs) / size
    linear = _weighted_kappa(pairs, quadratic=False)
    quadratic = _weighted_kappa(pairs, quadratic=True)
    alpha = _krippendorff_alpha_ordinal(pairs)
    positive = _positive_specific_agreement(pairs)
    conflicts = Counter(
        severity
        for a, b in pairs
        if a != b
        for severity in [conflict_severity(a, b)]
        if severity is not None
    )
    return {
        'rawAgreement': raw,
        'weightedCohenKappa': linear,
        'linearWeightedCohenKappa': linear,
        'quadraticWeightedCohenKappa': quadratic,
        'krippendorffAlphaOrdinal': alpha,
        'positiveSpecificAgreement': positive,
        'judgmentPairCount': size,
        'disagreementCount': sum(conflicts.values()),
        'conflictsBySeverity': dict(sorted(conflicts.items())),
    }


def validate_judgments(qrels, queries, artifacts, *, require_reviewed=False):
    known = set(artifacts)
    by_id = {q['id']: q for q in queries}
    require(
        qrels.get('annotationPolicyVersion') == ANNOTATION_POLICY_VERSION,
        'Qrels annotation policy version mismatch',
    )
    ids = [case['queryId'] for case in qrels['cases']]
    require(
        len(ids) == len(set(ids)) and set(ids) == set(by_id), 'Orphan or duplicate query judgments'
    )

    pending = 0
    pairs = []
    holdout_reviewers = None
    dual_reviewed_dev_judgments = 0
    hard_negative_count = 0
    rationale_count = 0

    for case in qrels['cases']:
        query = by_id[case['queryId']]
        cells = case['judgments']
        identifiers = [cell['artifactId'] for cell in cells]
        require(
            len(identifiers) == len(set(identifiers)) and set(identifiers) == known,
            'Incomplete or unknown artifact judgments',
        )

        primary = 0
        completed = not any(cell['grade'] is None for cell in cells)
        review_counts = set()

        for cell in cells:
            reviews = cell['reviews']
            reviewers = [r['reviewer'].strip().casefold() for r in reviews]
            require(all(reviewers), 'Empty reviewer identity')
            require(len(set(reviewers)) == len(reviewers), 'Duplicate reviewer identity')

            if cell['grade'] is None:
                require(
                    cell.get('rationaleCode') is None
                    and cell['reason'] is None
                    and not reviews
                    and cell['adjudication'] is None,
                    'Blank cell carries invented review',
                )
                pending += 1
                continue

            grade = cell['grade']
            rationale = cell.get('rationaleCode')
            validate_rationale(grade, rationale)
            rationale_count += 1
            hard_negative_count += rationale in HARD_NEGATIVE_CODES

            required = 2 if query['split'] == 'HOLDOUT' else 1
            allowed_counts = {2} if query['split'] == 'HOLDOUT' else {1, 2}
            require(
                len(reviews) in allowed_counts and len(reviews) >= required,
                'Final qrel lacks required independent human reviews',
            )
            review_counts.add(len(reviews))
            require(
                query['reviewStatus'] == 'REVIEWED' and query['provenance']['humanReviewed'],
                'Unreviewed query cannot be gold',
            )

            for review in reviews:
                validate_rationale(review['grade'], review.get('rationaleCode'))

            if len(reviews) == 2:
                ordered = tuple(sorted(reviewers))
                votes = {r['reviewer'].strip().casefold(): r['grade'] for r in reviews}
                pairs.append(tuple(votes[r] for r in ordered))
                if query['split'] == 'HOLDOUT':
                    holdout_reviewers = holdout_reviewers or ordered
                    require(
                        holdout_reviewers == ordered,
                        'Holdout agreement requires a consistent independent reviewer pair',
                    )

            grades = {r['grade'] for r in reviews}
            if len(grades) > 1:
                adjudication = cell['adjudication']
                require(
                    adjudication is not None and adjudication['grade'] == grade,
                    'Unadjudicated reviewer disagreement',
                )
                require(
                    adjudication['reviewer'].strip().casefold() not in reviewers,
                    'Adjudicator must be independent of the votes',
                )
                validate_rationale(adjudication['grade'], adjudication.get('rationaleCode'))
                require(
                    adjudication['rationaleCode'] == rationale,
                    'Final rationale differs from adjudication',
                )
            else:
                require(
                    grade == next(iter(grades)),
                    'Final grade differs from reviewer consensus',
                )
                require(cell['adjudication'] is None, 'Unnecessary adjudication record')

            primary += grade == 2

        if completed:
            require(primary > 0, 'Reviewed query has no primary answer')
            if query['split'] == 'DEV' and review_counts == {2}:
                dual_reviewed_dev_judgments += len(cells)
            if 'root-cause-conflict' in query.get('coverage', ()):
                require(
                    any(
                        cell.get('rationaleCode') in HARD_NEGATIVE_CODES
                        for cell in cells
                        if cell['grade'] == 0
                    ),
                    'Root-cause-conflict query needs an explicit hard negative',
                )

    if require_reviewed or qrels['status'] == 'REVIEWED':
        require(pending == 0 and qrels['status'] == 'REVIEWED', 'Human qrels are incomplete')
        require(
            dual_reviewed_dev_judgments >= 240,
            'At least 50% of DEV judgments require a second independent review',
        )

    return {
        'pendingJudgmentCount': pending,
        'totalJudgmentCount': len(queries) * len(known),
        'rationaleJudgmentCount': rationale_count,
        'hardNegativeCount': hard_negative_count,
        'dualReviewedDevJudgmentCount': dual_reviewed_dev_judgments,
        'agreement': annotation_agreement(pairs) if pairs else None,
    }
