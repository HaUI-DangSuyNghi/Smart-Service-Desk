from types import SimpleNamespace

import pytest

from cloud_providers.contracts import CloudFailure, Route, encode_json
from cloud_providers.providers import GeminiSelectionProvider, GroqSelectionProvider
from hdpi.generation.models import EvidenceExcerpt, GenerationRequest


def request():
    return GenerationRequest(
        question="Which shape is described?",
        policy_identity="probe@1",
        policy_instructions="Select evidence or refuse.",
        output_schema_version="grounded-claims@0.1.0",
        evidence=(EvidenceExcerpt(citation_id="C1", excerpt="The sample is round."),),
    )


def adapter(vendor, response):
    route = Route(
        id="probe",
        vendor=vendor,
        model="probe" if vendor == "gemini" else "openai/gpt-oss-20b",
        observed_models=("probe-observed",),
        priority=1,
        max_input_bytes=8192,
        max_output_tokens=1024,
        timeout_seconds=3,
        rpm=2,
        rpd=2,
        tpm=16384,
        tpd=16384,
        catalog_digest="0" * 64,
        app_prompt_version="probe@1",
    )
    sent = []

    def post(_, body, __):
        import json

        sent.append(json.loads(body))
        return encode_json(response)

    client = SimpleNamespace(post=post)
    credential = SimpleNamespace(read=lambda: "probe-only-key")
    cls = GeminiSelectionProvider if vendor == "gemini" else GroqSelectionProvider
    return cls(route, client=client, credential=credential), sent


def gemini(signature="opaque-signature"):
    return {
        "modelVersion": "probe-observed",
        "candidates": [
            {
                "finishReason": "STOP",
                "content": {
                    "role": "model",
                    "parts": [
                        {
                            "text": '{"decision":"SELECT","citation_ids":["C1"]}',
                            "thoughtSignature": signature,
                        }
                    ],
                },
            }
        ],
    }


def test_gemini_opaque_signature_is_not_a_claim_or_serialized_answer():
    provider, _ = adapter("gemini", gemini())
    draft = provider.generate(request())

    assert draft.claims[0].text == "The sample is round."
    assert "opaque-signature" not in repr(draft)
    assert provider.stage == "COMPLETED"


@pytest.mark.parametrize("signature", [123, {}, "x" * 16385])
def test_malformed_or_unbounded_signature_still_fails(signature):
    provider, _ = adapter("gemini", gemini(signature))

    with pytest.raises(CloudFailure):
        provider.generate(request())


def test_signature_does_not_authorize_tools_or_thought_parts():
    for extra in ({"functionCall": {}}, {"thought": True}):
        response = gemini()
        response["candidates"][0]["content"]["parts"][0].update(extra)
        provider, _ = adapter("gemini", response)

        with pytest.raises(CloudFailure):
            provider.generate(request())


def test_groq_usage_metadata_and_hidden_reasoning_preserve_strict_selection():
    response = {
        "object": "chat.completion",
        "model": "probe-observed",
        "choices": [
            {
                "index": 0,
                "finish_reason": "stop",
                "message": {
                    "role": "assistant",
                    "content": '{"decision":"REFUSE","citation_ids":[]}',
                },
            }
        ],
        "usage_breakdown": None,
    }
    provider, sent = adapter("groq", response)

    assert not provider.generate(request()).claims
    assert sent[0]["include_reasoning"] is False
    assert sent[0]["response_format"]["json_schema"]["strict"] is True


def test_usage_metadata_does_not_authorize_unknown_top_level_output():
    response = {"usage_breakdown": None, "untrusted_output": "never render"}
    provider, _ = adapter("groq", response)

    with pytest.raises(CloudFailure):
        provider.generate(request())
