"""Application data operations with explicit draft and review boundaries."""

import argparse
import csv
import json
import sys
from pathlib import Path

import yaml
from hdpi.evaluation.dataset import require
from hdpi.evaluation.errors import EvaluationError
from hdpi.evaluation.evidence import write_report

from .annotations import annotation_agreement, conflict_severity
from .dataset import load_dataset
from .experiments import acceptance, compare_corpora, evaluate_reviewed_split
from .projection import project, write_projection
from .quality import analyze_corpus


def write_json(path, value):
    if path is None:
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + '\n',
        encoding='utf-8',
        newline='\n',
    )


def validation(args):
    dataset = load_dataset(args.repo_root, args.dataset, require_reviewed=args.require_reviewed)
    result = {
        'schemaVersion': '0.2.0',
        'dataset': dataset.identity,
        'dataValidation': 'PASS',
        'phaseReadiness': 'READY'
        if dataset.spec['status'] == 'REVIEWED'
        else 'PENDING_HUMAN_REVIEW',
        'benchmarkClaim': False,
        'findings': dataset.findings,
    }
    write_json(args.output, result)
    print(json.dumps(result, sort_keys=True))
    return 0


def projection(args):
    sources, manifest = project(
        args.repo_root, args.dataset, enriched=not args.structural, allow_draft=args.draft
    )
    require(
        not args.output.resolve().is_relative_to(args.dataset.resolve()),
        'Generated projections must stay outside authored data',
    )
    write_projection(sources, manifest, args.output)
    print(
        json.dumps({'projectionDigest': manifest['projectionDigest'], 'status': manifest['status']})
    )
    return 0


def analysis(args):
    dataset = load_dataset(args.repo_root, args.dataset)
    report = analyze_corpus(dataset.bodies, dataset.queries)
    report.update(schemaVersion='0.2.0', dataset=dataset.identity, benchmarkClaim=False)
    write_json(args.output, report)
    print(
        json.dumps(
            {
                'nearDuplicateCount': len(report['nearDuplicates']),
                'phraseOverlapReviewCount': len(report['phraseOverlapReview']),
            }
        )
    )
    return 0


def worksheet(args):
    dataset = load_dataset(args.repo_root, args.dataset)
    require(not args.output.exists(), 'Refusing to overwrite existing human annotation work')
    require(
        not args.output.resolve().is_relative_to(args.dataset.resolve()),
        'Worksheets must be written outside immutable source data',
    )
    selected = [query for query in dataset.queries if query['split'] == args.split]
    args.output.parent.mkdir(parents=True, exist_ok=True)

    columns = [
        'queryId',
        'split',
        'title',
        'description',
        'artifactId',
        'artifactTitle',
        'grade',
        'rationaleCode',
        'reason',
        'reviewer',
        'reviewStatus',
    ]
    with args.output.open('w', encoding='utf-8-sig', newline='') as stream:
        writer = csv.DictWriter(stream, fieldnames=columns)
        writer.writeheader()
        for query in selected:
            for artifact in sorted(dataset.manifest['artifacts'], key=lambda value: value['artifactId']):
                writer.writerow(
                    dict(
                        queryId=query['id'],
                        split=query['split'],
                        title=query['title'],
                        description=query['description'],
                        artifactId=artifact['artifactId'],
                        artifactTitle=artifact['title'],
                        grade='',
                        rationaleCode='',
                        reason='',
                        reviewer='',
                        reviewStatus='PENDING',
                    )
                )

    print(
        f'Prepared {len(selected) * len(dataset.bodies)} blank cells for {args.split}; no grades assigned'
    )
    return 0


