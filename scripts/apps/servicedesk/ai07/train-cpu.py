"""Explicit weak-label research; never constructs TrainingApproval or saves weights."""

import argparse
from time import perf_counter

from _bootstrap import write_new
from hdpi.classification.protocols import ClassificationRequest
from hdpi.classification.research import SilverResearchClassifier, WeakExample
from servicedesk.intelligence.triage_ai07.dataset_profiles import (
    CLASSES,
    validate_dataset,
)
from servicedesk.intelligence.triage_ai07.guarded_decision import POLICY_DIGEST
from servicedesk.intelligence.triage_ai07.labops_evaluation import report_for


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset-root", required=True)
    parser.add_argument(
        "--allow-machine-silver-poc", action="store_true", required=True
    )
    parser.add_argument(
        "--features", choices=("lexical", "embedding"), default="lexical"
    )
    parser.add_argument("--embedding-directory")
    parser.add_argument("--output", required=True)
    parser.add_argument("--split", choices=("DEV", "CHALLENGE"), default="DEV")
    args = parser.parse_args()
    dataset = validate_dataset(args.dataset_root)
    encoder = None
    if args.features == "embedding":
        from hdpi.retrieval.dense.provider import OnnxEmbeddingProvider

        if not args.embedding_directory:
            parser.error(
                "Embedding research requires explicitly provisioned pinned artifacts"
            )
        encoder = OnnxEmbeddingProvider(args.embedding_directory)
    examples = tuple(
        WeakExample(
            case.case_id,
            case.family_digest,
            case.title + "\n" + case.description,
            case.class_id,
        )
        for case in dataset.partition("TRAIN")
        if case.decision == "PROPOSE"
    )
    classifier = SilverResearchClassifier(
        allowed_classes=CLASSES,
        dataset_digest=dataset.dataset_digest,
        policy_digest=POLICY_DIGEST,
        encoder=encoder,
        enabled=True,
    ).fit(
        examples,
        evaluation_families={
            case.family_digest for case in dataset.partition(args.split)
        },
    )
    attempts = []
    for case in dataset.partition(args.split):
        started = perf_counter()
        observation = classifier.observe(
            ClassificationRequest(
                fields=tuple(case.model_fields().items()),
                allowed_classes=CLASSES,
                policy_digest=POLICY_DIGEST,
            ),
            abstain_id="UNDETERMINED",
        )
        attempts.append(
            {
                "caseId": case.case_id,
                "incidentFamilyDigest": case.family_digest,
                "inputDigest": case.input_digest,
                "repeatIndex": 0,
                "state": observation.decision,
                "proposedClassOrNull": observation.class_id,
                "reasonCodeOrNull": None,
                "wallLatencyMs": round((perf_counter() - started) * 1000, 3),
                "nativeLatencyMsOrNull": None,
                "goldEligible": False,
            }
        )
    report = report_for(
        dataset,
        split=args.split,
        candidate_id="cpu-" + args.features,
        attempts=attempts,
        repeats=1,
        identity=classifier.manifest,
    )
    write_new(args.output, report)
    print(
        "PASS research-only CPU run; proxy silver agreement only; model NEVER_PROMOTABLE"
    )


if __name__ == "__main__":
    main()
