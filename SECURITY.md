# Chính sách bảo mật

Nhóm Smart Service Desk tiếp nhận báo cáo lỗ hổng qua kênh riêng để có thể xác minh, xử lý và phối hợp công bố mà không làm tăng rủi ro cho người sử dụng.

## Báo cáo lỗ hổng

**Không đăng chi tiết khai thác, mật khẩu, token hoặc dữ liệu nhạy cảm vào Issues, Pull Requests hay bình luận công khai.** Gửi email với tiêu đề `[Smart Service Desk][Security] Mô tả ngắn` đến một trong các địa chỉ:

- Nguyễn Minh Hiểu: [minhhieu28405@gmail.com](mailto:minhhieu28405@gmail.com).
- Nguyễn Mạnh Thi: [thikudo2809@gmail.com](mailto:thikudo2809@gmail.com).
- Bùi Huy Hoàng: [buihoang3425@gmail.com](mailto:buihoang3425@gmail.com).

Trong báo cáo, nên có:

- Tag phát hành hoặc commit SHA, thành phần và cấu hình triển khai liên quan.
- Mô tả vấn đề, điều kiện xảy ra và ảnh hưởng dự kiến.
- Các bước tái hiện tối thiểu trên môi trường thử nghiệm do bạn kiểm soát.
- Kết quả mong đợi và kết quả thực tế; log hoặc ảnh chụp đã che thông tin nhạy cảm.
- Cách liên hệ và tên muốn được ghi nhận, nếu đồng ý công khai sau khi xử lý.

Không gửi `.env`, khóa API, cookie phiên, dữ liệu cá nhân thật hoặc bản sao cơ sở dữ liệu trong email. Nếu nghi ngờ lộ thông tin xác thực, mô tả loại thông tin và vị trí liên quan mà không gửi giá trị bí mật.

## Phiên bản và phạm vi tiếp nhận

Nhóm ưu tiên xác minh báo cáo trên bản phát hành ổn định mới nhất và mã nguồn đang phát triển. Báo cáo trên phiên bản cũ vẫn được tiếp nhận, nhưng cần ghi rõ tag/SHA; việc có bản vá cho phiên bản đó sẽ được đánh giá theo từng trường hợp. Chính sách này không cam kết thời hạn hỗ trợ dài hạn hoặc thời gian phát hành bản vá cố định.

Phạm vi bao gồm mã nguồn, hợp đồng API, cấu hình triển khai và các tích hợp được phân phối trong repository. Khi vấn đề thuộc thư viện hoặc dịch vụ bên thứ ba, nhóm sẽ đánh giá ảnh hưởng đến dự án và phối hợp hoặc hướng dẫn báo cáo cho bên phụ trách phù hợp.

## Kiểm tra có trách nhiệm

- Chỉ kiểm tra trên hệ thống của bạn hoặc khi đã được chủ sở hữu cho phép rõ ràng. Chính sách này không cấp quyền kiểm thử bản demo trực tuyến hay hệ thống của người khác.
- Dùng dữ liệu và tài khoản thử nghiệm; không truy cập, sao chép hoặc sửa dữ liệu của người dùng khác.
- Không thử từ chối dịch vụ, dò mật khẩu hàng loạt, cài mã độc hay làm gián đoạn dịch vụ.
- Dừng kiểm tra nếu có nguy cơ ảnh hưởng người dùng hoặc dữ liệu thật; báo riêng cho nhóm.

## Phối hợp xử lý và công bố

Nhóm xem xét báo cáo, có thể yêu cầu thêm thông tin để tái hiện, đánh giá ảnh hưởng và trao đổi về hướng xử lý. Thời điểm công bố cần được thống nhất sau khi có biện pháp giảm thiểu hoặc bản vá phù hợp. Việc ghi nhận người báo cáo chỉ thực hiện khi có sự đồng ý của họ.

Email liên hệ không phải kênh trực sự cố 24/7; nhóm không cam kết SLA phản hồi hoặc chương trình thưởng lỗ hổng. Việc chưa nhận được phản hồi không đồng nghĩa vấn đề đã được xác minh hoặc xử lý.

## Khi nghi ngờ lộ bí mật

Người quản lý hệ thống nên thu hồi hoặc thay thông tin xác thực bị ảnh hưởng và kiểm tra hoạt động liên quan. Chỉ xóa bí mật khỏi phiên bản tệp hiện tại là chưa đủ vì giá trị có thể còn trong lịch sử Git, log hoặc artifact. Không công khai giá trị bí mật để minh họa lỗi.

Lỗi chức năng không liên quan bảo mật có thể báo qua GitHub Issues theo [hướng dẫn đóng góp](CONTRIBUTING.md). Các phản ánh về hành vi cộng đồng được xử lý theo [quy tắc ứng xử](CODE_OF_CONDUCT.md).
