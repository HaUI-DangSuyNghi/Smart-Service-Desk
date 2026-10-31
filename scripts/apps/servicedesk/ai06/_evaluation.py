"""App composition over public hdpi evaluation/runtime contracts."""

from collections import defaultdict
from dataclasses import asdict
from pathlib import Path
import subprocess
from uuid import UUID

from _contracts import ROOT, canonical, digest, read_json, require, tooling_pins
from _validation import validate_bundle


CONFIG = (
    ROOT / "intelligence/src/apps/servicedesk/intelligence/assistance-reference.json"
)
PROFILES = ROOT / "intelligence/src/apps/servicedesk/intelligence/profiles"


def implementation_identity():
    import hdpi

    package = Path(hdpi.__file__).resolve().parent
    contracts = package / "_resources/contracts"

    if not contracts.is_dir():
        contracts = ROOT / "contracts/intelligence/v1"

    state = []

    for group, directory in enumerate((package, contracts, PROFILES)):
        for path in sorted(directory.rglob("*")):
            relative = path.relative_to(directory).as_posix()

            if (
                path.is_file()
                and path.suffix in {".py", ".json", ".yaml", ".md"}
                and not relative.startswith("_resources/")
            ):
                state.append([group, relative, digest(path.read_bytes())])

    return {
        "sourceRevision": subprocess.check_output(
            ["git", "rev-parse", "HEAD"], cwd=ROOT, text=True
        ).strip(),
        "implementationDigest": digest(state),
        "configurationDigest": digest(CONFIG.read_bytes()),
        "toolingDigest": digest(tooling_pins()),
    }


def aggregate_by(results, metadata, field):
    groups = defaultdict(list)

    for result in results:
        key = (
            result["category"]
            if field == "category"
            else metadata[result["caseId"]][field]
        )
        groups[key].append(result)

    return {
        key: {
            "caseCount": len(rows),
            "technicalFailures": sum(not row["technicalPass"] for row in rows),
            "provisionalBehaviorMismatches": sum(
                not row["provisionalBehaviorMatch"] for row in rows
            ),
            "unsafeSourceSelections": sum(row["unsafeExactSelection"] for row in rows),
        }
        for key, rows in sorted(groups.items())
    }


def evaluate_cases(bundle, execute):
    from hdpi.evaluation.generation import evaluate_generation

    metadata = {item["caseId"]: item for item in bundle["curation"]["cases"]}
    observations = {}

    def observed_runner(case):
        response = execute(case)
        selected = {item["metadataId"] for item in response["citations"]}
        detail = metadata[case["caseId"]]
        required = set(detail["oracleGraph"]["supportingSourceIds"])
        observations[case["caseId"]] = {
            "symbolicOracleSupportMatch": (
                (required <= selected)
                if detail["oracleType"] == "symbolic_fact" and required
                else None
            ),
            "unsupportedSelection": (
                bool(selected - required)
                if detail["oracleType"] == "symbolic_fact" and required
                else None
            ),
        }
        return response

    report = evaluate_generation(bundle["dataset"]["cases"], observed_runner)

    for row in report["results"]:
        row.update(
            observations.get(
                row["caseId"],
                {"symbolicOracleSupportMatch": None, "unsupportedSelection": None},
            )
        )

    report.update(
        {
            "datasetRevision": "0.2.0",
            "datasetDigest": digest(bundle["dataset"]),
            "manifestDigest": digest(bundle["manifest"]),
            "curationManifestDigest": digest(bundle["curation"]),
            "comparisonStatus": "PROVISIONAL_NOT_REVIEWED_GOLD",
            "safetyDecision": (
                "FAIL"
                if report["unsafeExactSelections"]
                else "NO_DECLARED_UNSAFE_SELECTION_OBSERVED"
            ),
            "unsupportedSelections": sum(
                row["unsupportedSelection"] is True for row in report["results"]
            ),
            "symbolicSupportMismatches": sum(
                row["symbolicOracleSupportMatch"] is False for row in report["results"]
            ),
            "canaryEgressObserved": "NOT_MEASURED_OFFLINE",
            "byCategory": aggregate_by(report["results"], metadata, "category"),
            "byFamily": aggregate_by(report["results"], metadata, "familyId"),
            "byLanguage": aggregate_by(report["results"], metadata, "language"),
            "byRisk": aggregate_by(report["results"], metadata, "risk"),
            "realProviderRequests": 0,
            "providerUsageAndLatency": "NOT_MEASURED_OFFLINE_REFERENCE",
        }
    )
    return report


def build_report(bundle, paths=None):
    from hdpi.assistance_http import reference_runtime
    from hdpi.profiles.registry import ProfileRegistry

    quality = validate_bundle(bundle, paths)
    runtime = reference_runtime(
        CONFIG, digest(CONFIG.read_bytes()), profiles=ProfileRegistry(PROFILES)
    )
    expected_identity = asdict(runtime.generation.runtime)
    retrieval = {}

    def execute(case):
        response = runtime.assist(
            {
                "contractVersion": "0.1.0",
                "requestId": str(UUID(int=int(case["caseId"].rsplit("-", 1)[1]))),
                "workspaceId": "synthetic-v02-evaluation",
                "profileId": runtime.profile_id,
                "profileVersion": runtime.profile_version,
                "policyIdentity": runtime.policy.identity,
                "question": case["question"],
                "documents": case["documents"],
            }
        )
        require(
            response["generationRuntime"] == expected_identity, "RUNTIME_IDENTITY_DRIFT"
        )
        retrieval[case["caseId"]] = digest(response["retrievalRuntime"])
        return response

    report = evaluate_cases(bundle, execute)
    report.update(implementation_identity())
    report["generationRuntime"] = expected_identity
    report["retrievalIdentityDigests"] = retrieval
    report["dataQuality"] = quality
    return report


def verify_reports(paths, bundle, inputs=None):
    expected = build_report(bundle, inputs)

    for path in paths:
        observed = read_json(path)
        require(
            type(observed.get("technicalFailures")) is int
            and observed["technicalFailures"] == 0,
            "INVALID_TECHNICAL_COUNTER",
        )
        require(canonical(observed) == canonical(expected), "STALE_OR_FORGED_EVIDENCE")

    return expected
