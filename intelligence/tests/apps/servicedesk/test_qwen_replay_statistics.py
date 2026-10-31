"""The five-class silver proxy includes abstentions and invalid generations."""

import importlib.util
from pathlib import Path

import pytest


@pytest.mark.parametrize(
    "predictions, expected",
    [(list("AAAAA"), 0.066667), (["A", "B", "F", "INVALID_FORMAT", "E"], 0.6)],
)
def test_fixed_five_class_macro_includes_every_attempt(predictions, expected):
    path = (
        Path(__file__).resolve().parents[4]
        / "scripts/apps/servicedesk/ai07/replay-qwen-checkpoint.py"
    )
    spec = importlib.util.spec_from_file_location("qwen_replay_statistics", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    rows = [
        {"reference": reference, "predicted": predicted}
        for reference, predicted in zip("ABCDE", predictions)
    ]
    assert module.silver_proxy_macro_f1(rows) == expected
