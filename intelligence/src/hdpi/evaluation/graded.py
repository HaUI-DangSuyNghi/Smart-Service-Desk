"""Pure document-level metrics for exhaustive, externally reviewed 0/1/2 qrels."""

from math import log2

from .dataset import require
from .metrics import evaluate_retrieval_metrics

GRADED_METRIC_VERSION = 'graded-artifact-retrieval@0.2.0'


def graded_retrieval_metrics(cases, *, k=3):
    require(type(k) is int and k > 0, 'K must be a positive integer')
    rows = []
    for ranked, judgments in cases:
        require(bool(judgments), 'Graded judgments are required')
        require(
            all(type(grade) is int and grade in (0, 1, 2) for grade in judgments.values()),
            'Invalid relevance grade',
        )
        require(2 in judgments.values(), 'Each query needs primary answer-bearing knowledge')

        unique = tuple(dict.fromkeys(ranked))
        require(set(unique) <= set(judgments), 'Ranking contains an unjudged artifact')
        relevant = {identifier for identifier, grade in judgments.items() if grade > 0}
        gains = [2 ** judgments[identifier] - 1 for identifier in unique[:k]]
        ideal = sorted((2**grade - 1 for grade in judgments.values()), reverse=True)[:k]
        dcg = sum(gain / log2(rank + 1) for rank, gain in enumerate(gains, 1))
        idcg = sum(gain / log2(rank + 1) for rank, gain in enumerate(ideal, 1))

        rows.append((unique, relevant, bool(unique and judgments[unique[0]] == 2), dcg / idcg))

    require(bool(rows), 'Cannot evaluate an empty graded dataset')
    result = evaluate_retrieval_metrics(
        [(ranked, relevant) for ranked, relevant, _, _ in rows], k=k
    )
    result['primaryHitAt1'] = sum(primary for _, _, primary, _ in rows) / len(rows)
    result[f'recallRelevantAt{k}'] = result[f'recallAt{k}']
    result[f'ndcgAt{k}'] = sum(ndcg for _, _, _, ndcg in rows) / len(rows)
    return result
