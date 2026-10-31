## Bắt đầu ngày làm việc

1. Kiểm tra workspace và ca trực.
2. Kiểm tra trạng thái trực trong menu workspace, nhất là khi hôm trước đã chuyển sang nghỉ đột xuất.
3. Mở **Công việc**; giao diện mặc định là lịch ngày hôm nay.
4. Kiểm tra **Quá hạn** trước, sau đó các việc **Trong ngày**.
5. Mở một việc được giao để xem đầy đủ nội dung và thao tác.

## Đọc lịch công việc

Lịch dựa trên **hạn hoàn thành hiện có** của những công việc trong hàng đợi của bạn. Chọn ngày bằng lịch tháng hoặc thanh ngày trong tuần; dùng **Hôm nay** để quay lại ngày hiện tại.

| Nhóm        | Ý nghĩa                                                    |
| ----------- | ---------------------------------------------------------- |
| Trong ngày  | Các việc có hạn hoàn thành rơi vào ngày đang chọn          |
| Quá hạn     | Các việc đang quá hạn; không chỉ giới hạn ở ngày đang chọn |
| Chưa có hạn | Các việc chưa có hạn hoàn thành hợp lệ để xếp theo ngày    |

Một việc đến hạn hôm nay và đã quá giờ có thể xuất hiện khi xem cả Trong ngày lẫn Quá hạn. Hai nhóm này không phải hai tập dữ liệu luôn tách rời nhau.

Bạn có thể tìm công việc theo từ khóa, lọc **Chưa bắt đầu / Đang làm / Đang chờ** và bấm làm mới lịch.

**Lịch này không phải lịch hẹn tự đặt giờ.** Thời gian trên thẻ là hạn hoàn thành, không phải giờ bắt buộc bắt đầu làm. Lịch cũng không phải danh sách việc đã hoàn tất trong ngày.

<!-- TODO: Ảnh A05 — Lịch công việc hôm nay. Chụp giao diện đủ chiều rộng, ngày đang chọn, ba nhóm Trong ngày/Quá hạn/Chưa có hạn, bộ lọc và một thẻ việc có hạn, ưu tiên, ước tính còn lại. -->

## Chuyển sang danh sách công việc

Chọn **Danh sách** ở đầu trang Công việc.

- **Việc của tôi:** nhiệm vụ đang giao cho bạn.
- **Có thể nhận:** nhiệm vụ hợp lệ mà bạn có thể nhận theo quyền và nhóm xử lý.
- **Tất cả việc:** chỉ có khi được cấp quyền giao việc; hiển thị phạm vi điều phối được phép xem trong workspace.

Tài khoản có quyền giao việc mặc định mở Tất cả việc ở chế độ danh sách. Tài khoản không có quyền này mặc định mở Việc của tôi. Phạm vi Tất cả việc không thay đổi lịch cá nhân thành lịch toàn bộ nhân sự.

Bấm **Nhận việc** khi nhiệm vụ cho phép. Sau khi nhận thành công, việc chuyển sang phạm vi của bạn. Những ticket cần điều phối viên duyệt/giao vẫn phải đi qua đúng bước đó.

## Bắt đầu, tạm dừng và tiếp tục

| Tình huống                    | Thao tác         | Kết quả                                                                            |
| ----------------------------- | ---------------- | ---------------------------------------------------------------------------------- |
| Sẵn sàng xử lý việc được giao | **Bắt đầu**      | Chuyển sang Đang làm, ghi nhận thời gian làm                                       |
| Cần chờ thêm điều kiện        | **⋯ → Tạm dừng** | Chuyển sang Đang chờ; chọn lý do                                                   |
| Điều kiện đã sẵn sàng         | **Tiếp tục**     | Quay lại Đang làm                                                                  |
| Đã xử lý xong                 | **Báo xong**     | Hoàn tất bước xử lý và chuyển bước theo quy trình; ticket thường sang Chờ xác nhận |

Khi tạm dừng, chọn đúng lý do:

- Chờ người yêu cầu trả lời.
- Chờ linh kiện.
- Chờ bên ngoài.
- Lý do khác; trường hợp này cần ghi rõ nội dung.

