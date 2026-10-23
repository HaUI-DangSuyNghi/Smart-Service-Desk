# Human contract

HumanContext derives verified Actor workspace membership. ActivityJournal enforces workspace/actor correlation, transactional recording, event naming and safe reference details. No JWT/security-framework types cross the public Java boundary.

MemberDirectory lists the caller's workspaces with display names (`GET /api/hpdi/v1/workspaces`) and workspace members with roles (`GET .../{workspace}/members`). Data comes from Keycloak through the Core `WorkspaceDirectory` (Admin API, read-only service account `dx-directory`, 60-second cache). It is display-only: authorization still uses token roles. Directory outage returns `503 SERVICE_UNAVAILABLE` for members; workspace names and knowledge owner names degrade to codes / null.

Public Java API: `vn.haui.dx.hpdi.human.api`. REST projections: [shared specification](../openapi.yaml). Existing error codes and visibility semantics are preserved.
