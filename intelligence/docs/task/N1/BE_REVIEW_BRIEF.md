# AI-01 / N1 — BE Review Brief

Cập nhật: **08/10/2026, Asia/Saigon**. Branch: `feature/ai01-data-provenance`.
Trạng thái: **READY FOR BE REVIEW — design freeze candidate, PROPOSED**.
N1 chưa DONE; cần BE ratification, Core alignment và Core regression evidence.

## Package đã hoàn thiện ở mức proposal

| Nội dung | Contract / evidence |
| --- | --- |
| Data/provenance | [Field authority, immutable API, checksum trust](../../architecture/data-provenance-contract.md); Core sở hữu metadata/files/auth/audit, Intelligence chỉ giữ derived state rebuildable. |
| Triage + retrieval | [Capability contract](../../../../contracts/intelligence/v1/contract.md); 4 JSON Schema, trusted server-injected context; auth denial trước corpus/cache/engine, không phải DEGRADED. |
| Evidence + fallback | Triage v0.1 chỉ INPUT từ original input; search trả Core provenance hits; Application compose kết quả. DEGRADED yêu cầu manual review. |
| Service Desk profile | [5 categories](../../../profiles/service-desk/taxonomy.yaml), root cause ưu tiên symptom; OTHER không đồng nghĩa uncertainty. |
| Priority proposal | [Impact × Urgency](../../architecture/priority-policy.md); Service Desk / Process Owner sở hữu policy, Intelligence thực thi/giải thích; approval nghiệp vụ còn chờ. |
| Dữ liệu và inventory | 12 synthetic MANUAL_EXPECTED ticket; 5 knowledge document với exact-byte SHA-256, rights pending, null Core bindings; dependency/model chưa adopted. Retrieval gold và normalization chỉ là draft. |
| Kiểm chứng local | [Foundation report](FOUNDATION_REPORT.md): 4 schema, 16 ô priority, manifests và 70 negative cases PASS; không chạy engine hay đo accuracy. |

Core runtime evidence ngày 07/10/2026 thuộc baseline
`c92b38733d00c77bd90b96318f8c70d7b19be5c0`, được giữ riêng trong
[acceptance report](ACCEPTANCE_REPORT.md); đợt hardening không tái chạy Core.

## Năm quyết định cần BE / Repository Owner ratify

| Decision | Hiện trạng / đề xuất cần review |
| --- | --- |
| **D1 — Metadata role policy** | POST hiện cần writer + reader vì `create()` gọi GET. Đề xuất POST = workspace member + writer; GET = member + reader, tách internal loader và giữ transaction. Cần signed writer-only fixture và rollback evidence. |
| **D2 — Metadata response** | Thay raw DB row / `SELECT *` bằng explicit columns và DTO/mapper. Giữ nguyên 9 wire field, đặc biệt `usageRights`, `createdAt`; không cần migration V1. |
| **D3 — Trusted context** | App/Core authorize workspace/profile trước; adapter construct `context.workspaceId`, rồi mới cache/corpus/engine. Intelligence không grant access. Cần implementation tests; JSON Schema không chứng minh authorization. |
| **D4 — Citation identity** | Identity = authorized workspace + Core-issued `metadata.id`; artifact state = version + exact-byte checksum. Không dùng title/URL/local `kb-*` làm authority; UUID shape không chứng minh Core issuance. |
| **D5 — Audit path** | Intelligence trả suggestion metadata; App/Core ghi accepted decision / human override qua Core audit. Intelligence không ghi trực tiếp Core DB. |

Process/Service Owner cần duyệt riêng matrix/review fallback; `service-desk@0.1.0`
pin `priority-policy@0.1.0`. Owner cần chốt usage rights/license trước corpus ingest
hoặc release. Commit package không thay thế các approval này.

## Gate tiếp theo

Sau khi ratify D1–D5, BE triển khai Core alignment trong phạm vi được duyệt,
đồng bộ OpenAPI/requirements và bổ sung regression/transaction tests; chạy lại
acceptance trước khi đánh dấu N1 DONE. Đợt này chỉ thay `intelligence/`.

N1 chưa có FastAPI, rules executor, TF-IDF, vector DB, embedding, LLM/provider hoặc
production AI deployment để review. Chưa ingest 5 knowledge document; các tool
Python hiện có chỉ dùng kiểm tra artifact local.
