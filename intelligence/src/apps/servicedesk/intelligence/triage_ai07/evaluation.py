"""Reproducible DEV engineering controls, explicitly separate from model quality."""

from collections import Counter
from hashlib import sha256
import json
from pathlib import Path

from hdpi.domain.models import TrustedContext
from hdpi.profiles.registry import ProfileRegistry
from hdpi.services.triage import TriageService

from .datasets import SOURCE_FILES, inventory
from .hybrid import POLICY_DIGEST


def evaluate(root):
    root = Path(root)
    audit = inventory(root)
    profiles = root / "intelligence/src/apps/servicedesk/intelligence/profiles"
    service = TriageService(registry=ProfileRegistry(root=profiles))
    source = root / SOURCE_FILES[1]
    cases = [json.loads(line) for line in source.read_text("utf-8").splitlines()]
    receipts = []

    for item in cases:
        if item["split"] != "DEV":
            continue

        request = {
            "contractVersion": "0.1.0",
            "requestId": item["id"],
            "profile": "service-desk",
            "context": {"workspaceId": "ai07-offline"},
            "input": {"title": item["title"], "description": item["description"]},
        }
        response = service.triage(
            request,
            trusted_context=TrustedContext(
                workspace_id="ai07-offline", subject="offline-control"
            ),
            profile_version="0.3.0",
        ).response
        canonical = json.dumps(
            response, ensure_ascii=True, sort_keys=True, separators=(",", ":")
        )
        receipts.append(
            {
                "caseId": item["id"],
                "status": response["status"],
                "needsReview": response["needsReview"],
                "responseDigest": sha256(canonical.encode("utf-8")).hexdigest(),
            }
        )

    return {
        "contractVersion": "0.1.0",
        "scope": "DEV_ENGINEERING_CONTROLS_ONLY",
        "policyDigest": POLICY_DIGEST,
        "sources": audit,
        "attempts": len(receipts),
        "statuses": dict(sorted(Counter(item["status"] for item in receipts).items())),
        "reviewRequired": sum(item["needsReview"] for item in receipts),
        "receipts": receipts,
        "cpuModel": "NOT_TRAINED_PENDING_REVIEWED_LABELS_AND_RIGHTS",
        "gemmaModel": "NOT_PROVISIONED",
        "macroF1": None,
        "promotion": "BLOCKED_PENDING_REVIEW",
        "officialQualityClaim": False,
    }


def verify(root, report, *, require_promotion=False):
    expected = evaluate(root)

    if report != expected:
        raise ValueError(
            "Report differs from recomputed source identities and complete DEV results"
        )

    if require_promotion:
        raise ValueError(
            "Promotion blocked: reviewed labels, rights and independent holdout are pending"
        )

    return {"engineeringControls": "PASS", "modelPromotion": "BLOCKED_PENDING_REVIEW"}
