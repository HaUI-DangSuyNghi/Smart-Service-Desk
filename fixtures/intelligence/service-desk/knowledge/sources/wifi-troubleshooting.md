# Hướng dẫn xử lý nhanh sự cố Wi-Fi phòng thực hành

- Artifact ID: `kb-network-001`
- Version: `0.1.0`
- Source: `team-authored:knowledge/wifi-troubleshooting`
- Rights status: `PENDING_OWNER_APPROVAL`; chưa cấp license, chưa ingest public corpus.

## Mục tiêu

Kiểm tra ban đầu khi một hoặc nhiều máy không truy cập Wi-Fi/Internet. Đây là hướng
dẫn tự soạn cho tình huống synthetic, không xác nhận cấu hình hạ tầng thực tế.

## Kiểm tra nhanh

1. Xác định phạm vi: một máy, vài máy, cả phòng hay nhiều phòng.
2. Kiểm tra máy có nhìn thấy SSID và có kết nối Wi-Fi không.
3. Nếu chỉ một máy, kiểm tra Wi-Fi đã bật, thử kết nối lại và ghi trạng thái IP.
   Chỉ quên mạng nếu người dùng biết cách kết nối lại theo quy trình được phép.
4. Nếu nhiều máy cùng lỗi, ghi số máy, SSID/phòng và thời điểm; không yêu cầu mọi
   người reset máy hoặc thay cấu hình mạng đồng loạt.
5. Nếu Wi-Fi kết nối được nhưng một ứng dụng lỗi, thử dịch vụ khác được phép.
   Internet bình thường và chỉ ứng dụng lỗi chưa đủ để kết luận NETWORK.
6. Nếu lỗi xác thực do tài khoản bị khóa, chuyển kiểm tra ACCOUNT; không yêu cầu
   người dùng gửi mật khẩu, token hoặc OTP vào ticket.

## Thông tin ticket và escalation

Ghi phòng, số thiết bị, SSID, thời điểm, trạng thái Internet và workaround được phép.
Không tự đổi DNS, cấu hình access point hay quyền truy cập. Cả phòng mất mạng,
chặn buổi thực hành hiện tại, không có workaround: xem xét I3/U3 → HIGH. Nhiều phòng
hoặc hoạt động quan trọng bị chặn cần coordinator review theo chính sách priority.
