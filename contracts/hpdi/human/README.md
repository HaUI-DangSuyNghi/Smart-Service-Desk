# Human contract

HumanContext derives verified Actor workspace membership. ActivityJournal enforces workspace/actor correlation, transactional recording, event naming and safe reference details. No JWT/security-framework types cross the public Java boundary.

MemberDirectory lists the caller's workspaces with display names (`GET /api/hpdi/v1/workspaces`) and workspace members with roles (`GET .../{workspace}/members`). Data comes from Keycloak through the Core `WorkspaceDirectory` (Admin API, read-only service account `dx-directory`, 60-second cache). It is display-only: authorization still uses token roles. Directory outage returns `503 SERVICE_UNAVAILABLE` for members; workspace names and knowledge owner names degrade to codes / null.

Public Java API: `vn.haui.dx.hpdi.human.api`. REST projections: [shared specification](../openapi.yaml). Existing error codes and visibility semantics are preserved.

## Quyền nhỏ (Keycloak) và kiểm quyền API

- `GET .../{workspace}/permissions`: quyền của người gọi (role của client `dx-permissions` gán cho nhóm `/workspaces/{ws}/{role}`), kèm mô tả tiếng Việt.
- `PermissionGuard.require(workspace, permission)` kiểm quyền này ở **mọi API hpdi** trước khi gọi Core. Vai trò lấy từ token, quyền của từng vai trò lấy từ Keycloak (cache 60 giây). Keycloak lỗi → `503`, không cho qua. Core vẫn tự kiểm vai trò của mình nên hai lớp độc lập. Tên quyền: `vn.haui.dx.hpdi.human.api.Permissions`.
- `GET .../{workspace}/people?ids=a,b`: tên hiển thị của các subject người gọi đã thấy (tối đa 100), mọi thành viên; dùng thay cho danh bạ đầy đủ (`members` cần `member:view`).
- `GET .../{workspace}/activity` (`audit:view-own`): hoạt động của chính người gọi. `GET .../{workspace}/audit` (`audit:view`): nhật ký cả workspace, lọc `from`, `to`, `action`, `actor`.
