# Intelligence foundation

Owner: AI member. Branch: `feature/ai01-data-provenance`.
Updated: **08/10/2026, Asia/Saigon**.
Status: **READY FOR BE REVIEW — design freeze candidate, PROPOSED**.
N1 DONE still requires BE ratification, Core alignment and Core regression evidence.

Core acceptance baseline: `c92b38733d00c77bd90b96318f8c70d7b19be5c0`.
Branch HEAD before current foundation edits: `c006278af18e3d29b0f8d542e9cddc199d516695`.
Foundation package state: **COMMITTED** — commit containing this package/report;
resolve with `git log -1 --format="%H %s" -- intelligence/docs/AI_N1_INTELLIGENCE_FOUNDATION_REPORT.md`.
Core smoke/API evidence: historical run on **07/10/2026 +07:00**, against the Core
acceptance baseline above; API **23:19:54–23:19:59 +07:00**. Not rerun during hardening.
Foundation validator evidence: local design-artifact validation on **08/10/2026
+07:00**, against the foundation working tree before package commit; exact run
timestamp and coverage in the foundation report.

## Review entry point

Start with the [BE review brief](docs/AI_N1_BE_REVIEW_BRIEF.md): five decisions on
metadata role policy, explicit response mapping, trusted context, citation identity
and audit ownership. The [foundation report](docs/AI_N1_INTELLIGENCE_FOUNDATION_REPORT.md)
records current local checks; the [Core acceptance report](docs/AI_N1_ACCEPTANCE_REPORT.md)
preserves historical runtime evidence and its limitations.

This package changes `intelligence/` only. Core owns authorization, metadata,
canonical files and audit. Intelligence consumes authorized Core data and owns
rebuildable derived state. Direct DX-Core changes still require team review.

## Design artifacts

* [Data/provenance contract](docs/data-provenance-contract.md): field authority,
  immutable public API, exact-byte checksum trust and workspace isolation.
* [Capability contract](../contracts/intelligence/v1/contract.md): replaceable
  triage/search engines, server-injected trusted context and manual fallback.
  [Triage request](../contracts/intelligence/v1/triage-request.schema.json) /
  [result](../contracts/intelligence/v1/triage-result.schema.json) and
  [search request](../contracts/intelligence/v1/search-request.schema.json) /
  [result](../contracts/intelligence/v1/search-result.schema.json) are machine-readable.
* [Service Desk taxonomy](profiles/service-desk/taxonomy.yaml) and
  [priority policy](../docs/architecture/intelligence/priority-policy.md): 5 categories, root-cause
  conflicts, 16 Impact × Urgency cells. Service Desk / Process Owner owns the
  proposed policy; `service-desk@0.1.0` pins `priority-policy@0.1.0`.
* [12 ticket fixtures](../fixtures/intelligence/service-desk/tickets/tickets-v0.1.jsonl),
  [dataset manifest](../fixtures/intelligence/service-desk/tickets/manifest.json) and
  [DEGRADED example](../fixtures/intelligence/service-desk/tickets/triage-degraded-v0.1.json): synthetic manual
  expected suggestions, including known OTHER and ambiguous input. Ambiguous
  MEDIUM is a review fallback with null impact/urgency, not a matrix result.
* Five `knowledge/` documents and [manifest](../fixtures/intelligence/service-desk/knowledge/manifest.json):
  exact-byte SHA-256, rights pending, null Core bindings, not ingested. Local
  `kb-*` artifact IDs are not runtime citations.
* [Retrieval gold draft](../fixtures/intelligence/service-desk/retrieval/gold-v0.1.json): MANUAL_EXPECTED,
  NOT BENCHMARK, NOT ENGINE OUTPUT; no accuracy claim.
  [Normalization draft](profiles/service-desk/normalization.md) preserves Vietnamese
  diacritics and extracts INPUT evidence from original input.
* [Inventory](inventory/AI_DEPENDENCY_CANDIDATES.md): runtime candidates PROPOSED;
  no dependency/model adoption or verified license claim.

Triage v0.1 emits INPUT evidence only. Search carries authorized Core provenance
hits; Application may compose the two results. No request value grants access;
App/Core authorization runs before corpus/cache/engine access. Authorization denial
is not DEGRADED. Ranking score is engine-specific, not confidence/probability.

## Local artifact validation

Use existing host Python with PyYAML and jsonschema (authoring tools only):

```powershell
python scripts/intelligence/validate-foundation.py
```

The validator reads artifacts only: 4 schemas, annotations, trusted-context echoes,
evidence separation, routing/rule/policy versions, priority matrix, manifests,
retrieval-gold references and 70 negative cases. It does not call API/DB, ingest,
execute an engine or prove adapter authorization/citation issuance. No runtime
service, FastAPI, TF-IDF, embedding, vector DB or model is initialized.

Scoped `.gitattributes` preserves LF for checksum-sensitive source assets. Hash
exact bytes, never normalized text. Semantic knowledge edits require version,
checksum and byte-size updates followed by validation. Rights remain
`PENDING_OWNER_APPROVAL`, `usage_rights=null`; ingest/release requires owner approval.
The local `.backup/` plans remain Git ignored by `intelligence/.gitignore`.

## Core acceptance probe

For an explicitly requested future rerun with the baseline services running:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/acceptance/core/metadata-provenance.ps1
$proof = Get-Content .local/ai-n1/metadata-provenance.json -Raw | ConvertFrom-Json
Get-Content scripts/acceptance/core/check-metadata-audit.sql -Raw |
  docker compose exec -T postgres psql -U core -d core -v ON_ERROR_STOP=1 `
    -v "metadata_id=$($proof.record.id)" -v "owner=$($proof.record.owner)" `
    -v "workspace=$($proof.record.workspace)" -v "request_id=$($proof.record.requestId)"
if ($LASTEXITCODE -ne 0) { throw 'AI-N1 audit check failed' }
```

The API probe uses existing synthetic demo accounts and `.env` credentials without
printing them. Each successful run creates one synthetic metadata record via the
public API and retains it for audit inspection. Evidence under `.local/ai-n1/` is
Git ignored and contains no tokens/passwords. SQL inspection is read-only acceptance
evidence, not an Intelligence runtime data-access path.
