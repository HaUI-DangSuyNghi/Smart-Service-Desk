"""Service Desk retrieval-evaluation adapter over generic HPDI measurement."""

from pathlib import Path

from hdpi.evaluation.candidates import TfidfCandidate
from hdpi.evaluation.retrieval import evaluate as evaluate_generic, stable_report

from .dataset import load_dataset, retrieval_view


def evaluate(root: Path, dataset_root: Path, *, gates=None, baseline=None, candidate=None):
    dataset = load_dataset(dataset_root, root)
    report = evaluate_generic(
        retrieval_view(dataset),
        gates=gates,
        baseline=baseline,
        candidate=candidate or TfidfCandidate(),
        system_overrides={'queryStrategyVersion': 'service-desk-title-newline-description@0.1.0'},
    )
    # Preserve historical evidence field names at the Tier-3 adapter boundary.
    cases = []
    for case in report['cases']:
        cases.append({
            'ticketId': case['caseId'],
            'rankedLocalArtifacts': case['rankedArtifacts'],
            'expectedRelevantLocalArtifacts': case['expectedRelevantArtifacts'],
            'firstRelevantRank': case['firstRelevantRank'],
        })
    return report | {'cases': cases}


def load_authoring_index(root, dataset_root):
    dataset = load_dataset(dataset_root, root)
    candidate = TfidfCandidate()
    return candidate.build(dataset.sources), dataset.knowledge


__all__ = ['evaluate', 'load_authoring_index', 'stable_report']
