"""No weights, private rows or GPU downloads: adversarial observer controls."""

from pathlib import Path
import sys
from threading import Event, Thread

import pytest

from hdpi.classification.protocols import ClassificationError, ClassificationRequest
from hdpi.generation.errors import GenerationTimeout, GenerationUnavailable

if not sys.flags.isolated:
    sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "integrations"))

from local_classification.qwen_lora_runtime import ProcessGenerator, QwenLoraRuntime  # noqa: E402


class FakeGenerator:
    ready = True

    def __init__(self, completion="A"):
        self.completion = completion
        self.calls = []

    def generate(self, messages, *, timeout_seconds):
        self.calls.append((messages, timeout_seconds))
        if isinstance(self.completion, Exception):
            raise self.completion
        return self.completion

    def close(self):
        self.ready = False


def configuration():
    # A second domain proves that the runtime never imports Service Desk policy.
    return {
        "policyDigest": "b" * 64,
        "allowedClasses": ("OPEN", "CLOSED", "HELD", "VACANT", "FULL"),
        "abstainId": "UNKNOWN",
        "letters": dict(
            zip("ABCDEF", ("OPEN", "CLOSED", "HELD", "VACANT", "FULL", None))
        ),
        "messages": lambda value: [{"role": "user", "content": value["room"]}],
        "generation": {
            "maxInputTokens": 2047,
            "maxNewTokens": 4,
            "doSample": False,
            "seed": 42,
            "addGenerationPrompt": True,
            "skipSpecialTokens": True,
            "stripOutput": True,
        },
    }


def runtime(completion="A"):
    return QwenLoraRuntime(
        model_digest="a" * 64,
        configuration=configuration(),
        generator=FakeGenerator(completion),
        enabled=True,
    )


def request():
    return ClassificationRequest(
        fields=(("room", "Synthetic café 🚀"),),
        allowed_classes=configuration()["allowedClasses"],
        policy_digest="b" * 64,
    )


@pytest.mark.parametrize(
    "letter,category", list(zip("ABCDEF", (*configuration()["allowedClasses"], None)))
)
def test_exact_letter_mapping_and_no_confidence(letter, category):
    provider = runtime(" \n" + letter + "\t")
    result = provider.observe(request())
    assert result.class_id == category
    assert result.decision == ("ABSTAIN" if letter == "F" else "PROPOSE")
    assert result.model_scores is None and result.score_semantics == "NOT_AVAILABLE"
    assert (
        result.evidence_status == "UNVERIFIED_BY_MODEL"
        and not result.operational_authority
    )
    assert provider.generator.calls[0][1] == 3.2


@pytest.mark.parametrize(
    "raw",
    [
        "",
        "G",
        "a",
        "AB",
        "A\nB",
        "A.",
        "Answer: A",
        "<think>A</think>",
        "Ａ",
        "A\u200b",
        "A\x00",
        '{"letter":"A"}',
        "x" * 65,
        None,
    ],
)
def test_invalid_output_never_becomes_a_fallback(raw):
    with pytest.raises(ClassificationError):
        runtime(raw).observe(request())


def test_disabled_or_changed_policy_never_calls_generator():
    with pytest.raises(ClassificationError):
        QwenLoraRuntime(
            model_digest="a" * 64,
            configuration=configuration(),
            generator=FakeGenerator(),
            enabled=False,
        )
    provider = runtime()
    bad = ClassificationRequest(
        fields=request().fields,
        allowed_classes=request().allowed_classes,
        policy_digest="c" * 64,
    )
    with pytest.raises(ClassificationError):
        provider.observe(bad)
    assert provider.generator.calls == []


def test_busy_single_slot_is_rejected_without_queue():
    provider = runtime()
    started, release = Event(), Event()
    original = provider.generator.generate

    def wait(messages, **kwargs):
        started.set()
        assert release.wait(3)
        return original(messages, **kwargs)

    provider.generator.generate = wait
    thread = Thread(target=lambda: provider.observe(request()))
    thread.start()
    try:
        assert started.wait(2)
        with pytest.raises(GenerationUnavailable):
            provider.observe(request())
    finally:
        release.set()
        thread.join(3)


@pytest.mark.parametrize(
    "error", [GenerationTimeout("safe"), GenerationUnavailable("safe")]
)
def test_failures_propagate_without_retry(error):
    provider = runtime(error)
    with pytest.raises(type(error)):
        provider.observe(request())
    assert len(provider.generator.calls) == 1
    provider.close()
    with pytest.raises(GenerationUnavailable):
        provider.observe(request())


def test_hard_timeout_kills_worker_and_readiness():
    class Connection:
        def send(self, value):
            pass

        def poll(self, timeout):
            return False

        def close(self):
            pass

    class Process:
        alive = True

        def is_alive(self):
            return self.alive

        def terminate(self):
            self.alive = False

        def join(self, timeout):
            pass

    worker = object.__new__(ProcessGenerator)
    worker.connection, worker.process, worker.ready = Connection(), Process(), True
    with pytest.raises(GenerationTimeout):
        worker.generate([], timeout_seconds=0.01)
    assert not worker.ready and not worker.process.is_alive()


def test_stuck_worker_cleanup_fits_remaining_http_deadline():
    class Connection:
        def close(self):
            pass

    class Process:
        alive = True
        waits = []

        def is_alive(self):
            return self.alive

        def terminate(self):
            pass

        def join(self, timeout):
            self.waits.append(timeout)

        def kill(self):
            self.alive = False

    worker = object.__new__(ProcessGenerator)
    worker.connection, worker.process, worker.ready = Connection(), Process(), True
    worker.close()
    assert not worker.ready and not worker.process.is_alive()
    assert 3.2 + sum(worker.process.waits) < 4
