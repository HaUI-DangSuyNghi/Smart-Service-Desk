# AI-01 / N1 — Core Data & Provenance Acceptance Report

Acceptance thực hiện: **07/10/2026, Asia/Saigon**.
Báo cáo chuẩn hóa: **08/10/2026, Asia/Saigon**.
Branch: `feature/ai01-data-provenance`.
Trạng thái: Core baseline đã được kiểm chứng trong phạm vi AI được phép;
N1 chưa DONE. Foundation hiện **READY FOR BE REVIEW**, xem
[foundation report](FOUNDATION_REPORT.md) và
[BE review brief](BE_REVIEW_BRIEF.md).

Core acceptance baseline: `c92b38733d00c77bd90b96318f8c70d7b19be5c0`.
Branch HEAD before current foundation edits: `c006278af18e3d29b0f8d542e9cddc199d516695`.
Foundation package state: **COMMITTED** — original package before layout refactor;
inspect with `git show --no-patch --format="%H %s" 430416cf0accedb8f34e94a613583e14e8ad3fea`.
Core smoke/API evidence: historical run on **07/10/2026 +07:00**, against the Core
acceptance baseline above; API **23:19:54–23:19:59 +07:00**. Not rerun during hardening.
Foundation validator evidence: local design-artifact validation on **08/10/2026
+07:00**, against the foundation working tree before package commit; exact run
timestamp and coverage in the foundation report.

## Phạm vi evidence

Đây là **historical Core runtime evidence**, không phải lần chạy mới sau hardening.
Core source/config được giữ nguyên từ baseline; branch có các commit tài liệu/probe
AI nhưng không có Core alignment. Chủ repository yêu cầu review trước DX-Core edits.
Kế hoạch acceptance local: `.local/ai-n1/plans/AI_N1_DATA_PROVENANCE_DETAILED_PLAN.md`.

| Artifact | Bàn giao |
| --- | --- |
| [Data/provenance contract](../../architecture/data-provenance-contract.md) | Field authority, identity, immutable public API, source/version/rights/checksum, isolation, audit; draft chưa ratify. |
| [API probe](../../../../scripts/acceptance/core/metadata-provenance.ps1) | 53 assertion chạy qua APISIX với JWT demo accounts hiện có. |
| [Audit SQL](../../../../scripts/acceptance/core/check-metadata-audit.sql) | Read-only transaction; đối chiếu metadata/audit/request ID. |
| [README](../../../README.md) | Cách chạy, tác động tạo synthetic data và vị trí evidence. |

## Kết quả lần chạy Core ngày 07/10/2026

| Lệnh / kiểm tra | Kết quả lịch sử và giới hạn |
| --- | --- |
| `scripts/start.ps1` | PASS, exit 0; build backend baseline, 4 container healthy; Maven build dùng cache. |
| `scripts/smoke.ps1` | PASS, exit 0; identity, metadata, isolation, 2 workflow thực và concurrent completion. |
| `scripts/acceptance/core/metadata-provenance.ps1` | PASS, exit 0; **53/53 assertion**. |
| Audit SQL với ID/owner/workspace/request ID từ evidence | PASS, đúng 1 `metadata.create`, timestamp/context khớp. |
| Audit SQL với request ID cố ý sai | Từ chối, exit khác 0; đã sửa checker vì psql 16 bỏ qua `\quit 1`. |
| `scripts/check-db.sql` qua psql, `ON_ERROR_STOP=1` | PASS metadata/audit consistency và engine/scope. |
| `docker build --target build -t dx-core-ai-n1-test:0.1.0 backend` | PASS build stage hiện có, không sửa dependency/source. |
| `docker run --rm dx-core-ai-n1-test:0.1.0 mvn -B -o test` | PASS: **3 tests, 0 failures/errors/skips**; Security helper tests có sẵn, không phải MetadataApi regression tests. |
| OpenAPI validation | SKIP: host Python mặc định và Python311 thiếu `openapi_spec_validator`; không cài thêm, không đổi OpenAPI. |
| Git hygiene / Core scope | Diff check PASS; không có thay đổi backend/Core contracts/infra/scripts/docs Core/Compose. |

