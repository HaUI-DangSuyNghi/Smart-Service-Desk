"""Real private model -> neutral contract -> app guard probe, never a quality benchmark."""

import argparse
from dataclasses import fields
from hashlib import sha256
import json

from _bootstrap import write_new
from local_runtime_support import worker
from hdpi.classification.decision_observation import DecisionObservation
from hdpi.classification.protocols import ClassificationRequest
from hdpi.classification.service import ClassificationInferenceService
from servicedesk.intelligence.triage_ai07.dataset_profiles import (
    CLASSES,
    validate_dataset,
)
from servicedesk.intelligence.triage_ai07.guarded_decision import guard_observation


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset-root", required=True)
    parser.add_argument("--container", required=True)
    parser.add_argument("--candidate", default="tev1-t1")
    parser.add_argument("--case-id", required=True)
    parser.add_argument(
        "--allow-pending-rights-engineering", action="store_true", required=True
    )
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    dataset = validate_dataset(args.dataset_root)
    case = next(row for row in dataset.partition("DEV") if row.case_id == args.case_id)
    identity = worker(args.container)
    if identity.get("engineeringDatasetDigest") != dataset.dataset_digest:
        raise ValueError(
            "Reference probe requires the pinned private engineering runtime"
        )
    candidate = identity["candidates"][args.candidate]

    def observe(request):
        result = worker(
            args.container,
            candidate=args.candidate,
            fields=dict(request.fields),
            dataset_digest=dataset.dataset_digest,
        )
        if (
            result["runtimeDigest"] != identity["runtimeDigest"]
            or result["policyDigest"] != candidate["policyDigest"]
        ):
            raise ValueError("Probe deployment identity changed during inference")
        value = result["observation"]
        value["option_order"] = tuple(value["option_order"])
        value["input_spans"] = tuple(value["input_spans"])
        if value["model_scores"] is not None:
            value["model_scores"] = tuple(tuple(pair) for pair in value["model_scores"])
        return DecisionObservation(
            **{item.name: value[item.name] for item in fields(DecisionObservation)}
        )

    service = ClassificationInferenceService(
        model_digest=candidate["modelArtifactDigest"],
        policy_digest=candidate["policyDigest"],
        allowed_classes=CLASSES,
        abstain_id="UNDETERMINED",
        observe=observe,
    )
    model_fields = case.model_fields()
    input_digest = sha256(
        json.dumps(
            model_fields, ensure_ascii=False, sort_keys=True, separators=(",", ":")
        ).encode()
    ).hexdigest()
    response = service.infer(
        {
            "contractVersion": "classification-inference@0.1.0",
            "requestId": "00000000-0000-0000-0000-000000000001",
            "inputDigest": input_digest,
            "input": model_fields,
            "allowedClasses": list(CLASSES),
            "modelDigest": candidate["modelArtifactDigest"],
            "policyDigest": candidate["policyDigest"],
        }
    )
    wire = response["observation"]
    observation = DecisionObservation(
        contract_version="decision-observation@0.1.0",
        decision=wire["decision"],
        class_id=wire["classId"],
        option_order=(*CLASSES, "UNDETERMINED"),
        model_scores=None,
        score_semantics="NOT_AVAILABLE",
    )
    request = ClassificationRequest(
        fields=tuple(model_fields.items()),
        allowed_classes=CLASSES,
        policy_digest=candidate["policyDigest"],
    )
    guarded = guard_observation(observation, request)
    if (
        guarded["classification"]["category"] != wire["classId"]
        or guarded["triage"]["priority"] is not None
    ):
        raise ValueError("Rule guard replaced model category or inferred priority")
    if (
        guard_observation(observation, request, mode="SHADOW")["classification"]
        is not None
    ):
        raise ValueError("Shadow exposed a model category")
    write_new(
        args.output,
        {
            "schemaVersion": "ai07-real-model-contract-probe@0.1.0",
            "scope": "ENGINEERING_ONLY_NOT_BACKEND_ACTIVATION",
            "datasetDigest": dataset.dataset_digest,
            "caseId": case.case_id,
            "inputDigest": case.input_digest,
            "modelIdentity": candidate,
            "bridgeRevision": identity["bridgeRevision"],
            "decision": wire["decision"],
            "classId": wire["classId"],
            "modelCategoryPreserved": True,
            "needsReview": True,
            "priority": None,
            "operationalAuthority": False,
            "shadowConcealed": True,
            "officialQualityClaim": False,
            "promotion": "NO_GO",
        },
    )
    print(
        "PASS actual model proposal preserved by neutral contract and application guard; no activation"
    )


if __name__ == "__main__":
    main()
