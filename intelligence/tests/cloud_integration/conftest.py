from datetime import datetime, timedelta, timezone
from hashlib import sha256
import json
from pathlib import Path
import sys
import tempfile

import pytest


ROOT = Path(__file__).resolve().parents[3]
if not sys.flags.isolated:
    sys.path.insert(0, str(ROOT / "intelligence/integrations"))

from cloud_providers.contracts import encode_json
from cloud_providers.registry import ProviderRegistry


QUESTION = "device connection"
CONTENT = "Verify the device connection."
KEYS = {
    "GEMINI_API_KEY": "synthetic-gemini-key-123456789",
    "GROQ_API_KEY": "synthetic-groq-key-123456789",
}


def digest(value):
    return sha256(value.encode() if isinstance(value, str) else value).hexdigest()


def write_policy(root, *, status="APPROVED", expiry=None, routes=None):
    route = dict(
        id="gemini-primary",
        vendor="gemini",
        model="fixture-gemini",
        observedModels=["fixture-gemini-1"],
        priority=10,
        maxInputBytes=16000,
        maxOutputTokens=256,
        timeoutSeconds=3,
        rpm=20,
        rpd=100,
        tpm=200000,
        tpd=1000000,
    )
    secondary = {
        **route,
        "id": "groq-secondary",
        "vendor": "groq",
        "model": "fixture/groq",
        "observedModels": ["fixture/groq"],
        "priority": 20,
    }
    catalog = dict(
        apiVersion="cloud-selection@0.2.0",
        mode="synthetic-cloud-dev",
        policyId="synthetic-assistance",
        profileId="service-desk",
        profileVersion="0.3.0",
        allowPaidUsage=False,
        maxAttempts=1,
        policy=dict(
            identity="servicedesk-assistance@0.1.0",
            instructions="Select supplied synthetic guidance or refuse.",
            max_claims=3,
            needs_review=True,
        ),
        routes=routes or [route, secondary],
    )
    raw = encode_json(catalog)
    (root / "catalog.json").write_bytes(raw)
    catalog_digest = digest(raw)
    approval = dict(
        schemaVersion="cloud-egress@0.2.0",
        status=status,
        owner="TEST_ONLY_NOT_A_REAL_APPROVAL",
        environmentId="synthetic-unit-environment",
        expiresAt=expiry
        or (datetime.now(timezone.utc) + timedelta(hours=1)).isoformat(),
        catalogDigest=catalog_digest,
        dataClass="synthetic-public-evaluation",
        questionDigests=[digest(QUESTION)],
        sourceDigests=[digest(CONTENT)],
        actors=["synthetic-actor"],
        resources=[
            dict(
                workspace="synthetic-workspace",
                id="11111111-1111-4111-8111-111111111111",
                contentDigest=digest("Synthetic\nFixture"),
            )
        ],
    )
    approval_raw = encode_json(approval)
    (root / "approval.json").write_bytes(approval_raw)
    (root / "isolation.json").write_bytes(
        encode_json(
            dict(
                schemaVersion="isolated-synthetic@0.1.0",
                environmentId=approval["environmentId"],
                catalogDigest=catalog_digest,
                databasePurpose="synthetic-only",
                production=False,
            )
        )
    )
    return ProviderRegistry(
        root / "catalog.json",
        catalog_digest,
        root / "approval.json",
        digest(approval_raw),
        root / "isolation.json",
        "isolated-synthetic",
        repository_root=ROOT,
    )


@pytest.fixture
def registry():
    with tempfile.TemporaryDirectory(prefix="cloud-contract-policy-") as directory:
        yield write_policy(Path(directory))


def payload(registry, route_id="gemini-primary"):
    from dataclasses import asdict

    route = registry.routes[route_id]
    return dict(
        contractVersion="0.2.0",
        requestId="22222222-2222-4222-8222-222222222222",
        workspaceId="synthetic-workspace",
        profileId="service-desk",
        profileVersion="0.3.0",
        policyIdentity=registry.policy.identity,
        question=QUESTION,
        documents=[
            dict(
                metadataId="33333333-3333-4333-8333-333333333333",
                version="1",
                checksum=digest(CONTENT),
                content=CONTENT,
                mediaType="text/plain",
            )
        ],
        routeBinding=dict(
            routeId=route.id,
            policyId=registry.catalog["policyId"],
            dataClass="synthetic-public-evaluation",
            catalogDigest=registry.catalog_digest,
            routeDigest=route.digest,
            approvalDigest=registry.approval_digest,
            attemptId="44444444-4444-4444-8444-444444444444",
            expectedRuntime=asdict(route.runtime),
        ),
    )


def completion(vendor, selection=None):
    text = json.dumps(selection or {"decision": "SELECT", "citation_ids": ["C1"]})
    if vendor == "gemini":
        return {
            "modelVersion": "fixture-gemini-1",
            "candidates": [
                {
                    "finishReason": "STOP",
                    "content": {"role": "model", "parts": [{"text": text}]},
                }
            ],
        }
    return {
        "object": "chat.completion",
        "model": "fixture/groq",
        "choices": [
            {
                "index": 0,
                "finish_reason": "stop",
                "message": {"role": "assistant", "content": text},
            }
        ],
        "usage": {"completion_time": 0.1},
    }


class FakeClient:
    def __init__(self, response=None, failure=None):
        self.response = response
        self.failure = failure
        self.calls = []

    def post(self, route, body, credential):
        self.calls.append((route.vendor, body, credential))
        if self.failure:
            raise self.failure
        return encode_json(
            self.response if self.response is not None else completion(route.vendor)
        )
