# Intelligence — foundation, triage và truy xuất tri thức

## Python package identity

Import and run the runtime as `hdpi`, for example `python -m hdpi.http_service`.
The wheel distribution remains `smart-service-intelligence==0.3.0` for artifact
compatibility. Historical AI-02/AI-03 reports record the package names used at
their validation time. The former Python import path is no longer provided.

Application profiles are external configuration. From the repository root in PowerShell:

```powershell
$env:HDPI_PROFILE_ROOT = (Resolve-Path intelligence/src/apps/servicedesk/intelligence/profiles).Path
$env:HDPI_REQUIRED_PROFILES = 'service-desk@0.2.0'
```

The generic wheel contains contracts and runtime code. `ProfileRegistry(root=...)`
or `HDPI_PROFILE_ROOT` is required; there is no bundled application fallback.
`src/hdpi` owns reusable runtime code; `src/apps/<app>/intelligence` owns external
application policy. Setuptools discovers only `hdpi*`, and the generic Docker
build context excludes `src/apps`, so adjacent directories do not share packaging.
The private HTTP endpoint accepts an HPDI envelope with `request`, `profileVersion`
and independently verified `trustedContext` (`workspaceId`, `subject`). The Java
capability constructs this envelope after authorization; the public ticket endpoint
continues to reject request bodies. Compose mounts the selected app profile root
read-only at `/profiles`.

## Truy xuất AI-03

[Báo cáo thực thi](docs/task/AI03/RUNTIME_REPORT.md) ghi kết quả kiểm thử và giới
hạn. [Kiến trúc](docs/architecture/retrieval-runtime.md), [vòng đời index](docs/architecture/corpus-index-lifecycle.md),
[ADR-004](docs/decisions/ADR-004-tfidf-retrieval-baseline.md) và
[dependency/license](docs/governance/ai03-dependencies.md) phục vụ review.

Hardening: package **0.3.0**, giữ profile `service-desk@0.2.0` và wire contract
`0.1.0`. Đã bổ sung adapter Core HTTP và acceptance thật, xem
[hợp đồng scope](docs/architecture/access-scope-contract.md) và
[bằng chứng tích hợp](docs/task/AI03/CORE_INTEGRATION_REPORT.md). Review BE và
quyền dữ liệu vẫn còn chờ; chưa coi là phê duyệt production.

Sau khi cài wheel bằng bộ pin `requirements.lock`, chạy từ repository root:

```powershell
python -I -m hdpi.search --profile service-desk@0.2.0 --fixture-repo-root . --query "không đăng nhập được tài khoản" --top-k 3
python -I -m hdpi.evaluation.retrieval --repo-root . --fixtures-root fixtures/intelligence/service-desk --gates '{"hitAt1":0.8333333333333334,"hitAt3":1}' --output .local/ai03-evaluation.json
python -I scripts/intelligence/verify-installed-package.py
python -I -m pytest -c scripts/intelligence/installed-pytest.ini intelligence/tests -q
```

CLI bắt buộc chỉ rõ fixture repo và gắn nhãn `AUTHORING_ONLY_NOT_RUNTIME`;
`kb-*` chỉ là ID biên soạn. Điểm TF-IDF không phải confidence. Kết quả evaluation
được khử trùng lặp ở cấp artifact theo điểm chunk cao nhất; runtime trả chunk.
Không so hai phép đo này như cùng một benchmark production.

Runtime dùng `SearchService`, adapter triển khai `AuthorizedCorpusSource` và
`CoreSourceResolver`. Adapter phải xác thực, kiểm tra membership/quyền file, tạo
`TrustedContext` với scope opaque hiện tại trước khi gọi library. Luồng tích hợp:

```python
# context và profile do adapter tin cậy cung cấp, không deserialize từ request.
index = build_authorized_index(source=adapter, context=context, profile=profile)
indexes = IndexRegistry()
indexes.register(index)
service = SearchService(corpus_source=adapter, indexes=indexes,
                        citation_verifier=CoreCitationVerifier(adapter))
run = service.search(payload, trusted_context=context, profile_version='0.2.0')
```

