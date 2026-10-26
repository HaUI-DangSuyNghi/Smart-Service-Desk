"""Official scoring is reachable only through an externally authenticated review freeze."""

from collections import Counter
from hashlib import sha256
import base64
import hmac
import random
import time

from hdpi.classification.protocols import digest, require
from .dataset_profiles import CLASSES, bounded_json


def _family_intervals(references, predictions):
    """Fixed-seed cluster bootstrap; repeated attempts never increase sample size."""
    families = sorted({row["familyDigest"] for row in references.values()})
    randomizer = random.Random(0)
    values = {key: [] for key in ("macroF1", "coverage", "wrongAcceptedPerAllCases")}
    for _ in range(1000):
        weights = Counter(randomizer.choices(families, k=len(families)))
        tp, fp, fn = Counter(), Counter(), Counter()
        total = accepted = unsafe = 0
        for item in predictions:
            row = references[item["caseId"]]
            weight = weights[row["familyDigest"]]
            total += weight
            if item["state"] == "PROPOSE":
                accepted += weight
                unsafe += weight * (
                    row["decision"] == "ABSTAIN" or row["classId"] != item["classId"]
                )
            if row["decision"] == "PROPOSE":
                if item["state"] == "PROPOSE" and item["classId"] == row["classId"]:
                    tp[row["classId"]] += weight
                else:
                    fn[row["classId"]] += weight
                    if item["state"] == "PROPOSE":
                        fp[item["classId"]] += weight
        score = sum(
            2 * tp[label] / (2 * tp[label] + fp[label] + fn[label])
            if 2 * tp[label] + fp[label] + fn[label]
            else 0
            for label in CLASSES
        ) / len(CLASSES)
        values["macroF1"].append(score)
        values["coverage"].append(accepted / total)
        values["wrongAcceptedPerAllCases"].append(unsafe / total)
    return {
        "method": "FAMILY_CLUSTER_BOOTSTRAP_1000_SEED0_PERCENTILE95",
        "independentFamilies": len(families),
        "intervals": {
            key: [sorted(items)[24], sorted(items)[974]]
            for key, items in values.items()
        },
    }


def verify_review_manifest(raw, rows_raw, *, trusted_key):
    require(type(trusted_key) is bytes and len(trusted_key) >= 32)
    envelope = bounded_json(raw, 32768)
    require(type(envelope) is dict and set(envelope) == {"payload", "signature"})
    require(type(envelope["payload"]) is str and type(envelope["signature"]) is str)
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
            "rubricDigest",
            "labelObjective",
            "trainingFamilyDigests",
            "split",
            "evaluationPlanDigest",
            "candidateModelDigest",
            "candidatePolicyDigest",
        }
    )
    require(manifest["purpose"] == "AI07_REVIEWED_CLASSIFICATION_EVALUATION")
    require(manifest["status"] == "REVIEWED_AND_FROZEN" and manifest["split"] == "DEV")
    require(type(manifest["issuer"]) is str and bool(manifest["issuer"].strip()))
    require(
        type(manifest["expiresEpochSecond"]) is int
        and manifest["expiresEpochSecond"] > time.time()
    )
    require(manifest["rowsDigest"] == sha256(rows_raw).hexdigest())
    require(
        all(
            digest(manifest[key])
            for key in (
                "rightsReceipt",
                "rubricDigest",
                "evaluationPlanDigest",
                "candidateModelDigest",
                "candidatePolicyDigest",
            )
        )
    )
    receipts = manifest["reviewerReceipts"]
    require(
        type(receipts) is list
        and len(receipts) >= 2
        and len(set(receipts)) == len(receipts)
    )
    require(all(digest(value) for value in receipts))
    require(manifest["labelObjective"] == "initial_receiving_team_by_observed_symptom")
    require(
        type(manifest["trainingFamilyDigests"]) is list
        and all(digest(value) for value in manifest["trainingFamilyDigests"])
    )
    return manifest


