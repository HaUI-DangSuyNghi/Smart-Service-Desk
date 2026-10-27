# ADR-014 — Vòng đời bổ sung thông tin và đánh giá lại an toàn

Trạng thái: triển khai MVP-A, policy DRAFT; production NO_GO.
Nhánh nền: `0e64b67`, chưa nằm trong `origin/develop` khi bắt đầu.

## Quyết định

Tách readiness theo tác vụ khỏi trạng thái workflow. Thiếu mã thiết bị có thể
cản trở xác nhận trùng nhưng không ngăn phân loại sơ bộ. Safety/security/data loss
hoặc sự kiện sắp diễn ra đi vào manual ngay, không đợi người dùng trả lời.

Contract `ticket-readiness@0.1.0` là additive. Không đổi enum, kiểu hoặc nghĩa của
assessment v0.2 và enrichment v0.1. `blocking` trong signal legacy chỉ mô tả phụ
thuộc của profile đó; không dùng làm lệnh tạm dừng ticket. Contract mới sử dụng
`tasks[].blockedOperations`; không có operation điều khiển Flowable hoặc SLA.

Policy readiness được pin riêng trong grant qua `readinessPolicyDigest`. Grant
cũ tiếp tục dùng được cho API cũ, không tự cấp quyền cho API readiness mới. Câu
hỏi mới có ID gồm policy digest; lượt hỏi thực tế sẽ có requestInstanceId riêng.

MVP-A lưu case/job/deadline trong Service Desk. Worker AI phải được gọi với danh
tính đã xác thực và grant hợp lệ, không giả làm actor từ outbox. Timer nền chỉ
chuyển metadata tới manual và tạo sự kiện chờ thông báo; không suy luận từ dữ
liệu người dùng khi không có danh tính. API/worklist vẫn thấy ticket khi AI tắt.
Khi chưa cấu hình sender, trạng thái delivery là PENDING, không tuyên bố đã gửi.

Reassessment dùng snapshot persisted theo revision, có source map nguyên văn,
guard trước/sau inference và lease/CAS trước ghi kết quả. Model augmentation là
đường opt-in, mặc định tắt; cần grant pin policy input mới và benchmark riêng.

## Giới hạn chủ động

`workflowGateApplied=false` trong mọi response mới. `handle/confirm` vẫn chạy
theo BPMN v1. MVP-B (review/await-info, SLA pause và migration instance) cần chủ
quy trình chốt riêng; không triển khai như một phần của MVP-A.

Các mốc nhắc 1h, hết hạn 24h, khẩn 30m và tối đa 2 vòng là DRAFT engineering
defaults trong policy, không phải SLA đã duyệt. Timeout không đóng hoặc gộp ticket.
