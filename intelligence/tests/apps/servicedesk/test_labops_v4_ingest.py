"""Owned synthetic package controls. Private LabOps text is never a CI fixture."""

from hashlib import sha256
import json
import zipfile

import pytest

from servicedesk.intelligence.triage_ai07.dataset_profiles import (
    CLASSES,
    ROW_KEYS,
    SPLITS,
    validate_dataset,
    import_dataset,
)


def write_package(root, *, mutate=None):
    root.mkdir(parents=True, exist_ok=True)
    records = {}
    for index, split in enumerate(SPLITS):
        row = {key: "synthetic" for key in ROW_KEYS}
        row.update(
            id=f"OWNED-{split}",
            split=SPLITS[split][1],
            family_id=f"family-{index}",
            allowed_classes=list(CLASSES),
            class_id="NETWORK",
            decision="PROPOSE",
            title=f"Owned sample {index}",
            description=f"Unique content {index}",
            evidence={
                "field": "title",
                "start": 0,
                "end": 5,
                "text": "Owned",
                "rule_id": "FIXTURE",
            },
            label_status="MACHINE_SILVER_UNREVIEWED",
            data_rights="PENDING_OWNER_CONFIRMATION",
            gold_approved=False,
            human_reviewed=False,
            official_quality_eligible=False,
            raw_record_sha256="a" * 64,
            flags=[],
            rule_ids=[],
        )
        records[split] = [row]
    if mutate:
        mutate(records)
    files = {}
    for split, rows in records.items():
        filename = "data/" + SPLITS[split][0]
        files[filename] = b"".join((json.dumps(row) + "\n").encode() for row in rows)
    for split in ("TRAIN", "DEV", "CHALLENGE"):
        files[f"data/model_input_{split.lower()}_NO_LABELS.jsonl"] = b"".join(
            (
                json.dumps({key: row[key] for key in ("id", "title", "description")})
                + "\n"
            ).encode()
            for row in records[split]
        )
    files["meta/legacy_holdout_QUARANTINED_INVENTORY_ONLY.json"] = json.dumps(
        {
            "count": 40,
            "status": "QUARANTINED_LEGACY_EXPOSED_UNSAFE_FOR_FINAL_HOLDOUT",
        }
    ).encode()
    pins = []
    for relative, raw in files.items():
        target = root / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(raw)
        pins.append(
            {"path": relative, "size": len(raw), "sha256": sha256(raw).hexdigest()}
        )
    manifest = json.dumps(
        {"contract": "ai07-dataset-integrity@1.0.0", "files": pins}
    ).encode()
    (root / "meta/file_integrity_manifest.json").write_bytes(manifest)
    return {
        "identity": "OWNED_TEST_PACKAGE",
        "manifestDigest": sha256(manifest).hexdigest(),
        "counts": {split: len(rows) for split, rows in records.items()},
        "files": pins,
    }


def test_pinned_engineering_package_never_exports_labels_to_model(tmp_path):
    profile = write_package(tmp_path)
    dataset = validate_dataset(tmp_path, profile=profile)
    assert dataset.summary["total"] == 4
    assert dataset.summary["gold"] == "NOT_APPROVED"
    for case in dataset.cases:
        assert set(case.model_fields()) == {"title", "description"}
        assert "Owned sample" not in repr(case)


@pytest.mark.parametrize(
    "mutation",
    [
        "gold",
        "human",
        "quality",
        "unknown_class",
        "abstain_pair",
        "extra",
        "evidence",
        "duplicate",
        "family",
        "text_leak",
        "unknown_split",
        "bool_offset",
    ],
)
def test_semantic_forgeries_fail_even_with_rehashed_fixture(tmp_path, mutation):
    def change(rows):
        row = rows["DEV"][0]
        if mutation in ("gold", "human", "quality"):
            row[
                {
                    "gold": "gold_approved",
                    "human": "human_reviewed",
                    "quality": "official_quality_eligible",
                }[mutation]
            ] = True
        elif mutation == "unknown_class":
            row["class_id"] = "FOREIGN"
        elif mutation == "abstain_pair":
            row["decision"] = "ABSTAIN"
        elif mutation == "extra":
            row["approved_by_assistant"] = True
        elif mutation == "evidence":
            row["evidence"]["text"] = "fabricated"
        elif mutation == "duplicate":
            row["id"] = rows["TRAIN"][0]["id"]
        elif mutation == "family":
            row["family_id"] = rows["TRAIN"][0]["family_id"]
        elif mutation == "text_leak":
            row["title"] = rows["TRAIN"][0]["title"]
            row["description"] = rows["TRAIN"][0]["description"]
        elif mutation == "unknown_split":
            row["split"] = "HOLDOUT"
        elif mutation == "bool_offset":
            row["evidence"]["start"] = False

    profile = write_package(tmp_path, mutate=change)
    with pytest.raises(ValueError):
        validate_dataset(tmp_path, profile=profile)


