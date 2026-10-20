from pathlib import Path

import pytest
from hdpi.evaluation.errors import EvaluationError
from servicedesk.intelligence.knowledge.annotations import annotation_agreement
from servicedesk.intelligence.knowledge.dataset import load_dataset
from servicedesk.intelligence.knowledge.quality import (
    longest_phrase,
    safety_findings,
    token_containment,
    trigram_containment,
)

ROOT = Path(__file__).resolve().parents[3]
DATA = ROOT / 'fixtures/intelligence/service-desk'


def test_draft_dataset_is_complete_as_a_worksheet_not_as_gold():
    dataset = load_dataset(ROOT, DATA)
    assert len(dataset.queries) == 72 and len(dataset.bodies) == 10
    assert dataset.findings['pendingJudgmentCount'] == 720
    assert dataset.findings['unreviewedQueryCount'] == 72
    assert dataset.findings['secretFindingCount'] == dataset.findings['splitLeakageCount'] == 0
    with pytest.raises(EvaluationError):
        load_dataset(ROOT, DATA, require_reviewed=True)


@pytest.mark.parametrize(
    'text,kind',
    [
        ('Write to person@private.example', 'personalEmail'),
        ('Liên hệ 0912345678', 'phoneNumber'),
        ('student_id: 2026123456', 'studentIdentifier'),
        ('password: ActualSensitiveValue99', 'credentialValue'),
        ('OTP=123456', 'credentialValue'),
        ('-----BEGIN PRIVATE KEY-----', 'privateKey'),
        ('AKIAABCDEFGHIJKLMNOP', 'cloudAccessKey'),
    ],
)
def test_synthetic_provenance_does_not_bypass_safety_scanner(text, kind):
    assert kind in safety_findings(text)


def test_safe_markers_and_security_topics_do_not_trigger_findings():
    assert not safety_findings(
        'synthetic-user-001@example.invalid; OTP must never be shared; password: token-placeholder'
    )


def test_longest_phrase_and_mathematical_agreement():
    assert longest_phrase('a b c d e f g h i j', 'z a b c d e f g h i x') == 9
    result = annotation_agreement([(0, 0), (1, 1), (2, 2)])
    assert result['rawAgreement'] == result['linearWeightedCohenKappa'] == 1
    assert result['quadraticWeightedCohenKappa'] == 1
    assert result['krippendorffAlphaOrdinal'] == 1
    assert result['positiveSpecificAgreement'] == 1
    assert annotation_agreement([(1, 1)])['weightedCohenKappa'] is None
    with pytest.raises(EvaluationError):
        annotation_agreement([])



def test_lexical_leakage_signals_are_query_normalized():
    query = 'one two three four five six seven eight nine'
    document = 'zero one two three four five six seven eight nine ten'
    assert trigram_containment(query, document) == 1
    assert token_containment(query, document) == 1
