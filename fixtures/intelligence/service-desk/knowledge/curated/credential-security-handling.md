# Bảo vệ thông tin bí mật trong yêu cầu hỗ trợ

Nhân sự hỗ trợ không yêu cầu người dùng gửi thông tin bí mật qua ticket. Nội dung kiến thức phải giúp giảm thu thập dữ liệu nhạy cảm, không hướng dẫn vượt kiểm soát truy cập.

## Khi dùng
Người báo hỏi có cần gửi mật khẩu, mã xác thực hay ảnh chứa thông tin đăng nhập không. Phân biệt thông báo lỗi cần chẩn đoán với dữ liệu bí mật không cần biết.

## Dấu hiệu
Password, OTP, access token, recovery code và khóa riêng có thể cho phép truy cập tài khoản. Chỉ nhắc tên loại thông tin trong tài liệu; dữ liệu mẫu dùng ký hiệu như token-placeholder, không dùng giá trị thật.

## Kiểm tra ban đầu
Yêu cầu che phần nhạy cảm trước khi gửi ảnh hoặc log theo quy trình được phép. Nếu đã lộ, báo đầu mối an toàn và làm theo quy trình xử lý của đơn vị; không sao chép secret sang ticket khác. Khôi phục tài khoản phải qua kênh chính thức đã được xác minh.

## Loại trừ
Không đưa hướng dẫn vô hiệu bảo vệ, cấp quyền quản trị hay mở khóa tùy ý. Không tự xưng tài liệu là chính sách chính thức khi chủ sở hữu chưa duyệt.

## Liên quan
Hướng dẫn xác thực mô tả triệu chứng truy cập; người có quyền quyết định thao tác thực tế.