def review_report(args):
    def read_sheet(path):
        with path.open(encoding='utf-8-sig', newline='') as stream:
            rows = list(csv.DictReader(stream))
        require(bool(rows), 'Reviewer worksheet is empty')
        required = {'queryId', 'artifactId', 'grade', 'rationaleCode', 'reviewer'}
        require(required <= set(rows[0]), 'Reviewer worksheet columns are incomplete')
        parsed, reviewers = {}, set()
        for row in rows:
            key = (row['queryId'].strip(), row['artifactId'].strip())
            require(all(key) and key not in parsed, 'Duplicate or empty reviewer worksheet row')
            require(row['grade'].strip() in ('0', '1', '2'), 'Reviewer grade is incomplete')
            reviewer = row['reviewer'].strip().casefold()
            require(bool(reviewer), 'Reviewer alias is missing')
            reviewers.add(reviewer)
            parsed[key] = {
                'grade': int(row['grade']),
                'rationaleCode': row['rationaleCode'].strip(),
            }
        require(len(reviewers) == 1, 'Each worksheet must belong to exactly one reviewer alias')
        return parsed, next(iter(reviewers))

    first, reviewer_a = read_sheet(args.reviewer_a)
    second, reviewer_b = read_sheet(args.reviewer_b)
    require(reviewer_a != reviewer_b, 'Independent worksheets require distinct reviewers')
    require(set(first) == set(second), 'Reviewer worksheets cover different query/artifact cells')

    pairs, conflicts = [], []
    for key in sorted(first):
        left, right = first[key], second[key]
        pairs.append((left['grade'], right['grade']))
        if left['grade'] != right['grade']:
            conflicts.append(
                {
                    'queryId': key[0],
                    'artifactId': key[1],
                    'reviewerAGrade': left['grade'],
                    'reviewerBGrade': right['grade'],
                    'reviewerARationaleCode': left['rationaleCode'],
                    'reviewerBRationaleCode': right['rationaleCode'],
                    'severity': conflict_severity(left['grade'], right['grade']),
                }
            )

    agreement = annotation_agreement(pairs)
    result = {
        'schemaVersion': '0.2.0',
        'kind': 'BLINDED_REVIEW_AGREEMENT',
        'reviewers': [reviewer_a, reviewer_b],
        'agreement': agreement,
        'conflicts': conflicts,
        'acceptanceTargets': {
            'rawAgreement': 0.88,
            'linearWeightedCohenKappa': 0.70,
            'krippendorffAlphaOrdinal': 0.70,
            'positiveSpecificAgreement': 0.75,
        },
        'authoritativeQrelsModified': False,
    }
    write_json(args.output, result)
    print(json.dumps({'agreement': agreement, 'conflictCount': len(conflicts)}, sort_keys=True))
    return 0


def comparison(args):
    reports = compare_corpora(args.repo_root, args.dataset)
    args.output.mkdir(parents=True, exist_ok=True)
    for identifier, report in reports.items():
        write_report(report, args.output / (identifier.lower() + '-report.json'))

    result = 'PASS' if reports['A0']['result'] == reports['A2']['result'] == 'PASS' else 'FAIL'
    summary = {
        'schemaVersion': '0.2.0',
        'result': result,
        'comparisonKind': 'CROSS_CORPUS_LEGACY_FAMILY_CONTROL',
        'benchmarkClaim': False,
        'humanV02Qrels': 'PENDING',
        'holdout': 'NOT_EVALUATED',
        'reports': {
            identifier: {
                'metrics': report['metrics'],
                'fingerprint': report['fingerprint'],
                'result': report['result'],
            }
            for identifier, report in reports.items()
        },
    }
    write_json(args.output / 'comparison-summary.json', summary)
    print(json.dumps(summary, sort_keys=True))
    return 0 if result == 'PASS' else 1


def evaluate(args):
    report = evaluate_reviewed_split(args.repo_root, args.dataset, split=args.split)
    write_report(report, args.output)
    print(json.dumps(report['metrics'], sort_keys=True))
    return 0


