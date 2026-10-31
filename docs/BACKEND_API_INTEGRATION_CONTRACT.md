# Backend API integration contract

Verified source baseline: `f94f0ef102cbc7d5737ca05270b0affab1fde1a6` plus the
uncommitted backend hardening changes, 2026-10-25. This describes implemented
controllers, not an assertion that the full Docker stack passed acceptance.
See `docs/BACKEND_HARDENING_REPORT.md` for actual test evidence and release blockers.

## 1. Authentication and trust boundaries

- Browser login: Keycloak realm `dx-lab`, public client `dx-web`, authorization
  code + PKCE S256. No browser client secret or password-grant implementation.
- Browser API base: `http://localhost:9080/api`. Send `Authorization: Bearer <access-token>`.
  Java, not a UI role check or gateway-supplied user header, validates issuer,
  signature, expiry and audience `dx-core`. `/actuator/health` is public.
- Workspace always comes from the URL and must match signed groups
  `/workspaces/{workspace}/{role}`. `platform-admin` grants no implicit document,
  task or public workspace-audit access. Unknown/foreign resource IDs yield 403/404.
- Current Keycloak `dx-permissions` group mappings are intersected with the
  caller's signed roles. Group-directory members/display names never grant roles.
  Core HTTP document/task guards and the HPDI guard share `WorkspacePermissions`.
- Directory cache lasts 60 seconds; mapping revocation is effective on the next
  lookup after expiry, not instantly. An expired-cache refresh failure returns 503;
  it never falls back to stale permissions. Signed membership/role removal also
  requires token renewal/expiry (configured realm access-token lifetime: 300 seconds).
- Core Java capability calls remain in-process APIs, with their existing
  workspace/role/resource policies. They are not HTTP service-token exemptions.
  Ordinary task completion additionally checks current `task:handle` inside the
  locked workflow transaction. Exact BPMN `${initiator}` confirmation is the
  deliberate exception, and still requires owner AND assignee.
- The private Intelligence bearer token is neither an OIDC user token nor a Core
  grant. Do not expose it to frontend code, logs, fixtures or public routes.

## 2. Common wire conventions

JSON uses camelCase; UUID identifiers are opaque, timestamps are ISO-8601 instants.
Unknown JSON properties are rejected. Pagination: `page=0`, `size=20`, maximum 100;
negative page, size outside 1..100 and unsupported filters return 400.
Responses use `X-Request-ID`; retain this value for support, never JWTs/secrets.

Errors are `application/problem+json`, for example:

```json
{"type":"about:blank","title":"Forbidden","status":403,"detail":"Permission required: document:view","code":"FORBIDDEN","requestId":"example-request-id"}
```

| HTTP | Code / meaning | Frontend behavior |
| --- | --- | --- |
| 400 | VALIDATION_FAILED | Show validation; do not auto-retry unchanged input |
| 401 | UNAUTHENTICATED | Renew/login through OIDC; never substitute service credentials |
| 403 | FORBIDDEN | Re-fetch permissions; clear restricted content/actions |
| 404 | NOT_FOUND | Resource absent or intentionally concealed |
| 409 | CONFLICT | Refresh workflow/task state; do not replay stale completion |
| 413 / 415 | PAYLOAD_TOO_LARGE / UNSUPPORTED_MEDIA_TYPE | Reject file/input locally too |
| 503 | SERVICE_UNAVAILABLE | Permission directory unavailable; no optimistic access |
| 502 | INTELLIGENCE_CONTRACT_FAILED / INTELLIGENCE_INTEGRATION_FAILED | Keep manual workflow available |
| 503 / 504 | INTELLIGENCE_UNAVAILABLE / INTELLIGENCE_TIMEOUT | Report unavailable suggestion, not a business failure |

## 3. Human and process APIs

Let `H=/api/hpdi/v1/workspaces/{workspace}` and `C=/api/core/v1/workspaces/{workspace}`.
All entries require a valid JWT and workspace membership in addition to the rules below.