@pytest.mark.parametrize("mutation", [None, "identity", "runtime", "missing"])
def test_native_report_requires_exact_trusted_runtime_binding(tmp_path, mutation):
    from servicedesk.intelligence.triage_ai07.labops_evaluation import (
        report_for,
        verify_report,
    )

    dataset = validate_dataset(tmp_path, profile=write_package(tmp_path))
    case = dataset.partition("DEV")[0]
    runtime = {
        "mode": "ENGINEERING_LAB_ONLY",
        "engineeringDatasetDigest": dataset.dataset_digest,
        "candidates": {
            "fixture": {"modelArtifactDigest": "a" * 64, "policyDigest": "b" * 64}
        },
        "bridgeRevision": "c" * 64,
        "ollamaVersion": "0.40.8",
    }
    raw = json.dumps(runtime).encode()
    identity = {
        **runtime["candidates"]["fixture"],
        "bridgeRevision": runtime["bridgeRevision"],
        "providerVersion": "0.40.8",
        "runtimeDigest": sha256(raw).hexdigest(),
    }
    receipt = {
        "caseId": case.case_id,
        "inputDigest": case.input_digest,
        "repeatIndex": 0,
        "state": "ABSTAIN",
        "proposedClassOrNull": None,
        "reasonCodeOrNull": None,
        "wallLatencyMs": 1.0,
        "nativeLatencyMsOrNull": None,
        "goldEligible": False,
    }
    report = report_for(
        dataset,
        split="DEV",
        candidate_id="fixture",
        attempts=[receipt],
        repeats=1,
        identity=identity,
    )
    if mutation == "identity":
        report["identity"]["modelArtifactDigest"] = "f" * 64
    elif mutation == "runtime":
        raw += b"\n"
    elif mutation == "missing":
        raw = None
    if mutation:
        with pytest.raises(ValueError):
            verify_report(dataset, report, runtime_raw=raw)
    else:
        assert verify_report(dataset, report, runtime_raw=raw)["production"] == "NO_GO"


def test_source_tampering_is_detected_before_rows_are_interpreted(tmp_path):
    profile = write_package(tmp_path)
    (tmp_path / "data" / SPLITS["DEV"][0]).write_bytes(b"PRIVATE MALFORMED BYTES")
    with pytest.raises(ValueError) as error:
        validate_dataset(tmp_path, profile=profile)
    assert "PRIVATE" not in str(error.value)


@pytest.mark.parametrize(
    "mutation", [None, "missing", "duplicate", "gold", "summary", "input"]
)
def test_labops_report_verifier_recomputes_denominator_and_proxy_only(
    tmp_path, mutation
):
    from servicedesk.intelligence.triage_ai07.labops_evaluation import (
        report_for,
        verify_report,
    )

    profile = write_package(tmp_path)
    dataset = validate_dataset(tmp_path, profile=profile)
    case = dataset.partition("DEV")[0]
    receipt = {
        "caseId": case.case_id,
        "inputDigest": case.input_digest,
        "repeatIndex": 0,
        "state": "ABSTAIN",
        "proposedClassOrNull": None,
        "reasonCodeOrNull": None,
        "wallLatencyMs": 1.0,
        "nativeLatencyMsOrNull": None,
        "goldEligible": False,
    }
    report = report_for(
        dataset,
        split="DEV",
        candidate_id="cpu-lexical",
        attempts=[receipt],
        repeats=1,
        identity={
            "role": "NEVER_PROMOTABLE",
            "scope": "UNREVIEWED_SILVER_POC",
            "datasetDigest": dataset.dataset_digest,
            "officialQualityClaim": False,
            "promotion": "NO_GO",
        },
    )
    if mutation == "missing":
        report["attempts"].clear()
    elif mutation == "duplicate":
        report["attempts"].append(dict(receipt))
    elif mutation == "gold":
        report["officialQualityClaim"] = True
    elif mutation == "summary":
        report["summary"]["proxyAgreement"]["rate"] = 1.0
    elif mutation == "input":
        receipt["inputDigest"] = "f" * 64
    if mutation:
        with pytest.raises(ValueError):
            verify_report(dataset, report)
    else:
        assert verify_report(dataset, report)["production"] == "NO_GO"


@pytest.mark.parametrize(
    "name", ["../escape.json", "/escape.json", "C:/escape.json", "a/../../escape.json"]
)
def test_unsafe_zip_paths_never_escape_private_staging(tmp_path, name):
    archive = tmp_path / "unsafe.zip"
    with zipfile.ZipFile(archive, "w") as stream:
        stream.writestr(name, "unsafe")
    with pytest.raises(ValueError):
        import_dataset(archive, tmp_path / "new-dataset")
    assert not (tmp_path / "new-dataset").exists()
