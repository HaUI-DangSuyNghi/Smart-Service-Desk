# Core baseline status

Verification date: 2026-10-07 (Asia/Saigon). Local Windows / Docker Desktop Linux engine 29.5.2. This is a tested local foundation, NOT completed tier 1 or a completed competition submission.

## Implemented and exercised

- Spring Boot assembly with identity/data/workflow/audit package boundaries; no app-domain imports or frontend dependency.
- Keycloak realm with locally generated demo credentials, two workspaces and three Core roles. Real JWT validation at the backend; APISIX forwards protected requests.
- Four healthy services: PostgreSQL, Keycloak, Core and APISIX. Only loopback ports 8081 and 9080 are published.
- PostgreSQL metadata create/read with provenance/version/rights/checksum/workspace/owner. Invalid and unknown fields rejected.
- Real Flowable deployment, process start, task completion and persisted history for document-review (one task) and equipment-approval (two tasks).
- Duplicate business-key rejection with transaction rollback; incorrect task key and replay rejection; concurrent completion has exactly one success.
- Transactional audit with actor, action, resource, workspace, timestamp and request ID. Read-only DB probe checks metadata/process/task audit consistency and detects orphan engine history/scope records.
- File contract, schema and dedicated volume only. There is NO upload/download implementation and no fake success endpoint.
- Requirements mapping, architecture, N1-N4 three-person plan, OpenAPI, dependency tree and source/license inventory.

## Commands and results

### Directory reorganization verification (2026-10-07)

- Moved source/build files to `backend/`, Core packages to `vn.haui.dx.core`, OpenAPI to `contracts/openapi/core.yaml`, and original documents to `docs/references/`. File hashes checked across document moves; contents unchanged.
- Extracted `infra/keycloak/realm-template.json`; compared generated realm recursively with the prior local realm, including credential values without printing them: identical. Existing secrets and database volumes preserved.
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start.ps1`: exit 0, backend recompiled from new paths, 3 unit tests passed, all four services healthy.
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke.ps1`: exit 0, auth/data/two workflows/concurrent completion checks passed after package moves.
- `Get-Content scripts/check-db.sql -Raw | docker compose exec -T postgres psql -U core -d core -v ON_ERROR_STOP=1`: passed.
- `C:\Users\admin\AppData\Local\Programs\Python\Python311\python.exe -m openapi_spec_validator contracts/openapi/core.yaml`: OK.
- `git diff --check`: passed. `frontend/`, `intelligence/` and `apps/` contain scope README files only; no new feature/framework/dependency added.

### Original baseline verification

The table below records the original baseline verification before the directory reorganization. Historical commands retain their original paths; current backend build context is `backend/` and OpenAPI is `contracts/openapi/core.yaml`.

| Command actually run | Result |
| --- | --- |
| `git status --short` before edits | Only three supplied PDF/XLSX documents untracked; preserved; README originally title only |
| `docker info --format '{{.ServerVersion}}'` | Initially daemon unavailable; `docker desktop start` restored it; 29.5.2 |
| `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start.ps1` | Final direct run exit 0; source image built, all four containers healthy |
| `docker compose config --quiet` (inside start) | Passed |
| Dockerfile `mvn -B verify` | Source compile/package passed; 3 tests, 0 failures/errors/skips |
| `docker build --target build -t dx-core-build:0.1.0 .` | Passed; reusable build image for verification |
| `docker run --rm dx-core-build:0.1.0 mvn -B -o verify` | Passed; 3 tests, zero failures/errors/skips; cached offline Maven verification only, NOT clean offline install |
| `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke.ps1` | Passed with real services through gateway, including three pairs of concurrent completion requests |
| `Get-Content scripts/check-db.sql -Raw \| docker compose exec -T postgres psql -U core -d core -v ON_ERROR_STOP=1` | Passed: engine/scope rollback and audit consistency |
| `docker compose exec -T core curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/api/core/v1/me` | 401 directly at backend without gateway |
| `docker compose ps` | All four healthy; no backend host port |
| `docker run --rm --mount "type=bind,source=D:\Smart-Service-Desk\docs,target=/evidence" dx-core-build:0.1.0 mvn -B dependency:tree '-DoutputFile=/evidence/dependency-tree.txt'` | Passed; full resolved Java dependency tree saved in the workspace; no Git commit |
| `C:\Users\admin\AppData\Local\Programs\Python\Python311\python.exe -m openapi_spec_validator docs/openapi.yaml` | `docs/openapi.yaml: OK`; validation tool installed on host only, not app dependency |
| `git diff --check` | Passed for tracked diff; not a full secret or security scan |

Smoke assertions: no token 401; malformed token 401; valid token 200; reader write 403; foreign workspace 403; scoped missing resource 404; wrong process owner 404; invalid/extra body fields 400; duplicate start 409; wrong task key 409; concurrent complete returns 204+409; replay 409; engine history ended with exact task count and actor/time; metadata round trip matches submitted provenance.

Initial failures and corrections: Windows native argument quoting damaged the initial POM namespace, corrected before successful compile; gateway healthcheck assumed curl (not in APISIX image), replaced with Bash TCP HTTP status check; interrupted Docker daemon recovered. One prior Compose wait observed a replaced container while the corrected stack was recreated. A PowerShell-redirection run reported nonzero despite healthy logs; the final unredirected start was rerun and returned 0. An unquoted Maven `-DoutputFile` was split by PowerShell; rerun quoted successfully. These are not reported as initial passes.

## Not implemented

Private File upload/download/link authorization and failure compensation; audit read/export/retention; independent approver/claim/delegate/cancel; workflow deployment API; granular app-specific ACL; metadata search/version update; UI, business ticket/equipment apps, Intelligence, notification and production observability.

## Unverified / chua kiem chung

- Expired, wrong-issuer and wrong-audience JWT negative fixtures: validation is configured, but those specific live-token cases were not exercised. FE/BE must add tests before broader integration.
- Independent clean clone on a second machine, full offline runtime after disconnect, backup/restore, load/scale, image CVE remediation and transitive license audit.
- Production readiness: old compatibility versions, start-dev HTTP, shared DB principal and Flowable auto-schema-update are explicitly local-only. Upgrade/test before public exposure.
- The initial implementation made no commits or pushes. The owner subsequently authorized Git Flow handover via `feature/core-foundation` into `develop`; see `CONTRIBUTING.md`. This is not a release. Source LICENSE approval, release/tag, issue tracking and repository protection settings remain team actions; no repository visibility change is authorized by this handover.
- Idea PDF and a specifically labeled V2 plan were unavailable. Official brief and existing plan/workbook remain the verified source basis. Contest deadline is unknown; schedule is relative.

## Next owners

BE: task actor/claim policy and richer transaction/error tests; review supported-version upgrade; maintain build/OpenAPI ownership.

FE: independently run README, signed-token negative fixtures, supported image/digest/security review and production-safe IdP/gateway plan. No business UI before Core gate.

AI: implement File I/O only after linked-resource authorization contract, audit read/retention and metadata evolution tests. No Intelligence implementation in this stage.

Use `core-team-plan.md` for each task's lead/reviewer, hours, dependencies, paths, handover and acceptance. Final foundation is left running locally for inspection; `docker compose stop` preserves data.
