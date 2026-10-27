# PR 47 — Xử lý conflict sau backend PR #51

Ngày: 27/10/2026. Nhánh nguồn: `feature/ai08-context-enrichment-dedup`.
Merge commit source: `d19fa609c747b30c30cc462c101d7bd9a3365965`, giữ cả hai parent
`9057dd7` và `b9e2a55`. Các suite chạy trên working tree được commit thành SHA
này; commit tài liệu tiếp theo chỉ thay Markdown.

## Nguyên nhân và cách hợp nhất

Sau lần đồng bộ với backend #49, `develop` có thêm PR #51, HEAD
`b9e2a5537cf047a634f2e98698f217ea587f1575`. Nhánh PR 47 ở `9057dd7` phát sinh
conflict constructor `TicketService`: AI08 thêm `ApplicationEventPublisher`,
backend thêm `BackgroundTicketAssessment`. Merge giữ cả hai dependency.

Trong transaction tạo ticket, backend vẫn tạo workflow reviewed và enqueue
background assessment, ghi audit; AI08 vẫn phát `TicketContextChanged` để enroll
enrichment khi bật flag. Không gọi model trực tiếp trong transaction tạo ticket.
Enrichment enrollment thất bại phải rollback ticket/process/audit cùng background
job và lịch sử job. Không bỏ một nhánh hành vi để giải conflict.

Backend #51 đã dùng V10, nên giải marker Java chưa đủ để backend boot được.
Chuỗi migration sau hợp nhất:

| Version | Migration | Xử lý |
| --- | --- | --- |
| V8 | `servicedesk_review` | Giữ nguyên backend |
| V9 | `servicedesk_estimate_history_index` | Giữ nguyên backend |
| V10 | `servicedesk_background_assessment` | Giữ nguyên backend #51 |
| V11 | `servicedesk_clarification` | Đổi số AI08, giữ nguyên SQL |
| V12 | `servicedesk_enrichment_lifecycle` | Đổi số AI08, giữ nguyên SQL |
| V13 | `servicedesk_contextual_attempt` | Đổi số AI08, giữ nguyên SQL |

Áp dụng chuỗi này cho DB mới hoặc DB từ backend chuẩn V9/V10. DB từng chạy
migration AI08 dưới số cũ cần phân loại và reconciliation riêng nếu giữ dữ liệu.
Không sửa checksum, repair Flyway hoặc xóa DB local đang chạy trong phiên này.

## Kiểm chứng sau hợp nhất

`ServiceDeskMigrationIntegrationTest` kiểm tra cả V9→V13 và V10→V13 trong schema
synthetic riêng: ticket/process reference, review và estimate history giữ nguyên.
Nhánh V10 còn lưu một background assessment PENDING cùng lịch sử QUEUED trước
nâng cấp và đối chiếu toàn bộ row sau nâng cấp. Migrate lại không phát sinh SQL.

Lifecycle integration kiểm tra ticket mới có cả background job lẫn enrichment
outbox. Test rollback tạo ticket làm enrollment lỗi có chủ đích, xác nhận không
còn ticket/process/audit hoặc background job/history được commit riêng lẻ.

| Kiểm tra | Kết quả |
| --- | --- |
| Full backend PostgreSQL/Flowable, gồm 20 test background assessment | 615 PASS, 0 fail/error/skip |
| Core-only / second-domain | 104 / 30 PASS, 0 fail/error/skip |
| Tier boundaries và mutation | PASS; 42 mutation test PASS |
| Frontend lint, test, production/platform build | PASS; 83 Vitest PASS |
| HTTP execution contract | 14 positive/negative checks PASS |
| HTTP background assessment contract | 9 positive/negative checks PASS |
| OpenAPI Service Desk hiện hành và target | PASS |
| Whitespace / conflict markers | PASS |

Runner: `scripts/test-task-execution.ps1` với `-BuildImage ai07-v19-maven:local`
và lần lượt `-Profile full`, `-Profile core-only`, `-Profile second-domain`.
Runner mới của backend #51
tạo riêng DB `background_assessment` và truyền `BACKGROUND_TEST_JDBC_URL`, do đó
suite background thực sự chạy. Test dùng PostgreSQL tạm/private network; không
thay stack local. Log/XML giữ trong `.local/task-execution-*`.

Python runtime không đổi trong lần xử lý conflict này; kết quả 1.479 pass/7 skip
ở báo cáo trước là bằng chứng lịch sử. Hosted CI phải kiểm riêng trên SHA push
mới; không dùng kết quả local để tự tuyên bố hosted CI PASS.

## Phạm vi

Giữ worker background của backend #51 và các guard/service identity hiện có.
Worker này chưa được chuyển thành pipeline intake gate mới: không khẳng định
duplicate preflight đã chạy trước model hoặc mọi ticket chỉ phân loại một lần
sau enrichment. Các bước PR-B→PR-F vẫn thực hiện theo kế hoạch riêng. Không bật
thêm feature flag, không merge PR vào develop/main, không triển khai production.
