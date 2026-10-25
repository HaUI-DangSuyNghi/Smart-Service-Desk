import os
from pathlib import Path
import subprocess
import sys

import pytest


ROOT = Path(__file__).resolve().parents[3]


def test_real_model_command_fails_closed_without_approved_deployment(tmp_path):
    output = tmp_path / 'readiness.json'
    result = subprocess.run(
        [
            sys.executable,
            str(ROOT / 'scripts/intelligence/ai06-provider-evidence.py'),
            '--mode',
            'approved-local-model',
            '--output',
            str(output),
        ],
        cwd=ROOT,
        capture_output=True,
        text=True,
        timeout=15,
    )

    assert result.returncode == 2
    assert 'REAL_MODEL_VALIDATION_BLOCKED' in result.stdout
    assert 'BLOCKED' in output.read_text('utf-8')


@pytest.mark.generation_model
def test_real_model_validation_requires_separate_owner_evidence():
    if os.environ.get('HDPI_REQUIRE_GENERATION_MODEL') != '1':
        pytest.skip(
            'REAL_MODEL_VALIDATION_BLOCKED: opt-in owner approval and pinned deployment required'
        )

    pytest.fail(
        'REAL_MODEL_VALIDATION_BLOCKED: no approved model manifest or real-run receipt exists'
    )
