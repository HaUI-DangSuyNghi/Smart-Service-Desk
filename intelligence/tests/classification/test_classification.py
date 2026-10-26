from dataclasses import replace
import json

import pytest

from hdpi.classification.candidate import (
    TfidfLogRegCandidate,
    TrainingApproval,
    TrainingExample,
    dataset_digest,
)
from hdpi.classification.protocols import ClassificationError, ClassificationRequest
from hdpi.classification.validation import decode_proposal


def request():
    return ClassificationRequest(
        fields=(("input", "alpha café rocket"),),
        allowed_classes=("A", "B"),
        policy_digest="a" * 64,
    )


def examples():
    return (
        TrainingExample(
            sample_id="1",
            family="f1",
            text="alpha rocket engine",
            class_id="A",
            split="TRAIN",
            reviewed=True,
        ),
        TrainingExample(
            sample_id="2",
            family="f2",
            text="rocket launch alpha",
            class_id="A",
            split="TRAIN",
            reviewed=True,
        ),
        TrainingExample(
            sample_id="3",
            family="f3",
            text="beta ocean marine",
            class_id="B",
            split="TRAIN",
            reviewed=True,
        ),
        TrainingExample(
            sample_id="4",
            family="f4",
            text="ocean blue beta",
            class_id="B",
            split="TRAIN",
            reviewed=True,
        ),
        TrainingExample(
            sample_id="5",
            family="f5",
            text="alpha space",
            class_id="A",
            split="DEV",
            reviewed=True,
        ),
    )


def approval(rows):
    return TrainingApproval(
        dataset_digest=dataset_digest(rows),
        rights_receipt="b" * 64,
        labels_receipt="c" * 64,
    )


def candidate(**kwargs):
    return TfidfLogRegCandidate(
        allowed_classes=("A", "B"),
        policy_digest="a" * 64,
        enabled=True,
        **kwargs,
    )


def wire():
    return {
        "contractVersion": "0.1.0",
        "decision": "PROPOSE",
        "classId": "A",
        "spans": [{"field": "input", "start": 6, "end": 10, "text": "café"}],
    }


def test_unicode_span_contract_and_abstention():
    proposal = decode_proposal(json.dumps(wire()).encode(), request())
    assert proposal.spans[0].text == "café"
    value = {
        "contractVersion": "0.1.0",
        "decision": "ABSTAIN",
        "classId": None,
        "spans": [],
    }
    assert decode_proposal(json.dumps(value).encode(), request()).class_id is None


@pytest.mark.parametrize(
    "mutation",
    ["class", "field", "offset", "boolean", "text", "extra", "empty", "abstain"],
)
def test_hostile_output_rejected(mutation):
    value = wire()

    if mutation == "class":
        value["classId"] = "C"
    elif mutation == "field":
        value["spans"][0]["field"] = "secret"
    elif mutation == "offset":
        value["spans"][0]["end"] = 1000
    elif mutation == "boolean":
        value["spans"][0]["start"] = True
    elif mutation == "text":
        value["spans"][0]["text"] = "invented"
    elif mutation == "extra":
        value["tools"] = []
    elif mutation == "empty":
        value["spans"] = []
    elif mutation == "abstain":
        value["decision"] = "ABSTAIN"

    with pytest.raises(ClassificationError):
        decode_proposal(json.dumps(value).encode(), request())


@pytest.mark.parametrize(
    "raw",
    [
        b'{"decision":"ABSTAIN","decision":"PROPOSE"}',
        b"{} {}",
        b"null",
        b"[]",
        b"\xff",
        b"x" * 8193,
    ],
)
def test_malformed_wire_rejected(raw):
    with pytest.raises(ClassificationError):
        decode_proposal(raw, request())


def test_fit_predict_manifest_and_opt_in():
    with pytest.raises(ClassificationError):
        TfidfLogRegCandidate(allowed_classes=("A", "B"), policy_digest="a" * 64)

    rows = examples()
    model = candidate().fit(rows, approval=approval(rows))
    result = model.predict(request())
    assert result.class_id == "A"
    assert result.score_semantics == "UNCALIBRATED_PROBABILITY"
    assert model.manifest["role"] == "SHADOW"
    manifest = model.manifest
    manifest["classes"].append("C")
    assert model.manifest["classes"] == ["A", "B"]
    assert (
        candidate(abstain_below=1.0)
        .fit(rows, approval=approval(rows))
        .predict(request())
        .decision
        == "ABSTAIN"
    )


@pytest.mark.parametrize(
    "mutation", ["draft", "family", "duplicate", "class", "digest", "empty"]
)
def test_training_rejects_leaks_and_unapproved_labels(mutation):
    rows = list(examples())

    if mutation == "draft":
        rows[0] = replace(rows[0], reviewed=False)
    elif mutation == "family":
        rows[-1] = replace(rows[-1], family="f1")
    elif mutation == "duplicate":
        rows[-1] = replace(rows[-1], text=rows[0].text.upper())
    elif mutation == "class":
        rows[0] = replace(rows[0], class_id="C")
    elif mutation == "empty":
        rows[0] = replace(rows[0], text=" ")

    rows = tuple(rows)
    receipt = approval(rows)

    if mutation == "digest":
        receipt = replace(receipt, dataset_digest="d" * 64)

    with pytest.raises(ClassificationError):
        candidate().fit(rows, approval=receipt)


def test_request_policy_and_classes_must_match_fitted_model():
    rows = examples()
    model = candidate().fit(rows, approval=approval(rows))

    with pytest.raises(ClassificationError):
        model.predict(replace(request(), policy_digest="d" * 64))

    with pytest.raises(ClassificationError):
        model.predict(replace(request(), allowed_classes=("A", "C")))
