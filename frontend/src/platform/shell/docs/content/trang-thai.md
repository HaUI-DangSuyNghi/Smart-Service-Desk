## Trạng thái ticket

| Trạng thái      | Ý nghĩa                                                                   | Việc cần làm tiếp                                  |
| --------------- | ------------------------------------------------------------------------- | -------------------------------------------------- |
| Đang tiếp nhận  | Đang kiểm tra thông tin hoặc chuẩn bị đề xuất                             | Bổ sung khi được hỏi; điều phối kiểm tra tiếp nhận |
| Theo ticket gốc | Đang theo dõi sự cố đã liên kết                                           | Theo dõi; báo điều phối nếu cần tách               |
| Chờ duyệt       | Chờ quyết định loại, ưu tiên và phân công                                 | Điều phối viên duyệt                               |
| Đã giao         | Đã giao người xử lý, chưa bắt đầu                                         | Người giữ việc kiểm tra rồi Bắt đầu                |
| Đang làm        | Kỹ thuật viên đang thực hiện                                              | Tiếp tục xử lý, cập nhật đúng tình huống           |
| Đang chờ        | Đã tạm dừng vì cần thêm điều kiện                                         | Theo dõi lý do chờ và Tiếp tục khi sẵn sàng        |
| Chờ xác nhận    | Kỹ thuật viên đã báo xong                                                 | Người gửi kiểm tra, chọn Đã ổn hoặc Chưa xong      |
| Mở lại          | Người gửi báo chưa khắc phục                                              | Điều phối tiếp nhận/duyệt lại theo quy trình       |
| Đã đóng         | Quy trình đã kết thúc theo quyết định xác nhận hoặc cơ chế tự đóng hợp lệ | Đọc lịch sử nếu cần tra cứu                        |
| Không xác định  | Không đủ dữ liệu để ánh xạ trạng thái chuẩn                               | Liên hệ điều phối/quản trị để kiểm tra             |

## Mức ưu tiên

| Mức        | Cách hiểu                                        |
| ---------- | ------------------------------------------------ |
| Thấp       | Ảnh hưởng và độ gấp thấp theo chính sách         |
| Trung bình | Cần xử lý theo lịch và hạn được giao             |
| Cao        | Cần ưu tiên xử lý sớm                            |
| Khẩn cấp   | Cần theo dõi và phản hồi khẩn; hạn tính liên tục |

Phân tích có thể sử dụng phạm vi ảnh hưởng và độ gấp để đề xuất ưu tiên. Điều phối viên kiểm tra bằng chứng và chốt quyết định; chỉ dùng từ “gấp” trong mô tả không đảm bảo được duyệt ở mức Khẩn cấp.

## SLA: hạn bắt đầu và hạn hoàn thành

SLA là mục tiêu thời gian cho một lượt xử lý đã được duyệt. Có hai mốc cần phân biệt:

- **Hạn bắt đầu:** cần bắt đầu xử lý trước mốc này.
- **Hạn hoàn thành:** cần báo xử lý xong trước mốc này.

Đối với luồng ticket có duyệt, thời gian SLA bắt đầu khi bước duyệt tạo lượt xử lý hợp lệ, không tính ngược từ mọi thời điểm tiếp nhận. Chờ người gửi xác nhận sau khi đã báo xong không tính tiếp vào hạn xử lý của lượt đã hoàn tất.

| Ưu tiên    | Bắt đầu trong — mặc định | Hoàn thành trong — mặc định | Cách tính                                                |
| ---------- | ------------------------ | --------------------------- | -------------------------------------------------------- |
| Khẩn cấp   | 15 phút                  | 120 phút                    | Liên tục, kể cả ngoài giờ và cuối tuần                   |
| Cao        | 60 phút                  | 480 phút                    | Theo lịch làm việc                                       |
| Trung bình | 240 phút                 | 1.020 phút                  | Theo lịch làm việc; tương đương 2 ngày làm việc mặc định |
| Thấp       | 510 phút                 | 2.550 phút                  | Theo lịch làm việc; tương đương 5 ngày làm việc mặc định |

Các mốc trên là cấu hình khởi tạo. Hãy ưu tiên hạn hiển thị trên công việc vì quản trị có thể đã thay chính sách.

Lịch làm việc mặc định gồm **07:00–11:30** và **13:00–17:00**, từ thứ Hai đến thứ Sáu, theo múi giờ nghiệp vụ mặc định `Asia/Ho_Chi_Minh`. Một ngày làm việc mặc định có 510 phút; không phải 24 giờ. Ngày lễ cần được cấu hình, không tự mặc định có đủ mọi ngày nghỉ theo lịch bên ngoài.

## Những điều dễ nhầm

- **Ước tính 90 phút** là thời gian làm dự kiến, không phải lời hẹn “90 phút kể từ lúc gửi ticket”.
- **Tạm dừng công việc** luôn ngừng ghi nhận một khoảng làm đang chạy, nhưng đồng hồ SLA chỉ dừng theo lý do và chính sách hợp lệ.
- Chờ người yêu cầu trả lời được dừng SLA; chờ linh kiện/lý do khác không tự được miễn tính hạn. Chờ bên ngoài phụ thuộc cấu hình, mặc định vẫn tính.
- Một mức ưu tiên có thể được nâng một bậc vì chờ lâu; việc nâng bậc không đồng nghĩa tự đặt lại hạn hoặc tự phân công người xử lý.
- Khi yêu cầu làm lại được duyệt lần tiếp theo, hệ thống có thể tạo lượt SLA mới; lịch sử lượt cũ được giữ.
- Đổi cấu hình không tự tính lại mọi hạn của các ticket đã duyệt. Các lượt đang xử lý có bản chính sách đã ghi nhận.
