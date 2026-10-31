from pathlib import Path
from http.server import BaseHTTPRequestHandler, HTTPServer
from threading import Thread
import sys

import pytest

from hdpi.classification.protocols import ClassificationError, ClassificationRequest


sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "integrations"))

from local_classification.adapters import gemma_chat_json, tev1_systemone  # noqa: E402
from local_classification.ollama_transport import OllamaTransport, strict_json  # noqa: E402


@pytest.fixture
def classification_request():
    return ClassificationRequest(
        fields=(("input", "alpha café"),),
        allowed_classes=("A", "B"),
        policy_digest="a" * 64,
    )


def native_choice():
    return {
        "model": "pinned:one",
        "answers": {
            "decision": {
                "type": "choice",
                "choice": "A",
                "probabilities": {"A": 0.7, "B": 0.2, "UNKNOWN": 0.1},
                "confidence": 0.4,
            }
        },
        "usage": {"input_tokens": 20, "output_tokens": 1},
    }


def test_choice_serialization_keeps_state_and_policy_separate(classification_request):
    policy = {
        "instructions": "Trusted decision instructions",
        "criteria": {
            "A": "first",
            "B": "second",
            "UNKNOWN": "not determined",
        },
        "abstainId": "UNKNOWN",
    }
    body = tev1_systemone.request_payload(
        classification_request, model="pinned:one", policy=policy
    )
    assert body["state"] == dict(classification_request.fields)
    assert set(body) == {"model", "state", "questions", "keep_alive"}
    assert "options" not in body
    value, usage = tev1_systemone.decode_response(
        native_choice(),
        classification_request,
        model="pinned:one",
        abstain_id="UNKNOWN",
    )
    assert value.class_id == "A" and value.input_spans == ()
    assert value.concentration == 0.4
    assert usage["input_tokens"] == 20


@pytest.mark.parametrize(
    "mutation",
    [
        "model",
        "type",
        "option",
        "extra",
        "missing",
        "nan",
        "boolean",
        "zero",
        "usage",
        "authority",
    ],
)
def test_choice_native_envelope_rejects_corruption(classification_request, mutation):
    value = native_choice()
    answer = value["answers"]["decision"]

    if mutation == "model":
        value["model"] = "unapproved:other"
    elif mutation == "type":
        answer["type"] = "noul"
    elif mutation == "option":
        answer["choice"] = "C"
    elif mutation == "extra":
        answer["probabilities"]["C"] = 0.0
    elif mutation == "missing":
        del answer["probabilities"]["B"]
    elif mutation == "nan":
        answer["probabilities"]["A"] = float("nan")
    elif mutation == "boolean":
        answer["probabilities"]["A"] = True
    elif mutation == "zero":
        answer["probabilities"] = dict.fromkeys(("A", "B", "UNKNOWN"), 0.0)
    elif mutation == "usage":
        value["usage"]["input_tokens"] = 3000
    elif mutation == "authority":
        answer["priority"] = "override"

    with pytest.raises(ClassificationError):
        tev1_systemone.decode_response(
            value, classification_request, model="pinned:one", abstain_id="UNKNOWN"
        )


def test_option_order_is_bound_and_missing_scores_are_not_invented(
    classification_request,
):
    policy = {
        "instructions": "Trusted",
        "criteria": {
            "B": "second",
            "A": "first",
            "UNKNOWN": "unknown",
        },
        "abstainId": "UNKNOWN",
    }

    with pytest.raises(ClassificationError):
        tev1_systemone.request_payload(
            classification_request, model="pinned:one", policy=policy
        )

    value = native_choice()
    del value["answers"]["decision"]["probabilities"]
    observation, _ = tev1_systemone.decode_response(
        value, classification_request, model="pinned:one", abstain_id="UNKNOWN"
    )
    assert (
        observation.model_scores is None
        and observation.score_semantics == "NOT_AVAILABLE"
    )


def chat_reply(content):
    return {
        "model": "pinned:chat",
        "created_at": "synthetic",
        "done": True,
        "done_reason": "stop",
        "message": {"role": "assistant", "content": content},
        "total_duration": 100,
        "load_duration": 10,
        "prompt_eval_count": 20,
        "eval_count": 15,
    }


