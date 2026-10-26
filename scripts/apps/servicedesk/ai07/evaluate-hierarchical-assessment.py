"""Đánh giá phân cấp từ bản đóng băng riêng tư đã được ký duyệt độc lập."""

import argparse
from hashlib import sha256
import json
import os
from pathlib import Path
import sys

from _bootstrap import ROOT, write_new
from servicedesk.intelligence.triage_ai07.dataset_profiles import bounded_json
from servicedesk.intelligence.triage_ai07.hierarchical_evaluation import (
    evaluate_reviewed_hierarchy,
)


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--review-manifest", required=True, type=Path)
    parser.add_argument("--reviewed-rows", required=True, type=Path)
    parser.add_argument("--predictions", required=True, type=Path)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    key_path = os.environ.get("AI07_REVIEW_TRUST_KEY_FILE")
    if not key_path:
        raise ValueError(
            "Chưa cấu hình khóa xác minh của bên duyệt độc lập; Gold vẫn PENDING"
        )
    resources = ROOT / "backend/src/main/resources/apps/servicedesk/intelligence"
    names = {
        "taxonomyDigest": "taxonomy-v0.1.json",
        "assessmentPolicyDigest": "assessment-policy-v0.1.json",
        "priorityPolicyDigest": "triage-policy.json",
    }
    raw = {key: (resources / name).read_bytes() for key, name in names.items()}
    predictions_raw = args.predictions.read_bytes()
    if (
        len(predictions_raw) > 64 * 1024 * 1024
        or args.reviewed_rows.stat().st_size > 64 * 1024 * 1024
    ):
        raise ValueError("Tệp đánh giá vượt giới hạn 64 MiB")
    predictions = [
        bounded_json(line, 131072)
        for line in predictions_raw.splitlines()
        if line.strip()
    ]
    schema = json.loads(
        (
            ROOT
            / "contracts/apps/servicedesk/intelligence/ticket-assessment-v0.2.schema.json"
        ).read_bytes()
    )
    from jsonschema import Draft202012Validator

    review_schema = json.loads(
        (
            ROOT
            / "contracts/apps/servicedesk/intelligence/private-hierarchical-review-v0.1.schema.json"
        ).read_bytes()
    )
    rows_raw = args.reviewed_rows.read_bytes()
    row_validator = Draft202012Validator(review_schema)
    for line in rows_raw.splitlines():
        if line.strip():
            row_validator.validate(bounded_json(line, 32768))
    report = evaluate_reviewed_hierarchy(
        args.review_manifest.read_bytes(),
        rows_raw,
        predictions,
        trusted_key=Path(key_path).read_bytes(),
        schema=schema,
        taxonomy=json.loads(raw["taxonomyDigest"]),
        matrix=json.loads(raw["priorityPolicyDigest"])["matrix"],
        artifact_digests={key: sha256(value).hexdigest() for key, value in raw.items()},
    )
    write_new(args.output, report)
    print("Đã đánh giá bản đóng băng có chữ ký; production vẫn NO_GO")


if __name__ == "__main__":
    main()
