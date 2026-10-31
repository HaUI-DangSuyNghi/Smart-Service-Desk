# ADR-015 — Admission intake và phân phối AI D2

Trạng thái: triển khai theo cụm trên baseline `origin/develop@050a32f`.
Production vẫn NO_GO; không thay đổi quyền dữ liệu hoặc phê duyệt phân phối model.

## Quyết định

Service Desk sở hữu admission trước review, snapshot và final assessment. Core/HPDI
chỉ cung cấp identity, process, audit và intelligence trung tính. Giữ các process
đang chạy. Giai đoạn đầu dùng server admission ở cả review API và callback generic;
task review có thể đã tồn tại. Chỉ claim true pre-review khi có process-key mới và
kiểm chứng wait/release riêng. Legacy ticket không có admission tiếp tục đường cũ.

Mọi admission lock ticket trước metadata, bind revision/digest/policy/review round.
Manual release cần coordinator độc lập, reason và CAS; hết hạn chuyển human intake,
không tự approve. Late answer không đổi assignment hoặc kết quả đã review. Model
chỉ vào final job sau khi seal; GET chỉ đọc. Service principal được cấp quyền thật
và quyền người gửi được kiểm trước/sau xử lý; không giả actor người dùng.

Một kết quả final logic cho một identity; lease không hứa exactly-once vật lý khi
worker chết trong network call. Model lỗi/không có grant đi manual/degraded. Category
model không thành verified category; canonical duplicate cần quyết định nghiệp vụ
và evidence đủ điều kiện. Không gộp tự động theo similarity hoặc policy DRAFT.

## Danh tính image

Giữ serving manifest v0.1: runtimeIdentity là config ID của code image được xác
minh. Wrapper thêm model/app layers, không sửa runtime. Model bundle digest và
policy pin bên trong; final OCI digest/config ID nằm ở manifest phân phối bên
ngoài để tránh tự tham chiếu. Kiểm ancestry và byte allowlist, không tin riêng env.

Build/export là private engineering; publish chỉ khi có receipt quyền phân phối
và kênh private đã duyệt. Không sinh processing grant production trong onboarding.
Token được tạo ở máy nhận và mount file. Bundle không chứa dữ liệu training,
credentials, user tickets hoặc keys ký release. GPU thiếu phải báo PARTIAL.

## Process-key mới

Triển khai option B: `service-desk-intake-reviewed` có task `intake` của coordinator
trước `review`; bật riêng bằng `SERVICE_DESK_INTAKE_TRUE_WAIT=true` và new-ticket
admission. Không thêm quyền giả coordinator cho worker. Final assessment có thể
sẵn sàng tự động, nhưng người có quyền vẫn xác nhận handoff qua intake API.
Callback generic đòi receipt trong cùng transaction. Rework về intake mới, tăng
review round; không sửa instance cũ. Handlers luôn được đăng ký để drain khi tắt
route mới. Follower branch chỉ được mở qua canonical disposition có guard riêng.

## Kiểm chứng

Theo dõi từng cụm, test và giới hạn ở
[AI_D2_DELIVERY.md](../implementation/AI_D2_DELIVERY.md). Chỉ ghi PASS khi có receipt
thực thi; signing/Gold/owner approvals và máy nhận độc lập không thể thay bằng unit test.
