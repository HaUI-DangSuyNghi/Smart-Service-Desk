# Hướng dẫn sự cố tài khoản và đăng nhập

- Artifact ID: `kb-account-001`
- Version: `0.1.0`
- Source: `team-authored:knowledge/account-access-guide`
- Rights status: `PENDING_OWNER_APPROVAL`; chưa cấp license, chưa ingest public corpus.

## Dấu hiệu ACCOUNT

Tài khoản bị khóa, sai mật khẩu, thiếu quyền hoặc xác thực thất bại trong khi user
khác dùng được cùng dịch vụ. Nhiều account lỗi sau đồng bộ là một tín hiệu để hỏi
phạm vi, không phải bằng chứng chắc chắn về nguyên nhân.

## Kiểm tra ban đầu

1. Xác nhận kết nối Internet và xác định lỗi trước hay sau bước đăng nhập.
2. Ghi thông báo lỗi, thời điểm và dịch vụ; che thông tin riêng tư không cần thiết.
3. Không thu thập password, access token, secret, OTP hoặc mã khôi phục trong ticket.
4. Hướng dẫn người dùng theo quy trình khôi phục chính thức khi quy trình đã được
   xác nhận; không tự reset mật khẩu, mở khóa hoặc cấp quyền thay nhân sự được phép.
5. Nếu nhiều người bị ảnh hưởng, xác định một nhóm, cả lớp hay nhiều phòng và hoạt
   động có bị chặn hiện tại không. Không suy priority chỉ từ từ khóa đăng nhập.

## Root cause và escalation

Wi-Fi dùng được với người khác nhưng tài khoản cụ thể báo khóa: đề xuất ACCOUNT,
IT-ACCOUNT thay vì NETWORK. I1/U2 → LOW nếu chỉ một người và có thể chờ; I2/U3 →
HIGH nếu nhóm bị chặn công việc hiện tại. Coordinator quyết định xử lý, không dùng
gợi ý AI như quyền cho phép thay đổi account hoặc workflow.
