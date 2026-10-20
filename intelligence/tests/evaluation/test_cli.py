import json
import os
from pathlib import Path
import subprocess
import sys

import hdpi
import pytest

ROOT = Path(__file__).resolve().parents[3]
DATASET = ROOT / 'fixtures/intelligence/service-desk'


def cli(module, arguments):
    environment = os.environ.copy()
    installed = 'site-packages' in Path(hdpi.__file__).parts
    if not installed:
        environment['PYTHONPATH'] = str(Path(hdpi.__file__).resolve().parent.parent)
    return subprocess.run([sys.executable, *(['-I'] if installed else []), '-m', module, *map(str, arguments)],
                          env=environment, cwd=ROOT, text=True, capture_output=True, timeout=60)


@pytest.mark.parametrize('threshold,expected', [(0, 0), (1, 1)])
def test_retrieval_cli_returns_real_quality_decision(tmp_path, threshold, expected):
    output = tmp_path / 'report.json'
    result = cli('hdpi.evaluation.retrieval', ['--repo-root', ROOT, '--fixtures-root', DATASET,
                 '--gates', json.dumps({'hitAt1': threshold}), '--output', output])
    assert result.returncode == expected, result.stderr
    report = json.loads(output.read_text('utf-8'))
    assert report['result'] == ('PASS' if expected == 0 else 'FAIL')
    assert bool(report['gates']['violations']) == bool(expected)


def test_invalid_dataset_returns_nonzero(tmp_path):
    result = cli('hdpi.evaluation.retrieval', ['--dataset', tmp_path / 'missing'])
    assert result.returncode == 2


def test_incompatible_baseline_returns_nonzero(tmp_path):
    baseline = json.loads((DATASET / 'evaluation/baseline-v0.1.json').read_text('utf-8'))
    baseline['dataset']['id'] = 'different-authored-dataset'
    path = tmp_path / 'baseline.json'
    path.write_text(json.dumps(baseline), 'utf-8')
    result = cli('hdpi.evaluation.retrieval', ['--repo-root', ROOT, '--dataset', DATASET, '--baseline', path])
    assert result.returncode == 2
    assert 'Incompatible baseline dataset' in result.stderr


@pytest.mark.parametrize('changed,expected', [(False, 0), (True, 1)])
def test_conformance_cli_accepts_file_pins_without_shell_json_quoting(tmp_path, changed, expected):
    config = json.loads((DATASET / 'evaluation/conformance-config.json').read_text('utf-8'))
    if changed:
        config['expectedRuntime']['rulesVersion'] = 'incorrect-version'
    path, output = tmp_path / 'config.json', tmp_path / 'report.json'
    path.write_text(json.dumps(config), 'utf-8')
    result = cli('hdpi.evaluation.conformance', ['--repo-root', ROOT, '--dataset', DATASET,
                 '--config', path, '--output', output])
    assert result.returncode == expected, result.stderr
    report = json.loads(output.read_text('utf-8'))
    assert report['result'] == ('PASS' if expected == 0 else 'FAIL')
    assert report['metrics']['conformancePassRate'] == (0 if changed else 1)
