"""Opt-in, approved synthetic direct-Python cloud smoke; never a release gate."""

import argparse
from dataclasses import asdict
from hashlib import sha256
import importlib.util
import json
import os
from pathlib import Path
import subprocess
from time import perf_counter
from uuid import uuid4


ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--real-api", action="store_true")
    parser.add_argument("--installed", action="store_true")
    parser.add_argument("--cases", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    report = {
        "evidenceClass": "DIRECT_PYTHON_SYNTHETIC_CLOUD_SMOKE",
        "sourceRevision": subprocess.check_output(
            ["git", "rev-parse", "HEAD"], cwd=ROOT, text=True
        ).strip(),
        "workingTreeDirty": bool(
            subprocess.check_output(
                ["git", "status", "--porcelain"], cwd=ROOT, text=True
            ).strip()
        ),
        "result": "BLOCKED",
        "reason": "REAL_API_OPT_IN_AND_OPERATOR_CONFIGURATION_REQUIRED",
        "fullCoreJavaKeycloakApisixBrowser": "NOT_VERIFIED",
        "productionDecision": "BLOCKED",
        "providers": [],
    }

    try:
        if not args.real_api or args.cases is None:
            return write_report(args.output, report, 2)

        cases_file = args.cases.resolve(strict=True)

        if cases_file.is_relative_to(ROOT) or cases_file.stat().st_size > 131072:
            raise ValueError()

        spec = importlib.util.spec_from_file_location(
            "cloud_composition", Path(__file__).with_name("serve-cloud-assistance.py")
        )
        composition = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(composition)
        runtime = composition.build_runtime(installed=args.installed)

        from cloud_providers.contracts import decode_json

        cases = decode_json(cases_file.read_bytes())

        if not isinstance(cases, list) or len(cases) != 2:
            raise ValueError()

        if {case["expectedStatus"] for case in cases} != {
            "SUCCESS",
            "INSUFFICIENT_EVIDENCE",
        }:
            raise ValueError()

        registry = runtime.registry
        report["catalogDigest"] = registry.catalog_digest
        report["approvalDigest"] = registry.approval_digest
        report["casesDigest"] = sha256(cases_file.read_bytes()).hexdigest()
        records = []

        for route in sorted(registry.routes.values(), key=lambda item: item.priority):
            events = []
            runtime.observer = events.append

            for case in cases:
                if set(case) != {"question", "documents", "expectedStatus"}:
                    raise ValueError()

                payload = {
                    "contractVersion": "0.2.0",
                    "requestId": str(uuid4()),
                    "workspaceId": "isolated-direct-smoke",
                    "profileId": registry.catalog["profileId"],
                    "profileVersion": registry.catalog["profileVersion"],
                    "policyIdentity": registry.policy.identity,
                    "question": case["question"],
                    "documents": case["documents"],
                    "routeBinding": {
                        "routeId": route.id,
                        "policyId": registry.catalog["policyId"],
                        "dataClass": "synthetic-public-evaluation",
                        "catalogDigest": registry.catalog_digest,
                        "routeDigest": route.digest,
                        "approvalDigest": registry.approval_digest,
                        "attemptId": str(uuid4()),
                        "expectedRuntime": asdict(route.runtime),
                    },
                }
                started = perf_counter()
                response = runtime.assist(payload)
                elapsed = round((perf_counter() - started) * 1000)
                feedback = response["routeFeedback"]
                event = events[-1]
                verified = (
                    response["status"] == case["expectedStatus"]
                    and feedback["state"] == "READY"
                    and feedback["providerCalls"] == 1
                    and event["observedModelDigest"] is not None
                )
                records.append(
                    {
                        "provider": route.vendor,
                        "routeDigest": route.digest,
                        "model": route.model,
                        "expectedStatus": case["expectedStatus"],
                        "observedStatus": response["status"],
                        "reasonCode": response["reasonCode"],
                        "verified": verified,
                        "latencyMs": elapsed,
                        "metrics": event,
                    }
                )
                report["providers"] = records

        report["result"] = (
            "PASS"
            if len(records) == 4 and all(item["verified"] for item in records)
            else "FAIL"
        )
        report["reason"] = "DIRECT_SMOKE_ONLY_FULL_ACCEPTANCE_STILL_REQUIRED"
        return write_report(args.output, report, 0 if report["result"] == "PASS" else 1)
    except Exception:
        # Never serialize exception strings, paths, prompts or provider bodies.
        report["reason"] = "INVALID_UNAPPROVED_OR_UNAVAILABLE_CLOUD_CONFIGURATION"
        return write_report(args.output, report, 2)


def write_report(path, report, code):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(report, indent=2, ensure_ascii=True) + "\n", encoding="utf-8"
    )
    print(report["result"] + " [" + report["reason"] + "]")
    return code


if __name__ == "__main__":
    raise SystemExit(main())
