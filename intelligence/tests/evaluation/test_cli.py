import json
import os
from pathlib import Path
import subprocess
import sys

import hdpi
import pytest

ROOT = Path(__file__).resolve().parents[3]
DATASET = ROOT / 'fixtures/intelligence/service-desk'


def cli(command, arguments):
    environment = os.environ.copy()
    installed = 'site-packages' in Path(hdpi.__file__).parts
    script = ROOT / 'scripts/intelligence/service-desk-evaluation.py'
    command_line = [
        sys.executable,
        *(['-I'] if installed else []),
        str(script),
        '--repo-root', str(ROOT),
        *(['--installed'] if installed else []),
        command,
        *map(str, arguments),
    ]
    return subprocess.run(
        command_line, env=environment, cwd=ROOT,
        text=True, capture_output=True, timeout=60,
    )


@pytest.mark.parametrize('threshold,expected', [(0, 0), (1, 1)])
def test_retrieval_cli_returns_real_quality_decision(tmp_path, threshold, expected):
    output = tmp_path / 'report.json'
    result = cli('retrieval', ['--fixtures-root', DATASET,
                 '--gates', json.dumps({'hitAt1': threshold}), '--output', output])
    assert result.returncode == expected, result.stderr
    report = json.loads(output.read_text('utf-8'))
    assert report['result'] == ('PASS' if expected == 0 else 'FAIL')
    assert bool(report['gates']['violations']) == bool(expected)


def test_invalid_dataset_returns_nonzero(tmp_path):
    result = cli('retrieval', ['--dataset', tmp_path / 'missing'])
    assert result.returncode == 2


def test_dense_cli_requires_explicit_available_artifacts(tmp_path):
    result = cli('retrieval', ['--dataset', DATASET, '--candidate', 'dense',
                 '--model-directory', tmp_path / 'unavailable'])
    assert result.returncode == 2
    assert 'Evaluation rejected' in result.stderr


@pytest.mark.parametrize('threshold,expected', [(0, 0), (1, 1)])
def test_bm25_cli_preserves_quality_exit_codes_and_identity(tmp_path, threshold, expected):
    output = tmp_path / 'bm25.json'
    result = cli('retrieval', ['--dataset', DATASET, '--candidate', 'bm25',
                 '--gates', json.dumps({'hitAt1': threshold}), '--output', output])
    assert result.returncode == expected, result.stderr
    report = json.loads(output.read_text('utf-8'))
    assert report['system']['engine'] == 'bm25'
    assert report['benchmarkClaim'] is False


def test_incompatible_baseline_returns_nonzero(tmp_path):
    baseline = json.loads((DATASET / 'evaluation/baseline-v0.1.json').read_text('utf-8'))
    baseline['dataset']['id'] = 'different-authored-dataset'
    path = tmp_path / 'baseline.json'
    path.write_text(json.dumps(baseline), 'utf-8')
    result = cli('retrieval', ['--dataset', DATASET, '--baseline', path])
    assert result.returncode == 2
    assert 'Incompatible baseline dataset' in result.stderr


@pytest.mark.parametrize('changed,expected', [(False, 0), (True, 1)])
def test_conformance_cli_accepts_file_pins_without_shell_json_quoting(tmp_path, changed, expected):
    config = json.loads((DATASET / 'evaluation/conformance-config.json').read_text('utf-8'))
    if changed:
        config['expectedRuntime']['rulesVersion'] = 'incorrect-version'
    path, output = tmp_path / 'config.json', tmp_path / 'report.json'
    path.write_text(json.dumps(config), 'utf-8')
    result = cli('conformance', ['--dataset', DATASET, '--config', path, '--output', output])
    assert result.returncode == expected, result.stderr
    report = json.loads(output.read_text('utf-8'))
    assert report['result'] == ('PASS' if expected == 0 else 'FAIL')
    assert report['metrics']['conformancePassRate'] == (0 if changed else 1)