| Method and path | Permission / resource policy | Input / response |
| --- | --- | --- |
| GET /api/core/v1/me | authenticated identity | subject, memberships; not an authorization override |
| GET /api/core/v1/process-definitions | authenticated identity | registered process policies; not process instance content |
| GET /api/hpdi/v1/workspaces | signed memberships | workspace contexts |
| GET H/permissions | own permissions | list of key/description |
| GET H/members | member:view | workspace member list |
| GET H/people?ids=... | member; at most 100 IDs | ID -> display name |
| GET H/activity | audit:view-own | own paged activity |
| GET H/audit | audit:view plus Core audit policy | paged workspace audit |
| GET C/audit | audit:view plus coordinator / platform-admin policy | from/to/action/actor/page/size |
| GET H/processes | member | descriptors/labels/selfService/canStart |
| POST H/processes/{processKey}/instances | request:create; self-service process only; starter role | {variables:{...}}; 201 process handle |
| GET H/instances?scope=mine | request:create; own | page of process summaries |
| GET H/instances?scope=all | request:view-all; process reader policy | page of process summaries |
| GET H/inbox?scope=CANDIDATE or ASSIGNED | task:handle; candidate/assignee filtering | page of work items |
| GET H/inbox?scope=ALL | task:assign; coordinator | page of work items |
| POST H/tasks/{id}/claim | task:handle; independent candidate; unclaimed | task; 409 on concurrent loser |
| POST H/tasks/{id}/assign | task:assign; coordinator; eligible target | {assignee:"OIDC-sub"}; task |
| POST H/tasks/{id}/complete | current task:handle for worker; exact initiator exception | completion JSON below; 204 |
| GET H/instances/{id}/timeline | initiator, participant or process reader | currentTasks/steps/ended |
| GET H/processes/statistics | dashboard:view; coordinator | required from/to, optional IANA zone |
| GET C/tasks?scope=assigned or candidate | task:handle plus Core filtering | paged tasks; scope is lowercase here |
| POST C/processes/{key}/instances | request:create plus registered starter role; registered selfService=true only | businessKey/title/variables; 201 |
| GET C/instances/{id}/tasks or /history | existing process visibility policy | tasks or history |
| POST C/instances/{id}/tasks/{taskId}/claim | task:handle; parent binding; independent candidate | task |
| POST C/instances/{id}/tasks/{taskId}/complete | parent binding plus completion policy | completion JSON; 204 |

Completion request (use the actual key and an outcome advertised for that process):

```json
{"expectedTaskKey":"handle","outcome":"resolved","comment":"Connection restored"}
```

Limits: task key/outcome 100 characters; comment 1000. The server checks assignee
before exposing stale-key errors. Directory permissions do not replace candidate,
independent approver, parent-instance or assignee checks. IDs/roles from JSON cannot
override the actor. On 409 re-read progress; never silently force another outcome.

Core start requires nonblank businessKey <=200 characters, optional title <=200,
and only registered start variables (string values <=2000). Do not supply title
both as a top-level field and in variables. Workspace, initiator and outcome
variables are server-owned, not caller-controlled authorization inputs.

Generic Core and HPDI HTTP starts reject application-only processes with 403
before engine, scope or audit writes. `service-desk` and `room-request` must use
their application creation APIs; supplying a business key, variables or a
client-controlled `selfService` value cannot authorize generic start. The sample
`document-review` and `equipment-approval` processes explicitly allow self-service.
Unknown process keys still return 404 after the workspace permission check.

Core process-definition responses now include the additive boolean `selfService`.
Core Java registrations using the existing four/six-argument `ProcessPolicy`
constructors remain source-compatible but default to application-only (false).
Registrations intended for generic HTTP start must explicitly pass true using the
seven-argument constructor. Recompile Java consumers of the changed record; binary
compatibility is not promised. Internal `WorkflowService.start` and HPDI's
in-process capability retain their existing membership, starter-role, variable
validation and transaction behavior. This policy is not a caller-supplied flag
and does not grant additional workspace permissions.

## 4. Knowledge and file APIs

| Method and path | Permission and additional checks | Request / response |
| --- | --- | --- |
| GET H/knowledge | document:view | q/page/size; page of summaries |
| GET H/knowledge/{id} | document:view; resource workspace | authorized knowledge |
| GET H/knowledge/{id}/record | document:view; resource workspace | record/provenance |
| GET C/metadata or /metadata/{id} | document:view | q/source/sort/page/size, or one record |
| POST C/metadata | document:upload AND agent/coordinator | metadata JSON; 201 |
| POST C/files | document:upload, file service policy AND upload gate | multipart field file; 201 descriptor |
| GET C/files/{id} or /content | document:view AND file ownership/link policy | descriptor or attachment bytes |

