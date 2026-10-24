"""AI06-03 synthetic DEV evidence; official model/quality evaluation stays blocked."""

import argparse
from dataclasses import asdict
from hashlib import sha256
import json
from pathlib import Path
import subprocess
import sys
from uuid import UUID


ROOT = Path(__file__).resolve().parents[2]


def current_identity(dataset, config, package):
    bundled_contracts = package / "_resources/contracts"
    roots = [
        package,
        ROOT / "intelligence/src/apps/servicedesk/intelligence/profiles",
        (
            bundled_contracts
            if bundled_contracts.is_dir()
            else ROOT / "contracts/intelligence/v1"
        ),
    ]
    state = []

    for index, root in enumerate(roots):
        for path in sorted(
            root.rglob("*"), key=lambda item: item.relative_to(root).as_posix()
        ):
            relative = path.relative_to(root).as_posix()

            if relative.startswith("_resources/"):
                continue

            if path.is_file() and path.suffix in {".py", ".yaml", ".json", ".md"}:
                state.append([index, relative, sha256(path.read_bytes()).hexdigest()])

    encoded = json.dumps(
        state, ensure_ascii=False, sort_keys=True, separators=(",", ":")
    )

    return {
        "datasetDigest": sha256(dataset.read_bytes()).hexdigest(),
        "configurationDigest": sha256(config.read_bytes()).hexdigest(),
        "sourceRevision": subprocess.check_output(
            ["git", "rev-parse", "HEAD"], cwd=ROOT, text=True
        ).strip(),
        "implementationDigest": sha256(encoded.encode("utf-8")).hexdigest(),
        "runnerDigest": sha256(Path(__file__).read_bytes()).hexdigest(),
    }


def build_report(dataset, config):
    from hdpi.assistance_http import reference_runtime
    from hdpi.evaluation.generation import evaluate_generation
    from hdpi.profiles.registry import ProfileRegistry

    raw = dataset.read_bytes()
    document = json.loads(raw)

    if (
        document["evidenceClass"] != "SYNTHETIC_TECHNICAL_EVIDENCE"
        or document["rightsDecision"] != "PENDING"
        or document["holdoutState"] != "NOT_LOCKED"
    ):
        raise ValueError("Unexpected provisional dataset state")

    runtime = reference_runtime(
        config,
        sha256(config.read_bytes()).hexdigest(),
        profiles=ProfileRegistry(
            ROOT / "intelligence/src/apps/servicedesk/intelligence/profiles"
        ),
    )
    retrieval_digests = {}
    retrieval_pins = {}

    def execute(case):
        response = runtime.assist(
            {
                "contractVersion": "0.1.0",
                "requestId": str(UUID(int=int(case["caseId"].rsplit("-", 1)[1]))),
                "workspaceId": "synthetic-evaluation",
                "profileId": runtime.profile_id,
                "profileVersion": runtime.profile_version,
                "policyIdentity": runtime.policy.identity,
                "question": case["question"],
                "documents": case["documents"],
            }
        )

        identity = response["retrievalRuntime"]
        encoded = json.dumps(identity, sort_keys=True, separators=(",", ":"))
        retrieval_digests[case["caseId"]] = sha256(encoded.encode("utf-8")).hexdigest()
        retrieval_pins.update(
            {
                key: identity[key]
                for key in ("engine", "engine_version", "config_version")
            }
        )
        return response

    report = evaluate_generation(document["cases"], execute)
    report["generationRuntime"] = asdict(runtime.generation.runtime)
    report["retrievalRuntimePins"] = {
        **retrieval_pins,
        "profileId": runtime.profile_id,
        "profileVersion": runtime.profile_version,
    }
    report["retrievalIdentityDigests"] = retrieval_digests
    # HEAD alone cannot identify uncommitted implementation changes. Bind the
    # actual code/profile/contract bytes as well, including the installed wheel.
    import hdpi

    package = Path(hdpi.__file__).resolve().parent
    report.update(current_identity(dataset, config, package))
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--installed", action="store_true")
    parser.add_argument(
        "--mode", choices=("synthetic", "official"), default="synthetic"
    )
    parser.add_argument(
        "--dataset",
        type=Path,
        default=ROOT / "fixtures/intelligence/service-desk/generation/dev-v0.1.json",
    )
    parser.add_argument(
        "--config",
        type=Path,
        default=ROOT
        / "intelligence/src/apps/servicedesk/intelligence/assistance-reference.json",
    )
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    if args.mode == "official":
        report = {
            "officialDecision": "BLOCKED",
            "reasons": [
                "MODEL_NOT_APPROVED",
                "HUMAN_REVIEW_PENDING",
                "RIGHTS_PENDING",
                "HOLDOUT_NOT_LOCKED",
                "THRESHOLDS_NOT_APPROVED",
            ],
        }
        code = 2
    else:
        if not args.installed:
            sys.path.insert(0, str(ROOT / "intelligence/src"))

        report = build_report(args.dataset, args.config)
        code = int(report["technicalFailures"] > 0)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    print(
        json.dumps(
            {
                key: report[key]
                for key in ("officialDecision", "technicalFailures", "caseCount")
                if key in report
            }
        )
    )
    return code


if __name__ == "__main__":
    raise SystemExit(main())