def test_chat_quotes_become_exact_spans(classification_request):
    value, _ = gemma_chat_json.decode_response(
        chat_reply('{"decision":"PROPOSE","classId":"A","evidenceQuotes":["café"]}'),
        classification_request,
        model="pinned:chat",
    )
    assert value.spans[0].text == "café" and value.spans[0].start == 6


@pytest.mark.parametrize(
    "content",
    [
        '{"decision":"PROPOSE","classId":"A","evidenceQuotes":["invented"]}',
        '{"decision":"PROPOSE","classId":"C","evidenceQuotes":["alpha"]}',
        '{"decision":"ABSTAIN","classId":"A","evidenceQuotes":[]}',
        '{"decision":"ABSTAIN","decision":"PROPOSE","classId":null,"evidenceQuotes":[]}',
        '{"decision":"PROPOSE","classId":"A","evidenceQuotes":["alpha"],"tools":[]}',
    ],
)
def test_chat_rejects_ungrounded_or_malformed_json(classification_request, content):
    with pytest.raises(ClassificationError):
        gemma_chat_json.decode_response(
            chat_reply(content), classification_request, model="pinned:chat"
        )


@pytest.mark.parametrize(
    "mutation", ["unfinished", "model", "tools", "context", "output"]
)
def test_chat_native_envelope_failures(classification_request, mutation):
    value = chat_reply('{"decision":"ABSTAIN","classId":null,"evidenceQuotes":[]}')

    if mutation == "unfinished":
        value["done_reason"] = "length"
    elif mutation == "model":
        value["model"] = "other"
    elif mutation == "tools":
        value["message"]["tool_calls"] = []
    elif mutation == "context":
        value["prompt_eval_count"] = 2048
    elif mutation == "output":
        value["eval_count"] = 257

    with pytest.raises(ClassificationError):
        gemma_chat_json.decode_response(
            value, classification_request, model="pinned:chat"
        )


@pytest.mark.parametrize(
    "endpoint",
    [
        "https://127.0.0.1:11434",
        "http://localhost:11434",
        "http://example.org:11434",
        "http://127.0.0.1:11434/path",
        "http://user@127.0.0.1:11434",
        "http://127.0.0.1:11434?x=y",
    ],
)
def test_native_transport_rejects_nonliteral_or_untrusted_origins(endpoint):
    with pytest.raises(ClassificationError):
        OllamaTransport(endpoint=endpoint)


@pytest.mark.parametrize(
    "raw",
    [b'{"a":1,"a":2}', b'{"a":NaN}', b"{} {}", b"x" * 65537],
    ids=["duplicate", "nan", "trailing", "oversized"],
)
def test_native_json_is_strict_and_bounded(raw):
    with pytest.raises(ClassificationError):
        strict_json(raw)


def test_inference_transport_cannot_pull_or_create_models():
    transport = OllamaTransport()

    for path in ("/api/pull", "/api/create", "/api/delete", "/api/copy"):
        with pytest.raises(ClassificationError):
            transport.exchange("POST", path, {})

    with pytest.raises(ClassificationError):
        transport.exchange(
            "POST", "/api/generate", {"model": "pinned:one", "prompt": "generate"}
        )


@pytest.mark.parametrize("status", [200, 302, 429])
def test_native_http_consumes_bounded_close_delimited_responses_without_retry(status):
    from hdpi.generation.errors import GenerationUnavailable

    calls = []

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *args):
            pass

        def do_GET(self):
            calls.append(self.path)
            body = b'{"version":"0.40.8"}'
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Connection", "close")
            self.send_header("Location", "http://example.org/redirect")
            self.end_headers()
            try:
                self.wfile.write(body)
            except OSError:
                pass

    server = HTTPServer(("127.0.0.1", 0), Handler)
    thread = Thread(target=server.serve_forever, daemon=True)
    thread.start()
    transport = OllamaTransport(endpoint=f"http://127.0.0.1:{server.server_port}")
    try:
        if status == 200:
            assert transport.exchange("GET", "/api/version") == {"version": "0.40.8"}
        else:
            with pytest.raises(
                ClassificationError if status == 302 else GenerationUnavailable
            ):
                transport.exchange("GET", "/api/version")
        assert calls == ["/api/version"]
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=2)
