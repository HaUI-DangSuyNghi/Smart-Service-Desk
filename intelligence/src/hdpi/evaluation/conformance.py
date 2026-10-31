"""Generic conformance report assembly.

Application adapters own request schemas, business assertions and runtime
expectations. Tier 2 only assembles deterministic evidence from already-executed
case results.
"""

from .evidence import build_report
from .gates import evaluate_gates


def build_conformance_report(*, dataset, system, cases, metric_version='exact-conformance@1.0'):
    passed = sum(case.get('status') == 'PASS' for case in cases)
    metrics = {'conformancePassRate': passed / len(cases)}
    return build_report(
        evidence_class='AUTHORED_CONFORMANCE',
        dataset=dataset,
        system=system,
        metric_version=metric_version,
        metrics=metrics,
        cases=cases,
        gates=evaluate_gates(metrics=metrics, policy={'conformancePassRate': 1}),
    )
