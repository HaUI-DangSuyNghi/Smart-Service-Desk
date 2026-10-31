## Tìm và đọc tài liệu

1. Chọn đúng workspace.
2. Mở **Tài liệu**.
3. Nhập từ khóa theo tiêu đề nếu cần.
4. Bấm tài liệu để xem nguồn, phiên bản, người tạo và nội dung.

Nội dung xem trước là **văn bản đã trích xuất**. Bố cục trang, hình ảnh, bảng phức tạp hoặc định dạng gốc có thể không được giữ như trong phần mềm đọc PDF/Word.

Nếu có quyền tải bản gốc, chọn **Tải về** ở trang chi tiết. Có thể đọc nội dung không đồng nghĩa luôn có quyền tải file gốc.

<!-- TODO: Ảnh A10 — Kho và chi tiết tài liệu. Chụp danh sách có cột Nguồn tài liệu/Phiên bản/Ai được xem và trang chi tiết có nội dung trích xuất. Chỉ chụp nút Tải về bằng tài khoản có quyền. -->

## Tải tài liệu lên

Người có quyền tải lên mở **Tài liệu** và chọn thao tác tải lên.

| Trường           | Bắt buộc?       | Hướng dẫn                                                                |
| ---------------- | --------------- | ------------------------------------------------------------------------ |
| File             | Có              | Chọn một file thuộc định dạng hỗ trợ, tối đa 5 MiB                       |
| Tiêu đề tài liệu | Có              | Tên ngắn gọn giúp người khác tìm thấy; tối đa 200 ký tự                  |
| Nguồn tài liệu   | Có              | Nhập tên tác giả, đơn vị hoặc đường dẫn tài liệu gốc; tối đa 1.000 ký tự |
| Phiên bản        | Có              | Mặc định `1.0`; tối đa 100 ký tự                                         |
| Ai được xem?     | Có, đã chọn sẵn | Mặc định Công khai; chuyển Nội bộ nếu cần giới hạn                       |

Sau khi chọn file và điền thông tin, bấm nút xác nhận của form, đợi báo thành công rồi kiểm tra tài liệu trong danh sách.

## Nguồn tài liệu nên điền gì?

Trường này cho biết **tài liệu đến từ đâu**, giúp người đọc đối chiếu và liên hệ khi cần. Bạn nhập trực tiếp vào ô; không bắt buộc phải là một URL.

| Tình huống                     | Có thể nhập                                         |
| ------------------------------ | --------------------------------------------------- |
| Bạn tự viết tài liệu           | `Tự soạn` hoặc `Nguyễn Văn A — Tổ hỗ trợ phần cứng` |
| Tài liệu do phòng ban cung cấp | `Phòng CNTT`                                        |
| Tài liệu của nhà cung cấp      | `Tài liệu hãng ABC — Hướng dẫn thiết bị XYZ`        |
| Có trang gốc để đối chiếu      | Đường dẫn trang tài liệu gốc                        |

Nguồn tài liệu **phải có thông tin** trong form hiện tại. Nếu tự biên soạn, ghi **Tự soạn** là phù hợp. Trường này không phải nơi chọn mức công khai và không tự tải nội dung từ một đường dẫn bạn nhập.

## Ai được xem?

| Lựa chọn             | Phạm vi đọc                                                                                     |
| -------------------- | ----------------------------------------------------------------------------------------------- |
| Công khai — mặc định | Thành viên của workspace có quyền xem tài liệu                                                  |
| Nội bộ               | Kỹ thuật viên, điều phối viên hoặc quản trị viên đáp ứng kiểm tra quyền và thành viên workspace |

**Công khai ở đây không có nghĩa công khai trên Internet.** Người chưa đăng nhập hoặc người ngoài workspace không được đọc chỉ vì tài liệu có nhãn Công khai.

**Nội bộ không có nghĩa chỉ mình người tải lên đọc được.** Đây là phạm vi dành cho nhóm xử lý và quản trị hợp lệ của workspace.

Lựa chọn người được xem không thay thế quyền tác giả hoặc giấy phép phân phối. Chỉ tải lên nội dung bạn có quyền cung cấp cho đơn vị.

<!-- TODO: Ảnh A11 — Form tải tài liệu. Chụp ô Nguồn tài liệu với ví dụ “Tự soạn”, phiên bản 1.0 và hai lựa chọn Công khai/Nội bộ. Chú thích rõ Công khai là trong workspace. -->

## Định dạng và giới hạn

| Định dạng                     | Hỗ trợ                              | Ghi chú                                                                              |
| ----------------------------- | ----------------------------------- | ------------------------------------------------------------------------------------ |
| `.txt`                        | Có                                  | Văn bản                                                                              |
| `.md`, `.markdown`            | Có                                  | Xem trước dạng chữ thuần                                                             |
| `.pdf` có lớp chữ             | Có                                  | Cần trích xuất được văn bản; bản xem trước không phải trình đọc PDF giữ nguyên trang |
| `.docx`                       | Có                                  | Trích xuất nội dung chữ                                                              |
| `.doc`, Word có macro         | Không thuộc form hiện tại           | Chuyển sang DOCX trước                                                               |
| PDF chỉ chứa ảnh scan         | Không có OCR tích hợp               | OCR bằng công cụ phù hợp rồi tải bản có lớp chữ                                      |
| Excel, PowerPoint, ảnh, video | Không thuộc luồng tài liệu hiện tại | Không dùng các định dạng này cho hỏi đáp tài liệu                                    |

Mỗi file tối đa **5 MiB**. Bộ trích xuất hiện giới hạn PDF tối đa **200 trang** và nội dung tối đa **200.000 ký tự**. File tải lên được không đảm bảo mọi nội dung đều trích xuất đọc được; kiểm tra bản xem trước sau khi tải.

## Nguồn, phiên bản và checksum

Nguồn cho biết xuất xứ; phiên bản giúp xác định bản tài liệu; checksum SHA-256 hỗ trợ kiểm tra tính toàn vẹn nội dung file được liên kết.

Nếu hệ thống báo nội dung hoặc nguồn không khớp, liên hệ người quản lý tài liệu. Hướng dẫn này không giả định có trình chỉnh sửa nội dung, ghi đè phiên bản hoặc quản lý phiên bản cộng tác trực tiếp trong giao diện.
