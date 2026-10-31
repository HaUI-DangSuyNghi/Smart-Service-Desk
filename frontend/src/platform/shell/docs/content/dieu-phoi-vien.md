## Kiểm tra hàng chờ

Mở **Smart Service Desk**. Trong chế độ bảng dữ liệu, các nhóm hỗ trợ theo dõi gồm **Tất cả**, **Tiếp nhận**, **Chờ duyệt**, **Quá hạn** và **Giao cho tôi**, tùy quyền.

Sử dụng từ khóa, trạng thái và mức ưu tiên để thu hẹp danh sách. Ở **Giao cho tôi**, người có quyền điều phối có thể chọn nhân sự để xem hàng đợi và tải xử lý của họ.

Ưu tiên kiểm tra các sự cố khẩn cấp, cảnh báo chưa nhận xử lý, công việc quá hạn và ticket đang thiếu thông tin.

## Tiếp nhận ticket

Ticket có thể ở bước Đang tiếp nhận trước khi xuất hiện nhiệm vụ duyệt. Tại đây, kiểm tra:

1. Người gửi đã mô tả đủ phòng, thiết bị, triệu chứng và phạm vi ảnh hưởng chưa?
2. Có yêu cầu bổ sung nào chưa được trả lời?
3. Kết quả phân tích đang chờ, đã sẵn sàng hay cần người xem xét?
4. Có dấu hiệu trùng với sự cố đang xử lý không?

Khi xử lý tiếp nhận thủ công, mở **Tiếp nhận**, đọc đề xuất và ghi lý do quyết định. Hoàn tất bước tiếp nhận để chuyển sang Chờ duyệt.

AI không khả dụng không có nghĩa ticket đã được duyệt hoặc tự được giao. Dùng thao tác tiếp nhận/duyệt thủ công được hệ thống cho phép.

## Duyệt và giao việc

1. Mở ticket **Chờ duyệt**.
2. Chọn **Duyệt và giao việc**.
3. Chốt loại sự cố: Phần cứng, Mạng, Phần mềm, Tài khoản hoặc Khác.
4. Kiểm tra mức ưu tiên và bằng chứng ảnh hưởng/độ gấp nếu có.
5. Kiểm tra ước tính theo phút làm; điều chỉnh khi có căn cứ.
6. Chọn người xử lý hợp lệ trong đề xuất.
7. Ghi lý do duyệt, đặc biệt khi khác đề xuất AI.
8. Xác nhận và kiểm tra trạng thái mới.

Đề xuất nhân sự xét các điều kiện như tổ xử lý, trạng thái trực và tải công việc. Một người nghỉ trực hoặc không thuộc tổ phù hợp có thể bị từ chối khi xác nhận, dù trước đó còn xuất hiện trong danh sách.

Khi không có người trực phù hợp, dùng lựa chọn chờ ca sau nếu giao diện cung cấp. Việc đã được duyệt vẫn cần được theo dõi hạn và giao người khi đủ điều kiện.

**Không tự duyệt ticket mình tạo.** Cần một điều phối viên độc lập thực hiện quyết định đó.

<!-- TODO: Ảnh A08 — Duyệt và giao việc. Chụp form có loại sự cố, ưu tiên, ước tính, đề xuất nhân sự và lý do. Dùng hai tài khoản khác nhau cho người tạo và người duyệt. -->

## Sử dụng Board

Chọn **Board** tại trang Smart Service Desk nếu được cấp quyền. Các cột thể hiện luồng từ Tiếp nhận đến Đã đóng.

Board giúp quan sát số lượng và phân bố công việc. Khi kéo thẻ hoặc dùng menu hành động, hệ thống vẫn kiểm tra quyền, trạng thái và các thông tin bắt buộc; không phải thẻ nào cũng có thể chuyển đến mọi cột.

Một số trạng thái được nhóm chung trong Board. Ví dụ, Theo ticket gốc được nhóm ở cột Đang chờ; Mở lại có thể nằm cùng cột Đã giao. Mở chi tiết để xem trạng thái và bước thực tế, không suy luận toàn bộ nghiệp vụ chỉ từ tên cột.

<!-- TODO: Ảnh A09 — Board ticket. Chụp các cột với một vài ticket mẫu, làm rõ thẻ trạng thái và menu hành động. Không dùng ảnh khiến người đọc hiểu mọi cột đều cho phép kéo thả tùy ý. -->

## Điều chỉnh phân loại

Người giữ việc hoặc điều phối viên có thể thấy **Sửa phân loại** khi ticket đang ở bước xử lý và đáp ứng điều kiện.

Chọn loại/ưu tiên mới rồi nhập lý do. Mỗi lần sửa có lịch sử người thực hiện và lý do. Nếu đổi loại dẫn đến đổi tổ xử lý, ticket có thể quay về hàng chờ duyệt để giao lại.

Không coi việc sửa phân loại là cách bỏ qua bước bàn giao hoặc chuyển quyền xử lý cho một người khác.

## Quyết định yêu cầu sửa ước tính

Mở **Điều phối → Sửa ước tính**:

1. Chọn danh sách **Chờ duyệt**.
2. Đọc ước tính hiện tại, đề xuất mới và lý do.
3. Kiểm tra công việc liên quan.
4. Chọn **Đồng ý** hoặc **Từ chối**.

Đề xuất có thể **Hết hiệu lực** nếu người giữ việc hoặc phân loại đã thay đổi. Khi đó cần xem lại công việc hiện hành, không áp dụng quyết định cho dữ liệu cũ.

## Xác minh ticket trùng và liên kết sự cố

Hệ thống có thể gợi ý ticket cần đối chiếu. Gợi ý tương đồng chưa phải xác nhận cùng sự cố.

Chỉ liên kết sau khi đối chiếu phòng, thiết bị vật lý, triệu chứng và loại đã được người duyệt xác nhận ở ticket gốc. Nhập lý do và xác nhận bằng chứng theo form.

Nếu danh sách ứng viên không đầy đủ hoặc không đủ căn cứ, xử lý độc lập. Nếu đã liên kết sai, dùng **Tách và tiếp nhận độc lập** khi hệ thống cho phép và ghi rõ lý do.

Việc ticket gốc tự đóng do quá hạn xác nhận không tự chứng minh các ticket liên kết đã được người gửi kiểm tra thành công.

## Quản lý ca của workspace

Ở **Ca trực**, người có quyền điều phối có thể xem lịch nhân sự, đăng ký/hủy hộ theo điều kiện, duyệt hoặc từ chối ca chờ duyệt. Kiểm tra lịch hợp lệ trước khi phân công; trạng thái nghỉ đột xuất vẫn ảnh hưởng khả năng nhận việc.
