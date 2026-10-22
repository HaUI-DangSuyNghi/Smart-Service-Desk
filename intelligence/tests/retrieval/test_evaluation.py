from pathlib import Path

import pytest

from hdpi.evaluation.metrics import first_relevant_rank, retrieval_metrics
from servicedesk.intelligence.evaluation.retrieval import evaluate

ROOT = Path(__file__).resolve().parents[3]


def test_metrics_document_dedup_and_no_matches():
    assert first_relevant_rank(['a', 'a', 'b'], {'b'}) == 2
    assert first_relevant_rank(['a'], {'b'}) is None
    assert retrieval_metrics([(['a'], ['a']), (['b', 'a'], ['a']), ([], ['a'])]) == {
        'caseCount': 3, 'hitAt1': 1/3, 'hitAt3': 2/3, 'mrr': .5}
    with pytest.raises(ValueError):
        retrieval_metrics([])
    with pytest.raises(ValueError):
        first_relevant_rank([], set())


def test_authored_evaluation_is_deterministic_and_honest():
    report = evaluate(ROOT, ROOT / 'fixtures/intelligence/service-desk', gates={'hitAt1': 10/12, 'hitAt3': 1})
    assert report == evaluate(ROOT, ROOT / 'fixtures/intelligence/service-desk', gates={'hitAt1': 10/12, 'hitAt3': 1})
    assert report['benchmarkClaim'] is False and report['runtimeCitations'] is False
    assert report['rightsStatus'] == 'PENDING_OWNER_APPROVAL'
    assert report['caseCount'] == 12
    # Freeze the measured baseline. These values deliberately do not claim the
    # aspirational plan gates passed; changes require a reviewed regression update.
    assert report['hitAt1'] == 7/12
    assert report['hitAt3'] == 11/12
    assert report['mrr'] == pytest.approx(.7388888888888889)
    assert report['suggestedGates'] == {'hitAt1': False, 'hitAt3': False}
    assert next(c for c in report['cases'] if c['ticketId'] == 'TICKET-011')['firstRelevantRank'] == 5