def chunk_experiment(args):
    from .section_experiment import compare_chunking

    reports = compare_chunking(args.repo_root, args.dataset)
    args.output.mkdir(parents=True, exist_ok=True)
    for identifier, report in reports.items():
        write_report(report, args.output / (identifier.lower() + '-report.json'))

    accepted = reports['A3']['result'] == 'PASS'
    summary = {
        'schemaVersion': '0.2.0',
        'benchmarkClaim': False,
        'holdout': 'NOT_EVALUATED',
        'runtimePolicyUnchanged': True,
        'recommendation': 'REVIEW_EXPERIMENT_ONLY' if accepted else 'REJECT_SECTION_POLICY',
        'reports': {
            identifier: {'metrics': report['metrics'], 'result': report['result']}
            for identifier, report in reports.items()
        },
    }
    write_json(args.output / 'chunk-comparison.json', summary)
    print(json.dumps(summary, sort_keys=True))
    # Rejection is an experiment outcome. Runtime still uses the paragraph policy.
    return 0


def accept(args):
    reports = acceptance(args.repo_root, args.dataset)
    result = 'PASS' if all(row['gates']['result'] == 'PASS' for row in reports.values()) else 'FAIL'
    write_json(
        args.output,
        {'schemaVersion': '0.2.0', 'result': result, 'reports': reports, 'benchmarkClaim': False},
    )
    return 0 if result == 'PASS' else 1


def parser():
    root = argparse.ArgumentParser(description=__doc__)
    root.add_argument('--installed', action='store_true')
    root.add_argument('--repo-root', type=Path, default=Path('.'))
    root.add_argument('--dataset', type=Path, default=Path('fixtures/intelligence/service-desk'))
    commands = root.add_subparsers(dest='command', required=True)

    for name, handler in [
        ('validate', validation),
        ('project', projection),
        ('analyze', analysis),
        ('worksheet', worksheet),
        ('compare', comparison),
        ('review-report', review_report),
        ('evaluate', evaluate),
        ('acceptance', accept),
        ('chunk-experiment', chunk_experiment),
    ]:
        command = commands.add_parser(name)
        command.set_defaults(handler=handler)
        command.add_argument(
            '--output',
            type=Path,
            required=name
            in ('project', 'worksheet', 'compare', 'review-report', 'evaluate', 'acceptance', 'chunk-experiment'),
        )
        if name == 'validate':
            command.add_argument('--require-reviewed', action='store_true')
        if name == 'project':
            command.add_argument('--draft', action='store_true')
            command.add_argument('--structural', action='store_true')
        if name in ('worksheet', 'evaluate'):
            command.add_argument('--split', choices=['DEV', 'HOLDOUT'], default='DEV')
        if name == 'review-report':
            command.add_argument('--reviewer-a', type=Path, required=True)
            command.add_argument('--reviewer-b', type=Path, required=True)
    return root


def main():
    args = parser().parse_args()
    output_safe = False
    try:
        if args.output:
            target = args.output.resolve()
            dataset = args.dataset.resolve()
            allowed_evidence = dataset / 'evaluation/corpus-experiments'
            require(
                not target.is_relative_to(dataset) or target.is_relative_to(allowed_evidence),
                'Output would overwrite authored data',
            )
            for source in ('intelligence/src', 'contracts'):
                require(
                    not target.is_relative_to(args.repo_root.resolve() / source),
                    'Output overlaps source/contracts',
                )
        output_safe = True
        return args.handler(args)
    except (EvaluationError, OSError, ValueError, KeyError, TypeError, yaml.YAMLError) as error:
        message = 'Knowledge operation rejected: ' + str(error)
        print(message, file=sys.stderr)
        if output_safe and args.output and args.command in ('validate', 'acceptance', 'evaluate'):
            write_json(
                args.output,
                {
                    'schemaVersion': '0.2.0',
                    'result': 'FAIL',
                    'diagnostic': message,
                    'benchmarkClaim': False,
                },
            )
        return 2
