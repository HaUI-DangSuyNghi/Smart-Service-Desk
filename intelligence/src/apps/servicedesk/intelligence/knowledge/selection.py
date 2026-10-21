"""Data readiness and a single-attempt reviewed HOLDOUT workflow."""
import json

from hdpi.evaluation.dataset import parse_json, require
from hdpi.evaluation.errors import EvaluationError
from hdpi.retrieval.models import digest

from .candidate_benchmark import KEYS, freeze_descriptor
from .dataset import load_dataset
from .working_dataset import load_reviewed_dataset, load_working_dataset


def readiness(root, directory):
    base = load_dataset(root, directory)
    working, working_diagnostic = None, None
    try:
        working = load_working_dataset(root, directory)
    except (EvaluationError, OSError) as error:
        working_diagnostic = str(error)
    reviewed, diagnostic = False, None
    try:
        load_reviewed_dataset(root, directory)
        reviewed = True
    except EvaluationError as error:
        diagnostic = str(error)
    return {'schemaVersion': '0.2.0', 'benchmarkClaim': False,
            'dataset': base.identity, 'workingDataset': working.identity if working else None,
            'provisionalEvaluationAvailable': working is not None,
            'provisionalJudgmentBasis': working.judgment_basis if working else None,
            'workingDatasetDiagnostic': working_diagnostic,
            'workingJudgmentCount': sum(len(case['judgments']) for case in working.qrels['cases']) if working else 0,
            'pendingHumanJudgmentCount': base.findings['pendingJudgmentCount'],
            'unreviewedQueryCount': base.findings['unreviewedQueryCount'],
            'unreviewedCorpusCount': sum(item['status'] != 'REVIEWED' for item in base.manifest['artifacts']),
            'agreement': base.findings['agreement'], 'rightsStatus': base.spec['rightsStatus'],
            'holdoutState': base.spec['holdout']['state'], 'reviewedBenchmarkReady': reviewed,
            'reviewedGateDiagnostic': diagnostic,
            'runtimePromotion': 'REQUIRES_SELECTION_AND_RUNTIME_REVIEW' if reviewed else 'NOT_AUTHORIZED',
            'reranker': 'OPTIONAL_DEFERRED_NO_REVIEWED_EVIDENCE',
            'result': 'READY_FOR_REVIEWED_COMPARISON' if reviewed else
                      ('PROVISIONAL_ENGINEERING_READY' if working else 'DATA_PREPARATION_REQUIRED')}


def validate_holdout_freeze(dataset, engines, summary):
    expected = freeze_descriptor(dataset, engines)
    require(dataset.judgment_basis == 'HUMAN_REVIEWED'
            and summary.get('judgmentBasis') == 'HUMAN_REVIEWED' and summary.get('humanReviewed') is True,
            'HOLDOUT requires reviewed labels; provisional evidence cannot unlock it')
    require(summary.get('split') == 'DEV' and summary.get('holdout') == 'NOT_EVALUATED'
            and summary.get('engineeringResult') == 'PASS' and set(summary['reports']) == set(KEYS)
            and summary.get('runtimePromotion') == 'NOT_AUTHORIZED', 'Invalid DEV freeze evidence')
    require(summary['freeze'] == expected and summary['freezeDigest'] == 'sha256:' + digest(expected),
            'Dataset, candidate, model or evaluation configuration changed after DEV freeze')
    return summary['freezeDigest']


def claim_holdout(directory, dataset_identity, freeze_digest):
    """Operator-owned local journal, not a tamper-proof external audit authority."""
    directory.mkdir(parents=True, exist_ok=True)
    path = directory / (digest(dataset_identity) + '.json')
    record = {'dataset': dataset_identity, 'freezeDigest': freeze_digest, 'state': 'EVALUATION_STARTED'}
    try:
        with path.open('x', encoding='utf-8', newline='\n') as stream:
            stream.write(json.dumps(record, sort_keys=True, indent=2) + '\n')
    except FileExistsError:
        raise EvaluationError('HOLDOUT attempt already recorded for this dataset; review the existing journal') from None
    return path


def finish_holdout(path, summary):
    record = parse_json(path.read_bytes())
    require(record['state'] == 'EVALUATION_STARTED' and record['freezeDigest'] == summary['freezeDigest']
            and record['dataset'] == summary['dataset'],
            'HOLDOUT journal association changed')
    record.update(state='COMPLETED', summaryDigest='sha256:' + digest(summary))
    path.write_text(json.dumps(record, sort_keys=True, indent=2) + '\n', encoding='utf-8', newline='\n')
