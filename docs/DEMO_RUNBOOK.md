# Exact-baseline demo runbook

Latest recovery: see `DEMO_RECOVERY_20261028.md` for the running demo-only PostgreSQL
16.6 stack, restart/fallback PASS and exact startup. Missing FE actions are in
`FE_DEMO_BLOCKERS.md`. The 16.15 failure below is historical, not current fallback status.

2026-10-28, Asia/Saigon. Source: `ef047a31abfd8e35ce9b6155e3a06c224262269d`.
**Observed: startup FAIL; full browser/API workflow NOT VERIFIED.**
Expected results below are acceptance instructions, not claims of success.

## Isolated startup

Prerequisites: Docker Engine/Desktop and Compose v2, registry network access,
free localhost ports 5173/8081/9080, preferably PowerShell 7. Builds provide Java
17/Maven, Node 22.14, Python 3.12. Host Python 3.11/MSYS Python is not the production
Intelligence runtime. No cloud key, unapproved weights/dataset or new grant needed
for this rules stack. First build is not guaranteed offline.

Use a separate checkout at the exact SHA; never reset the original workspace.

```powershell
git rev-parse HEAD
$env:COMPOSE_PROJECT_NAME = 'ssd-submission-20261028'
./scripts/setup.ps1
$env:INTELLIGENCE_TRIAGE_BASE_URL = 'http://intelligence:8000'
docker compose -f compose.yaml -f infra/compose/compose.triage.yaml --profile intelligence config --quiet
docker compose --progress plain -f compose.yaml -f infra/compose/compose.triage.yaml --profile intelligence build
if ($LASTEXITCODE -ne 0) { throw 'Build failed' }
docker compose --progress plain -f compose.yaml -f infra/compose/compose.triage.yaml --profile intelligence up -d --no-build --wait --wait-timeout 300
if ($LASTEXITCODE -ne 0) { throw 'Startup failed; inspect project logs' }
docker compose ps -a
```

Transient triage URL reproduces existing scripts/start.ps1 -Intelligence behavior.
Leave defaults, R7, policies and grants unchanged. Setup creates ignored .env and
.local/dx-lab-realm.json; never publish either. Avoid non-quiet resolved Compose
output (contains secrets). Never use -Reset on an existing realm.

Frontend: http://localhost:5173; realm: http://localhost:8081/realms/dx-lab;
gateway: http://localhost:9080. Use localhost consistently for browser issuer and
redirect matching. PostgreSQL/Python stay private on Compose network.
Stop only this project with `docker compose stop`; preserve volumes for restart
proof. No global prune, shared-image deletion or down -v.

## Reproduced failure and minimal isolated check

Compose exit 1; PostgreSQL exit 1 with
`find: 'postgres' is not the name of a known user`. Dependent services blocked.
Actual Chromium frontend/identity navigation refuses connection.

