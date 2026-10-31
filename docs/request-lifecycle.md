# Sửa, hủy và đóng yêu cầu dùng chung

Tầng 2 cung cấp `RequestContribution`, API `/api/hpdi/v1/workspaces/{workspace}/instances/{id}/request` và component `RequestActions`. Ứng dụng đăng ký các process key, form, chính sách và cập nhật dữ liệu thuộc ứng dụng. Tầng 2 không biết trường riêng của ticket hay đặt phòng.

- Người tạo có quyền `request:create` sửa trước duyệt. Ticket đã từng được xử lý chỉ bổ sung thông tin; không sửa đè nội dung ban đầu.
- Chỉ chủ ticket được sửa/hủy trước lần duyệt đầu tiên và đóng sau duyệt. Điều phối viên không được sửa/hủy/đóng ticket của người khác. Đóng chủ động kết thúc các công việc còn mở, giữ lý do riêng với kết quả kỹ thuật viên xử lý thành công. Ticket có liên kết sự cố phải xử lý liên kết trước. Đặt phòng đã duyệt hoặc từ chối đã kết thúc nên không sửa/hủy tiếp.
- Bắt buộc lý do và `expectedVersion` từ lần đọc hiện tại. Nội dung, phiên bản hoặc bước xử lý thay đổi trả 409; giao diện giữ dữ liệu đang nhập và cho tải lại.
- Mọi thay đổi khóa cùng aggregate quy trình như các thao tác hoàn thành/giao việc. Khóa bận trả 409, tránh chờ chéo khóa ứng dụng/quy trình. Callback ứng dụng kiểm lại phiên bản sau khi khóa dữ liệu riêng.
- Hủy giữ lịch sử engine và đánh dấu `cancelled`, không tính là xử lý thành công. Ticket dừng tiếp nhận, kết quả AI đang chờ và theo dõi cảnh báo; sửa trước duyệt tăng revision, xóa nhãn AI cũ, xếp đánh giá mới.
- `hpdi_request_change` lưu người sửa, lý do, giá trị trước/sau. Dữ liệu ứng dụng, lịch sử và hàng đợi email cùng transaction; rollback không giữ thông báo/email.
- Bảng ticket dùng nút Sửa/Hủy hoặc Đóng; Board đặt cùng thao tác trong menu ⋯; chi tiết thay nút Quản lý yêu cầu bằng nút trực tiếp. Hủy/Đóng luôn có modal xác nhận và lý do. Tiến độ trộn các lần sửa/hủy/đóng theo thời gian với các bước quy trình, xem được nội dung trước/sau.
- Sửa nội dung đặt `content_revision` làm mốc ngữ cảnh mới. Câu trả lời trước mốc vẫn giữ trong dữ liệu lịch sử nhưng không ghép vào nội dung mới; chỉ các lượt bổ sung sau mốc được tính vào giới hạn hỏi AI.

## Realtime và email

Tái sử dụng `LiveUpdates`, `Notifications` và `NotificationMailer`. Realtime chỉ phát sau commit, không chứa nội dung người dùng. Client tải lại qua REST với quyền hiện tại; kết nối lại cũng tải lại dữ liệu. Sự kiện request dùng chung làm mới dữ liệu ứng dụng đang mở mà không đưa tên ứng dụng vào Tầng 2.

Email quan trọng: sửa/hủy/đóng gửi điều phối viên và những người liên quan; đặt phòng mới gửi điều phối viên, duyệt/từ chối gửi người tạo. Ticket giữ thông báo tạo, giao việc, cần bổ sung, chờ phản hồi, báo xong, mở lại và tự đóng; xác nhận đóng cũng gửi người xử lý. Người thực hiện không nhận thông báo của chính mình.

Email gửi qua outbox hiện có, thử lại khi SMTP lỗi. Cần `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` và email người nhận trong Keycloak. Không có địa chỉ email thì đánh dấu SKIPPED; thông báo trong ứng dụng vẫn hoạt động. Không gửi email từ trình duyệt.
