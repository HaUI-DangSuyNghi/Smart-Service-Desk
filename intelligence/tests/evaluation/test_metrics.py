import pytest

from hdpi.evaluation.metrics import evaluate_retrieval_metrics, first_relevant_rank, recall_at_k


@pytest.mark.parametrize('ranked,relevant,rank,recall', [
    (['a'], ['a'], 1, 1),
    (['x', 'y', 'a'], ['a'], 3, 1),
    (['x'], ['a'], None, 0),
    (['x', 'x', 'a'], ['a'], 2, 1),
    (['a', 'x', 'b'], ['a', 'b', 'c'], 1, 2/3),
    ([], ['a'], None, 0),
])
def test_mathematical_rank_and_recall(ranked, relevant, rank, recall):
    assert first_relevant_rank(ranked, set(relevant)) == rank
    assert recall_at_k(ranked, relevant, 3) == recall


def test_macro_average_and_large_k():
    assert evaluate_retrieval_metrics([(['a'], ['a', 'b']), (['x', 'y', 'a'], ['a']), ([], ['a'])]) == {
        'caseCount': 3, 'hitAt1': 1/3, 'hitAt3': 2/3, 'mrr': (1 + 1/3) / 3, 'recallAt3': .5}
    assert recall_at_k(['a'], ['a', 'b'], 100) == .5


@pytest.mark.parametrize('cases', [[], [([], [])]])
def test_empty_inputs_rejected(cases):
    with pytest.raises(ValueError):
        evaluate_retrieval_metrics(cases)


@pytest.mark.parametrize('k', [0, -1, True, 1.5])
def test_invalid_k(k):
    with pytest.raises(ValueError):
        recall_at_k(['a'], ['a'], k)
