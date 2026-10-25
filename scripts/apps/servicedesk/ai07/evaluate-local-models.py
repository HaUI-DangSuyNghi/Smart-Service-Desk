"""Complete-denominator synthetic DEV resource experiment; never classification gold."""

import argparse
from collections import Counter
from datetime import datetime, timezone
from hashlib import sha256
import json
import math
import statistics
import subprocess
from threading import Event, Thread
from time import perf_counter

from _bootstrap import ROOT, write_new
from local_runtime_support import (
    WorkerRejected,
    deployment_evidence,
    gpu_identity,
    worker,
    rule_baseline,
)
from servicedesk.intelligence.triage_ai07.datasets import SOURCE_FILES, inventory
from local_classification.diagnostics import safe_telemetry


class GpuSampler:
    def __init__(self):
        self.stop = Event()
        self.samples = []
        self.errors = 0
        self.thread = Thread(target=self._sample, daemon=True)

    def _sample(self):
        while not self.stop.is_set():
            try:
                result = subprocess.run(
                    [
                        "nvidia-smi",
                        "--query-gpu=memory.used,utilization.gpu",
                        "--format=csv,noheader,nounits",
                    ],
                    capture_output=True,
                    check=True,
                    text=True,
                    timeout=3,
                )
                memory, utilization = (
                    int(value.strip())
                    for value in result.stdout.splitlines()[0].split(",")
                )
                self.samples.append((memory, utilization))
            except (ValueError, OSError, subprocess.SubprocessError):
                self.errors += 1
            self.stop.wait(0.25)


