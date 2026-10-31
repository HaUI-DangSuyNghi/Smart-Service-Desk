"""Pinned private LabOps engineering data; this module cannot approve labels."""

from collections import Counter
from dataclasses import dataclass, field
from hashlib import sha256
import json
from pathlib import Path, PurePosixPath
import re
import stat
import tempfile
import unicodedata
import zipfile

from hdpi.classification.protocols import require
from hdpi.classification.validation import _object


PROFILE = Path(__file__).with_name("labops-v4-profile.json")
CLASSES = ("ACCOUNT", "HARDWARE", "NETWORK", "OTHER", "SOFTWARE")
ROW_KEYS = frozenset(
    {
        "allowed_classes",
        "class_id",
        "data_rights",
        "decision",
        "description",
        "evidence",
        "family_id",
        "flags",
        "gold_approved",
        "human_reviewed",
        "id",
        "label_origin",
        "label_status",
        "official_quality_eligible",
        "policy_ref",
        "raw_record_sha256",
        "review_priority",
        "review_tier",
        "rule_ids",
        "source_collection",
        "source_original_category",
        "split",
        "supervision_type",
        "title",
    }
)
SPLITS = {
    "TRAIN": ("01_train_all_with_abstain.jsonl", "TRAIN_SILVER"),
    "DEV": ("04_dev_all_with_abstain.jsonl", "DEV_SILVER"),
    "CASCADE": ("07_cascade_reserved.jsonl", "CASCADE_RESERVED"),
    "CHALLENGE": (
        "08_ambiguous_and_scenario_challenge_60.jsonl",
        "CHALLENGE_QUARANTINED",
    ),
}


def bounded_json(raw, maximum=5 * 1024 * 1024):
    def reject_constant(_value):
        raise ValueError("Non-finite dataset JSON rejected")

    require(type(raw) is bytes and 0 < len(raw) <= maximum)
    try:
        return json.loads(
            raw.decode("utf-8"),
            object_pairs_hook=_object,
            parse_constant=reject_constant,
        )
    except (ValueError, RecursionError):
        raise ValueError("Dataset JSON rejected") from None


def _relative(value):
    require(type(value) is str and "\\" not in value and ":" not in value)
    path = PurePosixPath(value)
    require(not path.is_absolute() and str(path) == value)
    require(all(part not in ("", ".", "..") for part in path.parts))
    return path


def _read(root, relative):
    path = root / str(_relative(relative))
    require(path.resolve(strict=True).is_relative_to(root))
    for item in (path, *path.parents):
        if item == root:
            break
        require(not item.is_symlink() and not item.is_junction())
    require(path.is_file() and path.stat().st_size <= 5 * 1024 * 1024)
    return path.read_bytes()


def _normalized(title, description):
    value = unicodedata.normalize("NFC", title + "\n" + description).casefold()
    return " ".join(value.split())


@dataclass(frozen=True)
class EngineeringCase:
    case_id: str
    split: str
    family_digest: str
    input_digest: str
    title: str = field(repr=False)
    description: str = field(repr=False)
    decision: str
    class_id: str | None
    source_digest: str

    def model_fields(self):
        return {"title": self.title, "description": self.description}


@dataclass(frozen=True)
class EngineeringDataset:
    identity: str
    dataset_digest: str
    cases: tuple[EngineeringCase, ...] = field(repr=False)
    summary: dict

    def partition(self, split):
        require(split in SPLITS)
        return tuple(row for row in self.cases if row.split == split)


def _case(row, split, source_digest):
    require(type(row) is dict and set(row) == ROW_KEYS)
    require(row["split"] == SPLITS[split][1])
    require(row["allowed_classes"] == list(CLASSES))
    require(row["label_status"] == "MACHINE_SILVER_UNREVIEWED")
    require(row["data_rights"] == "PENDING_OWNER_CONFIRMATION")
    require(
        all(
            row[key] is False
            for key in ("gold_approved", "human_reviewed", "official_quality_eligible")
        )
    )
    require(type(row["id"]) is str and re.fullmatch(r"[A-Za-z0-9_-]{1,100}", row["id"]))
    require(type(row["family_id"]) is str and 0 < len(row["family_id"]) <= 200)
    require(all(type(row[key]) is str for key in ("title", "description")))
    require(0 < len(row["title"]) + len(row["description"]) <= 8192)
    require(
        type(row["raw_record_sha256"]) is str
        and re.fullmatch(r"[0-9a-f]{64}", row["raw_record_sha256"])
    )
    require(row["decision"] in ("PROPOSE", "ABSTAIN"))
    if row["decision"] == "ABSTAIN":
        require(row["class_id"] is None and row["evidence"] is None)
    else:
        require(type(row["class_id"]) is str and row["class_id"] in CLASSES)
        evidence = row["evidence"]
        require(
            type(evidence) is dict
            and set(evidence)
            in (
                {"field", "start", "end", "text", "rule_id"},
                {"field", "start", "end", "text", "rule_id", "category", "strength"},
            )
        )
        require(evidence["field"] in ("title", "description"))
        require(type(evidence["start"]) is int and type(evidence["end"]) is int)
        original = row[evidence["field"]]
        require(0 <= evidence["start"] < evidence["end"] <= len(original))
        require(
            type(evidence["text"]) is str
            and original[evidence["start"] : evidence["end"]] == evidence["text"]
        )

    fields = {"title": row["title"], "description": row["description"]}
    return EngineeringCase(
        row["id"],
        split,
        sha256(row["family_id"].encode()).hexdigest(),
        sha256(
            json.dumps(
                fields, ensure_ascii=True, sort_keys=True, separators=(",", ":")
            ).encode()
        ).hexdigest(),
        row["title"],
        row["description"],
        row["decision"],
        row["class_id"],
        source_digest,
    )


