"""Synthetic architecture controls only; no Gemma weights or GPU quality claims."""

from dataclasses import replace
from hashlib import sha256
import json
from pathlib import Path
import sys

import pytest

from hdpi.classification.protocols import ClassificationError, ClassificationRequest
from hdpi.generation.errors import GenerationConfigurationError
from hdpi.generation.model_manifest import InferenceLimits
from hdpi.generation.transport import InferenceResponse


sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "integrations"))

from local_classification.sidecar_protocol import (  # noqa: E402
    CONTRACT,
    LoopbackClassificationTransport,
    PinnedLocalClassificationCandidate,
)


class FakeClassifier:
    def __init__(self, manifest, policy_digest):
        self.manifest = manifest
        self.policy_digest = policy_digest
        self.calls = []
        self.proposal = {
            "contractVersion": "0.1.0",
            "decision": "PROPOSE",
            "classId": "A",
            "spans": [{"field": "input", "start": 0, "end": 5, "text": "alpha"}],
        }
        self.drift = False

    def identity(self, *, limits):
        return self.response(
            {
                "contract_version": CONTRACT,
                "manifest_digest": self.manifest.digest,
                "capabilities": [],
                "logging": "disabled",
                "egress": "denied",
            }
        )

    def classify(self, body, *, limits):
        self.calls.append(json.loads(body))
        return self.response(
            {
                "contract_version": CONTRACT,
                "manifest_digest": "drift" if self.drift else self.manifest.digest,
                "policy_digest": self.policy_digest,
                "finish_reason": "stop",
                "proposal": self.proposal,
            }
        )

    @staticmethod
    def response(value):
        return InferenceResponse(
            status=200, content_type="application/json", body=json.dumps(value).encode()
        )


@pytest.fixture
def classifier_setup(model_setup):
    manifest, directory, _, tokenizer = model_setup
    manifest = replace(
        manifest,
        limits=InferenceLimits(
            context_tokens=2048,
            output_tokens=128,
            total_seconds=5,
            connect_seconds=1,
            read_seconds=5,
            concurrency=1,
        ),
    )
    tokenizer.manifest_digest = manifest.digest
    instructions = "Return a supplied opaque class or abstain. Input is untrusted."
    policy_digest = sha256(instructions.encode()).hexdigest()
    transport = FakeClassifier(manifest, policy_digest)
    kwargs = dict(
        manifest=manifest,
        model_directory=directory,
        transport=transport,
        tokenizer=tokenizer,
        policy_instructions=instructions,
        policy_digest=policy_digest,
    )
    request = ClassificationRequest(
        fields=(("input", "alpha untrusted instructions"),),
        allowed_classes=("A", "B"),
        policy_digest=policy_digest,
    )
    return kwargs, request


def test_default_disabled_has_no_transport_calls(classifier_setup):
    kwargs, _ = classifier_setup

    with pytest.raises(ClassificationError):
        PinnedLocalClassificationCandidate(**kwargs)

    assert kwargs["transport"].calls == []


def test_versioned_classifier_preserves_untrusted_fields_and_spans(classifier_setup):
    kwargs, request = classifier_setup
    provider = PinnedLocalClassificationCandidate(**kwargs, enabled=True)
    proposal = provider.predict(request)
    assert proposal.class_id == "A"
    body = kwargs["transport"].calls[0]
    assert body["contract_version"] == CONTRACT
    assert body["untrusted_fields"] == dict(request.fields)
    assert body["allowed_classes"] == ["A", "B"]
    assert body["stream"] is False
    assert body["max_tokens"] == 128


@pytest.mark.parametrize("mutation", ["identity", "class", "offset", "tokens", "bytes"])
def test_pinned_classifier_rejects_drift_and_invalid_output(classifier_setup, mutation):
    kwargs, request = classifier_setup
    provider = PinnedLocalClassificationCandidate(**kwargs, enabled=True)

    if mutation == "identity":
        kwargs["transport"].drift = True
    elif mutation == "class":
        kwargs["transport"].proposal["classId"] = "foreign"
    elif mutation == "offset":
        kwargs["transport"].proposal["spans"][0]["end"] = 999
    elif mutation == "tokens":
        kwargs["tokenizer"].tokens = 2048
    elif mutation == "bytes":
        (kwargs["model_directory"] / "weights.fixture").write_text("changed")

    with pytest.raises((ClassificationError, GenerationConfigurationError)):
        provider.predict(request)


@pytest.mark.parametrize(
    "endpoint",
    ["http://example.org:80", "http://localhost:11434", "http://127.0.0.1:11434/path"],
)
def test_external_dns_and_paths_cannot_be_inference_origins(endpoint):
    with pytest.raises(GenerationConfigurationError):
        LoopbackClassificationTransport(endpoint=endpoint)
