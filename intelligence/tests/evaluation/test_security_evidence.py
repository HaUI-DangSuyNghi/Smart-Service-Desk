import json
import os
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET

import hdpi
import pytest

from hdpi.evaluation.errors import EvaluationError
from servicedesk.intelligence.evaluation.security import evaluate

ROOT = Path(__file__).resolve().parents[3]
DATASET = ROOT / 'fixtures/intelligence/service-desk'
CHECKS = DATASET / 'evaluation/security-checks.json'


def junit_fixture(tmp_path, mutation=None):
    tree = ET.Element('testsuite')
    for check in json.loads(CHECKS.read_text('utf-8'))['checks']:
        for expected in check['tests']:
            node = ET.SubElement(tree, 'testcase', classname=expected['module'], name=expected['name'])
            if mutation and check['id'] == 'crossWorkspaceRetrieval':
                if mutation in ('skipped', 'error', 'failure'):
                    ET.SubElement(node, mutation).text = 'sensitive failure details excluded from evidence'
                elif mutation == 'missing':
                    tree.remove(node)
                elif mutation == 'duplicate':
                    ET.SubElement(tree, 'testcase', classname=expected['module'], name=expected['name'])
    path = tmp_path / 'tests.xml'
    ET.ElementTree(tree).write(path)
    return path


def test_security_report_has_independent_dimensions(tmp_path):
    report = evaluate(ROOT, DATASET, junit=junit_fixture(tmp_path), checks=CHECKS)
    assert report['result'] == 'PASS' and report['caseCount'] == 9
    assert set(report['dimensions']) == {'SECURITY', 'PROVENANCE', 'CONTRACT'}
    assert all(value == 0 for value in report['metrics'].values())


@pytest.mark.parametrize('mutation', ['skipped', 'error', 'failure', 'missing', 'duplicate'])
def test_missing_or_failing_negative_proof_is_a_hard_failure(tmp_path, mutation):
    report = evaluate(ROOT, DATASET, junit=junit_fixture(tmp_path, mutation), checks=CHECKS)
    assert report['result'] == report['dimensions']['SECURITY'] == 'FAIL'
    assert report['metrics']['crossWorkspaceRetrieval'] > 0
    assert 'sensitive failure' not in json.dumps(report)


def test_empty_security_suite_is_not_evidence(tmp_path):
    path = tmp_path / 'empty.xml'
    path.write_text('<testsuite/>', 'utf-8')
    with pytest.raises(EvaluationError):
        evaluate(ROOT, DATASET, junit=path, checks=CHECKS)


def test_disabling_workspace_authorization_breaks_negative_proof(tmp_path):
    environment = os.environ.copy()
    installed = 'site-packages' in Path(hdpi.__file__).parts
    if not installed:
        environment['PYTHONPATH'] = str(Path(hdpi.__file__).resolve().parent.parent)
    output = tmp_path / 'mutated.xml'
    config = ROOT / ('scripts/intelligence/installed-pytest.ini' if installed else 'intelligence/pyproject.toml')
    arguments = ['-c', str(config), str(ROOT / 'intelligence/tests/retrieval/test_models.py') +
                 '::test_partition_exact_versions_and_scope', '-q', '-p', 'no:cacheprovider', '--junitxml=' + str(output)]
    code = ('from hdpi.retrieval.models import PartitionKey; '
            'PartitionKey.authorize = lambda self, context: None; '
            'import pytest; raise SystemExit(pytest.main(' + repr(arguments) + '))')
    process = subprocess.run([sys.executable, *(['-I'] if installed else []), '-c', code],
                             env=environment, cwd=ROOT, capture_output=True, text=True, timeout=60)
    assert process.returncode == 1, process.stderr
    assert list(ET.parse(output).iter('failure')), process.stdout
    report = evaluate(ROOT, DATASET, junit=output, checks=CHECKS)
    assert report['result'] == 'FAIL' and report['metrics']['crossWorkspaceRetrieval'] > 0
