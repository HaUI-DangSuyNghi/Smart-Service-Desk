"""Document-level metrics for authored associations, not production accuracy."""


def first_relevant_rank(ranked, relevant):
    if not relevant:
        raise ValueError('An evaluation case requires a nonempty relevance set')
    unique = tuple(dict.fromkeys(ranked))
    return next((rank for rank, item in enumerate(unique, 1) if item in relevant), None)


def retrieval_metrics(cases):
    ranks = [first_relevant_rank(ranked, set(relevant)) for ranked, relevant in cases]
    if not ranks:
        raise ValueError('Cannot evaluate an empty dataset')
    return {'caseCount': len(ranks),
            'hitAt1': sum(r == 1 for r in ranks) / len(ranks),
            'hitAt3': sum(r is not None and r <= 3 for r in ranks) / len(ranks),
            'mrr': sum(1/r if r else 0 for r in ranks) / len(ranks)}
