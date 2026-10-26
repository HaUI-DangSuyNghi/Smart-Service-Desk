"""Paired private LabOps evaluation with complete failure accounting; never gold."""

import argparse
import json
from pathlib import Path
import subprocess
from time import perf_counter

from _bootstrap import ROOT, write_new
from local_runtime_support import WorkerRejected, deployment_evidence, worker
from local_classification.diagnostics import safe_telemetry
from servicedesk.intelligence.triage_ai07.dataset_profiles import validate_dataset
from servicedesk.intelligence.triage_ai07.labops_evaluation import report_for


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset-root", required=True)
    parser.add_argument("--split", choices=("DEV", "CHALLENGE"), required=True)
    parser.add_argument("--candidate", default="tev1,tev1-t1,gemma3-g1")
    parser.add_argument("--container", required=True)
    parser.add_argument(
        "--allow-pending-rights-engineering", action="store_true", required=True
    )
    parser.add_argument("--repeats", type=int, choices=(1, 2, 3), default=1)
    parser.add_argument("--output-directory", type=Path, required=True)
    args = parser.parse_args()
    args.output_directory = args.output_directory.resolve()
    if not args.output_directory.is_relative_to((ROOT / ".local/ai07").resolve()):
        raise ValueError("Private engineering journals must stay under .local/ai07")
    if args.output_directory.exists():
        raise ValueError("Study directory must be new; prior ledgers are immutable")
    dataset = validate_dataset(args.dataset_root)
    identity = worker(args.container)
    if (
        identity.get("mode") != "ENGINEERING_LAB_ONLY"
        or identity.get("engineeringDatasetDigest") != dataset.dataset_digest
    ):
        raise ValueError("Runtime is not explicitly bound to this engineering dataset")
    candidates = args.candidate.split(",")
    if len(set(candidates)) != len(candidates) or any(
        name not in identity["candidates"] for name in candidates
    ):
        raise ValueError("Unknown or duplicate pinned candidate")
    deployment = deployment_evidence(args.container)
    args.output_directory.mkdir(parents=True)
    for name in candidates:
        attempts = []
        # Append and flush safe attempt receipts before moving to the next input.
        journal = args.output_directory / (name + "-attempts.jsonl")
        with journal.open("x", encoding="utf-8", newline="\n") as stream:
            for repeat in range(args.repeats):
                for case in dataset.partition(args.split):
                    started = perf_counter()
                    receipt = {
                        "caseId": case.case_id,
                        "incidentFamilyDigest": case.family_digest,
                        "inputDigest": case.input_digest,
                        "repeatIndex": repeat,
                        "state": "REJECTED",
                        "proposedClassOrNull": None,
                        "reasonCodeOrNull": None,
                        "nativeLatencyMsOrNull": None,
                        "coldLoadedBeforeOrNull": None,
                        "observedGpuPlacementOrNull": None,
                        "goldEligible": False,
                    }
                    try:
                        result = worker(
                            args.container,
                            candidate=name,
                            fields=case.model_fields(),
                            dataset_digest=dataset.dataset_digest,
                        )
                        if (
                            result["runtimeDigest"] != identity["runtimeDigest"]
                            or result["policyDigest"]
                            != identity["candidates"][name]["policyDigest"]
                            or result["operationalAuthority"] is not False
                        ):
                            raise ValueError("Runtime identity drift")
                        value = result["observation"]
                        receipt.update(
                            {
                                "state": value["decision"],
                                "proposedClassOrNull": value["class_id"],
                                "nativeLatencyMsOrNull": result["nativeElapsedMs"],
                                "coldLoadedBeforeOrNull": result["loadedBefore"],
                                "observedGpuPlacementOrNull": result["placement"],
                            }
                        )
                    except WorkerRejected as error:
                        receipt["state"] = {
                            502: "INVALID",
                            503: "UNAVAILABLE",
                            504: "TIMEOUT",
                        }.get(error.status, "REJECTED")
                        receipt["reasonCodeOrNull"] = error.reason_code
                        telemetry = safe_telemetry(error.telemetry)
                        receipt["nativeLatencyMsOrNull"] = telemetry.get(
                            "nativeElapsedMs"
                        )
                        receipt["coldLoadedBeforeOrNull"] = telemetry.get(
                            "loadedBefore"
                        )
                    except subprocess.TimeoutExpired:
                        receipt.update(state="TIMEOUT", reasonCodeOrNull="TIMEOUT")
                    except Exception:
                        receipt.update(
                            state="REJECTED", reasonCodeOrNull="SCHEMA_MISMATCH"
                        )
                    receipt["wallLatencyMs"] = round(
                        (perf_counter() - started) * 1000, 3
                    )
                    attempts.append(receipt)
                    stream.write(json.dumps(receipt, ensure_ascii=True) + "\n")
                    stream.flush()
                print(
                    f"Completed {name} repeat {repeat + 1}: {len(dataset.partition(args.split))} cases",
                    flush=True,
                )
        candidate_identity = {
            **identity["candidates"][name],
            "bridgeRevision": identity["bridgeRevision"],
            "providerVersion": identity["ollamaVersion"],
            "runtimeDigest": identity["runtimeDigest"],
        }
        report = report_for(
            dataset,
            split=args.split,
            candidate_id=name,
            attempts=attempts,
            repeats=args.repeats,
            identity=candidate_identity,
        )
        report["deployment"] = deployment
        if validate_dataset(args.dataset_root).dataset_digest != dataset.dataset_digest:
            raise ValueError("Dataset drifted during inference")
        write_new(args.output_directory / (name + ".json"), report)
    print("PASS full private engineering ledgers; proxy silver only; production NO_GO")


if __name__ == "__main__":
    main()
