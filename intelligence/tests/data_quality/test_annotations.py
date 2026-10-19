from copy import deepcopy

import pytest
from hdpi.evaluation.errors import EvaluationError
from servicedesk.intelligence.knowledge.annotations import validate_judgments


def synthetic_reviews():
    """Fictitious votes for validator unit tests, never repository qrels."""
    queries = [
        {
            'id': 'q',
            'split': 'HOLDOUT',
            'reviewStatus': 'REVIEWED',
            'provenance': {'humanReviewed': True},
        }
    ]
    cells = []
    for identifier, grade in [('a', 2), ('b', 1), ('c', 0)]:
        reviews = [
            dict(
                reviewer='synthetic-reviewer-' + person,
                grade=grade,
                reason='Synthetic test',
                independent=True,
                queryReviewed=True,
            )
            for person in ('a', 'b')
        ]
        cells.append(
            dict(
                artifactId=identifier,
                grade=grade,
                reason='Synthetic test',
                reviews=reviews,
                adjudication=None,
            )
        )
    return queries, {'status': 'REVIEWED', 'cases': [{'queryId': 'q', 'judgments': cells}]}


def test_two_independent_reviewers_and_complete_matrix():
    queries, qrels = synthetic_reviews()
    result = validate_judgments(qrels, queries, ['a', 'b', 'c'], require_reviewed=True)
    assert result['pendingJudgmentCount'] == 0
    assert result['agreement']['rawAgreement'] == result['agreement']['weightedCohenKappa'] == 1


@pytest.mark.parametrize(
    'mutation',
    ['missing-vote', 'same-person', 'disagreement', 'consensus', 'no-primary', 'draft-query'],
)
def test_invalid_review_cannot_be_accepted(mutation):
    queries, qrels = synthetic_reviews()
    cell = qrels['cases'][0]['judgments'][0]
    if mutation == 'missing-vote':
        cell['reviews'].pop()
    elif mutation == 'same-person':
        cell['reviews'][1]['reviewer'] = ' SYNTHETIC-REVIEWER-A '
    elif mutation == 'disagreement':
        cell['reviews'][1]['grade'] = 0
    elif mutation == 'consensus':
        cell['grade'] = 1
    elif mutation == 'no-primary':
        cell['grade'] = 1
        for review in cell['reviews']:
            review['grade'] = 1
    else:
        queries[0]['reviewStatus'] = 'DRAFT'
    with pytest.raises(EvaluationError):
        validate_judgments(qrels, queries, ['a', 'b', 'c'])


def test_disagreement_needs_separate_domain_owner():
    queries, qrels = synthetic_reviews()
    cell = qrels['cases'][0]['judgments'][0]
    cell['reviews'][1]['grade'] = 0
    cell['adjudication'] = dict(
        reviewer='synthetic-domain-owner', grade=2, role='DOMAIN_OWNER', reason='Synthetic test'
    )
    validate_judgments(qrels, queries, ['a', 'b', 'c'])
    invalid = deepcopy(qrels)
    invalid['cases'][0]['judgments'][0]['adjudication']['reviewer'] = 'synthetic-reviewer-a'
    with pytest.raises(EvaluationError):
        validate_judgments(invalid, queries, ['a', 'b', 'c'])
