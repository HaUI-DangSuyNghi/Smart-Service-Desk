## Giao diện chỉ hiện loading

1. Kiểm tra địa chỉ web có tải được không.
2. Kiểm tra Docker Engine và các dịch vụ bằng `.\dx doctor`, `.\dx ps`.
3. Kiểm tra Keycloak và gateway/backend; frontend có thể đã tải được nhưng còn chờ đăng nhập hoặc API.
4. Xem log đúng dịch vụ rồi tải lại trang sau khi dịch vụ sẵn sàng.

Không kết luận lỗi giao diện chỉ từ một màn hình đang xoay. FE chạy được chưa chứng minh backend và đăng nhập đang hoạt động.

## Docker trả 500 rồi báo thiếu model image

Nếu log có `dockerDesktopLinuxEngine`, `500 Internal Server Error` hoặc `docker version` không trả được phần Server, cần khôi phục Docker Engine trước.

Kiểm tra:

```powershell
docker version
docker image inspect <ten-image-trong-LOCAL_MODEL_IMAGE>
```

Script khởi động hiện có thể gom lỗi kiểm tra image thành thông báo “image is missing”. Lỗi 500/timeout chưa chứng minh image đã bị xóa. Chỉ nạp lại image khi Docker hoạt động và thực sự báo không tìm thấy image.

Nếu Docker Engine không phản hồi, thử Restart Docker Desktop. Nếu vẫn lỗi trên Windows/WSL, thoát Docker Desktop, chạy `wsl --shutdown` rồi mở Docker lại. Lệnh này dừng các phiên WSL đang chạy. Khi `docker version` có đủ Client/Server, thử lại `dx up`.

## Không thấy nút hoặc nhận lỗi quyền

- Kiểm tra tài khoản và workspace đang chọn.
- Xem vai trò/quyền ở trang Tài khoản.
- Kiểm tra bạn có phải người giữ việc, người tạo hoặc người được phép duyệt bước hiện tại không.
- Đăng nhập lại sau khi được thay quyền.
- Nếu là tài liệu, kiểm tra phạm vi Công khai/Nội bộ và quyền tải file riêng.

Một tài nguyên không tồn tại hoặc không được phép xem có thể cùng được trả về thông báo không tìm thấy để bảo vệ dữ liệu.

## Không có người để giao việc

Kiểm tra người được chọn còn là thành viên workspace, thuộc tổ chuyên môn đúng loại sự cố, có lịch trực hợp lệ và không đang nghỉ đột xuất. Xem đề xuất mới khi vừa có thay đổi trạng thái trực hoặc phân loại.

## Không tải lên hoặc đọc được tài liệu

Kiểm tra đuôi file, giới hạn 5 MiB, các trường bắt buộc và khả năng trích xuất văn bản. Với PDF scan, chuẩn bị bản đã OCR; với `.doc`, chuyển thành `.docx`.

File có hình ảnh không có nghĩa hệ thống đọc được nội dung trong hình. Nếu preview báo lỗi toàn vẹn hoặc quyền, không tiếp tục dùng file đó làm căn cứ hỏi đáp cho đến khi được kiểm tra.

## AI tạm không khả dụng

Người dùng có thể tiếp tục đọc nguồn hoặc theo dõi ticket. Điều phối viên xử lý thủ công khi luồng cho phép.

Người vận hành kiểm tra chế độ AI, model/provider, khóa, hạn mức, kết nối và log. Dịch vụ healthy chỉ cho biết khởi động được, chưa chứng minh tài khoản API còn quota hoặc được phép dùng model; startup không mặc định gửi một lần hỏi tính phí để kiểm tra.

## Dữ liệu đã thay đổi hoặc thao tác bị xung đột

Đóng và mở lại form hoặc làm mới dữ liệu, đọc trạng thái hiện tại rồi quyết định tiếp. Không bấm liên tục để ép một quyết định cũ lên công việc đã được người khác cập nhật.

## Đăng nhập báo client không tồn tại hoặc tài khoản mẫu không đúng

Người vận hành kiểm tra địa chỉ Keycloak, cấu hình frontend và realm đang chạy. Realm cũ không tự được ghi đè chỉ vì cập nhật source hoặc chạy lại import.

Không dùng reset realm như bước xử lý thường xuyên. Reset có thể xóa tài khoản, nhóm và cấu hình đăng nhập đã chỉnh trong realm; chỉ người quản trị thực hiện sau khi kiểm tra ảnh hưởng và dữ liệu cần giữ.

Với bản cài cũ thiếu quyền tài liệu mới, dự án có script đồng bộ quyền riêng; tham khảo [tài liệu hỏi đáp](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/blob/develop/docs/KNOWLEDGE_CHAT.md) để đồng bộ và đăng nhập lại, thay vì tự xóa toàn bộ realm.

## Gửi thông tin để được hỗ trợ

Chuẩn bị:

- Màn hình/đường dẫn đang mở và workspace liên quan.
- Thời điểm xảy ra lỗi, múi giờ nếu cần.
- Thao tác vừa thực hiện, kết quả mong đợi và kết quả thực tế.
- Mã ticket/công việc phù hợp để quản trị đối chiếu.
- Nội dung lỗi và **requestId** nếu giao diện cung cấp.
- Ảnh chụp đã che dữ liệu cá nhân hoặc bí mật.

Không gửi nguyên `.env`, token đăng nhập, khóa API hoặc log chưa kiểm tra nội dung nhạy cảm.
