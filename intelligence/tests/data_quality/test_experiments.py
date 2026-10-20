import csv
import json
import subprocess
import sys
from pathlib import Path

import hdpi
import pytest
from hdpi.evaluation.candidates import TfidfCandidate
from hdpi.evaluation.errors import EvaluationError
from servicedesk.intelligence.knowledge.experiments import (
    compare_corpora,
    evaluate_family_control,
    evaluate_reviewed_split,
)

ROOT = Path(__file__).resolve().parents[3]
DATA = ROOT / 'fixtures/intelligence/service-desk'
SCRIPT = ROOT / 'scripts/intelligence/knowledge-data.py'


@pytest.fixture(scope='module')
def reports():
    return compare_corpora(ROOT, DATA)


def run_cli(*arguments):
    installed = 'site-packages' in Path(hdpi.__file__).parts
    return subprocess.run(
        [
            sys.executable,
            *(['-I'] if installed else []),
            str(SCRIPT),
            *(['--installed'] if installed else []),
            *map(str, arguments),
        ],
        cwd=ROOT,
        capture_output=True,
        text=True,
        timeout=90,
    )


def test_same_engine_corpus_control_discloses_family_projection(reports):
    assert reports['A0']['metrics']['hitAt1'] == 7 / 12
    assert reports['A1']['metrics']['hitAt1'] == 10 / 12
    assert reports['A2']['metrics']['hitAt1'] == 11 / 12
    assert reports['A2']['metrics']['hitAt3'] == 1
    assert reports['A2']['metrics']['mrr'] > reports['A0']['metrics']['mrr']
    assert reports['A2']['comparison']['sameDatasetComparison'] is False
    assert reports['A2']['comparison']['v02DocumentQrels'] is False
    for key in (
        'engine',
        'engineVersion',
        'engineConfigVersion',
        'normalizationVersion',
        'chunkPolicyVersion',
        'queryStrategyVersion',
    ):
        assert len({report['system'][key] for report in reports.values()}) == 1
    assert len({report['dataset']['digest'] for report in reports.values()}) == 3


def test_degraded_data_candidate_cannot_pass_regression_gate(reports):
    candidate = TfidfCandidate()
    candidate.rank = lambda query, index: ()
    report = evaluate_family_control(
        ROOT, DATA, enriched=True, reference=reports['A0'], candidate=candidate
    )
    assert report['result'] == 'FAIL' and report['metrics']['hitAt1'] == 0


@pytest.mark.parametrize('split', ['DEV', 'HOLDOUT'])
def test_pending_gold_cannot_produce_v02_metrics(split):
    with pytest.raises(EvaluationError):
        evaluate_reviewed_split(ROOT, DATA, split=split)


def test_cli_prepares_blank_readable_csv_and_preserves_human_work(tmp_path):
    output = tmp_path / 'review.csv'
    result = run_cli('worksheet', '--split', 'HOLDOUT', '--output', output)
    assert result.returncode == 0, result.stderr
    with output.open(encoding='utf-8-sig', newline='') as stream:
        rows = list(csv.DictReader(stream))
    assert len(rows) == 240
    assert all(not row['grade'] and not row['reason'] and not row['reviewer'] for row in rows)
    before = output.read_bytes()
    assert run_cli('worksheet', '--split', 'HOLDOUT', '--output', output).returncode != 0
    assert output.read_bytes() == before



def test_review_report_compares_blinded_sheets_without_touching_qrels(tmp_path):
    first = tmp_path / 'review-a.csv'
    second = tmp_path / 'review-b.csv'
    assert run_cli('worksheet', '--split', 'HOLDOUT', '--output', first).returncode == 0
    assert run_cli('worksheet', '--split', 'HOLDOUT', '--output', second).returncode == 0

    for path, reviewer in ((first, 'rev_a'), (second, 'rev_b')):
        with path.open(encoding='utf-8-sig', newline='') as stream:
            rows = list(csv.DictReader(stream))
            fieldnames = list(rows[0].keys())
        for index, row in enumerate(rows):
            row['grade'] = '2' if index == 0 else ('1' if index == 1 else '0')
            row['rationaleCode'] = (
                'PRIMARY_DIRECT_ANSWER'
                if index == 0
                else ('SUPPORT_SECONDARY_CHECK' if index == 1 else 'IRRELEVANT_DOMAIN')
            )
            row['reviewer'] = reviewer
            row['reviewStatus'] = 'DONE'
        with path.open('w', encoding='utf-8-sig', newline='') as stream:
            writer = csv.DictWriter(stream, fieldnames=fieldnames)
            writer.writeheader()
            writer.writerows(rows)

    output = tmp_path / 'agreement.json'
    before = (DATA / 'retrieval/qrels-v0.2.json').read_bytes()
    result = run_cli(
        'review-report',
        '--reviewer-a',
        first,
        '--reviewer-b',
        second,
        '--output',
        output,
    )
    assert result.returncode == 0, result.stderr
    report = json.loads(output.read_text('utf-8'))
    assert report['agreement']['rawAgreement'] == 1
    assert report['agreement']['linearWeightedCohenKappa'] == 1
    assert report['conflicts'] == [] and report['authoritativeQrelsModified'] is False
    assert (DATA / 'retrieval/qrels-v0.2.json').read_bytes() == before

def test_cli_review_gate_is_nonzero_and_does_not_fake_holdout(tmp_path):
    output = tmp_path / 'readiness.json'
    result = run_cli('acceptance', '--output', output)
    assert result.returncode != 0
    assert json.loads(output.read_text('utf-8'))['result'] == 'FAIL'


def test_cli_rejects_output_over_authoritative_qrels():
    path = DATA / 'retrieval/qrels-v0.2.json'
    before = path.read_bytes()
    assert run_cli('validate', '--output', path).returncode != 0
    assert path.read_bytes() == before