Các symbol lần lượt nằm trong `retrieval.index`, `retrieval.registry`,
`services.search` và `citations.verifier` của package. Adapter HTTP nằm trong
`integrations.core`, có `CoreHttpClient`, `CoreCorpusCatalog`,
`CoreAuthorizedCorpusAdapter`. Cấp token provider từ middleware đã xác thực,
catalog từ cấu hình Application tin cậy, rồi gọi `adapter.trusted_context()`;
không lấy subject/scope từ request. BE cần duyệt cơ chế scope trước production. Snapshot phải lưu
ngoài Git, có ACL thích hợp, nạp bằng `load_index(path, expected_partition)`.
Truy vấn không có kết quả phù hợp trả SUCCESS và yêu cầu review; nguồn stale bị bỏ; lỗi quyền
luôn được truyền ra ngoài. Mặc định lỗi vận hành cũng được truyền ra ngoài;
chỉ bật `allow_operational_degraded=True` sau khi thống nhất chính sách adapter.

Phụ trách: AI. Trạng thái: **ĐÃ TRIỂN KHAI LOCAL, CHỜ REVIEW**.
AI-01.5 có profile thực thi; AI-02 có triage deterministic; AI-03 bổ sung library
TF-IDF, partition theo workspace/scope và kiểm chứng citation. Bằng chứng hiện tại
và các gate còn chờ được ghi trong [báo cáo AI-03](docs/task/AI03/RUNTIME_REPORT.md).
Có adapter Core HTTP đã kiểm chứng local; chưa có endpoint Intelligence public.
Xem [báo cáo AI-01.5](docs/task/AI015/CLOSEOUT_REPORT.md) và
[báo cáo AI-02](docs/task/AI02/RUNTIME_REPORT.md). Phê duyệt BE/Process Owner
và merge gate AI-01.5 vẫn chờ; chưa coi là release hoặc hoàn tất approval.

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
| Profile thực thi | [Registry](profiles/registry.yaml), [profile v0.2](profiles/service-desk/versions/0.2.0/profile.yaml), [luật v0.2](profiles/service-desk/versions/0.2.0/rules.yaml), [thiết kế thực thi](docs/architecture/executable-profile.md) |
| Profile lịch sử | [profile v0.1](profiles/service-desk/profile.yaml), [taxonomy](profiles/service-desk/taxonomy.yaml), [priority policy](profiles/service-desk/priority-policy.yaml), [normalization](profiles/service-desk/normalization.md) |
| Synthetic fixtures | [tickets](../fixtures/intelligence/service-desk/tickets/manifest.json), [retrieval gold](../fixtures/intelligence/service-desk/retrieval/gold-v0.1.json) and [knowledge sources](../fixtures/intelligence/service-desk/knowledge/manifest.json) |
| Architecture | [Overview](docs/architecture/overview.md), [Core boundary](docs/architecture/core-intelligence-boundary.md), [data/provenance](docs/architecture/data-provenance-contract.md) and [policy rationale](docs/architecture/priority-policy.md) |
| Decisions | [Profile composition](docs/decisions/ADR-001-profile-driven-intelligence.md), [Rules + TF-IDF proposal](docs/decisions/ADR-002-rules-tfidf-mvp.md), [evidence separation](docs/decisions/ADR-003-evidence-separation.md) |
| N1 review evidence | Start with the [BE review brief](docs/task/N1/BE_REVIEW_BRIEF.md); see [foundation](docs/task/N1/FOUNDATION_REPORT.md) and historical [Core acceptance](docs/task/N1/ACCEPTANCE_REPORT.md) reports |
| Dependency | [Danh mục và nguồn license](docs/governance/intelligence-dependency-candidates.md), [resolution metadata](docs/governance/ai02-dependency-resolution.json), [pins](requirements.lock) |
| Utilities | [Foundation validator](../scripts/intelligence/validate-foundation.py) and [Core acceptance probe](../scripts/acceptance/core/metadata-provenance.ps1) / [audit SQL](../scripts/acceptance/core/check-metadata-audit.sql) |

The directory `contracts/intelligence/v1/` groups the v1 capability family;
the unchanged N1 wire contract is still **`contractVersion=0.1.0`**, proposed.
The historical manifest pins `service-desk@0.1.0`; the executable registry resolves
only the explicitly selected `service-desk@0.2.0`. Both pin `priority-policy@0.1.0`.
`priority-policy.yaml` is the machine-readable authority for the matrix and review
fallback. The review rule references that fallback. The immutable knowledge policy
document is a historical fixture, checked for consistency with the proposed policy.