def evaluate_reviewed(manifest_raw, rows_raw, predictions_raw, *, trusted_key):
    manifest = verify_review_manifest(manifest_raw, rows_raw, trusted_key=trusted_key)
    rows = [bounded_json(line, 8192) for line in rows_raw.splitlines() if line.strip()]
    predictions = [
        bounded_json(line, 8192)
        for line in predictions_raw.splitlines()
        if line.strip()
    ]
    require(rows and len({row["sampleId"] for row in rows}) == len(rows))
    require(
        Counter(row["sampleId"] for row in rows)
        == Counter(row["caseId"] for row in predictions)
    )
    references = {}
    families = {label: set() for label in CLASSES}
    for row in rows:
        require(
            set(row)
            == {"sampleId", "familyDigest", "inputDigest", "decision", "classId"}
        )
        require(digest(row["familyDigest"]) and digest(row["inputDigest"]))
        require(row["familyDigest"] not in manifest["trainingFamilyDigests"])
        require(
            row["decision"] == "ABSTAIN"
            and row["classId"] is None
            or row["decision"] == "PROPOSE"
            and row["classId"] in CLASSES
        )
        if row["decision"] == "PROPOSE":
            families[row["classId"]].add(row["familyDigest"])
        references[row["sampleId"]] = row
    require(all(len(values) >= 2 for values in families.values()))
    confusion = {label: {predicted: 0 for predicted in CLASSES} for label in CLASSES}
    missed = Counter()
    accepted = correct = unsafe = 0
    states = Counter()
    for item in predictions:
        require(
            set(item)
            == {
                "caseId",
                "inputDigest",
                "state",
                "classId",
                "modelDigest",
                "policyDigest",
            }
        )
        require(
            item["modelDigest"] == manifest["candidateModelDigest"]
            and item["policyDigest"] == manifest["candidatePolicyDigest"]
        )
        reference = references[item["caseId"]]
        require(item["inputDigest"] == reference["inputDigest"])
        require(
            item["state"]
            in ("PROPOSE", "ABSTAIN", "INVALID", "TIMEOUT", "UNAVAILABLE", "REJECTED")
        )
        states[item["state"]] += 1
        if item["state"] == "PROPOSE":
            require(item["classId"] in CLASSES)
            accepted += 1
            is_correct = (
                reference["decision"] == "PROPOSE"
                and item["classId"] == reference["classId"]
            )
            correct += is_correct
            unsafe += not is_correct
            if reference["decision"] == "PROPOSE":
                confusion[reference["classId"]][item["classId"]] += 1
        else:
            require(item["classId"] is None)
            if reference["decision"] == "PROPOSE":
                missed[reference["classId"]] += 1
    per_class = {}
    for label in CLASSES:
        true_positive = confusion[label][label]
        false_negative = sum(confusion[label].values()) - true_positive + missed[label]
        false_positive = sum(
            confusion[actual][label] for actual in CLASSES if actual != label
        )
        precision = (
            true_positive / (true_positive + false_positive)
            if true_positive + false_positive
            else 0
        )
        recall = (
            true_positive / (true_positive + false_negative)
            if true_positive + false_negative
            else 0
        )
        per_class[label] = {
            "precision": precision,
            "recall": recall,
            "f1": 2 * precision * recall / (precision + recall)
            if precision + recall
            else 0,
            "independentFamilies": len(families[label]),
        }
    return {
        "qualityStatus": "REVIEWED_GOLD_EVALUATED",
        "uniqueCases": len(rows),
        "macroF1": sum(value["f1"] for value in per_class.values()) / len(CLASSES),
        "macroF1Scope": "FIVE_CLASS_LABELLED_GOLD_WITH_NONDECISIONS_AS_FALSE_NEGATIVES",
        "perClass": per_class,
        "confusionBusinessClasses": confusion,
        "nonDecisionByGoldClass": dict(missed),
        "states": dict(states),
        "coverage": accepted / len(rows),
        "selectiveAccuracy": correct / accepted if accepted else None,
        "wrongAcceptedPerAllCases": unsafe / len(rows),
        "uncertainty": _family_intervals(references, predictions),
        "officialQualityClaim": True,
        "evaluationPlanDigest": manifest["evaluationPlanDigest"],
        "modelDigest": manifest["candidateModelDigest"],
        "policyDigest": manifest["candidatePolicyDigest"],
        "promotion": "NO_GO",
        "selectedModel": None,
        "holdout": "NOT_OPENED_BY_THIS_DEV_SCORER",
    }
