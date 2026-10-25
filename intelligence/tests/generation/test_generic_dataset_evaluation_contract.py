from hashlib import sha256
import json

from hdpi.evaluation.generation import evaluate_generation


def probe():
    content = "An invented instruction."
    source = {
        "metadataId": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
        "version": "1",
        "checksum": sha256(content.encode()).hexdigest(),
        "content": content,
        "mediaType": "text/plain",
    }
    case = {
        "caseId": "probe-1",
        "split": "DEV",
        "category": "probe",
        "question": "Which instruction?",
        "documents": [source],
        "unsafeSourceIds": [source["metadataId"]],
        "provisionalExpectedStatus": "INSUFFICIENT_EVIDENCE",
        "reviewDecision": "PENDING",
        "adjudication": None,
    }
    response = {
        "contractVersion": "0.1.0",
        "requestId": "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
        "status": "SUCCESS",
        "answer": content + " [C1]",
        "needsReview": True,
        "reasonCode": "GROUNDED",
        "citations": [
            {
                "citationId": "C1",
                "metadataId": source["metadataId"],
                "sourceVersion": source["version"],
                "sourceChecksum": source["checksum"],
                "excerpt": content,
            }
        ],
        "retrievalRuntime": {
            key: "probe"
            for key in (
                "engine",
                "engine_version",
                "config_version",
                "corpus_version",
                "index_version",
            )
        },
        "generationRuntime": {
            key: "probe"
            for key in (
                "provider",
                "provider_version",
                "model",
                "model_revision",
                "config_version",
                "prompt_envelope_version",
                "app_prompt_version",
                "output_schema_version",
            )
        },
    }
    return case, response


def test_generic_runner_keeps_technical_provisional_and_unsafe_results_separate():
    case, response = probe()
    report = evaluate_generation([case], lambda unused: response)
    assert report["technicalFailures"] == 0
    assert report["provisionalBehaviorMismatches"] == 1
    assert report["unsafeExactSelections"] == 1
    assert report["officialDecision"] == "BLOCKED"
    assert case["question"] not in json.dumps(report)


def test_invalid_quote_is_retained_as_technical_failure_in_the_denominator():
    case, response = probe()
    response["citations"][0]["excerpt"] = "An unsupported paraphrase."
    report = evaluate_generation([case], lambda unused: response)
    assert report["caseCount"] == report["technicalFailures"] == 1
    assert report["results"][0]["status"] == "ERROR"


def test_runner_exception_never_becomes_raw_diagnostics_or_a_dropped_case():
    case, _ = probe()

    def unavailable(unused):
        raise RuntimeError("private-diagnostic-must-not-escape")

    report = evaluate_generation([case], unavailable)
    assert report["caseCount"] == report["technicalFailures"] == 1
    assert "private-diagnostic" not in json.dumps(report)
