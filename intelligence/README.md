# Intelligence foundation

Owner: AI member. Status: **READY FOR BE REVIEW — PROPOSED**.
N1 human review remains pending. Current implementation alignment is recorded below.

## Trạng thái đối chiếu Core ngày 16/10/2026

Baseline: `0238c590b905e6a47913fe844406894ef81e0cfd`. Bằng chứng N1 ngày
07–08/10 được giữ nguyên theo baseline lịch sử, không coi là lần chạy mới.
Core hiện có Metadata search, File HTTP, `fileId`, checksum do Core tính cho
file upload, DTO camelCase và audit trong transaction. Metadata read/search yêu
cầu thành viên workspace; create yêu cầu `agent` hoặc `coordinator`.
Các vai trò mặc định là `requester`, `agent`, `coordinator`.

AI-01.5 bổ sung profile thực thi riêng `service-desk@0.2.0`; bản v0.1 tiếp tục
là đầu vào lịch sử, không đổi nội dung hoặc nhãn version. AI-02 triển khai local
library để review; merge gate AI-01.5 và phê duyệt nghiệp vụ vẫn chờ BE/Process
Owner. Không suy diễn quyền truy cập từ `context.workspaceId` trong payload.

Intelligence currently owns the versioned Service Desk domain profile: taxonomy,
matching rules, priority configuration and normalization proposal. Core owns
identity, authorization, workspace, canonical metadata/files, workflow and audit.
The Service Desk / Process Owner owns business priority policy; human decisions
remain authoritative. Applications can use Core independently of Intelligence.

## Artifact navigation

| Artifact | Location / ownership |
| --- | --- |
| Shared capability contracts | [contracts/intelligence/v1](../contracts/intelligence/v1/contract.md): generic triage/search envelopes for Application and Intelligence consumers |
| Service Desk profile | [profile.yaml](profiles/service-desk/profile.yaml) pins [taxonomy](profiles/service-desk/taxonomy.yaml), [rules](profiles/service-desk/rules.yaml), [priority policy](profiles/service-desk/priority-policy.yaml) and [normalization](profiles/service-desk/normalization.md) |
| Synthetic fixtures | [tickets](../fixtures/intelligence/service-desk/tickets/manifest.json), [retrieval gold](../fixtures/intelligence/service-desk/retrieval/gold-v0.1.json) and [knowledge sources](../fixtures/intelligence/service-desk/knowledge/manifest.json) |
| Architecture | [Overview](docs/architecture/overview.md), [Core boundary](docs/architecture/core-intelligence-boundary.md), [data/provenance](docs/architecture/data-provenance-contract.md) and [policy rationale](docs/architecture/priority-policy.md) |
| Decisions | [Profile composition](docs/decisions/ADR-001-profile-driven-intelligence.md), [Rules + TF-IDF proposal](docs/decisions/ADR-002-rules-tfidf-mvp.md), [evidence separation](docs/decisions/ADR-003-evidence-separation.md) |
| N1 review evidence | Start with the [BE review brief](docs/task/N1/BE_REVIEW_BRIEF.md); see [foundation](docs/task/N1/FOUNDATION_REPORT.md) and historical [Core acceptance](docs/task/N1/ACCEPTANCE_REPORT.md) reports |
| Dependency governance | [Candidate inventory](docs/governance/intelligence-dependency-candidates.md): proposals awaiting adoption/license review |
| Utilities | [Foundation validator](../scripts/intelligence/validate-foundation.py) and [Core acceptance probe](../scripts/acceptance/core/metadata-provenance.ps1) / [audit SQL](../scripts/acceptance/core/check-metadata-audit.sql) |

The directory `contracts/intelligence/v1/` groups the v1 capability family;
the unchanged N1 wire contract is still **`contractVersion=0.1.0`**, proposed.
The profile manifest pins `service-desk@0.1.0` and `priority-policy@0.1.0`.
`priority-policy.yaml` is the machine-readable authority for the matrix and review
fallback. The review rule references that fallback. The immutable knowledge policy
document is a historical fixture, checked for consistency with the proposed policy.

## Validate locally

Use the existing host Python with PyYAML and jsonschema, as authoring tools:

```powershell
python scripts/intelligence/validate-foundation.py
git diff --check
```

Validation covers the shared schemas, profile composition, 12 manual expected
ticket annotations, literal INPUT evidence, priority/routing/rule versions,
exact-byte manifests, retrieval gold and invalid data. It reads local artifacts
without calling Core or executing an engine. Adapter authorization and Core
citation issuance require separate implementation tests.

Fixtures are synthetic authoring/evaluation seeds, not production data or engine
predictions. The five knowledge sources have exact-byte SHA-256, pending rights,
null Core metadata/workspace bindings and `NOT_INGESTED` status. Local `kb-*` IDs
are not runtime citations. Root `.gitattributes` preserves LF for moved assets;
semantic source edits require version/checksum/size updates. Local plans and probe
evidence belong under ignored `.local/ai-n1/`.

At the historical N1 baseline no Intelligence runtime existed. AI-02 runtime
progress is recorded separately; retrieval, HTTP and model adoption remain later work.
Approved integration seeds must be ingested
through Core APIs; future indexes consume authorized Core interfaces. Additional
domains add profiles and fixtures without adding Service Desk semantics to Core.

## Core acceptance utilities

The [acceptance report](docs/task/N1/ACCEPTANCE_REPORT.md)
preserves the historical run of 07/10/2026 against baseline
`c92b38733d00c77bd90b96318f8c70d7b19be5c0`. The structural refactor does not rerun
that acceptance or claim new Core regression evidence.

For a requested rerun with local baseline services running:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/acceptance/core/metadata-provenance.ps1
$proof = Get-Content .local/ai-n1/metadata-provenance.json -Raw | ConvertFrom-Json
Get-Content scripts/acceptance/core/check-metadata-audit.sql -Raw |
  docker compose exec -T postgres psql -U core -d core -v ON_ERROR_STOP=1 `
    -v "metadataId=$($proof.record.id)" -v "owner=$($proof.record.owner)" `
    -v "workspace=$($proof.record.workspace)" -v "requestId=$($proof.record.requestId)"
if ($LASTEXITCODE -ne 0) { throw 'Core metadata audit check failed' }
```

The API probe uses existing demo accounts and local `.env` credentials without
printing them. Each successful run creates and retains one synthetic metadata
record for audit inspection. Evidence is saved to ignored
`.local/ai-n1/metadata-provenance.json`; SQL is a read-only Core acceptance check.
