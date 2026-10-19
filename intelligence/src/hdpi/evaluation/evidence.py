"""Build and serialize deterministic, safe evidence without runtime content."""
import json

from .models import EVALUATOR_VERSION, SCHEMA_VERSION, fingerprint, validate_report


def build_report(*, evidence_class, dataset, system, metric_version, metrics, cases, gates, **extra):
    report = dict(schemaVersion=SCHEMA_VERSION, evidenceClass=evidence_class, benchmarkClaim=False,
                  dataset=dataset.identity, components=dataset.component_digests, system=system,
                  evaluatorVersion=EVALUATOR_VERSION, metricVersion=metric_version,
                  caseCount=len(cases), metrics=metrics, gates=gates, cases=cases, result=gates['result'], **extra)
    if 'FAIL' in report.get('dimensions', {}).values():
        report['result'] = 'FAIL'
    report['fingerprint'] = fingerprint(report)
    return validate_report(report)


def serialize_report(report):
    return json.dumps(validate_report(report), sort_keys=True, ensure_ascii=True, indent=2, allow_nan=False) + '\n'


def write_report(report, output):
    raw = serialize_report(report)
    if output:
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(raw, encoding='utf-8', newline='\n')
