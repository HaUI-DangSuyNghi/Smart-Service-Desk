"""Executable, fail-closed quality and hard invariant policy."""
import math

from .dataset import require


def number(value):
    return type(value) in (int, float) and math.isfinite(value)


def evaluate_gates(*, metrics, policy=None, invariants=None):
    violations = []
    for metric, threshold in sorted((policy or {}).items()):
        require(number(threshold) and 0 <= threshold <= 1, 'Invalid quality threshold: ' + metric)
        require(metric in metrics and number(metrics[metric]) and 0 <= metrics[metric] <= 1,
                'Missing or invalid gated metric: ' + metric)
        if metrics[metric] < threshold:
            violations.append({'metric': metric, 'constraint': '>= threshold', 'expected': threshold, 'actual': metrics[metric]})
    for invariant, failures in sorted((invariants or {}).items()):
        require(type(failures) is int and failures >= 0, 'Invalid invariant failure count')
        if failures:
            violations.append({'metric': invariant, 'constraint': '== 0', 'expected': 0, 'actual': failures})
    return {'result': 'FAIL' if violations else 'PASS', 'violations': violations}


def exit_code(report):
    return 0 if report['result'] == 'PASS' else 1
