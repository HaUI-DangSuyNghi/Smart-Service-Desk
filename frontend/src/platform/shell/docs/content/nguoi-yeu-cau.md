## Tạo một ticket hỗ trợ

1. Chọn workspace xảy ra sự cố.
2. Mở **Smart Service Desk**.
3. Bấm **Tạo ticket**.
4. Điền tiêu đề và mô tả.
5. Nếu biết, bổ sung phòng, phạm vi bị ảnh hưởng và tình trạng công việc ngay trong form.
6. Gửi yêu cầu và chờ thông báo thành công.
7. Kiểm tra trang chi tiết ticket vừa được tạo.

| Trường               | Cách điền                                                                      | Ví dụ                                                                                               |
| -------------------- | ------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------- |
| Tiêu đề              | Tóm tắt sự cố trong một dòng; tối đa 200 ký tự                                 | Máy 15 phòng A201 không lên nguồn                                                                   |
| Mô tả                | Nêu hiện tượng, thời điểm, phạm vi, việc đang bị ảnh hưởng; tối đa 4.000 ký tự | Máy không sáng đèn khi bấm nguồn. Các máy bên cạnh vẫn chạy. Tôi cần dùng máy để thực hành lúc 14h. |
| Phòng                | Không bắt buộc; điền mã phòng nếu biết                                         | A201                                                                                                |
| Phạm vi ảnh hưởng    | Không bắt buộc; chọn mức phù hợp với thực tế                                   | Một người hoặc một máy                                                                              |
| Tình trạng công việc | Không bắt buộc; cho biết bị dừng hoàn toàn hay còn cách làm khác               | Bị dừng, không làm tiếp được                                                                        |

Mô tả tốt giúp giảm số lần hỏi lại. Đừng ghi mật khẩu, mã OTP hoặc khóa truy cập trong ticket.

Nếu có ô chọn dữ liệu mẫu, đó là tính năng phục vụ thử nghiệm được quản trị viên bật. Khi gửi yêu cầu thật, hãy tự điền thông tin sự cố thực tế.

<!-- TODO: Ảnh A03 — Form tạo ticket. Chụp đầy đủ form với dữ liệu ví dụ trên, chỉ rõ hai trường bắt buộc và phần thông tin bổ sung. Không dùng ticket chứa dữ liệu cá nhân thật. -->

## Theo dõi yêu cầu đã gửi

Có hai cách:

- **Smart Service Desk:** xem ticket và thông tin chuyên biệt của sự cố.
- **Yêu cầu → Của tôi:** xem các yêu cầu mình đã tạo, gồm ticket và quy trình khác như đặt phòng.

Bấm một dòng để mở chi tiết. Kiểm tra trạng thái, người xử lý nếu có, tiến độ quy trình và thông báo liên quan.

**Chờ xác nhận** nghĩa là kỹ thuật viên đã báo xử lý xong, còn bạn cần kiểm tra kết quả. **Đã đóng** là trạng thái khác với việc kỹ thuật viên vừa báo xong.

## Bổ sung thông tin

Khi ticket thiếu thông tin, mở ticket và chọn **Bổ sung thông tin** nếu giao diện yêu cầu hoặc cung cấp hành động này.

1. Đọc câu hỏi hoặc chọn loại thông tin cần bổ sung.
2. Điền câu trả lời cụ thể: phòng, mã máy, triệu chứng, số thiết bị ảnh hưởng hoặc thời điểm cần khắc phục.
3. Bấm **Gửi bổ sung**.
4. Chờ thông báo đã lưu.

Ví dụ: thay vì “vẫn lỗi”, hãy ghi “PC-A201-15 vẫn không sáng đèn nguồn; tôi đã thử ổ cắm bên cạnh”.

Nếu thông báo nội dung đã được cập nhật ở nơi khác, kiểm tra thông tin mới rồi gửi lại. Gửi bổ sung không tự phê duyệt hoặc tự mở lại công việc đã đi qua bước tiếp nhận.

## Xác nhận kết quả hoặc báo chưa xong

Khi nhận thông báo ticket **Chờ xác nhận**:

1. Mở ticket hoặc hành động xác nhận trong trang **Yêu cầu → Của tôi**.
2. Thử lại thiết bị hoặc thao tác từng gặp lỗi.
3. Chọn **Đã ổn** nếu sự cố đã được khắc phục.
4. Nếu vẫn lỗi, chọn **Chưa xong**, nhập phần còn lỗi rồi bấm **Gửi**.

Nếu báo chưa xong, ticket được mở lại và quay về bước tiếp nhận/duyệt phù hợp với quy trình. Không mặc định rằng việc sẽ lập tức được giao lại cho kỹ thuật viên cũ.

Theo cấu hình mặc định, ticket có thể tự đóng sau **3 ngày liên tục** ở bước chờ xác nhận nếu không có phản hồi và đáp ứng điều kiện xử lý tự động. Hãy kiểm tra hạn xác nhận thực tế trên ticket; đơn vị có thể thay đổi số ngày này.

<!-- TODO: Ảnh A04 — Xác nhận kết quả. Chụp ticket Chờ xác nhận và hộp thoại hai lựa chọn Đã ổn/Chưa xong; bổ sung ảnh ô “Còn lỗi gì?” khi chọn Chưa xong. -->

## Khi sự cố được liên kết với ticket gốc

Trạng thái **Theo ticket gốc** cho biết yêu cầu đang theo dõi một sự cố đã được điều phối viên liên kết. Đây không phải thông báo xóa hoặc bỏ qua yêu cầu của bạn.

Theo dõi tiến độ tại ticket của mình. Nếu tình huống thực tế khác sự cố chung, cung cấp thông tin cho điều phối viên để xem xét tách và tiếp nhận độc lập.

## Trao đổi bằng bình luận

Trong trang chi tiết, mở phần **Bình luận**, bấm **Thêm bình luận...**, nhập nội dung và chọn **Lưu**. Mỗi bình luận tối đa 2.000 ký tự; dùng để trao đổi tình hình hoặc ghi nhận kết quả kiểm tra.

Bình luận không tự đổi trạng thái ticket. Nếu được yêu cầu bổ sung thông tin có cấu trúc, hãy dùng **Bổ sung thông tin**; nếu cần mở lại, dùng **Chưa xong** trong bước xác nhận. Viết “chưa xong” vào bình luận không thay thế thao tác đó.

## Hỏi AI trong ticket

Nếu bản triển khai bật trợ lý, mở phần **Hỏi AI** của ticket, nhập câu hỏi tối đa 2.000 ký tự rồi chọn **Gửi câu hỏi** hoặc **Tìm hướng dẫn**, tùy chế độ đang hiển thị.

Đọc nhãn kết quả để phân biệt **Gợi ý từ model · Chưa đối chiếu tài liệu nội bộ** với hướng dẫn có nguồn. Trợ lý không tự sửa ticket, phê duyệt hoặc thực hiện thao tác khắc phục thay bạn.

Ở chế độ API, câu hỏi và tiêu đề/mô tả của ticket được gửi đến nhà cung cấp cấu hình phía máy chủ. Chức năng này khác trang **Hỏi đáp tài liệu** và không tự tìm trong toàn bộ thư viện chỉ vì bạn đang mở một ticket.
