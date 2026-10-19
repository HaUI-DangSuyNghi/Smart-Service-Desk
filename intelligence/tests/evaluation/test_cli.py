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
