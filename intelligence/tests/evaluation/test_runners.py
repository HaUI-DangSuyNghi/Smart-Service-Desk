from dataclasses import replace
import os
from pathlib import Path
import subprocess
import sys

import hdpi
import pytest

from hdpi.evaluation.candidates import TfidfCandidate
from servicedesk.intelligence.evaluation.conformance import evaluate as conformance
from hdpi.evaluation.evidence import serialize_report
from hdpi.evaluation.errors import EvaluationError
from servicedesk.intelligence.evaluation.retrieval import evaluate
from hdpi.evaluation.retrieval import stable_report

ROOT = Path(__file__).resolve().parents[3]
DATASET = ROOT / 'fixtures/intelligence/service-desk'
BASELINE = DATASET / 'evaluation/baseline-v0.1.json'
RUNTIME = {'engine': 'rules', 'profileVersion': 'service-desk@0.3.0', 'rulesVersion': '0.3.0'}


class DegradedCandidate(TfidfCandidate):
    def rank(self, query, index):
        return ()


def test_candidate_injection_and_baseline_regression():
    report = evaluate(ROOT, DATASET, baseline=BASELINE, candidate=DegradedCandidate(), gates={'hitAt1': 0, 'hitAt3': 0})
    assert report['result'] == 'FAIL'
    assert report['metrics'] == dict(hitAt1=0, hitAt3=0, mrr=0, recallAt3=0)
    assert len(report['gates']['violations']) == 2
    assert report['comparison']['deltas']['hitAt1'] == -7/12


def test_candidate_unknown_artifact_rejected():
    candidate = DegradedCandidate()
    candidate.rank = lambda query, index: ('unknown',)
    with pytest.raises(EvaluationError, match='unknown artifact'):
        evaluate(ROOT, DATASET, candidate=candidate)


def test_app_owned_cli_propagates_quality_failure():
    installed = 'site-packages' in Path(hdpi.__file__).parts
    script = ROOT / 'scripts/intelligence/service-desk-evaluation.py'
    result = subprocess.run([
        sys.executable,
        *(['-I'] if installed else []),
        str(script),
        '--repo-root', str(ROOT),
        *(['--installed'] if installed else []),
        'retrieval',
        '--dataset', str(DATASET),
        '--baseline', str(BASELINE),
        '--gates', '{"hitAt1": 1}',
    ], cwd=ROOT, capture_output=True, text=True, timeout=60)
    assert result.returncode == 1, result.stderr
    assert '"result": "FAIL"' in result.stdout


def test_repeat_runs_include_identical_rankings_metrics_and_fingerprints():
    first = stable_report(evaluate(ROOT, DATASET, baseline=BASELINE))
    second = stable_report(evaluate(ROOT, DATASET, baseline=BASELINE))
    assert serialize_report(first) == serialize_report(second)
    assert first['result'] == 'PASS'
    assert first['metrics']['hitAt1'] == 7/12


def run_conformance(service):
    return conformance(ROOT, DATASET, profile_version='0.3.0', expected_runtime=RUNTIME,
                       normalization_version='text-normalization@0.2.0', service=service)


def test_conformance_is_repeatable_without_wall_clock_trace(service):
    first = run_conformance(service)
    assert first['metrics']['conformancePassRate'] == 1
    assert first['result'] == 'PASS' and first['caseCount'] == 12
    assert serialize_report(first) == serialize_report(run_conformance(service))


def test_changed_rule_decision_fails_conformance(service, monkeypatch):
    original = service.engine.triage
    def changed(request, profile):
        decision = original(request, profile)
        return replace(decision, priority='LOW' if decision.priority != 'LOW' else 'HIGH')
    monkeypatch.setattr(service.engine, 'triage', changed)
    report = run_conformance(service)
    assert report['result'] == 'FAIL' and report['metrics']['conformancePassRate'] == 0
    assert all(case['status'] == 'FAIL' for case in report['cases'])