On a clean, separately selected Docker context (not by deleting this host's cache):

```powershell
$image = 'postgres:16.15-bookworm@sha256:0ea6700a3b4f0ae6ce746519073558aed4d88a79d8d07622a9a644946c7319c4'
docker pull $image
docker run --rm --network none --entrypoint sh $image -c 'id postgres && test -x /usr/lib/postgresql/16/bin/postgres'
if ($LASTEXITCODE -ne 0) { throw 'Pinned PostgreSQL image/runtime check failed' }
```

Then rerun unchanged Compose under a fresh project name. Expected: PostgreSQL
healthy, generated realm imported, Flyway/Flowable/backend healthy, gateway,
frontend/Python healthy. Record engine/context/image IDs and SHA. Success elsewhere
does not prove this host repaired; no runtime-setting workaround accepted.

## Accounts and API authority

Password is locally generated DEMO_PASSWORD, shared privately at demo, not fixed
public credentials. `infra/keycloak/realm-template.json`: requester1 lab-a requester,
agent1 agent, coordinator1 coordinator; requester2/5/6 corresponding lab-b roles; admin1
platform-admin with no workspace group. Do not use superadmin as normal actor.

Current contracts: `contracts/openapi/core.yaml`, `contracts/hpdi/openapi.yaml`,
`contracts/openapi/servicedesk.yaml`, `contracts/apps/servicedesk/intelligence/`,
`contracts/intelligence/v1/`. Prefixes: /api/core/v1, /api/hpdi/v1,
/api/apps/servicedesk/v1. Target OpenAPI/historical scripts do not prove behavior.

## Primary browser workflow and API evidence

Separate browser profiles per actor; capture UI/redacted network methods, paths,
status/bodies, ticket/process/task IDs, timestamps and SHA. No tokens/passwords.
Discover fresh IDs from progress/inbox; use each current request schema.

| Step | Expected UI/API acceptance |
| --- | --- |
| Login requester1 | Real Keycloak authorization-code/PKCE redirect/callback, workspace selection; Core GET /me 200. No stubs. |
| Create | Lab-a synthetic title/description, unique clientRequestId; Service Desk POST /workspaces/lab-a/tickets 201, persisted IDs/review state. Refresh does not duplicate. |
| Intelligence | Supported advisory UI/route shows actual engine/profile/result and review requirement. Do not claim disabled models work. |
| Review coordinator1 | Current review/classification visible; approve independently with required category/priority/group/fields. Requester cannot approve. |
| Assign coordinator1 | Implemented assignment/review action selects eligible agent1; persisted assignee/current task agrees. No R7 admission/queue-promise claim. |
| Handle agent1 | Inbox agrees with progress; HPDI /workspaces/lab-a/tasks/{id}/start where required produces IN_PROGRESS. |
| Complete agent1 | Supported resolve/complete with required resolution/outcome creates requester confirmation, not automatic closure. Core task base /workspaces/lab-a/instances/{id}/tasks/{taskId}. |
| Confirm requester1 | Accept current confirmation; process ends/no active tasks; history records lifecycle. Wrong actor fails; replay conflicts. |
| Statistics | Service Desk GET /workspaces/lab-a/tickets/statistics and UI agree under same visibility/filters. Do not sum legacy aliases into V2 partitions. |

If UI action absent, record limitation; direct API success is not browser E2E.
scripts/acceptance/servicedesk.py has historical handle-first assumptions: inspect
before using for current review/assignment. Frontend e2e/*.spec.ts stub identity/API;
scripts/acceptance/browser-pkce.py explicitly tests HTTP PKCE, not browser UI.

### Persistence, permission and outage checks

- Record ticket/list/statistics/history; restart only this project's platform and
  PostgreSQL, wait healthy, reauthenticate; verify state/IDs/no duplicate processes.
  Keep volumes; browser refresh alone is not persistence proof.
- Check anonymous 401, cross-workspace documented 403/404, requester cannot review/
  assign, agent cannot confirm other's ticket, duplicate create 409, stale task 409.
  Validate actual payloads/status against current route schema.
- Stop only this project's Intelligence; call enabled triage: documented unavailable/
  timeout and UI advisory-unavailable state, no unauthorized mutation. Complete
  manually, restart Python/retry advisory. No configuration/grant changes.
- These integrated checks were BLOCKED. Actual private Python check alone:
  anonymous 401; authenticated synthetic request 200 SUCCESS, rules profile
  service-desk@0.3.0, OTHER/MEDIUM, needsReview=true. Not Java/fallback/browser or
  model-quality proof. Evidence in .local/submission, see SUBMISSION_READINESS.md.

## Core/HPDI reuse and rehearsal

When environment/UI supports it, create synthetic Room Request, get independent
approval and show shared Core identity/workflow/audit and HPDI with separate app
data/profile. Inspect `scripts/acceptance/tier-reuse.py` and actual Room Request
contract first. Do not switch selected Service Desk profile or enable settings
for this run. Unsupported UI/profile = NOT DEMONSTRATED.

10 minutes: 1 problem/architecture, 6 lifecycle, 1 Intelligence/fallback, 1 reuse if
available, 1 evidence/license/version/limits; up to 5 minutes questions. Optional
3-5 minute video discloses cuts/unavailable functionality.

References: `docs/core-architecture.md`,
`docs/architecture/ADR-005-dxos-three-tier-boundary.md`,
`docs/architecture/tier-boundary-map.md`, `docs/architecture/BOUNDARY_DEBT.md`.
Show T1 identity/workflow/storage/audit, T2 H-P-D-I, T3 Service Desk/Room Request.
Finish with final source/tag/issues links from SUBMISSION_READINESS.md.
