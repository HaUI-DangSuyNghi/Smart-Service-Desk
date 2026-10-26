"""Score frozen hierarchical observations without duplicating Java business policy."""

import base64
from collections import Counter
from hashlib import sha256
import hmac
import math
import time

from jsonschema import Draft202012Validator

from hdpi.classification.protocols import digest, require
from .dataset_profiles import bounded_json


def _metrics(pairs, labels):
    tp, fp, fn, support, confusion = (Counter() for _ in range(5))
    for actual, predicted in pairs:
        support[actual] += 1
        confusion[(actual, predicted)] += 1
        if actual == predicted and actual in labels:
            tp[actual] += 1
        else:
            if actual in labels:
                fn[actual] += 1
            if predicted in labels:
                fp[predicted] += 1
    per_class = {}
    for label in labels:
        precision = tp[label] / (tp[label] + fp[label]) if tp[label] + fp[label] else 0
        recall = tp[label] / (tp[label] + fn[label]) if tp[label] + fn[label] else 0
        per_class[label] = {
            "support": support[label],
            "precision": precision,
            "recall": recall,
            "f1": 2 * precision * recall / (precision + recall)
            if precision + recall
            else 0,
        }
    return {
        "macroF1": sum(value["f1"] for value in per_class.values()) / len(labels),
        "perClass": per_class,
        "confusion": [
            {"actual": actual, "predicted": predicted, "count": count}
            for (actual, predicted), count in sorted(
                confusion.items(), key=lambda item: str(item[0])
            )
        ],
    }


