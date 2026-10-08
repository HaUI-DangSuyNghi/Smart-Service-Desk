# AI-01 / N1 — Intelligence Foundation Report

Cập nhật: **08/10/2026, Asia/Saigon**. Branch: `feature/ai01-data-provenance`.
Trạng thái: **READY FOR BE REVIEW — design freeze candidate, PROPOSED**.
Pre-BE hardening đã hoàn tất; N1 chưa DONE vì BE ratification, Core alignment và
Core regression evidence còn chờ. Commit package không thay thế approval.
Kế hoạch local: `intelligence/.backup/AI_N1_PRE_BE_REVIEW_HARDENING_PLAN.md`;
file kế hoạch không đưa vào Git.

Core acceptance baseline: `c92b38733d00c77bd90b96318f8c70d7b19be5c0`.
Branch HEAD before current foundation edits: `c006278af18e3d29b0f8d542e9cddc199d516695`.
Foundation package state: **COMMITTED** — commit containing this package/report;
resolve with `git log -1 --format="%H %s" -- intelligence/docs/AI_N1_INTELLIGENCE_FOUNDATION_REPORT.md`.
Core smoke/API evidence: historical run on **07/10/2026 +07:00**, against the Core
acceptance baseline above; API **23:19:54–23:19:59 +07:00**. Not rerun during hardening.
Foundation validator evidence: local design-artifact validation on **08/10/2026
+07:00**, against the foundation working tree before package commit; exact run
timestamp and coverage in the foundation report.

## Package bàn giao

| Nhóm | Artifact / kết quả |
| --- | --- |
| Data/provenance | [Contract](data-provenance-contract.md): metadata UUID định danh một record, không phải logical document xuyên version; Core sở hữu authorization/files/audit. |
| Triage + retrieval | [Capability contract](../../contracts/intelligence/v1/contract.md), [triage request](../../contracts/intelligence/v1/triage-request.schema.json) / [result](../../contracts/intelligence/v1/triage-result.schema.json), [search request](../../contracts/intelligence/v1/search-request.schema.json) / [result](../../contracts/intelligence/v1/search-result.schema.json); đủ 4 machine-readable schema. |
| Trusted context | `context.workspace_id` là server-injected adapter context, không phải user grant. AUTH-INV-01..04: authorize trước corpus/cache/engine; auth failure không phải DEGRADED. |
| Evidence | Triage v0.1 chỉ INPUT từ original title/description; search trả Core provenance hits. Application compose; unified envelope cần contract revision có review. |
| Domain policy | [Taxonomy](../profiles/service-desk/taxonomy.yaml), [priority policy](../../docs/architecture/intelligence/priority-policy.md): 5 categories, root cause > symptom, OTHER khác uncertainty, 16 ô priority; Service Desk / Process Owner sở hữu policy. |
| Ticket fixtures | [12 JSONL](../../fixtures/intelligence/service-desk/tickets/tickets-v0.1.jsonl), [manifest](../../fixtures/intelligence/service-desk/tickets/manifest.json), [DEGRADED](../../fixtures/intelligence/service-desk/tickets/triage-degraded-v0.1.json). Chuyển workspace request vào trusted context; giữ nguyên input và expected labels. Recompute exact-byte dataset digest/size. |
| Knowledge | 5 team-authored Markdown, [manifest](../../fixtures/intelligence/service-desk/knowledge/manifest.json): nội dung/bytes giữ nguyên, exact-byte SHA-256/size hợp lệ; pending rights, null Core bindings, chưa ingest. |
| Draft bổ sung | [Retrieval gold](../../fixtures/intelligence/service-desk/retrieval/gold-v0.1.json): 12 local associations, MANUAL_EXPECTED / NOT BENCHMARK / NOT ENGINE OUTPUT. [Normalization](../profiles/service-desk/normalization.md): NFC, giữ dấu tiếng Việt, versioned case folding/whitespace/alias Wi-Fi. |
| Inventory + handover | [Inventory](../inventory/AI_DEPENDENCY_CANDIDATES.md), [BE brief](AI_N1_BE_REVIEW_BRIEF.md), [README](../README.md). Candidate/adoption/install/transitive/rejected được phân biệt; license chưa verified. |

Các artifact 0.1.0 là proposal trước lần freeze đầu tiên; chưa có runtime consumer.
Profile `service-desk@0.1.0` pin `priority-policy@0.1.0`; sửa matrix/review fallback
sau freeze cần business review và version mới. AI thực thi/giải thích policy, human
override là quyết định nghiệp vụ do App/Core lưu và audit.

