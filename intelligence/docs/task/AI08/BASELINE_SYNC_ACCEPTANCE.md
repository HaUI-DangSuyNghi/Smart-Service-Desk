# AI08-SYNC-00 — Đồng bộ PR 47 với baseline tích hợp

Ngày thực hiện: 27/10/2026. Phạm vi là bước **PR-A / BASELINE ALIGNMENT** trong
kế hoạch intake gate; các bước PR-B đến PR-F tiếp tục theo thứ tự phụ thuộc.

## Nhánh và lịch sử

- PR [#47](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/pull/47) có nhánh
  nguồn `feature/ai08-context-enrichment-dedup`, đích `develop`.
- Đã fetch origin và fast-forward nhánh local từ `0e64b67` tới HEAD PR
  `913f6664cfcede2d1739a8287a5444d54e4bd9cf`, bao gồm AI08-05 qua PR #50.
- Baseline tích hợp là `origin/develop@4cbe38c95676f9a45ee4d56a47e229b930c89bec`,
  chứa backend #48 và #49. Đồng bộ bằng merge commit, giữ lịch sử hai nhánh.
- Merge commit source: `7570c43600f7b7b8859851e3e953b21239a28681`, có đúng hai
  parent là HEAD PR `913f6664` và baseline `4cbe38c9`.
- `origin/main@11773eced0b07ddee6230038f905e5cae56c5250` là tổ tiên của `develop`.
  Theo [CONTRIBUTING](../../../../CONTRIBUTING.md), feature được tích hợp vào
  `develop`; đưa thay đổi lên `main` thuộc quy trình release riêng.

## Thay đổi compatibility

| Phiên bản | Migration sau đồng bộ | Nguồn |
| --- | --- | --- |
| V8 | `servicedesk_review` | Backend, giữ nguyên nội dung |
| V9 | `servicedesk_estimate_history_index` | Backend, giữ nguyên nội dung |
| V10 | `servicedesk_clarification` | AI08, chuyển từ V8 |
| V11 | `servicedesk_enrichment_lifecycle` | AI08-05, chuyển từ V9 |
| V12 | `servicedesk_contextual_attempt` | AI08-05, chuyển từ V10 |

Việc đổi số migration AI08 dành cho chuỗi tích hợp chưa áp dụng vào DB đích.
Không sửa nội dung SQL và checksum migration backend đã phát hành.

`TicketService.create()` tiếp tục tạo `service-desk-reviewed` và phát
`TicketContextChanged` trong cùng transaction. `TicketRepository` giữ
`process_key`, `awaiting_review` và projection HTTP của backend #49.
Event enrollment chỉ ghi metadata/outbox khi feature được cấu hình bật.

`ServiceDeskProcessState` tập trung chính sách trạng thái mở cho duplicate
candidate và enrichment lifecycle. Legacy `service-desk` nhận `handle/confirm`;
`service-desk-reviewed` nhận `review/handle/confirm`. Cả hai kiểm tra process ID,
workspace, creator, business key, history, trạng thái chưa kết thúc/xóa và đúng
một task đang active. Workflow không được hỗ trợ bị loại. Ticket đang chờ review
không còn bị gắn `CANCELLED_BY_PROCESS` chỉ vì khác process key legacy.

Contract assessment/advisory và BPMN cũ giữ nguyên. Flags context, enrichment,
timer và contextual model vẫn mặc định tắt. Chưa có gate trước review,
canonical link hoặc cam kết một lần inference sau intake trong bước này.

## Kiểm chứng

Các test DB dùng PostgreSQL 16.6 tạm, private Docker network và tmpfs. Maven chạy
offline trên image local `ai07-v19-maven:local`; không thay dữ liệu stack đang chạy.

| Kiểm tra | Kết quả |
| --- | --- |
| Backend full PostgreSQL/Flowable | 592 test PASS, 0 fail/error/skip |
| Core-only | 103 test PASS, 0 fail/error/skip |
| Second-domain | 30 test PASS, 0 fail/error/skip |
| Source tier boundaries | PASS |
| Boundary mutation | 41 test PASS |
| Foundation schema/provenance | 145 negative cases PASS |
| OpenAPI Service Desk hiện hành và target | PASS |
| Core contract route/schema regression | PASS |
| HTTP execution contract trên response thật của suite backend | 14 positive/negative checks PASS |
| Frontend lint, typecheck, platform build | PASS |
| Frontend Vitest và production build | 83 test PASS; build PASS |
| Python regression | 1.479 test PASS, 7 skip, 0 fail |

`ServiceDeskMigrationIntegrationTest` tạo schema riêng ở V9, chèn ticket đã có
process ID, quyết định review và lịch sử ước lượng, rồi nâng tới V12. Test kiểm
ba migration mới được áp dụng, Flyway validate thành công, dữ liệu cũ giữ nguyên,
revision khởi đầu bằng 0 và migrate lại không phát sinh thay đổi. Full Spring
suite kiểm chứng boot từ DB rỗng với toàn bộ chuỗi migration.

`ServiceDeskProcessStateTest` kiểm tra các stage của hai workflow, process lạ,
sai correlation/workspace/owner, thiếu history, kết thúc, hủy, task inactive và
nhiều task. Lifecycle integration xác nhận ticket tạo mới ở `AWAITING_REVIEW`
vẫn đánh giá readiness thành công mà không thay đổi workflow. Duplicate suite
hiện có chạy với ticket mới của backend #49, bao gồm candidate đang review và ACL.
Hosted context CI được bổ sung hai suite process-state/migration mới.

Lần full đầu có 592 test, một lỗi do assertion `pg_indexes` của backend chưa lọc
schema: probe nâng cấp tạo thêm index cùng tên ở schema riêng. Assertion đã sửa
để dùng `schemaname=current_schema()` trước khi chạy lại toàn bộ.

Log và Surefire XML nằm trong `.local/task-execution-{full,core-only,second-domain}`;
Python/frontend logs có tiền tố `.local/ai08-sync-`. Đây là bằng chứng local;
không đồng nhất với hosted CI hoặc đánh giá Gold/chất lượng model.
Các suite chạy trên working tree tích hợp trước commit `7570c43`; source backend,
frontend, scripts và contracts không thay đổi từ lần chạy PASS tới merge commit.
Commit tài liệu sau đó chỉ cập nhật Markdown. Muốn có hosted evidence gắn với SHA
remote cuối cùng vẫn phải chạy CI trên SHA đó.

Lệnh chạy lại:

```powershell
python scripts/check-tier-boundaries.py
python scripts/test-tier-boundaries.py
python scripts/intelligence/validate-foundation.py
python scripts/test-core-contract.py
python -m openapi_spec_validator contracts/openapi/servicedesk.yaml
python -m openapi_spec_validator contracts/openapi/servicedesk-target.yaml

& scripts/test-task-execution.ps1 -BuildImage ai07-v19-maven:local -Profile full
& scripts/test-task-execution.ps1 -BuildImage ai07-v19-maven:local -Profile core-only
& scripts/test-task-execution.ps1 -BuildImage ai07-v19-maven:local -Profile second-domain

python -m pytest intelligence/tests -q
npm --prefix frontend run lint
npm --prefix frontend run typecheck
npm --prefix frontend run test
npm --prefix frontend run build
npm --prefix frontend run build:platform
git diff --check
```

Python cần môi trường có dependency validation của repo, gồm
`openapi_schema_validator` và `openapi_spec_validator`. Phiên thực hiện dùng venv
sẵn có `.local/ai07/venv`. Trên Windows, mutation tests cần quyền thư mục Temp;
Vitest/Vite cần quyền tạo tiến trình con. Những lần sandbox từ chối đã được chạy
lại ngoài sandbox, không coi lỗi môi trường là kiểm chứng thành công.

## DB local cũ và startup

Đã đọc lịch sử Flyway của stack `dx-platform`: DB `core` đang có V8 clarification,
V9 lifecycle, V10 contextual attempt của nhánh AI08 thử nghiệm. DB này khác
baseline backend V8/V9, nên không dùng để chạy `start.ps1`/`smoke.ps1` của nhánh
đã đồng bộ. Hai lệnh này chưa được xác nhận trên deployment mới trong phiên này.

Không thực hiện `flyway repair`, đổi lịch sử Flyway hoặc xóa dữ liệu DB đang chạy.
DB synthetic có thể được tái tạo theo runbook riêng khi chủ dữ liệu xác nhận là
disposable. Nếu cần giữ dữ liệu, phải có reconciliation bảo toàn dữ liệu được DBA
duyệt. Kết quả upgrade trong báo cáo chỉ áp dụng cho DB từ backend V9 chuẩn.

## Phần tiếp theo

PR-B phải chứng minh kiến trúc wait/resume và ADR trước khi thêm gate. PR-C bổ
sung coordinator/timeout; PR-D triển khai duplicate preflight trước model; PR-E
thêm admission theo snapshot và release receipt; PR-F kiểm chứng rollout và recovery.
Không suy diễn việc pass compatibility thành khả năng chặn review hoặc phân loại
một lần đã hoàn tất. Production vẫn **NO_GO** tới khi đủ các gate dữ liệu, quyền,
Gold, vận hành và phê duyệt tương ứng.
