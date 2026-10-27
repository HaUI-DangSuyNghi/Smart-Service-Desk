# AI08-05A — Vòng đời bổ sung và reassessment an toàn

## Phần A.1 — Readiness

Commit riêng cho policy theo tác vụ và `POST .../assistant/readiness`.
Đã kiểm tra 27 test Java CPU/contract/grant; source boundary PASS.
Grant cũ không có `readinessPolicyDigest` không được tự dùng cho API mới.

Thiếu room/device để dò trùng không đồng nghĩa phải dừng triage. Readiness của
ASSESSMENT và DUPLICATE_CHECK được trả riêng, mọi response đều advisory. API v0.1
cũ giữ nguyên; không dùng boolean `blocking` legacy để điều khiển workflow.

## Phần A.2 — Case, outbox và timeout

Migration V9 thêm case, outbox, question-request và transition ledger trong app
Service Desk. V8 giữ nguyên. Tạo ticket và append câu trả lời phát event đồng bộ
trong transaction hiện có; listener chỉ ghi metadata/outbox, không gọi model.
Outbox lỗi thì rollback cùng câu trả lời/revision/audit. Replay không phát event.

`SERVICE_DESK_ENRICHMENT_ENABLED=false` mặc định không enroll case mới.
`SERVICE_DESK_ENRICHMENT_TIMERS_ENABLED=false` mặc định không chạy timer; cả hai
nhận true/false. Timer bật thì quét tối đa 50 case/đợt, mặc định 60 giây, khóa
ticket trước case với SKIP LOCKED. Deadline DB là nguồn sự thật, không dùng sleep.

Job sau create/append là durable. Xử lý bằng command có danh tính đã xác thực:
`POST /tickets/{id}/enrichment-state/evaluate`, body chỉ
`{"contractVersion":"ticket-enrichment-lifecycle@0.1.0"}`. Worker ngoài ứng dụng
có thể gọi cùng API bằng principal được cấp quyền; không giả làm actor từ outbox.
UI sẽ gọi command sau commit. Mỗi command nhận tối đa một generation; coalesce
revision cũ, debounce 750 ms, lease 60 giây, tối đa 2 attempts nếu worker chết.

Ứng dụng chưa có primitive danh tính background/notification sender. Scheduler
nội bộ vì thế chỉ chuyển metadata tới manual và tạo outbox PENDING, không gọi AI.
Không tuyên bố tự động gửi email/notification. Question ledger có requestInstanceId
riêng, khác questionId ổn định theo policy. API trả PENDING, không báo đã gửi.

`GET /tickets/{id}/enrichment-state` kiểm quyền ticket; dữ liệu suy luận chỉ trả
khi grant/actor/policy/snapshot còn hợp lệ. Thiếu quyền xử lý AI thì chỉ trả metadata
manual, không quote/câu hỏi cũ. `GET /enrichment-pending` chỉ dành coordinator trong
workspace, tối đa 50 case, vẫn dùng được khi tắt AI. Không có ticket ẩn vì timeout.

Timeout đặt TIMED_OUT + manualReviewRequired, không đóng ticket. Câu trả lời muộn
vẫn được lưu, gắn LATE_ANSWER_REVIEW_REQUIRED; kết quả mới không xóa quyết định
manual. Việc sửa trạng thái process, SLA, assignment hoặc canonical link không nằm
trong đường này. `workflowGateApplied=false` luôn đúng cho MVP-A.

Guard SQL đã được bổ sung để nhận đúng cú pháp PostgreSQL FOR UPDATE SKIP LOCKED /
NOWAIT, không nhầm SKIP thành tên bảng. Mutation test vẫn chặn truy cập bảng private.
Kiểm tra phần A.2: 61 test liên quan PASS, sau bổ sung audit và race/lease thì 11
test lifecycle PostgreSQL/Flowable PASS; source guard và 41 mutation test PASS.

## Phần A.3 — Reassessment có source map và admission bền vững

`POST /tickets/{id}/assistant/assessment-contextual` nhận duy nhất
`{"contractVersion":"ticket-assessment-contextual@0.1.0"}`. Backend tự đọc
snapshot, không nhận title/description/revision hoặc model output từ client.
AI07 assessment v0.2 giữ nguyên nghĩa cũ, không bị biến thành augmented-input.

Facts AI07 chạy theo từng nguồn gốc, cùng typed answers đã lưu. I/U và evidence
được tính lại trên revision hiện tại. Đính chính explicit loại dữ kiện lịch sử của
trục bị sửa; không âm thầm lấy impact/urgency từ v0. Quote giữ Unicode code-point
offset trên đúng source. Source-map chỉ nhận các đoạn nguyên văn; delimiter và
giá trị chuẩn hóa trong model description không được công bố như literal evidence.

Model input chỉ có title/description theo neutral capability hiện có, tối đa 8192
ký tự và request byte budget. Phần bổ sung gồm giá trị USER_REPORTED đã giải quyết
được theo policy; room/mã thiết bị không làm tăng lượt coarse classification.
Khi input model không đổi, category có thể được tái dùng với trạng thái
MODEL_REUSED_MATERIAL; facts, digest và revision vẫn được tính lại, không gọi là
model đã inference lần nữa. Không tái dùng assessment v0.2 làm kết quả contextual.

Migration V10 giữ admission theo workspace/ticket/input/model/policy/quyền xử lý.
Chỉ một caller được gọi transport; caller đồng thời hoặc attempt bỏ dở không
được tự gọi model lần nữa. Cache chỉ chứa category chưa xác minh, không chứa raw
prompt, answer, evidence hay token. Snapshot/actor/grant/process thay đổi sau
inference thì toàn response bị loại. Không giữ JDBC transaction trong transport.

`SERVICE_DESK_CONTEXTUAL_ASSESSMENT_ENABLED=false` mặc định chỉ tính facts CPU.
Để gọi model phải có flag true, classifier ASSISTED, policy digest cấu hình đúng,
grant context có `readinessPolicyDigest` và `contextualPolicyDigest`, cùng grant
AI07 pin assessment/model/prompt. Grant không được ứng dụng tự cấp.

Policy contextual còn DRAFT, model augmented chưa được benchmark/duyệt trên Gold.
Test transport synthetic chỉ chứng minh wiring, nguồn dữ liệu, call budget và
chống stale, không phải chất lượng thực tế của Qwen. Chưa được dùng synthetic
category để mở production. Model lỗi thì DEGRADED/manual, không vòng retry storm.

Câu trả lời đến sau deadline vẫn là late dù timer chưa kịp poll. Câu trả lời đúng
hạn đưa generation mới vào budget xử lý DRAFT 120 giây; deadline chờ cũ không làm
timeout nhầm trong lúc đang xử lý dữ kiện mới. Timeout và answer dùng cùng khóa
aggregate; câu trả lời không bị mất và không tự hủy quyết định manual.
Kiểm tra A.3: 29 test nền PASS; vòng cuối 26 test input/source-map, contextual,
lifecycle và quyền/stale/SHADOW PASS trên PostgreSQL/Flowable. Chưa chạy Qwen thật
trên input augmented vì chưa có benchmark/grant phát hành cho policy này.

## Gate chưa phải quyết định production

Mốc nhắc/hết hạn/số vòng là DRAFT trong readiness policy; cần process owner duyệt.
MVP-B (BPMN review/await-info, pause SLA, migration instance) chưa được triển khai.
Không nâng model hoặc dùng kết quả synthetic làm Gold. Hosted CI chưa được xác minh.
