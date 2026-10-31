## Workspace là gì?

Workspace là không gian làm việc của một đơn vị. Ticket, công việc và tài liệu được truy cập trong phạm vi workspace tương ứng. Một tài khoản có thể thuộc nhiều workspace và có vai trò khác nhau ở từng nơi.

Ví dụ: bạn có thể là kỹ thuật viên trong workspace Phòng máy A, nhưng chỉ là người yêu cầu trong workspace Phòng máy B.

Để chuyển workspace:

1. Bấm tên hoặc biểu tượng workspace ở đầu thanh bên.
2. Chọn workspace cần làm việc.
3. Kiểm tra lại tên đang chọn trước khi gửi yêu cầu hoặc thực hiện quyết định.

Nếu tài khoản chưa thuộc workspace nào, liên hệ quản trị viên để được thêm vào. Người dùng không tự tạo quyền bằng cách nhập đường dẫn một trang khác.

## Các vai trò

| Vai trò                            | Trách nhiệm chính                                                                                                      |
| ---------------------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| Người yêu cầu — Requester          | Mô tả sự cố, cung cấp thông tin, theo dõi yêu cầu của mình và xác nhận kết quả                                         |
| Kỹ thuật viên — Agent              | Xử lý công việc được giao, cập nhật trạng thái, bàn giao và đóng góp tài liệu                                          |
| Điều phối viên — Coordinator       | Tiếp nhận, kiểm tra thông tin, duyệt và phân công, theo dõi tiến độ trong workspace                                    |
| Quản trị nền tảng — Platform Admin | Quản trị tài khoản/phân quyền và cấu hình dùng chung; quyền nghiệp vụ còn phụ thuộc workspace và kiểm tra của hệ thống |

Tên vai trò cho biết trách nhiệm. Quyền thao tác thực tế còn phụ thuộc cấu hình quyền, người đang giữ việc, người tạo yêu cầu và trạng thái hiện tại.

## Quyền mặc định của các nhóm nghiệp vụ

| Chức năng                                     | Người yêu cầu   | Kỹ thuật viên                   | Điều phối viên                  |
| --------------------------------------------- | --------------- | ------------------------------- | ------------------------------- |
| Tạo ticket/yêu cầu                            | Có              | Có                              | Có                              |
| Xem ticket                                    | Ticket của mình | Theo phạm vi workspace được cấp | Theo phạm vi workspace được cấp |
| Xem mọi yêu cầu của workspace ở trang Yêu cầu | Không           | Không                           | Có                              |
| Xem và xử lý công việc                        | Không           | Có, theo nhiệm vụ hợp lệ        | Có, theo nhiệm vụ hợp lệ        |
| Duyệt và giao việc                            | Không           | Không                           | Có                              |
| Xem tài liệu Công khai                        | Có              | Có                              | Có                              |
| Đọc tài liệu Nội bộ                           | Không           | Có                              | Có                              |
| Tải lên/tải về file tài liệu                  | Không           | Có                              | Có                              |
| Xem Dashboard, nhật ký workspace              | Không           | Không                           | Có                              |
| Xem hoạt động cá nhân                         | Có              | Có                              | Có                              |

Đây là bộ quyền mặc định trong cấu hình mẫu, không phải cam kết rằng mọi tài khoản đang triển khai đều được cấp giống nhau. Quản trị viên có thể thay đổi quyền.

## Vì sao tôi không thấy một nút?

Một hành động chỉ xuất hiện khi tài khoản và dữ liệu đáp ứng điều kiện. Ví dụ:

- Có quyền xử lý không đồng nghĩa được thao tác công việc người khác đang giữ.
- Người tạo ticket không tự tiếp nhận hoặc tự duyệt ticket của chính mình.
- Người giữ việc xử lý sự cố; người tạo ticket xác nhận kết quả.
- Nút **Tải về** yêu cầu quyền tải tài liệu, riêng với quyền xem trước.
- Chức năng quản trị hệ thống dành cho quản trị nền tảng.

Kiểm tra **menu workspace → Tài khoản** để xem workspace, vai trò và quyền của mình. Khi vừa được đổi quyền, đăng xuất rồi đăng nhập lại nếu giao diện chưa cập nhật.

<!-- TODO: Ảnh A02 — Workspace và quyền. Chụp menu chuyển workspace và trang Tài khoản. Làm rõ cùng một tài khoản có thể có vai trò khác nhau giữa các workspace. -->