def evaluate_hierarchy(rows, predictions, *, schema, taxonomy, matrix):
    """Engineering report only. Artifacts are supplied from the authoritative resource set."""
    require(type(rows) is list and bool(rows) and len(rows) <= 10000)
    require(type(predictions) is list and len(predictions) == len(rows))
    references = {row["sampleId"]: row for row in rows}
    require(len(references) == len(rows))
    require(
        Counter(item["caseId"] for item in predictions) == Counter(references.keys())
    )
    parents = taxonomy["mainCategories"]
    children = {entry["id"]: entry["parent"] for entry in taxonomy["children"]}
    validator = Draft202012Validator(schema)
    pairs = {slot: [] for slot in ("mainCategory", "subCategory", "impact", "urgency")}
    assessment_contract = schema["properties"]["assessment"]["properties"]
    risk_labels = assessment_contract["risk"]["properties"]["flags"]["items"]["enum"]
    risk_pairs = {flag: [] for flag in risk_labels}
    invalid = accepted = parent_violations = child_accepted = child_eligible = 0
    abstentions = withheld = 0
    ambiguous = ambiguous_accepted = 0
    matrix_eligible = matrix_correct = priority_correct = 0
    evidence_total = evidence_valid = field_valid = degraded = 0
    unknown = {slot: [0, 0] for slot in ("impact", "urgency")}
    latencies = []
    for item in predictions:
        row = references[item["caseId"]]
        labels = row["labels"]
        require(
            type(row["fields"]) is dict
            and set(row["fields"]) == {"title", "description"}
        )
        require(
            all(
                type(value) is str and len(value) <= 8192
                for value in row["fields"].values()
            )
        )
        require(
            type(labels["needsClarification"]) is bool
            and type(labels["evidence"]) is list
        )
        for span in labels["evidence"]:
            source = row["fields"].get(span["field"])
            require(
                type(source) is str
                and type(span["start"]) is int
                and type(span["end"]) is int
            )
            require(
                0 <= span["start"] < span["end"] <= len(source)
                and source[span["start"] : span["end"]] == span["quote"]
            )
        require(labels["mainCategory"] is None or labels["mainCategory"] in parents)
        require(
            labels["subCategory"] is None
            or children.get(labels["subCategory"]) == labels["mainCategory"]
        )
        require(labels["impact"] in (None, "I1", "I2", "I3", "I4"))
        require(labels["urgency"] in (None, "U1", "U2", "U3", "U4"))
        require(
            type(labels["riskFlags"]) is list
            and set(labels["riskFlags"]) <= set(risk_pairs)
        )
        result = item.get("result")
        valid = type(result) is dict and not list(validator.iter_errors(result))
        invalid += not valid
        raw_classification = (
            result.get("classification") if type(result) is dict else None
        )
        raw_assessment = result.get("assessment") if type(result) is dict else None
        if type(raw_assessment) is dict:
            raw_impact, raw_urgency = (
                raw_assessment.get("impact"),
                raw_assessment.get("urgency"),
            )
            if (
                type(raw_impact) is str
                and type(raw_urgency) is str
                and raw_impact in matrix
                and raw_urgency in matrix[raw_impact]
            ):
                matrix_eligible += 1
                matrix_correct += matrix[raw_impact][raw_urgency] == raw_assessment.get(
                    "priority"
                )
        if (
            type(raw_classification) is dict
            and raw_classification.get("subCategory") is not None
        ):
            raw_child = raw_classification["subCategory"]
            parent_violations += type(raw_child) is not str or children.get(
                raw_child
            ) != raw_classification.get("mainCategory")
        classification = (result.get("classification") or {}) if valid else {}
        assessment = result["assessment"] if valid else {}
        predicted_main = classification.get("mainCategory")
        predicted_child = classification.get("subCategory")
        accepted += predicted_main is not None
        abstentions += valid and classification.get("decision") == "ABSTAIN"
        withheld += valid and result["mode"] == "SHADOW"
        child_accepted += predicted_child is not None
        child_eligible += labels["subCategory"] is not None
        ambiguous += labels["subCategory"] is None
        ambiguous_accepted += (
            labels["subCategory"] is None and predicted_child is not None
        )
        pairs["mainCategory"].append((labels["mainCategory"], predicted_main))
        pairs["subCategory"].append((labels["subCategory"], predicted_child))
        for slot in ("impact", "urgency"):
            predicted = assessment.get(slot)
            pairs[slot].append((labels[slot], predicted))
            if labels[slot] is None:
                unknown[slot][0] += 1
                unknown[slot][1] += predicted is None
        gold_priority = (
            matrix[labels["impact"]][labels["urgency"]]
            if labels["impact"] and labels["urgency"]
            else None
        )
        priority_correct += valid and gold_priority == assessment.get("priority")
        for flag in risk_pairs:
            risk_pairs[flag].append(
                (
                    flag in labels["riskFlags"],
                    flag in assessment.get("risk", {}).get("flags", []),
                )
            )
        if valid:
            degraded += result["status"] == "DEGRADED"
            # Validate attributes as well; a structurally valid offset can still quote invented text.
            spans = result["evidence"] + [
                attribute["evidence"] for attribute in result["attributes"]
            ]
            for span in spans:
                evidence_total += 1
                source = row["fields"].get(span["field"])
                field_valid += type(source) is str
                evidence_valid += type(source) is str and (
                    0 <= span["start"] < span["end"] <= len(source)
                    and source[span["start"] : span["end"]] == span["quote"]
                )
        latency = item.get("latencyMs")
        if latency is not None:
            require(
                type(latency) in (int, float)
                and math.isfinite(latency)
                and latency >= 0
            )
            latencies.append(latency)
    n = len(rows)
    scores = {
        "mainCategory": _metrics(pairs["mainCategory"], parents),
        "subCategory": _metrics(pairs["subCategory"], list(children)),
    }
    for slot in ("impact", "urgency"):
        axis_labels = [
            value for value in assessment_contract[slot]["enum"] if value is not None
        ]
        scores[slot] = _metrics(pairs[slot], axis_labels)
        total, correct = unknown[slot]
        scores[slot]["unknownSupport"] = total
        scores[slot]["unknownDetection"] = correct / total if total else None
    scores["mainCategory"].update(
        coverage=accepted / n,
        abstentionRate=abstentions / n,
        withheldRate=withheld / n,
        invalidRate=invalid / n,
    )
    scores["subCategory"]["macroF1Semantics"] = (
        "ALL_CONTROLLED_CHILDREN_WITH_ZERO_FOR_UNSUPPORTED_CLASSES"
    )
    scores["subCategory"].update(
        eligibleCases=child_eligible,
        coverage=child_accepted / n,
        eligibleExactMatch=sum(a == p for a, p in pairs["subCategory"] if a is not None)
        / child_eligible
        if child_eligible
        else None,
        parentChildViolationRate=parent_violations / n,
        ambiguityFalseAcceptance=ambiguous_accepted / ambiguous if ambiguous else None,
    )
    non_u4 = [(a, p) for a, p in pairs["urgency"] if a != "U4"]
    scores["urgency"]["u4FalsePositiveRate"] = (
        sum(p == "U4" for _, p in non_u4) / len(non_u4) if non_u4 else None
    )
    return {
        "purpose": "AI07_HIERARCHICAL_ENGINEERING_EVALUATION",
        "cases": n,
        "metrics": scores,
        "priority": {
            "matrixEligible": matrix_eligible,
            "matrixConsistency": matrix_correct / matrix_eligible
            if matrix_eligible
            else None,
            "endToEndExactMatch": priority_correct / n,
        },
        "risk": {
            "flags": {
                flag: _metrics(values, [True])["perClass"][True]
                for flag, values in risk_pairs.items()
            },
            "levelAccuracy": "NOT_AVAILABLE_PENDING_RUBRIC_APPROVAL",
        },
        "evidence": {
            "spans": evidence_total,
            "literalValidity": evidence_valid / evidence_total
            if evidence_total
            else None,
            "fieldValidity": field_valid / evidence_total if evidence_total else None,
            "semanticSupport": "REQUIRES_INDEPENDENT_REVIEW",
        },
        "operations": {
            "degradedRate": degraded / n,
            "latencySamples": len(latencies),
            **{
                f"p{percentile}Ms": sorted(latencies)[
                    min(
                        len(latencies) - 1,
                        math.ceil(percentile * len(latencies) / 100) - 1,
                    )
                ]
                if latencies
                else None
                for percentile in (50, 95, 99)
            },
            "gpuVram": "NOT_MEASURED",
            "coldStart": "NOT_MEASURED",
        },
        "calibration": "NOT_AVAILABLE",
        "selectedModel": None,
        "productionDecision": "NO_GO",
    }