Upload remains denied unless `DX_ALLOW_UNSCANNED_UPLOAD=true`; permission never
overrides that gate. Demo enables unscanned uploads, which is NOT production
content-scanning approval. Maximum file size is 5 MiB; media-type allowlisting,
integrity checks and sanitized filenames remain enforced by the file service.
Downloads carry `nosniff` and attachment disposition. Do not render blobs as HTML.

Upload first, then create metadata using its `fileId`; exactly ONE of fileId or
checksum is allowed. The server computes checksum for linked bytes and requires
the metadata creator to own that upload. Detached checksum must be 64 lowercase
hex characters. Metadata fields: nonblank title <=200, source <=1000, version
<=100, usageRights <=200. For example:

```json
{"title":"Network guide","source":"synthetic:demo","version":"1","usageRights":"CC0-1.0","fileId":"00000000-0000-0000-0000-000000000001"}
```

Metadata response fields: id, workspace, title, source, version, usageRights,
checksum, fileId, owner, createdAt. File descriptor fields: id, workspace,
originalName, mediaType, size, checksum, owner, createdAt. Pages are
`{"items":[],"page":0,"size":20,"total":0}`.

Existing frontend URLs need not change. A requester without document:view now
receives 403 on Core metadata, descriptors AND bytes, not just HPDI knowledge.
Do not use a Core fallback URL when HPDI returns 403. Clear cached data/download
links when rights change. Requester confirmation should use ticket progress or
instance tasks/timeline; worker inbox requires task:handle.

## 5. Service Desk and Room Request

`T=/api/apps/servicedesk/v1/workspaces/{workspace}/tickets`.

| API | Request / response and authorization |
| --- | --- |
| POST T | member with requester/agent/coordinator starter role; 201 Ticket |
| GET T/{id}, GET T/{id}/progress | own ticket for requester; workspace agent/coordinator reader |
| GET T | q/from/to/page/size; same ticket reader policy |
| GET T/statistics | application statistics service authorization and matching filters |
| POST T/{id}/triage | NO body, even `{}`; existing ticket visibility first; suggestion only |
| POST T/{id}/assistance | conditional feature; only {question:"..."}; visibility and configured workspace pins |

Create request:

```json
{"clientRequestId":"00000000-0000-0000-0000-000000000002","title":"Cannot connect","description":"The lab switch is unavailable"}
```

clientRequestId is required UUID; title nonblank <=200, description nonblank
<=4000; invalid controls rejected. Duplicate IDs within the same workspace AND
creator conflict (409),
not a promise to return the original ticket. Retain the ID across a network
retry, reconcile by listing before creating again. Ticket fields: id, workspace,
creator, clientRequestId, title, description, createdAt, process. Process contains
processInstanceId (and legacy id alias), processKey, businessKey, workspace.

Service Desk lifecycle: app create + process `service-desk` + audit in one DB
transaction; independent candidate claims `handle`; worker completes `resolved`;
requester assigned `confirm` chooses `accepted` or `rework`. Rework reopens
handling; accepted ends the process. Confirm requires the actual initiator AND
assignee, not task:handle; do not hide requester confirmation behind a worker role.

Room Request reuses unchanged HPDI capabilities. Base:
`/api/apps/roomrequest/v1/workspaces/{workspace}/requests`; POST body
`{"room":"A-101","knowledgeId":null}`, GET /{id}, GET /{id}/progress,
POST /{id}/suggestion without body. Room is nonblank <=50 with no controls;
optional knowledge must resolve within the workspace. Creator/agent/coordinator
read policy and process `room-request` apply. No second Core or workflow stack.

App-owned ticket/room reader and creation rules are not identical to the generic
HPDI process-list permissions. This patch does not claim that every application
operation is governed by every generic permission. Any broader fine-grained app
permission semantics require a separate policy decision and regression matrix.

## 6. Private Intelligence handoff

Public ticket triage already exists in this HEAD; older design-only descriptions
are historical. Java constructs authorized context from the visible stored ticket,
not caller-supplied workspace, profile, actor or privileges. Intelligence cannot
claim/complete tasks, assign users or persist a final decision.

- Triage: private POST `/internal/intelligence/v1/triage`, Bearer service token,
  JSON, logical contractVersion `0.1.0`, profile `service-desk`, profileVersion
  `0.3.0`. Schemas: `contracts/intelligence/v1/triage-{request,result}.schema.json`.
- Triage transport: 1 second connect, 4 second request/overall deadline, 32 KiB
  request cap, 256 KiB response cap; no redirects; duplicate/trailing JSON rejected.