Tạm dừng thời gian làm và tạm dừng đồng hồ SLA là hai việc khác nhau. **Chờ người yêu cầu trả lời** được loại khỏi thời gian tính hạn theo chính sách; chờ linh kiện hoặc bên ngoài không mặc nhiên làm dừng SLA. Xem chi tiết tại [Hạn xử lý](#trang-thai).

Hệ thống có giới hạn số việc Đang làm đồng thời; mặc định là **1 việc/người**. Nếu không bắt đầu được vì đang có việc khác, kiểm tra công việc hiện tại trước khi thao tác tiếp.

<!-- TODO: Ảnh A06 — Xử lý một công việc. Chụp vùng trạng thái và thao tác của người đang giữ việc; thêm ảnh menu Tạm dừng/Xin sửa ước tính/Trả việc và form chọn lý do tạm dừng. -->

## Xin sửa thời gian ước tính

Ước tính là thời gian làm dự kiến của công việc, không phải thời gian đã dùng và cũng không đồng nghĩa với hạn SLA.

1. Mở việc đang giữ.
2. Chọn **⋯ → Xin sửa ước tính**.
3. Nhập tổng số phút ước tính mới và lý do cụ thể.
4. Gửi đề xuất, chờ điều phối viên quyết định.

Ví dụ: “Cần 120 phút vì phải thay linh kiện và kiểm tra lại toàn bộ kết nối”. Số phút phải là số nguyên từ 1 đến 10.080 theo giới hạn hiện tại của form.

## Trả việc và bàn giao

Nếu sai tổ, hết ca cần bàn giao hoặc không thể tiếp tục:

1. Chọn **⋯ → Trả việc**.
2. Chọn lý do.
3. Ghi rõ đã làm đến đâu, kết quả kiểm tra và việc còn lại.
4. Xác nhận trả việc.

Việc quay về hàng chờ duyệt để điều phối viên giao lại. Thời gian đã làm được giữ; bấm Trả việc không phải thao tác xóa lịch sử xử lý.

## Đăng ký ca và chế độ hành chính

Mở **Điều phối → Ca trực**.

| Hình thức  | Cách hoạt động                                                                             |
| ---------- | ------------------------------------------------------------------------------------------ |
| Theo ca    | Đăng ký từng ca; ca hợp lệ hoặc đã được duyệt xác định khung thời gian trực                |
| Hành chính | Tự trực theo các khung giờ làm việc ngày thường; không cần đăng ký lại từng ca ngày thường |

Để đăng ký, chọn tuần rồi bấm ô ca phù hợp. Nếu đơn vị yêu cầu duyệt, ca tự đăng ký ở trạng thái chờ duyệt và cần điều phối viên xác nhận.

Quy tắc hiện tại:

- Kỹ thuật viên tự đăng ký/hủy ca của ngày hiện tại trước mốc đóng đăng ký, là giờ bắt đầu ca đầu tiên trong ngày.
- Có thể đăng ký trước tối đa **62 ngày**.
- Ngày đã qua không thể đăng ký hoặc hủy.
- Thứ Bảy, Chủ nhật và ngày lễ chỉ mở đăng ký khi cấu hình cho phép.
- Chuyển công tắc sang nghỉ đột xuất làm bạn không còn được xem là đang trực hiệu lực, kể cả khi có lịch.

Khi chuyển sang nghỉ, công việc chưa bắt đầu có thể được hệ thống bàn giao theo chính sách. Với việc đã bắt đầu, kiểm tra và thực hiện bàn giao rõ ràng; không coi việc tắt trực là đã hoàn tất trách nhiệm xử lý.

**Thứ Bảy, Chủ nhật có phải làm không?** Chế độ hành chính mặc định nghỉ cuối tuần và ngày lễ đã cấu hình. Nhân sự theo ca có thể trực cuối tuần/ngày lễ nếu đơn vị mở đăng ký và ca đáp ứng điều kiện. Việc lịch công việc hiển thị một ngày cuối tuần không tự tạo nghĩa vụ trực ngày đó.

<!-- TODO: Ảnh A07 — Ca trực. Chụp lịch tuần, lựa chọn Theo ca/Hành chính và ví dụ ca chờ duyệt. Thêm ghi chú cách nhận biết ngày nghỉ và công tắc nghỉ đột xuất. -->
