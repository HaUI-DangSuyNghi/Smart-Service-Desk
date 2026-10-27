# AI D2 — Tiến độ triển khai và bàn giao

Baseline: `050a32faf72c8306e719c8384a257b9b48fa2caa`, fetch ngày 27/10/2026.
Nhánh: `feature/ai-d2-intake-portable-delivery`. PR #47/#51 đã nằm trong baseline;
R1/R3 ở PR #52 là dependency bên backend, không triển khai lại trong nhánh này.

| Cụm | Nội dung | Trạng thái |
| --- | --- | --- |
| C0 | Pin baseline, ADR, phạm vi và acceptance | Đã ghi nhận |
| C1 | Server review admission, CAS, timeout/manual/rework | 17 PostgreSQL tests PASS (15 admission + 2 upgrades) |
| C2 | Sealed final contextual assessment và worker single-flight | 67 focused tests PASS; 13 final pipeline cases |
| C3 | Duplicate retrieval/incident, feedback và workflow/UI | Chưa kiểm chứng |
| C3a | Process-key riêng: intake trước review, handoff và rework | 65 regression tests PASS, gồm 6 true-wait cases |
| C4 | Full model image, provenance, transport secret và Compose | Image offline + GPU HTTP synthetic PASS; tooling đang hoàn thiện |
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

## C2 — Final snapshot và feedback

V15 bổ sung grant digest và review task binding; không sửa V14 đã commit. CPU
screening kiểm readiness trước duplicate Stage A, sau đó seal revision/context/
policy/grants/review round/task ID. Policy duplicate DRAFT chỉ advisory, không hold
hoặc merge. Job background hiện có chỉ nhận ticket intake đã seal; inference ở
ngoài transaction. Unique final-attempt identity và ContextualAttemptStore giữ
logical single-flight, material cache tái dùng category nhưng rebind revision.

Endpoint classify/contextual cũ bị chặn cho ticket enrolled, GET không infer.
Model lỗi, abstain, grant thiếu/đổi hoặc worker interrupted chuyển manual; không
bịa OTHER/confidence. Actor kỹ thuật được kiểm quyền workspace và quyền requester
thật qua BackgroundProcessing; observer/reader role không cho claim review/handle.
Grant directory hỗ trợ file `<workspace>.OWN_TICKET.json` và
`<workspace>.WORKSPACE_REVIEW.json` do issuer cấp, thay vì giả danh người gửi.

Final result chỉ publish khi revision/policy/grants và quyền đọc vẫn hợp lệ.
Requester không nhận candidate IDs. Review cần CAS và task ID được seal; feedback
AI-vs-human append-only cùng transaction, đánh dấu NOT_REVIEWED_GOLD. Late answer
sau review không tự inference hoặc thay assignment.

Kiểm chứng C2: FinalAssessmentIntegrationTest, IntakeAdmissionIntegrationTest,
TicketClassificationOrchestratorTest, ContextProcessingGateTest: **67 pass**, gồm
13 final pipeline cases, 0 fail/error/skip. Bộ riêng trước đó gồm worker identity,
context guard, legacy contextual và admission: 48 pass. Model transport trong
JUnit là synthetic; bằng chứng Qwen thật được giữ riêng ở cụm image.

## C3a — True pre-review workflow

`SERVICE_DESK_INTAKE_TRUE_WAIT=false` mặc định. Khi bật cùng new-ticket admission,
ticket mới dùng `service-desk-intake-reviewed`: chưa có task review/handle lúc tạo.
Coordinator disposition ghi receipt và hoàn tất intake qua WorkInbox trong cùng
transaction; sau đó mới tạo review. Generic task completion không bypass receipt.
Rework quay về intake của cùng process, tăng review round. Flag rollback chỉ đổi
route mới; legacy process và handler mới vẫn được giữ. ET/execution projection
tái dùng backend hiện có, không có model ET.

Kiểm chứng IntakeWorkflowIntegrationTest, IntakeAdmissionIntegrationTest,
FinalAssessmentIntegrationTest, TicketIntegrationTest: **65 pass**, 0 fail/error/skip.
Follower branch chưa được phép complete trực tiếp khi canonical disposition chưa
được thực hiện; không coi việc có BPMN branch là R10 đã hoàn tất.
