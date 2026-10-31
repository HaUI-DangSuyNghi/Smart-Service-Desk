## Đặt câu hỏi đầu tiên

1. Mở **Hỏi đáp tài liệu**.
2. Kiểm tra workspace và phạm vi dưới ô nhập.
3. Bấm **+** để chọn một tài liệu hoặc giữ **Tất cả tài liệu** nếu phạm vi cho phép.
4. Nhập câu hỏi cụ thể.
5. Bấm nút mũi tên hoặc **Enter** để gửi.
6. Đọc câu trả lời và mở các trích dẫn để kiểm tra.

**Shift + Enter** tạo dòng mới. Trong lúc AI trả lời, nút gửi chuyển thành nút **Dừng trả lời**.

Ví dụ câu hỏi:

- “Theo hướng dẫn này, cần kiểm tra những bước nào khi máy không lên nguồn?”
- “Tài liệu quy định ai được duyệt yêu cầu đặt phòng?”
- “Liệt kê các bước cấu hình được ghi trong tài liệu và dẫn nguồn cho từng bước.”

Nút **+** hiện dùng để chọn phạm vi tài liệu, không phải tải file đính kèm vào hội thoại.

<!-- TODO: Ảnh A12 — Hỏi đáp và phạm vi tài liệu. Chụp trạng thái bắt đầu với avatar AI, ô nhập và menu nút +. Chú thích Tất cả tài liệu/một tài liệu và nút gửi. -->

## Đọc câu trả lời có nguồn

Các ký hiệu như **[1]**, **[2]** liên kết tới đoạn tài liệu dùng để đối chiếu. Bấm trích dẫn hoặc tài liệu được dẫn để mở preview.

Trong preview:

1. Kiểm tra tên và phiên bản tài liệu.
2. Đọc đoạn được đánh dấu.
3. Dùng nút chuyển tới đoạn trích khi cần.
4. Mở trang chi tiết tài liệu để đọc rộng hơn.

Có trích dẫn giúp kiểm tra căn cứ, không đảm bảo mọi nhận định của AI đều đúng hoặc đã đầy đủ. Với thao tác ảnh hưởng hệ thống, đối chiếu nguồn và quy trình của đơn vị trước khi thực hiện.

<!-- TODO: Ảnh A13 — Hội thoại và trích dẫn. Chụp một câu hỏi, câu trả lời có [1], avatar AI, tin nhắn người dùng và nút nguồn. Thêm ảnh preview đang đánh dấu đúng đoạn trích. -->

## Khi AI chưa trả lời được

| Tình huống                                        | Bạn nên làm gì?                                                      |
| ------------------------------------------------- | -------------------------------------------------------------------- |
| Chưa có tài liệu                                  | Mở Thư viện tài liệu; người có quyền có thể tải tài liệu phù hợp lên |
| Không tìm được đoạn liên quan                     | Chọn tài liệu đúng chủ đề, dùng từ khóa cụ thể hơn                   |
| Tài liệu chưa đủ thông tin                        | Đọc nguồn và bổ sung câu hỏi; không yêu cầu AI tự đoán dữ liệu thiếu |
| Câu hỏi yêu cầu chọn đáp án nhưng thiếu phương án | Gửi đầy đủ các phương án lựa chọn                                    |
| Thư viện quá lớn                                  | Chọn một tài liệu thay cho Tất cả tài liệu                           |
| AI lỗi hoặc kết nối bị ngắt                       | Xem phần nguồn còn đọc được; thử gửi lại khi dịch vụ ổn định         |

Khi bấm Dừng hoặc kết nối bị ngắt, nội dung đã hiện chỉ là câu trả lời chưa hoàn tất. Không xem đó là một lượt trả lời đã được xác nhận đầy đủ.

## Lịch sử trò chuyện

- Bấm **Cuộc trò chuyện mới** để bắt đầu cuộc khác.
- Chọn cuộc cũ trong danh sách **Gần đây**; trên màn hình nhỏ dùng nút lịch sử ở đầu trang.
- Xóa một cuộc trò chuyện bằng thao tác xóa và xác nhận trên trình duyệt.

Lịch sử hiện lưu **trên trình duyệt**, tách theo tài khoản và workspace. Không đồng bộ giữa máy hoặc trình duyệt khác nhau; xóa dữ liệu trình duyệt có thể làm mất lịch sử.

Trên máy dùng chung, hãy xóa các cuộc trò chuyện cần bảo vệ trước khi rời máy. Đăng xuất không nên được hiểu là thao tác tự xóa toàn bộ lịch sử đã lưu trên trình duyệt.

Giới hạn lưu hiện tại là tối đa **20 cuộc**, mỗi cuộc tối đa **100 lượt hỏi**, và giới hạn dung lượng lưu. Cuộc/lượt cũ có thể bị loại khi vượt giới hạn.

Giao diện trình bày dạng hội thoại, nhưng **mỗi câu hỏi hiện được xử lý độc lập**. Khi hỏi tiếp, hãy nhắc lại tên đối tượng, nội dung và điều kiện cần thiết; đừng chỉ viết “còn trường hợp đó thì sao?”.

## Phạm vi dữ liệu và giới hạn truy vấn

- Chỉ dùng tài liệu người hỏi được phép xem trong workspace hiện tại.
- Phạm vi Tất cả tài liệu hỗ trợ tối đa **50 tài liệu**; thư viện lớn hơn cần chọn một tài liệu.
- Tổng nội dung trích xuất cho một truy vấn tối đa **1.000.000 byte UTF-8**. Một tài liệu quá dài cũng có thể cần được tách nhỏ.
- Danh sách chọn tài liệu hiện tải tối đa **100 tài liệu mới nhất**.
- Câu hỏi tối đa **2.000 ký tự**.
- Tìm đoạn liên quan hiện dựa trên đối sánh văn bản; không phải tìm kiếm ngữ nghĩa không giới hạn cho mọi loại nội dung.
- Không đọc được chữ chỉ có trong ảnh và không tra cứu xuyên workspace.

Khi dùng API bên ngoài, câu hỏi cùng tối đa năm đoạn nguồn được chọn được gửi tới nhà cung cấp đã cấu hình. Không đưa khóa API, mật khẩu hoặc dữ liệu không được phép gửi ra ngoài vào câu hỏi/tài liệu dùng với chế độ này.

Lịch sử hội thoại lưu ở trình duyệt không có nghĩa dữ liệu của từng lần hỏi không rời hệ thống: đường đi dữ liệu còn phụ thuộc chế độ AI đã bật.
