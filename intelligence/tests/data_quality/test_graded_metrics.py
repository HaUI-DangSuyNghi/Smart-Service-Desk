from math import log2

import pytest
from hdpi.evaluation.errors import EvaluationError
from hdpi.evaluation.graded import graded_retrieval_metrics


def test_primary_at_rank_three_and_supporting_at_rank_one():
    metrics = graded_retrieval_metrics(
        [(['support', 'irrelevant', 'primary'], {'support': 1, 'irrelevant': 0, 'primary': 2})]
    )
    assert metrics['primaryHitAt1'] == 0
    assert metrics['hitAt1'] == metrics['recallRelevantAt3'] == 1
    assert metrics['ndcgAt3'] == pytest.approx(2.5 / (3 + 1 / log2(3)))


def test_duplicate_ranking_no_hit_and_macro_average():
    metrics = graded_retrieval_metrics([(['a', 'a'], {'a': 2, 'b': 1}), ([], {'a': 2, 'b': 1})])
    assert metrics['primaryHitAt1'] == metrics['hitAt1'] == metrics['mrr'] == 0.5
    assert metrics['recallRelevantAt3'] == 0.25
    assert metrics['ndcgAt3'] == pytest.approx(3 / (3 + 1 / log2(3)) / 2)


@pytest.mark.parametrize(
    'cases',
    [
        [],
        [([], {})],
        [([], {'a': None})],
        [([], {'a': True})],
        [([], {'a': 1})],
        [(['unknown'], {'a': 2})],
    ],
)
def test_invalid_or_unreviewed_grades_fail(cases):
    with pytest.raises(EvaluationError):
        graded_retrieval_metrics(cases)
