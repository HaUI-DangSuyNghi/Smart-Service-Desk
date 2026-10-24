# Data contract

KnowledgeCatalog composes metadata authorization, file authorization, workspace binding and exact-byte checksum validation. It returns AuthorizedKnowledge with real provenance; callers cannot grant access by supplying a metadata ID.

`GET .../{workspace}/knowledge?q=&page=&size=` lists only text knowledge (text/plain, text/markdown), newest first, with `ownerName` from the member directory. AuthorizedKnowledge carries both `content` (base64 bytes) and `text` (UTF-8).

Public Java API: `vn.haui.dx.hpdi.data.api`. REST projections: [shared specification](../openapi.yaml). Existing error codes and visibility semantics are preserved.

`GET .../{workspace}/knowledge/{id}/record` trả provenance cho trang chi tiết (nguồn, phiên bản, quyền sử dụng, checksum, `fileId`, người tạo kèm `ownerName`). Mọi API knowledge cần quyền `document:view`; tải lên vẫn đi qua Core (kiểm vai trò agent/coordinator).
