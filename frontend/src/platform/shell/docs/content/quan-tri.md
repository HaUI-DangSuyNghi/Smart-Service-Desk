## Quản trị tài khoản và quyền

Từ menu workspace, quản trị nền tảng mở **Quản trị phân quyền** để truy cập giao diện Keycloak.

Công việc thường gặp:

1. Tạo hoặc cập nhật tài khoản theo chính sách của đơn vị.
2. Thêm người dùng vào đúng workspace và nhóm vai trò.
3. Thiết lập nhóm chuyên môn cho kỹ thuật viên.
4. Kiểm tra các quyền cần thiết với nghiệp vụ thực tế.
5. Yêu cầu người dùng đăng nhập lại sau khi thay đổi quyền.

Việc cấp quyền quản trị Keycloak và quản trị nền tảng cần được thực hiện bằng tài khoản có thẩm quyền. Không sử dụng tài khoản quản trị chung cho công việc hỗ trợ hằng ngày.

## Cấu hình hệ thống

Mở **menu workspace → Cấu hình hệ thống**. Bộ cấu hình hiện tại dùng chung cho các workspace, không phải bảng cấu hình riêng cho workspace đang chọn.

| Nhóm cấu hình      | Tác dụng                                                                                     |
| ------------------ | -------------------------------------------------------------------------------------------- |
| Giờ làm việc       | Khung ca để tính hạn và đăng ký trực                                                         |
| Phân công          | Giới hạn việc đang làm, hệ số tải việc chờ, ngưỡng so sánh tải                               |
| Ước tính           | Điều kiện dùng dữ liệu công việc tương tự, khoảng lấy mẫu và loại bỏ mẫu ngoại lệ            |
| Hạn xử lý          | Mục tiêu bắt đầu/hoàn thành theo ưu tiên, nâng bậc khi chờ lâu, tính thời gian chờ bên ngoài |
| Ca trực và ngày lễ | Mở đăng ký ngày nghỉ, yêu cầu duyệt ca, khai báo ngày lễ                                     |
| Cảnh báo           | Ngưỡng gần hạn, nhắc việc chờ, mở lại chưa nhận, khẩn cấp chưa duyệt                         |
| Đóng ticket        | Số ngày tự đóng khi chờ xác nhận                                                             |
| Service Desk       | Loại sự cố → tổ xử lý, bảng ước tính mặc định và tùy chọn dữ liệu mẫu                        |

Sau khi thay đổi, bấm **Lưu cấu hình chung** hoặc **Lưu cấu hình Service Desk** đúng phần đã sửa. Nếu người khác vừa cập nhật, tải và kiểm tra bản mới trước khi lưu lại.

Giá trị mới áp dụng cho những thao tác/lượt xử lý tiếp theo theo từng chính sách. Ticket đã duyệt giữ hạn và ước tính đã tính lúc duyệt; hạn xác nhận đã được tạo cũng không tự được viết lại chỉ vì đổi cấu hình.

<!-- TODO: Ảnh A16 — Cấu hình hệ thống. Chụp các nhóm cấu hình chính và nút lưu. Đặt chú thích “cấu hình dùng chung” và “không tự đặt lại hạn của ticket đã duyệt”. -->

## Vận hành AI

Phân biệt lựa chọn cho **phân loại** và lựa chọn cho **hỏi đáp**. Dùng Qwen local để phân loại không tự đảm bảo hỏi đáp tài liệu hoạt động nếu adapter/API cần thiết chưa được cấu hình.

Khóa API được cấu hình phía máy chủ. Người dùng nghiệp vụ không cần và không nên nhập khóa vào giao diện frontend. Với chế độ API bên ngoài, đơn vị cần xác định loại dữ liệu được phép gửi trước khi cung cấp tài liệu cho hỏi đáp.
