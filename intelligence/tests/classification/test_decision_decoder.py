"""No-evidence decisions must remain strict, neutral and non-authoritative."""

import json

import pytest

from hdpi.classification.decision_observation import decode_decision
from hdpi.classification.protocols import ClassificationError, ClassificationRequest


@pytest.fixture
def classification_request():
    return ClassificationRequest(
        fields=(("room", "Phòng café e\u0301 🚀"),),
        allowed_classes=("AVAILABLE", "OCCUPIED"),
        policy_digest="a" * 64,
    )


@pytest.mark.parametrize("class_id", ["AVAILABLE", "OCCUPIED", None])
def test_second_domain_decision_has_no_evidence_scores_or_authority(
    classification_request, class_id
):
    raw = json.dumps(
        {
            "decision": "ABSTAIN" if class_id is None else "PROPOSE",
            "classId": class_id,
        }
    ).encode()
    observation = decode_decision(raw, classification_request, abstain_id="UNKNOWN")
    assert observation.class_id == class_id
    assert observation.evidence_status == "UNVERIFIED_BY_MODEL"
    assert observation.model_scores is None
    assert observation.concentration is None
    assert observation.score_semantics == "NOT_AVAILABLE"
    assert observation.operational_authority is False
    assert observation.input_spans == ()


@pytest.mark.parametrize(
    "raw",
    [
        b'{"decision":"ABSTAIN","classId":"AVAILABLE"}',
        b'{"decision":"PROPOSE","classId":null}',
        b'{"decision":"PROPOSE","classId":"UNKNOWN"}',
        b'{"decision":"PROPOSE","classId":true}',
        b'{"decision":"ABSTAIN","classId":null,"confidence":0.99}',
        b'{"decision":"ABSTAIN","classId":null,"evidence":[]}',
        b'{"decision":"ABSTAIN","classId":null,"operationalAuthority":true}',
        b'{"decision":"PROPOSE","decision":"ABSTAIN","classId":null}',
        b'{"decision":"ABSTAIN","classId":NaN}',
        b'{"decision":"ABSTAIN","classId":null} trailing',
        b"null",
        b"[]",
        b"{}",
        b"\xff",
        b"x" * 8193,
    ],
)
def test_malformed_untrusted_decisions_fail_closed(classification_request, raw):
    with pytest.raises(ClassificationError):
        decode_decision(raw, classification_request, abstain_id="UNKNOWN")