def latency(values):
    if not values:
        return {"count": 0, "p50Ms": None, "p95Ms": None}
    ordered = sorted(values)
    return {
        "count": len(values),
        "p50Ms": round(statistics.median(ordered), 3),
        "p95Ms": round(ordered[math.ceil(len(ordered) * 0.95) - 1], 3),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--split", choices=("DEV",), required=True)
    parser.add_argument("--candidate", required=True)
    parser.add_argument("--container", required=True)
    parser.add_argument("--allow-draft-synthetic", action="store_true")
    parser.add_argument("--warmups", type=int, default=10)
    parser.add_argument("--repeats", type=int, default=3)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    started_at = datetime.now(timezone.utc).isoformat()

    if not args.allow_draft_synthetic:
        raise ValueError(
            "Reviewed labels/rights missing; explicit synthetic architecture experiment required"
        )
    if not 0 <= args.warmups <= 10 or not 1 <= args.repeats <= 3:
        raise ValueError("Experiment resource limits exceeded")

    audit = inventory(ROOT)
    raw = (ROOT / SOURCE_FILES[1]).read_bytes()
    cases = []

    for line in raw.decode("utf-8").splitlines():
        item = json.loads(line)
        if item["split"] != "DEV":
            continue
        if item["dataKind"] != "SYNTHETIC" or item["reviewStatus"] != "DRAFT":
            raise ValueError("This experiment accepts only pinned synthetic draft DEV")
        cases.append(
            {
                "caseId": item["id"],
                "title": item["title"],
                "description": item["description"],
            }
        )

    baseline = rule_baseline(cases)
    deployment = deployment_evidence(args.container)
    identity = worker(args.container)
    candidates = (
        tuple(identity["candidates"]) if args.candidate == "all" else (args.candidate,)
    )
    if any(candidate not in identity["candidates"] for candidate in candidates):
        raise ValueError("Candidate is not bound to this runtime")
    receipts, warmups = [], []
    sampler = GpuSampler()
    sampler.thread.start()

    def attempt(candidate, case, repeat, phase):
        started = perf_counter()
        fields = {"title": case["title"], "description": case["description"]}
        receipt = {
            "candidate": candidate,
            "caseId": case["caseId"],
            "repeat": repeat,
            "phase": phase,
            "inputLengths": {key: len(text) for key, text in fields.items()},
            "authoritativeRuleDigest": baseline[case["caseId"]]["responseDigest"],
            "inputDigest": sha256(
                json.dumps(
                    fields, ensure_ascii=True, sort_keys=True, separators=(",", ":")
                ).encode()
            ).hexdigest(),
        }
        try:
            result = worker(args.container, candidate=candidate, fields=fields)
            if (
                result["runtimeDigest"] != identity["runtimeDigest"]
                or result["policyDigest"]
                != identity["candidates"][candidate]["policyDigest"]
                or result["operationalAuthority"] is not False
            ):
                raise ValueError("Inference identity changed")
            value = result["observation"]
            rule_result = baseline[case["caseId"]]["result"]
            receipt.update(
                {
                    "state": value["decision"],
                    "classId": value["class_id"],
                    "spanCount": len(value.get("spans", value.get("input_spans", []))),
                    "usage": result["usage"],
                    "placement": result["placement"],
                    "loadedBefore": result["loadedBefore"],
                    "nativeElapsedMs": result["nativeElapsedMs"],
                    "evidenceStatus": value.get(
                        "evidence_status", "LITERAL_INPUT_SUPPORTED"
                    ),
                    "scoreSemantics": value.get("score_semantics"),
                    "disagreesWithRules": value["class_id"] != rule_result["category"]
                    if value["class_id"] is not None and rule_result is not None
                    else None,
                }
            )
            receipt.update(safe_telemetry(result.get("nativeTelemetry")))
        except WorkerRejected as error:
            receipt["state"] = {504: "TIMEOUT", 503: "UNAVAILABLE", 502: "INVALID"}.get(
                error.status, "REJECTED"
            )
            receipt["reasonCode"] = error.reason_code
            receipt.update(error.telemetry)
        except subprocess.TimeoutExpired:
            receipt["state"] = "TIMEOUT"
            receipt["reasonCode"] = "TIMEOUT"
        except Exception:
            receipt["state"] = "REJECTED"
            receipt["reasonCode"] = "SCHEMA_MISMATCH"
        receipt["wallMs"] = round((perf_counter() - started) * 1000, 3)
        return receipt

    try:
        previous = None
        for repeat in range(args.repeats):
            order = candidates if repeat % 2 == 0 else tuple(reversed(candidates))
            for candidate in order:
                if previous != candidate:
                    for number in range(args.warmups):
                        warmups.append(attempt(candidate, cases[0], number, "WARMUP"))
                for case in cases:
                    receipts.append(attempt(candidate, case, repeat, "DEV_ENGINEERING"))
                previous = candidate
                print(
                    f"Completed {candidate} repeat {repeat + 1}: {len(cases)} DEV attempts",
                    flush=True,
                )
    finally:
        sampler.stop.set()
        sampler.thread.join(timeout=5)

    if inventory(ROOT) != audit:
        raise ValueError("Immutable sources changed during experiment")

    summaries = {}
    for candidate in candidates:
        attempts = [item for item in receipts if item["candidate"] == candidate]
        summaries[candidate] = {
            "attempts": len(attempts),
            "states": dict(Counter(item["state"] for item in attempts)),
            "allAttemptWallLatency": latency([item["wallMs"] for item in attempts]),
            "validatedNativeLatency": latency(
                [
                    item["nativeElapsedMs"]
                    for item in attempts
                    if "nativeElapsedMs" in item
                    and item["state"] in ("PROPOSE", "ABSTAIN")
                ]
            ),
            "macroF1": None,
            "calibratedAccuracy": None,
        }

    report = {
        "contractVersion": "local-model-engineering@0.2.0",
        "split": "DEV",
        "scope": "SYNTHETIC_DRAFT_NO_QUALITY_CLAIM",
        "runtimeIdentity": identity,
        "deployment": deployment,
        "sources": audit,
        "datasetDigest": sha256(raw).hexdigest(),
        "caseIds": [item["caseId"] for item in cases],
        "repeats": args.repeats,
        "warmupsPerLoad": args.warmups,
        "authoritativeRules": baseline,
        "ruleProfile": "service-desk@0.3.0",
        "startedAtUtc": started_at,
        "completedAtUtc": datetime.now(timezone.utc).isoformat(),
        "warmupAttempts": warmups,
        "attempts": receipts,
        "summaries": summaries,
        "gpu": gpu_identity(),
        "gpuSampling": {
            "sampleCount": len(sampler.samples),
            "errors": sampler.errors,
            "peakGlobalUsedMiB": max(
                (item[0] for item in sampler.samples), default=None
            ),
            "method": "HOST_NVIDIA_SMI_250MS_GLOBAL_NOT_PROCESS_ATTRIBUTED",
        },
        "cpuModel": "NOT_TRAINED_PENDING_REVIEWED_TRAIN_AND_RIGHTS",
        "promotion": "BLOCKED_PENDING_REVIEW",
        "operationalAuthority": False,
        "officialQualityClaim": False,
    }
    write_new(args.output, report)
    print(
        "PASS complete DEV attempt ledger written; supervised quality/promotion NOT_EVALUATED"
    )


if __name__ == "__main__":
    main()
