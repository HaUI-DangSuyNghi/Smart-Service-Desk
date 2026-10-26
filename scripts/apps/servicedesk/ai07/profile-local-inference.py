"""Persistent private worker profiling; no hosted ports, labels or quality claim."""

import argparse
import json
import subprocess
from time import perf_counter

from _bootstrap import write_new
from local_runtime_support import worker, strict_json
from servicedesk.intelligence.triage_ai07.dataset_profiles import validate_dataset
from servicedesk.intelligence.triage_ai07.labops_evaluation import timing


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset-root", required=True)
    parser.add_argument("--container", required=True)
    parser.add_argument("--candidate", default="gemma3-g1,tev1,tev1-t1")
    parser.add_argument("--samples", type=int, choices=range(1, 11), default=5)
    parser.add_argument(
        "--allow-pending-rights-engineering", action="store_true", required=True
    )
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    dataset = validate_dataset(args.dataset_root)
    identity = worker(args.container)
    if identity.get("engineeringDatasetDigest") != dataset.dataset_digest:
        raise ValueError("Profiler dataset differs from the pinned runtime")
    cases = dataset.partition("DEV")[: args.samples]
    data = b"".join(
        (json.dumps(case.model_fields(), ensure_ascii=True) + "\n").encode()
        for case in cases
    )
    results = {}
    for name in args.candidate.split(","):
        if name not in identity["candidates"]:
            raise ValueError("Unknown candidate")
        started = perf_counter()
        result = subprocess.run(
            [
                "docker",
                "exec",
                "-i",
                args.container,
                "python",
                "-m",
                "local_classification.worker",
                "--candidate",
                name,
                "--dataset-digest",
                dataset.dataset_digest,
                "--stream",
            ],
            input=data,
            capture_output=True,
            timeout=180,
            check=True,
        )
        frames = [strict_json(raw, 65536) for raw in result.stdout.splitlines()]
        if len(frames) != len(cases):
            raise ValueError("Persistent profiling denominator is incomplete")
        attempts = []
        for case, frame in zip(cases, frames, strict=True):
            item = {
                "caseId": case.case_id,
                "inputDigest": case.input_digest,
                "wallLatencyMs": frame["wallLatencyMs"],
            }
            if "result" in frame:
                value = frame["result"]
                if value["runtimeDigest"] != identity["runtimeDigest"]:
                    raise ValueError("Runtime changed during profile")
                item.update(
                    state=value["observation"]["decision"],
                    nativeElapsedMs=value["nativeElapsedMs"],
                    loadedBefore=value["loadedBefore"],
                    usage=value["usage"],
                    placement=value["placement"],
                    nativeTelemetry=value.get("nativeTelemetry", {}),
                )
            else:
                item.update(
                    state="REJECTED",
                    reasonCode=frame["failure"]["reasonCode"],
                    diagnostics=frame["failure"]["diagnostics"],
                )
            attempts.append(item)
        results[name] = {
            "attempts": attempts,
            "batchWallMs": round((perf_counter() - started) * 1000, 3),
            "persistentAllAttemptWallLatency": timing(
                [item["wallLatencyMs"] for item in attempts]
            ),
            "nativeLatency": timing(
                [
                    item["nativeElapsedMs"]
                    for item in attempts
                    if "nativeElapsedMs" in item
                ]
            ),
            "modelIdentity": identity["candidates"][name],
        }
    write_new(
        args.output,
        {
            "schemaVersion": "ai07-persistent-profile@0.1.0",
            "datasetDigest": dataset.dataset_digest,
            "runtimeDigest": identity["runtimeDigest"],
            "bridgeRevision": identity["bridgeRevision"],
            "transport": "ONE_DOCKER_EXEC_PROCESS_PER_CANDIDATE_AUTHENTICATED_REQUESTS",
            "results": results,
            "qualityStatus": "NOT_EVALUATED",
            "promotion": "NO_GO",
        },
    )
    print("PASS complete persistent observer profile; no accuracy or production claim")


if __name__ == "__main__":
    main()
