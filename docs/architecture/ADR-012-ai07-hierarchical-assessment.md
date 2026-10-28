# ADR-012: Đánh giá phân cấp ticket chỉ đọc

Trạng thái: chấp nhận triển khai kỹ thuật; taxonomy và rubric rủi ro còn `DRAFT`.

Nền triển khai là AI07 trên origin tại `ada3476e5393`, đã gồm Qwen LoRA
`86d7187`, rebase lên origin develop `c6d3933`. Tiếp tục tuân thủ ADR-011.

Bổ sung `assistant/assessment`, hợp đồng `ticket-intelligence-assessment@0.2.0`.
Tái sử dụng luồng phân loại nội bộ, snapshot đã lưu và được phân quyền, processing
grant độc lập, một lần suy luận và kiểm tra quyền cuối luồng. Hai hợp đồng
`assistant/classify`, `assistant/assess` giữ nguyên.

Service Desk sở hữu taxonomy, trích xuất triệu chứng có bằng chứng nguyên văn,
đánh giá impact/urgency độc lập, cờ rủi ro dự thảo và ma trận từ `triage-policy.json`.
Không sao chép chính sách sang Python. HPDI và Core không phụ thuộc miền nghiệp vụ.
Thiếu hoặc mâu thuẫn bằng chứng thì để null; rủi ro không ghi đè priority.
Confidence và mức rủi ro luôn null. Nhãn con dự thảo phải được người duyệt và
không cấp quyền thực hiện thao tác nghiệp vụ.

Route mới yêu cầu thêm `serviceDesk.assessment.enabled=true` và SHA-256 ghim bộ
ba chính sách, mặc định tắt. Processing grant phải ký thêm trường
`assessmentPolicyBundleDigest`; grant cũ chỉ tiếp tục cấp quyền cho classify.
Xác minh grant trước và sau suy luận, kiểm tra lại byte chính sách trước khi trả kết quả.
SHADOW không công bố nhãn, đánh giá, thuộc tính hoặc bằng chứng. DISABLED và
PROMOTED từ chối suy luận.

Kết quả bất biến, có giới hạn; bằng chứng dùng offset Unicode code point và mã
quy tắc ổn định. Tối đa ba câu hỏi, không yêu cầu bí mật. Body công khai chỉ nhận
phiên bản; thông tin bổ sung phải được lưu qua luồng ticket có phân quyền.
Không sửa phân công, workflow, audit nghiệp vụ, escalation, SLA hay priority thật.

Kiểm thử kỹ thuật không thay thế phê duyệt production. Gold được phân xử độc lập,
quyền dữ liệu, duyệt rubric, holdout niêm phong, hiệu năng phục vụ thật, hosted CI
và ký duyệt phát hành vẫn là gate bên ngoài. `selectedModel=null`, production `NO_GO`.