## Kiểm chứng local hiện tại

Lệnh: `python scripts/intelligence/validate-foundation.py` — **PASS, exit 0**.
Timestamp UTC của lần kiểm tra artifact: **2026-10-07T17:17:01+00:00**
(08/10/2026, **00:17:01 +07:00**).
Tools có sẵn: Python `3.12.7`, PyYAML `6.0.1`, jsonschema `4.23.0`.
Không cài thêm hay khai báo tool thành dependency runtime.

| Kiểm tra | Evidence |
| --- | --- |
| JSON Schema Draft 2020-12 | 4 schema hợp lệ; 12 triage request/result, triage DEGRADED và search SUCCESS có hit / rỗng / DEGRADED hợp lệ. |
| Domain/annotation | Đủ 5 category, 4 priority level, 16 ô matrix; routing/rule IDs/profile/rules/policy versions khớp. Ambiguous dùng null I/U và MEDIUM review fallback. |
| Evidence/context | INPUT là exact substring của original input; response workspace khớp trusted context. Search hit workspace/correlation khớp request; hit count ≤ top_k. |
| Retrieval semantics | top_k biên 1/10; score hữu hạn, cho phép giá trị ngoài [0,1] theo engine scale. Corpus/index/engine versions bắt buộc. |
| Provenance/rights | 5 knowledge checksum/size/header và dataset digest PASS; local Core binding null, rights pending. Gold chỉ tham chiếu local artifact có thật. |
| Negative validation | **70 trường hợp bị từ chối**: evidence giả/trộn INPUT–knowledge, context/grants sai, priority/routing/version sai, blank input/query, top_k sai, DEGRADED sai, workspace/citation/checksum sai, thiếu runtime versions, confidence ngoài contract, NaN/Infinity và gold benchmark claim. |
| Replaceability | Generic profile reuse, model-version triage và search engine substitution PASS; chỉ kiểm tra schema, không chạy/chọn engine/model. |
| Repository hygiene | `git diff --check`, staged diff check và local Markdown links PASS; scope commit chỉ `intelligence/`, `.backup/` và `.local/` không stage. |

Search probe dùng UUID của record synthetic có thật từ acceptance 07/10/2026
(`529e4be2-53f5-4b45-84f4-aed6c2d82c0c`, lab-a, rev-1, checksum của probe).
Chunk/ranking/index là dữ liệu minh họa trong validator, không phải engine output
và không gán UUID này cho 5 knowledge document. Validator không đọc Core/DB hay
chứng minh UUID issuance, authorization, artifact resolution hoặc adapter security.
Các kiểm tra này cần implementation tests sau BE ratification.

Không tái chạy Maven/Docker/smoke: không thay Core code/config. Evidence Core ở
[acceptance report](AI_N1_ACCEPTANCE_REPORT.md) là lần chạy baseline trước;
không dùng kết quả đó như regression evidence cho code mới. Local validator không
đánh giá engine accuracy; retrieval gold chỉ chuẩn bị future Hit@1/Hit@3/MRR.

## Definition of Ready và gate còn lại

| Pre-BE requirement | Trạng thái |
| --- | --- |
| Triage + search schemas, trusted context, evidence separation, fallback | Hoàn tất ở mức design + local structural validation. |
| Taxonomy, root-cause/OTHER semantics, priority ownership/version trace | Hoàn tất proposal; business approval còn chờ. |
| 12 annotated tickets, 5 exact-byte knowledge manifests, rights/binding boundaries | PASS local; không benchmark, không fake Core binding, không ingest. |
| Inventory, normalization/gold drafts, review brief, evidence metadata sạch | Hoàn tất; chưa adopted runtime dependency/model. |
| Core source/API/schema/infra scope | Không thay đổi. |

BE cần ratify **D1–D5** trong [brief](AI_N1_BE_REVIEW_BRIEF.md): POST/GET role
policy, explicit response mapping, trusted adapter context, Core citation identity
và App/Core audit path. Process/Service Owner duyệt priority policy; Repository
Owner duyệt usage rights/license trước ingest/release.

Sau ratification mới triển khai Core alignment, writer-only/rollback/regression
tests, đồng bộ OpenAPI/requirements rồi chạy lại acceptance. N1 chỉ DONE khi gate
này hoàn tất. Không có FastAPI, rules executor, TF-IDF, embedding/RAG/LLM, vector
DB, model weights hay AI deployment trong package này.
