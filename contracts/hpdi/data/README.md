# Data contract

KnowledgeCatalog composes metadata authorization, file authorization, workspace binding and exact-byte checksum validation. It returns AuthorizedKnowledge with real provenance; callers cannot grant access by supplying a metadata ID.

`GET .../{workspace}/knowledge?q=&page=&size=` lists only text knowledge (text/plain, text/markdown), newest first, with `ownerName` from the member directory. AuthorizedKnowledge carries both `content` (base64 bytes) and `text` (UTF-8).

Public Java API: `vn.haui.dx.hpdi.data.api`. REST projections: [shared specification](../openapi.yaml). Existing error codes and visibility semantics are preserved.

`GET .../{workspace}/knowledge/{id}/record` trả provenance cho trang chi tiết (nguồn, phiên bản, quyền sử dụng, checksum, `fileId`, người tạo kèm `ownerName`). Mọi API knowledge cần quyền `document:view`; tải lên vẫn đi qua Core (kiểm vai trò agent/coordinator).

Form tải lên gửi `source="Tự soạn"` khi người dùng chọn tự biên soạn; nguồn bên ngoài là tên đơn vị, tác giả hoặc đường dẫn. `usageRights="PUBLIC"` là mặc định của form, công khai trong workspace với thành viên có quyền xem tài liệu; không phải truy cập Internet ẩn danh. `usageRights="INTERNAL"` chỉ cho agent/coordinator có vai trò trong token và còn thuộc vai trò đó trong danh bạ, hoặc platform-admin được xác thực hiện hành và thuộc workspace. Nhãn cũ `internal`, `private`, `nội bộ`, `noi bo`, `internal only`, `chỉ dùng nội bộ` cũng được coi là nội bộ (không phân biệt hoa/thường). Nhãn giấy phép cũ khác giữ nguyên và không tự tạo thêm hạn chế đọc.

Danh sách và tổng số được lọc trước phân trang. Đọc metadata, xem trước, truy xuất nguồn cho AI và tải file gốc đều kiểm cùng chính sách; tài liệu nội bộ không được phép đọc trả 404.