Host không có `mvn` trên PATH; Maven chạy trong build container Java 17 hiện có.
Đây là kiểm chứng local với volume/data đang có, không phải clean clone hoặc
independent clean run. Bảng giữ kết quả đã ghi nhận trong acceptance report commit
`c006278`; hardening không chạy lại các lệnh Core này.

## Coverage provenance / security

| Case | Evidence / giới hạn |
| --- | --- |
| META-01 | POST 201 bằng member writer + reader. **Chưa kiểm thử writer-only**: realm không có account tương ứng, không tự sửa realm. |
| META-02/03 | Owner khớp `/me.sub`, workspace khớp route; body owner/workspace/id/created_at bị từ chối 400. |
| META-04/05/06 | Reader write 403; foreign workspace write và direct read 403. |
| META-07 | Member lab-b lookup ID lab-a trong route lab-b nhận 404; ID không tồn tại cũng 404. |
| META-08 | Blank/missing/oversized title/source/version/usageRights đều 400. |
| META-09/10 | Checksum missing/blank/sai length/uppercase/non-hex đều 400. |
| META-11 | Owner và unknown property bị từ chối 400. |
| META-12 | DB read-only xác nhận đúng 1 audit, request ID khớp HTTP. **Chưa có failure-injection chứng minh rollback khi audit lỗi hoặc writer-only bị từ chối.** |
| META-13 | Create/read giữ nguyên 9 response fields và 5 caller values; reader khác trong cùng workspace đọc được. |

JWT thiếu/sai nhận 401. SHA-256 là caller-declared trên exact source bytes, chưa
Core-verified file integrity. Metadata UUID là identity một record; title/source/
checksum không thay thế identity. Immutability ở public API create/read, không
phải database-level enforcement.

Evidence API: `.local/ai-n1/metadata-provenance.json` (Git ignored, không token/password).
Started: `2026-10-07T23:19:54.4219097+07:00`.
Finished: `2026-10-07T23:19:59.5975911+07:00`.
Metadata: `529e4be2-53f5-4b45-84f4-aed6c2d82c0c`, workspace `lab-a`.
Request ID: `55c22e0e-1fa2-425b-9847-f053c70b9ff5`.
Probe tạo 1 synthetic record và giữ lại để audit; smoke cũng tạo demo data theo
script. Không xóa/reset dữ liệu. Không tạo thêm record trong hardening.

## Core implementation gate còn chờ

1. **Role policy (D1):** POST hiện gián tiếp cần reader vì `create()` gọi public GET.
   Đề xuất member + writer cho POST, member + reader cho GET; tách internal loader,
   giữ transaction, bổ sung signed writer-only fixture và rollback evidence.
2. **Response boundary (D2):** Thay `SELECT *`/raw DB row bằng 9 explicit columns
   và DTO/mapper, giữ `usage_rights`, `created_at`; không cần migration V1.
3. **Core regression:** MetadataApi tests cho validation/unknown fields, roles/
   workspaces, provenance round trip và stable fields; real-service rollback
   evidence. Mock tests không chứng minh transaction DB.
4. **Alignment sau review:** Đồng bộ OpenAPI/requirements mapping; BE có thể
   promote draft contract vào `docs/architecture/`. D3–D5 (trusted context,
   citation identity, audit path) cần ratify theo BE brief trước implementation.

Các thay đổi này chưa thực hiện; báo cáo không ghi nhận BE/FE/business approval.
Không merge/push. N1 chỉ DONE sau ratification, Core alignment, regression và
acceptance chạy lại xanh. File I/O, server checksum verification, metadata
list/search/update, audit read/retention và AI runtime thuộc phase sau.

## Commit evidence

| Commit | Nội dung |
| --- | --- |
| `1cf9465` | Data/provenance contract draft. |
| `049bb95` | API/SQL acceptance probes và hướng dẫn. |
| `c006278` | Historical Core acceptance report. |
| Commit chứa foundation report hiện tại | Foundation + pre-BE hardening, chuẩn hóa báo cáo; hash tra theo lệnh ở metadata header. |

`intelligence/.gitignore` giữ rule `.backup/` và được đưa vào foundation commit;
các plan local không stage. Không thêm AI fields/dependencies hoặc direct Core DB
access vào runtime; SQL hiện có chỉ là acceptance inspection.
