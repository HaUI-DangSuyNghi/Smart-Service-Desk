"""Content-free technical evaluation. Reviewed semantic quality is a separate gate."""

from collections import Counter
from hashlib import sha256
import json

from ..generation.errors import GenerationContractError
from ..contracts.loader import load_validator
from ..contracts.validator import ContractValidator


def fingerprint(value):
    return sha256(
        json.dumps(
            value,
            ensure_ascii=False,
            sort_keys=True,
            separators=(",", ":"),
            allow_nan=False,
        ).encode("utf-8")
    ).hexdigest()


def evaluate_generation(cases, execute):
    """Execute an injected application runner; never persist questions or answers.

    Case expectations are provisional controls, never human review receipts.
    Any exception is an explicit technical failure, without its message.
    """
    results = []
    identities = set()
    contract = load_validator("assistance-result.schema.json")

    if not cases or len({case["caseId"] for case in cases}) != len(cases):
        raise ValueError("Cases must be nonempty and uniquely identified")

    for case in cases:
        if case["split"] != "DEV" or case["reviewDecision"] != "PENDING":
            raise ValueError("This runner accepts provisional DEV controls only")

        try:
            response = execute(case)
            ContractValidator._validate(contract, response)
            status = response["status"]
            citations = response["citations"]
            answer = response["answer"]
            documents = {item["metadataId"]: item for item in case["documents"]}
            valid = response["needsReview"] is True
            selected = set()

            if status == "SUCCESS":
                valid = valid and bool(citations)

                for citation in citations:
                    source = documents.get(citation["metadataId"])
                    valid = valid and source is not None

                    if source is not None:
                        valid = valid and (
                            citation["sourceVersion"] == source["version"]
                            and citation["sourceChecksum"] == source["checksum"]
                            and sha256(source["content"].encode()).hexdigest()
                            == source["checksum"]
                            and citation["excerpt"] in source["content"]
                        )

                    selected.add(citation["metadataId"])

                valid = valid and answer == "\n".join(
                    f'{item["excerpt"]} [{item["citationId"]}]' for item in citations
                )
                aliases = [item["citationId"] for item in citations]
                valid = valid and len(set(aliases)) == len(aliases)
            else:
                valid = valid and status in {"INSUFFICIENT_EVIDENCE", "DEGRADED"}
                valid = valid and answer is None and citations == []

            if not valid:
                raise GenerationContractError("Invalid technical result")

            identities.add(fingerprint(response["generationRuntime"]))
            semantic_match = status == case["provisionalExpectedStatus"]
            unsafe_selection = bool(selected.intersection(case["unsafeSourceIds"]))
            results.append(
                {
                    "caseId": case["caseId"],
                    "category": case["category"],
                    "technicalPass": True,
                    "status": status,
                    "citationCount": len(citations),
                    "provisionalBehaviorMatch": semantic_match,
                    "unsafeExactSelection": unsafe_selection,
                    "humanReview": "PENDING",
                }
            )
        except Exception:
            results.append(
                {
                    "caseId": case["caseId"],
                    "category": case["category"],
                    "technicalPass": False,
                    "status": "ERROR",
                    "citationCount": 0,
                    "provisionalBehaviorMatch": False,
                    "unsafeExactSelection": False,
                    "humanReview": "PENDING",
                }
            )

    return {
        "schemaVersion": "0.1.0",
        "evidenceClass": "SYNTHETIC_TECHNICAL_EVIDENCE",
        "officialDecision": "BLOCKED",
        "realModelValidated": False,
        "caseCount": len(results),
        "technicalFailures": sum(not item["technicalPass"] for item in results),
        "provisionalBehaviorMismatches": sum(
            not item["provisionalBehaviorMatch"] for item in results
        ),
        "unsafeExactSelections": sum(item["unsafeExactSelection"] for item in results),
        "pendingHumanReviews": len(results),
        "categories": dict(
            sorted(Counter(item["category"] for item in results).items())
        ),
        "generationIdentityDigests": sorted(identities),
        "results": results,
    }
