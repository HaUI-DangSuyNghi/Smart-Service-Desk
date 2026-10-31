import io
import json
from uuid import uuid4
from unittest.mock import Mock

import pytest

from hdpi.classification.api_provider import ApiProvider
from hdpi.classification.protocols import ClassificationError
from hdpi.knowledge_api import INSUFFICIENT_EVIDENCE, MISSING_OPTIONS, provider_request, retrieve, stream_answer


def document(text="Inspect the network cable before restarting the router."):
    return {"id": str(uuid4()), "title": "Network troubleshooting", "version": "1", "text": text}

def test_configuration_coverage_instruction_is_generic_and_source_limited():
    provider = Mock(vendor="gemini", base_url="https://generativelanguage.googleapis.com/v1beta", model="synthetic", max_tokens=1024)
    provider.read_credential.return_value = "SYNTHETIC_NOT_A_REAL_SECRET"
    request = provider_request(provider, "Explain the configuration", [])
    instructions = json.loads(request.data)["systemInstruction"]["parts"][0]["text"]
    assert "preserve explicit prerequisites" in instructions
    assert "Never add prerequisites that are absent from the sources" in instructions
    assert "kiemtra2" not in instructions and "model_phong" not in instructions and "ir.model.access.csv" not in instructions


def test_retrieval_returns_only_exact_document_excerpts_and_abstains():
    source = document()
    sources = retrieve("network cable router", [source])
    assert sources and sources[0]["metadataId"] == source["id"]
    assert sources[0]["excerpt"] in source["text"]
    assert retrieve("zyxwvutsrq", [source]) == []
    assert retrieve("network cable", []) == []
    vietnamese = document("Kiểm tra kết nối mạng và dây cáp trước khi khởi động lại.")
    assert retrieve("kiem tra ket noi mang", [vietnamese])


@pytest.mark.parametrize("answer,valid", [(INSUFFICIENT_EVIDENCE, True), (MISSING_OPTIONS, True), ("Unsupported uncited answer", False)])
def test_only_explicit_refusal_can_complete_without_citations(tmp_path, answer, valid):
    key = tmp_path / "key"
    key.write_text("SYNTHETIC_NOT_A_REAL_SECRET")
    provider = ApiProvider(vendor="gemini", base_url="https://generativelanguage.googleapis.com/v1beta", model="synthetic", credential_file=key,
                          policy={"allowedClasses": ["HARDWARE"], "abstainId": "NONE", "instructions": "Synthetic"})
    chunk = {"candidates": [{"content": {"parts": [{"text": answer}]}, "finishReason": "STOP"}]}
    response = io.BytesIO(b"data: " + json.dumps(chunk).encode() + b"\n\n")
    response.status = 200
    response.headers = Mock()
    response.headers.get_content_type.return_value = "text/event-stream"
    provider.opener = Mock()
    provider.opener.open.return_value = response
    events = stream_answer(provider, {"question": "network cable price", "documents": [document()]})
    if valid:
        assert list(events)[-1] == {"type": "done", "grounded": False}
    else:
        with pytest.raises(ClassificationError):
            list(events)
    assert provider.slot.acquire(blocking=False)
    provider.slot.release()
    assert provider.opener.open.call_args.kwargs["timeout"] == 30

def test_bounds_and_duplicate_documents_are_rejected():
    source = document()
    for question, documents in [("", [source]), ("x" * 2001, []), ("network", [source, source]), ("network", [document("x" * 200001)])]:
        with pytest.raises(ClassificationError):
            retrieve(question, documents)

def test_explicit_xml_tags_rank_code_above_prose_and_abstains_for_unknown_tags():
    prose = [document("How do record and menuitem link to open the list and form interface? " * 10) for _ in range(6)]
    code = document('<record id="example_action" model="ir.actions.act_window"/>\n<menuitem action="example_action"/>')
    sources = retrieve("How do <record> and <menuitem> link to open the list and form interface?", prose + [code])
    assert sources[0]["metadataId"] == code["id"]
    assert retrieve("<unknown_tag>", [code]) == []


def test_no_evidence_never_calls_provider():
    provider = Mock()
    events = list(stream_answer(provider, {"question": "Missing guide", "documents": []}))
    assert events[-1] == {"type": "done", "grounded": False}
    provider.opener.open.assert_not_called()

def test_retrieval_deduplicates_identical_passages_without_dropping_distinct_code():
    repeated = "Network recovery command: restart router."
    sources = retrieve("network recovery command", [document(repeated), document(repeated),
        document("Network recovery command: restart router --safe.")])
    assert len(sources) == 2
    assert [source["index"] for source in sources] == [1, 2]
    assert any("--safe" in source["excerpt"] for source in sources)

def test_chunks_do_not_overlap_and_preserve_complete_code_lines():
    text = "".join(f"Network recovery step {index}: inspect cable before router restart.\n" for index in range(60))
    sources = retrieve("network recovery router", [document(text)])
    assert sources
    for source in sources:
        assert source["excerpt"] in text
        assert len(source["excerpt"]) <= 1200
        assert source["excerpt"].endswith("restart.")
    spans = sorted((text.index(source["excerpt"]), text.index(source["excerpt"]) + len(source["excerpt"])) for source in sources)
    assert all(left[1] <= right[0] for left, right in zip(spans, spans[1:]))


def test_real_stream_chunks_are_forwarded_and_invalid_citations_fail(tmp_path):
    key = tmp_path / "key"
    key.write_text("SYNTHETIC_NOT_A_REAL_SECRET")
    provider = ApiProvider(vendor="gemini", base_url="https://generativelanguage.googleapis.com/v1beta", model="synthetic", credential_file=key,
                           policy={"allowedClasses": ["HARDWARE"], "abstainId": "NONE", "instructions": "Synthetic"})
    for citation in ["[1]", "[9]"]:
        chunks = [{"candidates": [{"content": {"parts": [{"text": text}]}, **({"finishReason": "STOP"} if final else {})}]} for text, final in [("Check the cable ", False), (citation, True)]]
        response = io.BytesIO(b"".join(b"data: " + json.dumps(chunk).encode() + b"\n\n" for chunk in chunks))
        response.status = 200
        response.headers = Mock()
        response.headers.get_content_type.return_value = "text/event-stream"
        provider.opener = Mock()
        provider.opener.open.return_value = response
        events = stream_answer(provider, {"question": "network cable", "documents": [document()]})
        assert next(events)["type"] == "sources"
        assert next(events) == {"type": "delta", "text": "Check the cable "}
        assert next(events) == {"type": "delta", "text": citation}
        if citation == "[1]":
            assert next(events) == {"type": "done", "grounded": True}
        else:
            with pytest.raises(ClassificationError):
                next(events)
        events.close()
