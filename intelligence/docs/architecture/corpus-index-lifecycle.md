# Vòng đời corpus và index

Chỉ dựng index từ bytes do adapter đã được Core cho phép đọc. So SHA-256 trên
bytes trước khi decode UTF-8; hỗ trợ `text/plain`, `text/markdown`. Mỗi document
mang workspace, metadata UUID, version, checksum, title và media type.
Không thêm thuộc tính giả làm dữ liệu do Core cung cấp.

Chunk giữ excerpt gốc và text chuẩn hóa riêng. ID phụ thuộc workspace, metadata,
version, checksum, policy và ordinal. Manifest ghi source digest, số document/
chunk, phiên bản toàn bộ thuật toán, thư viện số học và index digest.

Lưu trữ local dùng snapshot JSON UTF-8 chứa bytes base64 và manifest; loader
kiểm tra và dựng lại TF-IDF. Cách này đổi thời gian load lấy khả năng tái lập,
không deserialize mã thực thi. Index là dữ liệu dẫn xuất có nội dung nhạy cảm:
adapter phải đặt trong thư mục riêng có ACL phù hợp. Hash không thay thế ACL
hay chữ ký xác thực. Không nhận đường dẫn index từ client.

Publish bằng file tạm cùng thư mục và thay thế nguyên tử sau self-check; registry
chỉ đăng ký index hoàn chỉnh. Lỗi build/load không làm mất index trước đó.
Không ghi snapshot vào Git. Xóa index không thay đổi file/metadata Core.

Khi quyền thay đổi, adapter phải cấp scope mới; không dùng lại partition cũ rồi
lọc sau scoring. Khi nguồn thay đổi, dựng corpus version mới và chọn chính xác
version đó; không tự tìm index gần nhất. Snapshot không được dùng để chứng minh
quyền hiện tại. Resolver Core vẫn bắt buộc tại thời điểm trả citation.
