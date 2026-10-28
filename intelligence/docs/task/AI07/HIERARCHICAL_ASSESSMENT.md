# AI07 v1.9 — phân loại phân cấp và đánh giá vấn đề

## Phạm vi và nền triển khai

Lấy origin ngày 26/10/2026: AI07 `ada3476e5393`, develop `c6d3933`.
Nhánh `feature/ai07-hierarchical-assessment` được tạo từ AI07 mới nhất và rebase
lên develop; nền sau rebase là `a3920b06ce5d`. Không push hoặc merge trong tác vụ này.

Route mới chỉ đọc:
`POST /api/apps/servicedesk/v1/workspaces/{workspace}/tickets/{id}/assistant/assessment`.
Body duy nhất: `{"contractVersion":"ticket-intelligence-assessment@0.2.0"}`.
Hai route `assistant/classify` và `assistant/assess` giữ hợp đồng cũ.

Qwen đề xuất một trong năm nhãn chính. Bộ phân giải triệu chứng chỉ đề xuất nhãn
con cùng parent, yêu cầu cụm bằng chứng nguyên văn và duy nhất. Thiếu bằng chứng,
nhiều ứng viên hoặc khác parent thì nhãn con null; không đổi nhãn chính. Các nhãn
`*_OTHER` chưa có quy tắc bằng chứng nên không được dùng làm nhãn dự phòng.

Impact và urgency dùng bộ tín hiệu riêng, không nhận category làm đầu vào.
Ma trận priority đọc trực tiếp từ `triage-policy.json`, không sao chép sang Python.
Thiếu một trục hoặc mâu thuẫn trong cùng trục thì priority null. Cụm từ chưa nằm
trong chính sách, lỗi gõ hoặc cách diễn đạt mơ hồ được chuyển sang hỏi bổ sung.
Đây là bộ quy tắc thận trọng ban đầu, chưa phải bộ hiểu ngôn ngữ tổng quát.

Risk chỉ có cờ dự thảo và trạng thái; chưa có rubric được phê duyệt nên level
luôn null. Không có tín hiệu thì `NOT_ASSESSED`, không suy ra LOW. Mọi kết quả
cần người duyệt, confidence null và không có quyền thao tác. Không triển khai ET,
gán việc, gộp trùng, escalation, SLA hoặc sửa priority thực tế trong AI07.

## Chính sách, quyền xử lý và bật thử

Các artifact nằm tại `backend/src/main/resources/apps/servicedesk/intelligence/`:

- `taxonomy-v0.1.json`: 5 parent, 28 child dự thảo; child không có bằng chứng bị tắt.
- `assessment-policy-v0.1.json`: tín hiệu I/U/risk, câu hỏi và slot cần bổ sung.
- `triage-policy.json`: nguồn duy nhất của ma trận 4×4.

Hash bundle được tính từ UTF-8 của chuỗi
`sha256(taxonomy):sha256(assessment-policy):sha256(triage-policy)` theo thứ tự đó.
Byte JSON được ghim LF để hash ổn định trên Windows/Linux. API công bố cả hash
từng artifact; kiểm tra lại byte trước khi trả kết quả.

In hash để bên cấp grant đối chiếu, không tạo hoặc ký grant:

```powershell
python scripts/apps/servicedesk/ai07/assessment-policy-identity.py
```

Cấu hình mặc định tắt:

```properties
serviceDesk.assessment.enabled=false
serviceDesk.assessment.policy-bundle-digest=
serviceDesk.classifier.mode=DISABLED
```

Chỉ chủ sở hữu quyền xử lý độc lập mới cấp grant. Ngoài các trường grant classifier
hiện có, route mới yêu cầu trường có chữ ký `assessmentPolicyBundleDigest` bằng
hash bundle đang triển khai. Grant cũ vẫn dùng được cho classify, không cấp quyền
cho assessment. Không có API tạo grant trong ứng dụng. Không tự ký grant thật
hoặc dùng dữ liệu khách hàng để thử nghiệm.

Sau khi có grant và cấu hình phù hợp, có thể thử SHADOW riêng tư trước ASSISTED.
SHADOW không xuất nhãn chính/con, assessment, attributes, evidence hay câu hỏi
suy ra từ model. DISABLED và PROMOTED từ chối suy luận. Timeout/invalid/unavailable
trong ASSISTED trả DEGRADED và các đề xuất null, không fallback sang classifier cũ.
Một lần suy luận có timeout transport 4 giây; toàn luồng có hạn 5 giây, không retry.

Kiểm tra thời gian trước/sau composition và trước khi công bố; composition có
ngân sách một giây. Kết quả quá hạn bị từ chối. Kiểm tra này không hủy một truy
vấn JDBC đang chạy; timeout kết nối/query của DB cần được vận hành quản lý riêng.

Phân quyền ticket/workspace trước khi suy luận; kiểm tra actor, snapshot và grant
sau khi dựng toàn bộ kết quả. Không giữ transaction DB qua lời gọi model.
Body giới hạn 1 KiB, cấm khóa trùng, JSON nối đuôi, tham số lạ và phiên bản sai.
Phản hồi `Cache-Control: no-store`. Evidence tối đa 64 span, offset Unicode code
point, quote đối chiếu lại với title/description đã lưu. Attributes mang fact cùng
span nguồn. Tối đa ba câu hỏi theo impact, urgency, nhãn con; không yêu cầu bí mật.

## Kiểm tra và giới hạn bằng chứng

Lệnh kiểm tra và kết quả thực tế được ghi trong
[VERIFICATION_V1_9.md](VERIFICATION_V1_9.md). Đánh giá Gold và quy trình riêng tư ở
[HIERARCHICAL_GOLD_EVAL.md](HIERARCHICAL_GOLD_EVAL.md).

Không đưa trọng số, dữ liệu riêng, grant, khóa, log hoặc `.local/` vào Git.
Không lấy agreement của 185 mẫu machine-silver làm độ chính xác Gold. Không suy
diễn p95 end-to-end từ số đo model-only ~713 ms trước đây. Không gọi hosted CI
hoặc Qwen thật là PASS khi chỉ chạy unit test hay transport giả.

## Rollback và gate phát hành

Tắt `serviceDesk.assessment.enabled`, đưa classifier về DISABLED và dừng sidecar
Qwen riêng tư theo quy trình vận hành. Giữ receipt và bundle bất biến để điều tra;
luồng thủ công và endpoint cũ tiếp tục sử dụng theo cấu hình đã được duyệt.
Không tự đổi model hoặc tự định tuyến khi lỗi.

Theo dõi số lượng status/abstain/review, latency từng tầng, hash model/chính sách,
lỗi transport và grant. Không ghi nội dung ticket, span, credential vào log/metric.

Production vẫn `NO_GO`, `selectedModel=null`, PROMOTED bị chặn cho đến khi đủ:
duyệt taxonomy/risk, quyền dữ liệu và license, Gold double-review/adjudication,
holdout độc lập, ngưỡng định trước, hosted CI đúng SHA, E2E phục vụ thật, benchmark
concurrency/cold-start/VRAM và ký duyệt Process Owner/Security/Operations.