def evaluate_reviewed_hierarchy(
    manifest_raw,
    rows_raw,
    predictions,
    *,
    trusted_key,
    schema,
    taxonomy,
    matrix,
    artifact_digests,
):
    """Authenticate a private review freeze; never infer Gold provenance from a filename."""
    require(type(trusted_key) is bytes and len(trusted_key) >= 32)
    require(
        set(artifact_digests)
        == {"taxonomyDigest", "assessmentPolicyDigest", "priorityPolicyDigest"}
    )
    require(all(digest(value) for value in artifact_digests.values()))
    envelope = bounded_json(manifest_raw, 32768)
    require(type(envelope) is dict and set(envelope) == {"payload", "signature"})
    payload = base64.b64decode(envelope["payload"], validate=True)
    require(
        hmac.compare_digest(
            hmac.new(trusted_key, payload, "sha256").hexdigest(), envelope["signature"]
        )
    )
    manifest = bounded_json(payload, 16384)
    require(
        set(manifest)
        == {
            "purpose",
            "status",
            "issuer",
            "expiresEpochSecond",
            "rowsDigest",
            "rightsReceipt",
            "reviewerReceipts",
            "adjudicationReceipt",
            "rubricDigest",
            "evaluationPlanDigest",
            "candidateModelDigest",
            "candidatePolicyDigest",
            "assessmentPolicyBundleDigest",
            "trainingFamilyDigests",
            "developmentFamilyDigests",
            "split",
        }
    )
    require(manifest["purpose"] == "AI07_HIERARCHICAL_REVIEWED_EVALUATION")
    require(
        manifest["status"] == "REVIEWED_AND_FROZEN"
        and manifest["split"] in ("DEV", "HOLDOUT")
    )
    require(type(manifest["issuer"]) is str and bool(manifest["issuer"].strip()))
    require(
        type(manifest["expiresEpochSecond"]) is int
        and manifest["expiresEpochSecond"] > time.time()
    )
    require(manifest["rowsDigest"] == sha256(rows_raw).hexdigest())
    for key in (
        "rightsReceipt",
        "adjudicationReceipt",
        "rubricDigest",
        "evaluationPlanDigest",
        "candidateModelDigest",
        "candidatePolicyDigest",
        "assessmentPolicyBundleDigest",
    ):
        require(digest(manifest[key]))
    receipts = manifest["reviewerReceipts"]
    require(
        type(receipts) is list
        and len(set(receipts)) >= 2
        and all(digest(value) for value in receipts)
    )
    rows = [bounded_json(line, 32768) for line in rows_raw.splitlines() if line.strip()]
    excluded = set()
    for key in ("trainingFamilyDigests", "developmentFamilyDigests"):
        require(
            type(manifest[key]) is list
            and all(digest(value) for value in manifest[key])
        )
        if key == "trainingFamilyDigests" or manifest["split"] == "HOLDOUT":
            excluded.update(manifest[key])
    for row in rows:
        require(digest(row["familyDigest"]) and row["familyDigest"] not in excluded)
        require(row["split"] == manifest["split"])
    for item in predictions:
        provenance = item.get("result", {}).get("provenance", {})
        require(provenance.get("modelDigest") == manifest["candidateModelDigest"])
        require(
            provenance.get("modelPolicyDigest") == manifest["candidatePolicyDigest"]
        )
        require(
            all(provenance.get(key) == value for key, value in artifact_digests.items())
        )
        bundle = ":".join(
            provenance.get(key, "")
            for key in (
                "taxonomyDigest",
                "assessmentPolicyDigest",
                "priorityPolicyDigest",
            )
        )
        require(
            sha256(bundle.encode()).hexdigest()
            == manifest["assessmentPolicyBundleDigest"]
        )
    report = evaluate_hierarchy(
        rows, predictions, schema=schema, taxonomy=taxonomy, matrix=matrix
    )
    report["reviewFreeze"] = {
        "manifestDigest": sha256(manifest_raw).hexdigest(),
        "split": manifest["split"],
        "independentFamilies": len({row["familyDigest"] for row in rows}),
    }
    return report
