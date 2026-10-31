# Kịch bản demo đánh giá — 10 phút

## Mục tiêu

Thể hiện một yêu cầu hỗ trợ đi qua đúng người, đúng quyền và có lịch sử; sau đó
minh họa ứng dụng Đặt phòng dùng chung nền tảng. Đây là kế hoạch thao tác và kết
quả mong đợi, không phải biên bản nghiệm thu đã PASS.

Bản đối chiếu: `v1.0.0`, commit `f845ff3dd0e2e49cb2a94608daaf365777f58877`.
Nếu demo phiên bản khác, công bố commit thực chạy ngay đầu buổi và ghi khác biệt.
Xem [bản đồ minh chứng](EVALUATION_GUIDE.md) để mở code tương ứng khi cần.

## Chuẩn bị trước buổi demo

- Cài từ source theo [README](../README.md#cài-đặt) trong checkout riêng; build và
  khởi động hoàn tất trước giờ trình bày, không tính thời gian tải image vào 10 phút.
- Dùng môi trường demo riêng, dữ liệu giả và không reset/xóa volume đang có dữ liệu.
  Chọn baseline `scripts/start.ps1 -NoAi`; AI chỉ trình diễn thêm khi đã kiểm tra
  cấu hình đó. Ghi mọi image override, không gọi đó là chạy cấu hình mặc định.
- Kiểm tra dịch vụ thật bằng `docker compose ps -a`, đăng nhập và thử trọn vòng
  xử lý trước buổi demo. Không lấy dòng “Ready” của script làm bằng chứng duy nhất.
- Mở ba browser profile riêng: `requester1`, `coordinator1`, `agent1`, cùng workspace
  `lab-a`. Mật khẩu lấy riêng từ `DEMO_PASSWORD` trong `.env`; không quay màn hình file này.
  Không dùng `superadmin` để thay cả ba vai trò.
- Chuẩn bị một kỹ thuật viên có thể nhận việc và một phòng/khung giờ phù hợp.
  Có thể chuẩn bị ticket dự phòng ở trạng thái đã giao, nhưng phải nói rõ là dữ liệu có sẵn.
- Dùng nội dung giả: “Máy DEMO-A01 không kết nối mạng”, mô tả “Dữ liệu mô phỏng,
  cần kiểm tra kết nối”. Gắn hậu tố giờ phút để nhận diện ticket của lần chạy.
- Tên nút có thể khác theo phiên bản: thao tác theo chức năng hiện có, không sửa
  database hay gọi API ngoài giao diện để che bước bị thiếu.

## Trình tự trình bày

| Thời gian | Vai trò / thao tác | Kết quả mong đợi và lời dẫn |
| --- | --- | --- |
| 0:00–0:45 | Giới thiệu bài toán, sơ đồ ba tầng và commit demo | “Một yêu cầu được theo dõi từ tiếp nhận đến xác nhận; các ứng dụng dùng chung Core và H-P-D-I.” Không dành toàn bộ thời gian để kể công nghệ |
| 0:45–2:00 | `requester1`: tạo yêu cầu hỗ trợ bằng nội dung giả; lưu mã ticket | Ticket xuất hiện trong danh sách/chi tiết của người gửi; mở lại hoặc tải lại để kiểm tra được lưu. Nêu rõ mã sẽ theo dõi xuyên suốt |
| 2:00–3:30 | `coordinator1`: tìm đúng mã, xem nội dung, duyệt/phân loại và giao `agent1` theo các trường bắt buộc | Tiến độ cập nhật và có người xử lý; chỉ vai trò phù hợp thực hiện điều phối. Không dùng thao tác quản trị thay thế |
| 3:30–5:00 | `agent1`: mở việc được giao, bắt đầu nếu quy trình yêu cầu, nhập kết quả “Đã kiểm tra kết nối mô phỏng” và hoàn tất xử lý | Công việc chuyển tới bước người yêu cầu xác nhận; không mặc định kỹ thuật viên hoàn tất là ticket đã đóng |
| 5:00–6:15 | `requester1`: xem kết quả và xác nhận đã giải quyết | Tiến độ/trạng thái phản ánh kết quả, lịch sử thể hiện các vai trò và thao tác. Tải lại trang, đối chiếu cùng mã ticket |
| 6:15–7:15 | Mở lịch sử; giải thích nhánh chưa hài lòng. Nếu đã tập dượt, dùng ticket thứ hai để yêu cầu làm lại | Nhánh làm lại phải được quan sát thực tế mới ghi PASS; nếu chỉ giải thích trên BPMN, ghi “giải thích thiết kế, chưa demo nhánh này” |
| 7:15–8:30 | Mở Đặt phòng, tạo một yêu cầu với phòng/khung giờ hợp lệ; xem chi tiết và tiến độ | Minh họa nghiệp vụ thứ hai trên cùng định danh/nền tảng quy trình. Không cần hoàn tất vòng duyệt đặt phòng trong thời gian này |
| 8:30–9:15 | Mở thư viện tài liệu mẫu được phép công bố; xem trước hoặc tải theo quyền | Minh họa dữ liệu gắn với công việc. Nếu chạy NoAi, nói rõ không trình diễn hỏi đáp mô hình; không đưa kết quả mẫu thành câu trả lời AI trực tiếp |
| 9:15–10:00 | Mở hướng dẫn đánh giá, CHANGELOG và SBOM/checksum | Chỉ vị trí source, hợp đồng, phạm vi minh chứng và cách tái kiểm tra. Chốt lợi ích thay vì tự công bố điểm số |

Nếu chỉ có 5 phút: giới thiệu 30 giây → tạo 60 giây → điều phối 60 giây → xử lý
60 giây → xác nhận/lịch sử 60 giây → chỉ vị trí minh chứng 30 giây. Bỏ phần ứng dụng
thứ hai/tri thức; không tuyên bố các phần bỏ qua đã được demo.

## Khi có câu hỏi về quyền và AI

- Dùng tài khoản đúng vai trò để giải thích phạm vi workspace. Chứng minh bảo vệ
  API cần request thực bằng tài khoản không có quyền, theo hợp đồng và log đã che
  bí mật; chỉ ẩn nút hoặc đăng xuất không đủ chứng minh phân quyền API.
- Chỉ trình diễn truy cập chéo workspace nếu đã chuẩn bị tài khoản, ID dữ liệu giả
  và tập dượt riêng. Không công khai token, không thử trên dữ liệu người dùng thật.
- Nếu AI đang bật: nêu engine/profile, đầu vào, đầu ra và bước con người duyệt.
  Nếu AI không chạy: giữ luồng thủ công và nói rõ giới hạn, không bật mode chưa kiểm tra
  chỉ để hoàn thành kịch bản.

## Dự phòng và ghi nhận kết quả

Nếu thao tác lỗi: ghi bước, thời điểm và thông báo đã che bí mật; không ghi PASS.
Có thể chuyển sang ticket dự phòng hoặc video quay trước, nhưng phải công bố đó là
trạng thái có sẵn/video, ngày và commit của bản ghi, không phải kết quả chạy trực tiếp.
Không reset realm, xóa volume hay sửa trực tiếp dữ liệu để tiếp tục buổi demo.

Mẫu ghi nhận sau khi chạy (để trống trước khi kiểm tra):

| Nội dung | Ghi nhận |
| --- | --- |
| Ngày giờ / người chạy | Chưa ghi nhận |
| Commit thực chạy / tag | Chưa ghi nhận |
| Môi trường / mode AI / override (không có bí mật) | Chưa ghi nhận |
| Mã ticket / yêu cầu đặt phòng giả | Chưa ghi nhận |
| Bước đã chạy và kết quả PASS/FAIL/NOT RUN | Chưa ghi nhận |
| Ảnh/video/log đã che thông tin riêng tư | Chưa ghi nhận |
| Issue và phần còn tồn đọng | Chưa ghi nhận |

Biên bản mới không thay thế [log lịch sử](publication/pof/FINAL_VERIFICATION.md).
Không sửa các file SBOM/hash đã công bố chỉ để thêm ghi nhận demo; lưu biên bản
riêng theo ngày/commit khi thực sự thực hiện.
