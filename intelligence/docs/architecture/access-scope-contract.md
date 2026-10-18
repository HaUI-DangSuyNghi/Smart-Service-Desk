# Hợp đồng scope truy xuất qua Core HTTP

Trạng thái: **ĐÃ TRIỂN KHAI ĐỂ REVIEW; CHỜ BE PHÊ DUYỆT**. Baseline Core:
`e39356cc44ef50421a9b81199756ec9784fe161a`. Không tự xác nhận BE đã duyệt.

Chọn phương án scope tường minh, suy ra từ tập nguồn thực sự đọc được qua Core
HTTP tại mỗi lần sử dụng. Không giả định cả workspace có cùng ACL. Adapter nhận
catalog metadata UUID và revision từ cấu hình Application tin cậy; catalog xác
định tập ứng viên có quyền sử dụng, không cấp quyền đọc. Không nhận catalog,
fingerprint, subject hoặc token từ search request. Authentication middleware cấp
token provider riêng cho principal; adapter gọi `/api/core/v1/me` để Core kiểm
chữ ký và xác nhận subject/membership, không tự decode JWT để cấp quyền.

Scope fingerprint = digest có version của authority Core, workspace, catalog
revision và tập source state đã đọc được (metadata UUID, file UUID, version,
checksum), sắp xếp xác định. Tập trạng thái tương đương cho cùng authority/catalog
cho fingerprint giống nhau, kể cả khác người dùng. Subject vẫn được kiểm tra
riêng với `/me`; không được dùng token của người này với context của người khác.
Raw token và raw fingerprint không được ghi log/trace/snapshot.

Adapter kiểm tra quyền metadata, file descriptor và download bytes trước scoring,
đối chiếu checksum metadata/file/bytes. Mỗi request tính lại scope; không cache
quyền hoặc source. Vì Core chưa cung cấp ACL revision, snapshot digest bao gồm
cả nội dung/version. Thay ACL, tập nguồn đọc được, catalog revision hoặc nội dung
làm context cũ không khớp; adapter từ chối trước registry/scoring. Application
phải tạo context mới và build/chọn index đúng key. Không fallback index cũ.
Chi phí: nhiều HTTP read/download; chỉ phù hợp baseline corpus nhỏ có giới hạn.

| Tình huống | Hành vi |
| --- | --- |
| Membership không có hoặc subject khác | AuthorizationRejected trước đọc corpus |
| Metadata/file 401 hoặc 403 | AuthorizationRejected, không DEGRADED |
| Metadata/file 404 | Nguồn không resolve được; không đưa vào scope; không kết luận đã xóa |
| Scope hiện tại khác context | AuthorizationRejected trước registry/scoring |
| Core 429/5xx hoặc lỗi network | CorpusUnavailable; DEGRADED mặc định tắt |
| JSON sai, checksum sai, nguồn khác workspace | CitationVerificationError, fail closed |
| Redirect | Từ chối, không chuyển Authorization sang host khác |

Citation được xác minh bằng lần đọc Core mới, kiểm subject/membership, giới hạn
catalog và quyền file. Nếu nguồn đổi sau scoring thì verifier loại stale; nếu mất
quyền thì lỗi quyền; 404 chỉ nghĩa là không resolve được. Digest không chứng minh
JWT còn hiệu lực hoặc cấp quyền; Core API mới là bên quyết định quyền.

Core hiện dựa trên group claim của JWT đã ký, không introspect group hiện tại ở
Keycloak. Thu hồi membership cần token mới hoặc chờ token cũ hết hạn (realm local
300 giây). Không tuyên bố thu hồi tức thời token đang còn hạn; BE phải duyệt mô
hình thời gian thu hồi hoặc bổ sung cơ chế khác trước production. Test revoke
sẽ tháo membership của principal test riêng và lấy token mới.

Ví dụ: catalog v1 chứa M1/M2; cả hai đọc được → S1. Core không cho đọc M2 → S2;
S1 không được chọn/scored. M1 chuyển nội dung v2 hoặc catalog chuyển revision →
scope mới; context/index cũ không được dùng. Core metadata bất biến: version mới
là record/UUID mới. Không diễn giải record v1 còn tồn tại là “đã bị xóa”.
