# AI D2 — Tiến độ triển khai và bàn giao

Baseline: `050a32faf72c8306e719c8384a257b9b48fa2caa`, fetch ngày 27/10/2026.
Nhánh: `feature/ai-d2-intake-portable-delivery`. PR #47/#51 đã nằm trong baseline;
R1/R3 ở PR #52 là dependency bên backend, không triển khai lại trong nhánh này.

| Cụm | Nội dung | Trạng thái |
| --- | --- | --- |
| C0 | Pin baseline, ADR, phạm vi và acceptance | Đã ghi nhận |
| C1 | Server review admission, CAS, timeout/manual/rework | 17 PostgreSQL tests PASS (15 admission + 2 upgrades) |
| C2 | Sealed final contextual assessment và worker single-flight | Chưa kiểm chứng |
| C3 | Duplicate retrieval/incident, feedback và workflow/UI | Chưa kiểm chứng |
| C4 | Full model image, provenance, transport secret và Compose | Chưa kiểm chứng |
| C5 | Offline distribution/onboarding/acceptance | Chưa kiểm chứng |

ET vẫn là bảng mặc định và coordinator override. Không thêm effortSize hay model ET.
Auto canonical merge, chất lượng Gold, SLA/notification của toàn bộ ticket spec và
production GO cần quyết định/receipt độc lập; không dùng synthetic để thay thế.

Artifact local tìm thấy: adapter safetensors và tokenizer tại
`.local/ai07_qwen35_silver_lora_results`. Base serving snapshot và receipt quyền
phân phối chưa được xác minh. Người dùng đã cho phép tải base phục vụ từ Hugging Face;
đang tải revision `23c69c53358a07516b5827588b3fdb12ae78fd65`. Không commit model,
prediction, dữ liệu hoặc secrets.

## C1 — Admission trước review

Migration V14 additive; V8–V13 không sửa. Bật cho ticket mới bằng
`SERVICE_DESK_INTAKE_NEW_TICKETS=true`, mặc định false. Ticket đã có intake vẫn
giữ guard và đường manual nếu tắt route mới. Deadline mặc định 86400 giây là
engineering bound, không phải SLA nghiệp vụ đã duyệt.

`GET tickets/{id}/intake` chỉ đọc metadata; `POST .../intake/manual-release` cần
coordinator độc lập, reason, operationId, expectedContextRevision/expectedVersion.
`ReviewDecision` thêm CAS cho ticket được enroll; legacy không có admission giữ
API cũ. Generic completion cũng kiểm admission. Timeout chuyển MANUAL_REQUIRED;
late answer sau review chỉ bật REVIEW_STALE_NOTICE. Task Flowable review vẫn có
thể tồn tại trước release; chưa claim true BPMN wait ở C1.

Kiểm chứng C1: `IntakeAdmissionIntegrationTest,ServiceDeskMigrationIntegrationTest`
trên PostgreSQL 16.6 tạm: 17 pass, 0 fail/error/skip; source boundary pass. Log giữ
trong `.local/task-execution-full-targeted`. Cụm C2 tiếp tục bổ sung auto progression
và final assessment; C1 đã chặn worker legacy trên ticket có admission.
