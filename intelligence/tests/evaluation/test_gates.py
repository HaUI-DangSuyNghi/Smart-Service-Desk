import pytest

from hdpi.evaluation.errors import EvaluationError
from hdpi.evaluation.gates import evaluate_gates, exit_code


def test_quality_regression_and_failure_exit():
    result = evaluate_gates(metrics={'hitAt1': .25}, policy={'hitAt1': .5})
    assert result == {'result': 'FAIL', 'violations': [
        {'metric': 'hitAt1', 'constraint': '>= threshold', 'expected': .5, 'actual': .25}]}
    assert exit_code(result) != 0
    assert exit_code(evaluate_gates(metrics={'hitAt1': .5}, policy={'hitAt1': .5})) == 0


@pytest.mark.parametrize('name', ['workspaceLeakage', 'unauthorizedAccess', 'invalidProvenance', 'unknownCitation', 'contractViolation'])
def test_security_failure_overrides_perfect_quality(name):
    result = evaluate_gates(metrics={'hitAt1': 1}, policy={'hitAt1': .5}, invariants={name: 1})
    assert exit_code(result) == 1 and result['violations'][0]['metric'] == name


@pytest.mark.parametrize('metrics,policy', [({}, {'hitAt1': .5}), ({'hitAt1': float('nan')}, {'hitAt1': .5}),
    ({'hitAt1': 1}, {'hitAt1': float('nan')}), ({'hitAt1': True}, {'hitAt1': .5}),
    ({'hitAt1': 1}, {'hitAt1': -1}), ({'hitAt1': 1}, {'hitAt1': 2}), ({'hitAt1': 1}, {'hitAt1': True})])
def test_invalid_policy_or_metric_fails_closed(metrics, policy):
    with pytest.raises(EvaluationError):
        evaluate_gates(metrics=metrics, policy=policy)