def validate_dataset(directory, *, profile=None):
    root = Path(directory).resolve(strict=True)
    profile = profile if profile is not None else bounded_json(PROFILE.read_bytes())
    manifest_raw = _read(root, "meta/file_integrity_manifest.json")
    require(sha256(manifest_raw).hexdigest() == profile["manifestDigest"])
    manifest = bounded_json(manifest_raw)
    require(
        manifest
        == {"contract": "ai07-dataset-integrity@1.0.0", "files": profile["files"]}
    )
    require(
        len({item["path"].casefold() for item in manifest["files"]})
        == len(manifest["files"])
    )
    verified = {}

    # Verify every source byte before interpreting any row or reference.
    for item in manifest["files"]:
        require(type(item) is dict and set(item) == {"path", "sha256", "size"})
        raw = _read(root, item["path"])
        require(len(raw) == item["size"] and sha256(raw).hexdigest() == item["sha256"])
        verified[item["path"]] = raw

    cases, ids, families, contents, counts = [], set(), {}, {}, {}
    for split, (filename, _) in SPLITS.items():
        relative = "data/" + filename
        rows = [
            bounded_json(line, 32768)
            for line in verified[relative].splitlines()
            if line.strip()
        ]
        require(len(rows) == profile["counts"][split])
        for row in rows:
            case = _case(row, split, sha256(verified[relative]).hexdigest())
            require(case.case_id not in ids)
            ids.add(case.case_id)
            if split in ("TRAIN", "DEV"):
                content = _normalized(case.title, case.description)
                require(
                    case.family_digest not in families
                    or families[case.family_digest] == split
                )
                require(content not in contents or contents[content] == split)
                families[case.family_digest] = split
                contents[content] = split
            cases.append(case)
        counts[split] = {
            "rows": len(rows),
            "states": dict(sorted(Counter(row["decision"] for row in rows).items())),
            "families": len({row["family_id"] for row in rows}),
        }

    for split, filename in (
        ("TRAIN", "model_input_train_NO_LABELS.jsonl"),
        ("DEV", "model_input_dev_NO_LABELS.jsonl"),
        ("CHALLENGE", "model_input_challenge_NO_LABELS.jsonl"),
    ):
        inputs = [
            bounded_json(line, 32768)
            for line in verified["data/" + filename].splitlines()
            if line.strip()
        ]
        expected = [
            {"id": case.case_id, **case.model_fields()}
            for case in cases
            if case.split == split
        ]
        require(inputs == expected)

    quarantine = bounded_json(
        verified["meta/legacy_holdout_QUARANTINED_INVENTORY_ONLY.json"]
    )
    require(
        quarantine["count"] == 40
        and quarantine["status"]
        == "QUARANTINED_LEGACY_EXPOSED_UNSAFE_FOR_FINAL_HOLDOUT"
    )
    summary = {
        "status": "PASS_ENGINEERING_DATASET_ONLY",
        "datasetProfile": profile["identity"],
        "datasetDigest": profile["manifestDigest"],
        "total": len(cases),
        "splits": counts,
        "manifestFiles": len(verified),
        "gold": "NOT_APPROVED",
        "rights": "PENDING_OWNER_CONFIRMATION",
        "holdout": "QUARANTINED_COUNT_40",
        "officialQualityClaim": False,
        "promotion": "NO_GO",
    }
    return EngineeringDataset(
        profile["identity"], profile["manifestDigest"], tuple(cases), summary
    )


def import_dataset(source, destination):
    source, destination = Path(source).resolve(strict=True), Path(destination).resolve()
    require(not destination.exists())
    destination.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(
        prefix="ai07-import-", dir=destination.parent
    ) as temporary:
        staging = Path(temporary) / "data"
        staging.mkdir()
        if source.is_dir():
            dataset = validate_dataset(source)
            profile = bounded_json(PROFILE.read_bytes())
            for relative in [
                "meta/file_integrity_manifest.json",
                *(item["path"] for item in profile["files"]),
            ]:
                target = staging / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(_read(source, relative))
        else:
            with zipfile.ZipFile(source) as archive:
                files = [item for item in archive.infolist() if not item.is_dir()]
                require(
                    1 <= len(files) <= 128
                    and sum(item.file_size for item in files) <= 32 * 1024 * 1024
                )
                names = [str(_relative(item.filename)) for item in files]
                require(len({name.casefold() for name in names}) == len(names))
                prefix = ""
                if "meta/file_integrity_manifest.json" not in names:
                    roots = {PurePosixPath(name).parts[0] for name in names}
                    require(len(roots) == 1)
                    prefix = next(iter(roots)) + "/"
                for item in files:
                    require(not stat.S_ISLNK(item.external_attr >> 16))
                    require(
                        item.file_size <= 5 * 1024 * 1024
                        and item.file_size <= max(item.compress_size, 1) * 200
                    )
                    relative = str(_relative(item.filename.removeprefix(prefix)))
                    target = staging / relative
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_bytes(archive.read(item))
            dataset = validate_dataset(staging)
        validate_dataset(staging)
        staging.rename(destination)
    return dataset.summary