## Cài và kiểm tra local

Chạy từ repository root với Python 3.12.7 (Unicode 15.0.0). Tạo môi trường riêng;
không sử dụng package tình cờ có trên host làm dependency của project:

```powershell
python -m venv .local/ai02-venv
.local/ai02-venv/Scripts/python -m pip install -r intelligence/requirements.lock
.local/ai02-venv/Scripts/python scripts/intelligence/validate-foundation.py
.local/ai02-venv/Scripts/python -m pytest intelligence/tests -q
.local/ai02-venv/Scripts/python -m build --no-isolation --outdir .local/ai03-dist intelligence
.local/ai02-venv/Scripts/python scripts/intelligence/install-built-wheel.py --dist .local/ai03-dist
.local/ai02-venv/Scripts/python -I scripts/intelligence/verify-installed-package.py
git diff --check
```

Thư mục dist phải có đúng một wheel. Discovery từ chối không có artifact, nhiều
wheel hoặc metadata version khác `pyproject.toml`; tránh trộn wheel 0.2/0.3 cũ.
Để chạy acceptance Core thật với project/port riêng:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/setup.ps1
docker compose -p dx-ai03-hardening -f compose.yaml -f scripts/acceptance/intelligence-compose.yaml up -d --build --wait --wait-timeout 300
python -I scripts/acceptance/intelligence-retrieval.py --output .local/ai03-core-acceptance.json
docker compose -p dx-ai03-hardening -f compose.yaml -f scripts/acceptance/intelligence-compose.yaml stop
```

Cần Compose >=2.24.4; cổng 18081/19080. Runner đọc `.env` riêng tư, chỉ seed
source tổng hợp độc lập và principal test riêng, rồi xóa principal đó. Metadata
test còn trong volume acceptance vì Core không có API delete. Không dùng fixture
`PENDING_OWNER_APPROVAL`. Không chạy script này trên hệ thống production.

Linux dùng `.local/ai02-venv/bin/python` thay cho đường dẫn `Scripts/python`.
Developer CLI chạy sau khi cài wheel, chỉ dành cho engineering local:

```powershell
.local/ai02-venv/Scripts/python -m hdpi.triage --profile service-desk@0.2.0 --workspace lab-a --title "Wi-Fi chập chờn" --description "Một máy bị ảnh hưởng; có thể chờ." --trace
.local/ai02-venv/Scripts/python -m hdpi.evaluation.conformance --fixtures fixtures/intelligence/service-desk/tickets/tickets-v0.1.jsonl --profile-version 0.2.0 --expected-runtime '{"engine":"rules","profileVersion":"service-desk@0.2.0","rulesVersion":"0.2.0"}' --normalization-version text-normalization@0.1.0 --output .local/ai02-conformance.json
```

CLI không xác thực hay cấp quyền. Adapter Core HTTP phải authorize và truyền
`TrustedContext` riêng; payload workspace chỉ dùng correlation. Service chỉ map
`EngineUnavailable` sang DEGRADED, còn authorization/configuration/contract error
được raise. Trace không chứa nội dung ticket/credential; normalization version
nằm trong trace, không thêm trường ngoài schema wire v0.1.

Validation covers the shared schemas, profile composition, 12 manual expected
ticket annotations, literal INPUT evidence, priority/routing/rule versions,
exact-byte manifests, retrieval gold and invalid data. It reads local artifacts
without calling Core or executing an engine. Runtime tests execute triage
separately. Hardening adds the real Core HTTP acceptance runner described above;
this does not replace BE approval or a production public endpoint review.

Fixtures are synthetic authoring/evaluation seeds, not production data or engine
predictions. The five knowledge sources have exact-byte SHA-256, pending rights,
null Core metadata/workspace bindings and `NOT_INGESTED` status. Local `kb-*` IDs
are not runtime citations. Root `.gitattributes` preserves LF for moved assets;
semantic source edits require version/checksum/size updates. Local plans and probe
evidence belong under ignored `.local/ai-n1/`.

Ở baseline N1 chưa có runtime. AI-02 triển khai triage; AI-03 triển khai truy xuất
lexical dạng library. HTTP, tích hợp Core production và mô hình vẫn thuộc giai đoạn sau.
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