- SUCCESS is a suggestion with result(category/priority/suggestedGroup), literal
  INPUT evidence, explanation, runtime versions, needsReview; it is not an approved
  workflow outcome. DEGRADED has null result/explanation/runtime, empty evidence,
  needsReview=true and INTELLIGENCE_UNAVAILABLE. Never fabricate SUCCESS on outage.
- 401/403 from Python -> public 502 integration failure; 429/5xx -> 503 unavailable;
  timeout -> 504; malformed/oversized/wrong correlation or contract -> 502.
- Assistance is opt-in (`serviceDesk.assistance.enabled`), not a prerequisite for
  the basic demo. Public question <=2000 characters; body <=16 KiB and exactly one
  question field. Backend selects workspace catalog pins/checksums and validates
  citations against authorized snapshots; response is `Cache-Control: no-store`.
- Assistance transport uses the private `/internal/intelligence/v1/assistance`,
  1 second connect and 15 second deadline. Reference schemas are assistance request
  and result; cloud schemas are separate files. See current routing configuration
  and `contracts/intelligence/` rather than mixing cloud/native envelope versions.
- Cloud route is `synthetic-cloud-dev` only under existing explicit authorization,
  policy, budget and runtime pins. This patch neither enables cloud egress nor
  changes algorithms, taxonomy, model, profiles, evaluation or dataset ownership.

AI Owner: verify actual private Python runtime with the current wheel, configured
token and profile pin; run the existing real-triage E2E gate. Verify wrong-token,
timeout, invalid response and manual-processing cases. Java HTTP-server/validator
tests are not a claim that this full real-Python path ran here. Do not publish the
service token or let frontend set trusted context or source bytes.

## 7. Reproduction and operational limits

In a fresh, owner-approved local checkout with Docker/Compose and no conflicting
ports, the canonical basic full-demo command is:

```powershell
.\scripts\start.ps1 -Intelligence
```

This composes the existing triage overlay, selects its profile, sets the private
in-network URL, and shares one existing or generated session token with Java and
Python. It restores the caller's process environment, does not rotate existing
`.env` secrets and does not enable cloud/generation. Never pass -Reset against a
shared realm. Ports: frontend 5173, Keycloak 8081, gateway 9080; Python has no host
port. On this machine the pinned PostgreSQL 16.15 image cannot boot; repair/re-pull
its local image storage before claiming this startup path passed.

DB acceptance already exists and rejects failures/errors/skips:

```powershell
.\scripts\test-file.ps1 -BuildImage dx-platform-build:0.1.0
.\scripts\test-file.ps1 -BuildImage dx-platform-build:0.1.0 -MavenProfile core-only
.\scripts\test-file.ps1 -BuildImage dx-platform-build:0.1.0 -MavenProfile second-domain -Tests 'RoomRequestIntegrationTest,TierBoundaryTest,CoreKnowledgeCatalogTest,CoreProcessCapabilitiesTest,WorkspaceActivityJournalTest,TrustedIntelligenceCapabilityTest,CoreAuthorizationRegressionTest,KeycloakPermissionGuardTest,KeycloakWorkspaceDirectoryTest,SecurityHeadersTest'
```

Build the matching build image first; these commands use unique disposable
databases/networks, not the development DB. Linux is required for the full POSIX
permissions/symlink tests. The report distinguishes local PostgreSQL 16.6 test
evidence from the pinned 16.15 deployment target.

Second-domain intentionally removes sample-domain BPMN resources: use its explicit
CI reuse scope above, not an indiscriminate `*Test` run that requires those removed
fixtures. The report records the failed broad attempt as well as the passing proof.

To explicitly select the local fallback test image, append
`-DatabaseImage postgres:16.6-bookworm`; this changes only disposable tests, never
the deployment pin. Do not describe such results as PostgreSQL 16.15 acceptance.

After rebuilding actual deployed images, scan ALL Compose services (including
frontend and Intelligence), not a historical subset:

```powershell
.\scripts\scan-core.ps1 -FromCompose -ComposeFiles @('compose.yaml','infra/compose/compose.triage.yaml') -Profiles intelligence
```

Successful scanner execution is not a clean security gate. Source manifest scans
do not certify installed image packages, OS libraries or cloud/profile locks.
License choice, data rights, publication, release tags and production approval
remain owner decisions. No commit, deployment or publication is performed here.
