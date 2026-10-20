import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
DATA = ROOT / 'fixtures/intelligence/service-desk'
CANDIDATE = DATA / 'retrieval/ai-assisted/qrels-candidate-v0.2.json'


def test_ai_assisted_candidate_qrels_match_current_ticket_and_corpus_matrix():
    candidate = json.loads(CANDIDATE.read_text('utf-8'))
    tickets = [
        json.loads(line)
        for line in (DATA / 'tickets/tickets-v0.2.jsonl').read_text('utf-8').splitlines()
        if line.strip()
    ]
    manifest = json.loads((DATA / 'knowledge/manifest-v0.2.json').read_text('utf-8'))

    query_ids = [ticket['id'] for ticket in tickets]
    artifact_ids = {artifact['artifactId'] for artifact in manifest['artifacts']}

    assert candidate['kind'] == 'AI_ASSISTED_CANDIDATE_QRELS'
    assert candidate['authoritative'] is False
    assert candidate['benchmarkClaim'] is False
    assert candidate['governance']['blindHumanReviewRequired'] is True
    assert candidate['governance']['mayPopulateAuthoritativeQrelsAutomatically'] is False

    cases = candidate['cases']
    assert len(cases) == 72
    assert [case['queryId'] for case in cases] == query_ids

    judgments = [judgment for case in cases for judgment in case['judgments']]
    assert len(judgments) == 720
    assert all(
        {judgment['artifactId'] for judgment in case['judgments']} == artifact_ids
        and len(case['judgments']) == 10
        and any(judgment['grade'] == 2 for judgment in case['judgments'])
        for case in cases
    )
    assert all(judgment['grade'] in (0, 1, 2) for judgment in judgments)
    assert all(judgment['rationaleCode'] and judgment['reason'] for judgment in judgments)


def test_authoritative_qrels_still_require_actual_human_judgments():
    qrels = json.loads((DATA / 'retrieval/qrels-v0.2.json').read_text('utf-8'))
    assert qrels['status'] == 'AWAITING_HUMAN_JUDGMENTS'
    assert all(
        judgment['grade'] is None
        for case in qrels['cases']
        for judgment in case['judgments']
    )
