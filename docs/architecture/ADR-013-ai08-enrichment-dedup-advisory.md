# ADR-013 — Bổ sung ngữ cảnh và gợi ý ticket trùng

- Trạng thái: triển khai kỹ thuật; chưa phê duyệt phát hành production.
- Baseline ban đầu: `origin/develop`, `122519e046a1153aebe997a67bb522b914aa78c5`.
- Đồng bộ AI08-SYNC-00: `develop@4cbe38c`; xem
  [báo cáo đồng bộ PR 47](../../intelligence/docs/task/AI08/BASELINE_SYNC_ACCEPTANCE.md).
- Phạm vi: Service Desk, sau AI07; không thay đổi contract assessment v0.2.

## Quyết định

Ứng dụng lưu câu trả lời vào **ticket hiện có** bằng ID đã kiểm tra quyền. Mỗi
lần bổ sung tăng `context_revision` và tạo một sự kiện bất biến. Parser CPU đọc
nội dung gốc cùng các sự kiện theo revision, chỉ đưa ra slot có bằng chứng nguyên
văn, câu hỏi cần bổ sung và ứng viên ticket trùng. Đường này không gọi Qwen.

Các thành phần mới nằm trong `apps/servicedesk`. Core và HPDI giữ cơ chế identity,
audit, quan sát quy trình hiện có; không nhận thêm room/device/taxonomy/R10.
Không tạo port generic khi chưa có nhu cầu dùng chung. Python chỉ tổng hợp bằng
chứng kiểm thử Java, không duy trì một bản policy nghiệp vụ thứ hai.

## Ghi bổ sung

Migration `V10`, dưới `db/migration/apps/servicedesk`, thêm revision vào bảng
ticket và bảng `servicedesk_ticket_clarification`. Không sửa các migration cũ.
FK `(workspace,ticket_id)` chỉ tham chiếu bảng của Service Desk.

Một transaction khóa hàng ticket, kiểm tra replay, kiểm tra revision, cập nhật
revision, thêm sự kiện và ghi audit. Khóa được lấy trước replay để hai request
giống nhau đồng thời vẫn chỉ tạo một sự kiện. Audit chỉ chứa `answerId`,
`previousContextVersion`, `contextVersion`; actor lấy từ identity đã xác thực.
Giao dịch rollback cả revision và sự kiện nếu audit thất bại.

Khóa idempotency là `(ticket_id,actor,client_answer_id)`. Cùng payload chuẩn hóa
trả `200` cùng sự kiện; khác payload hoặc revision cũ trả `409`. Digest dùng JSON
có thứ tự khóa ổn định giữa các lần khởi động. Replay trả revision của **sự kiện**,
không giả làm revision mới nhất của ticket. Client có API đọc revision độc lập AI.

Giữ nguyên title/description, quy trình, phân công và trạng thái. Ticket đã đóng
vẫn có thể nhận bổ sung theo quyền hiện hành; thao tác này không mở lại quy trình.
R0 tạo ticket trùng `clientRequestId` tiếp tục trả `409` như trước.

## Snapshot và bằng chứng

Nguồn là `title`, `description`, `clarification:<server-issued UUID>:<answer index>`.
Mỗi câu trả lời có slot được allowlist, revision và cờ đính chính do người gửi xác
nhận. Không đưa map field tùy ý từ client vào AI07. Offset tính theo Unicode code
point trong đúng nguồn gốc; quote phải khớp nguyên văn, kể cả emoji và Unicode NFD.

Giá trị `USER_REPORTED` không trở thành dữ liệu đã xác minh. Thiếu dữ kiện trả
`UNKNOWN`; nhiều giá trị hoặc lời khai dè dặt trả `CONFLICT`/`AMBIGUOUS`, value null.
Đính chính rõ ràng thay giá trị dùng để so sánh và giữ bằng chứng cũ. Thông tin dè
dặt không có quyền đè lên dữ kiện trước. Không suy tầng từ mã phòng hoặc dựng mã
thiết bị/dịch vụ dùng chung. Grammar và câu hỏi là policy DRAFT có digest.

Adapter tái sử dụng parser factual AI07 trên từng nguồn có giới hạn, rồi ánh xạ
offset về nguồn đó. Không thay đổi input/model/contract AI07. Sau bổ sung, response
ghi `AI07_REFRESH_REQUIRED`; assessment AI07 hiện vẫn chỉ đọc title/description.
Không gọi kết quả cũ là đã đánh giá lại từ câu trả lời mới.

## Truy xuất và quyết định trùng

Fallback ban đầu đọc tối đa 50 ticket tiền nhiệm gần nhất trong 90 ngày theo index
workspace/creator/created_at hiện có. Requester chỉ tìm trong ticket của mình.
Agent/coordinator có thể tìm trong workspace khi có grant `WORKSPACE_REVIEW`.
Query và đọc sự kiện đều theo batch, không quét toàn corpus hoặc N+1 theo ticket.

Process capability quyết định trạng thái mở: đúng workspace, owner, business key,
process key, có history, chưa kết thúc và chỉ một task `handle`/`confirm` đang active.
Không đọc trực tiếp bảng Flowable hoặc thêm cột status làm nguồn sự thật thứ hai.
50 hàng là giới hạn **trước lọc trạng thái mở**; cửa sổ đầy được báo rõ. Cách này
có thể bỏ sót ứng viên cũ hơn hoặc nằm sau các ticket đã đóng. Chưa có bằng chứng
recall@20; chưa triển khai index semantic hoặc projection phòng/thiết bị lưu sẵn.

Engine tách `SAME_TICKET_ID`, `MATCH_READY_FOR_REVIEW`, `POSSIBLE_DUPLICATE`,
`RELATED_INCIDENT_POSSIBLE`, `DISTINCT`, `INSUFFICIENT_EVIDENCE`.
MATCH cần cùng phòng, đúng thiết bị, category đã xác minh, tiền nhiệm đang mở,
symptom tương thích và sự cố đang hoạt động. Mâu thuẫn phòng/thiết bị/category/
symptom là blocker. Nhiều phòng được báo cùng nguồn là tín hiệu related riêng;
không đánh đồng với mâu thuẫn qua các câu trả lời.

Baseline chưa có category đã được duyệt lưu trong ticket. Adapter runtime truyền
category null, vì vậy chỉ có thể đưa gợi ý cần xem xét, chưa phát MATCH. Nhãn
category được xác minh trong test là fixture synthetic, không phải nguồn production.
Không dùng confidence từ classifier hoặc điểm rank làm xác suất. Shared object
mapping chưa được duyệt nên không xác nhận exact duplicate cho dịch vụ dùng chung.

## Quyền và publication

Mặc định `SERVICE_DESK_CONTEXT_ENABLED=false`, `MODE=DISABLED`. `SHADOW` chạy nội
bộ nhưng không trả slot, câu hỏi, candidate hoặc provenance suy luận. `ASSISTED`
luôn cần người duyệt. `PROMOTED` bị từ chối. Lưu bổ sung không phụ thuộc các cờ này.

Grant HMAC được cấp độc lập, pin workspace, scope, digest policy, thời hạn và issuer.
Runtime chỉ xác minh, không có API cấp grant. Kiểm tra grant trước extraction/
candidate lookup và trước response. Actor phải có vai trò trong workspace;
platform-admin không có membership không được tìm chéo workspace.

Requester không nhận ID/title/creator/quote của ứng viên. Reviewer chỉ nhận ID đã
được phép đọc và reason codes; không trả raw title/description. `duplicateOf` và
`canonicalTicketId` luôn null. `confidence=null`, `operationalAuthority=false`,
`autoMergeAllowed=false`. Không tạo/gộp ticket, hoàn thành task hoặc sửa ET/SLA.

Trước response, đọc lại cùng cửa sổ ứng viên, snapshot, workflow, quyền và grant.
Khác digest hoặc revision trả lỗi, không công bố kết quả từng phần. Không cache
prediction trong phiên bản này, nên không có cache theo ticketId bỏ qua workspace.

## Giới hạn và triển khai

Body bổ sung tối đa 16 KiB, 8 answers, 256 ký tự/value, 32 events/ticket; advisory
request tối đa 1 KiB, tối đa 3 câu hỏi, 3 candidates và response 256 KiB. Quá 64
facts/slot thì abstain thay vì giữ một tập con như thể đầy đủ.

Rollback bằng cờ bật/tắt. Giữ dữ liệu bổ sung và migration, không xóa câu trả lời.
Auto-link/canonical workflow, Gold độc lập, SLO DB/end-to-end và phê duyệt rollout
là gate phát hành riêng; synthetic PASS không thay thế các gate đó.
