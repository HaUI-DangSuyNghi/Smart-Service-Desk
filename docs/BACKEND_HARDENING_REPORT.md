# Backend hardening evidence - 2026-10-25

## 1. Executive summary

Baseline: branch `develop`, HEAD `f94f0ef102cbc7d5737ca05270b0affab1fde1a6`.
The old audit used `24ba2820c2cfa56b24f68fe026312becaf530b40`; it is not evidence
for this HEAD. In particular, public ticket triage and opt-in grounded/cloud
assistance now exist. Their absence must not be repeated as a current finding.

Implemented Phase 1: shared neutral Core permission decision, Core HTTP document
and worker-task guards, current worker-completion permission under the existing
workflow lock/transaction, and public audit permission parity. Core never imports
HPDI. Ownership, candidate groups, independent approval, exact initiator
confirmation and the upload-disabled gate remain in force.

The original metadata regression failed before the patch (one assertion failure,
zero skipped): no forbidden exception was raised. Expanded HTTP regression tests
now cover both metadata read forms, file descriptors/bytes, worker inbox/claim,
policy changes, foreign workspace, service identity, directory outage and writes.
Real RSA-signed JWT tests also exercise permission revocation at the Spring
resource-server boundary; they do not use `jwt()` to bypass token decoding.

Two identical final HTTP test methods were also run against exported HEAD
controllers in an isolated archive (only the regression/policy test harness was
added, no production endpoint guards). Both failed with expected 403 versus actual
200: candidate inbox access and document access after a policy change. Their
patched versions pass in the final suite. Evidence: `http-baseline-red.log` and
`http-baseline-red-reports` under the evidence directory.

Database/Flowable tests execute against private, disposable PostgreSQL 16.6
instances on Linux. All original migrations are unchanged. The production-pinned
16.15 local image is damaged and could not boot; no shared database/image/volume
was repaired or reset. This is NOT PostgreSQL 16.15 deployment acceptance.

Applied the Spring-published eager-header mitigation for CVE-2026-22732 without
changing dependency versions. A focused test failed on deferred headers before
the mitigation, then passed. Other scanner-reported findings remain release
blockers; this change is neither a clean scan nor blanket remediation.

## 2. Changed files

Paths below are repository-relative; grouped paths share the same requirement.

| File | Change and reason | Requirement |
| --- | --- | --- |
| backend/src/main/java/vn/haui/dx/core/identity/api/WorkspacePermissions.java | Intersect signed workspace roles with current directory mappings; fail closed | F04, neutral policy |
| backend/src/main/java/vn/haui/dx/core/identity/api/RequirePermission.java | Mark HTTP method permission requirements | F04, end-user boundary |
| backend/src/main/java/vn/haui/dx/core/identity/internal/EndUserPermissionConfiguration.java | Enforce annotations on resolved MVC handlers before controller execution | F04, no path-string bypass |
| backend/src/main/java/vn/haui/dx/core/identity/api/WorkspaceDirectory.java | Correct obsolete display-only permission documentation | Policy accuracy |
| backend/src/main/java/vn/haui/dx/hpdi/human/internal/KeycloakPermissionGuard.java | Delegate to the same neutral Core decision | HPDI/Core parity |
| backend/src/main/java/vn/haui/dx/core/data/MetadataApi.java | document:view reads; document:upload create | F04-A |
| backend/src/main/java/vn/haui/dx/core/data/FileApi.java | document:view descriptor/bytes; document:upload upload; keep scan gate | F04-A |
| backend/src/main/java/vn/haui/dx/core/workflow/WorkflowApi.java | request:create start; task:handle inbox and claim | F04-B |
| backend/src/main/java/vn/haui/dx/core/workflow/internal/FlowableWorkflowService.java | Require current task:handle for non-initiator completion; mandatory directory | F04-B, requester exception |
| backend/src/main/java/vn/haui/dx/core/audit/web/AuditController.java | audit:view before public Core audit reads | Cross-workspace/audit parity |
| backend/src/main/java/vn/haui/dx/core/identity/internal/SecurityConfig.java | Write default security headers eagerly | Verified vendor mitigation |
| backend/src/test/java/vn/haui/dx/core/CoreAuthorizationRegressionTest.java | Six HTTP regressions using real shared policy, mocked storage/engine | F04 A-E |
| backend/src/test/java/vn/haui/dx/core/CoreRuntimeIntegrationTest.java | Real signed JWT revocation/service-token regressions; new file denial expectations | Real resource-server integration |
| backend/src/test/java/vn/haui/dx/core/identity/internal/KeycloakWorkspaceDirectoryTest.java | Clock-controlled 59/60/120-second cache/revocation/outage regression | F04-D |
| backend/src/test/java/vn/haui/dx/core/identity/internal/SecurityHeadersTest.java | Actual configured HeaderWriterFilter checked before downstream failure | Security red/green |
| backend/src/test/java/vn/haui/dx/core/workflow/internal/WorkflowApprovalTest.java | Revoked-assignee completion/outage rollback; explicit permission fixtures | F04-B; real Flowable |
| backend/src/test/java/vn/haui/dx/hpdi/human/KeycloakPermissionGuardTest.java | Shared policy, refresh, outage and all three knowledge reads | HPDI parity |
| backend/src/test/java/vn/haui/dx/core/identity/PermissionTestDirectory.java | Explicit test-only realm-like mappings | Isolated DB tests, no real Keycloak claim |
| backend/src/test/java/vn/haui/dx/TestApplication.java | Primary test directory fixture; no production override | Reproducible tests |
| scripts/acceptance/core/verify-runtime.py | Expect requester Core document denial; authorized agent reads; no implicit admin audit | Contract regression alignment |
| scripts/start.ps1 | Opt-in full local triage stack with shared private token and restored environment | Reproducible demo |
| scripts/scan-core.ps1 | Optional actual Compose image inventory, including frontend/Intelligence | Scan coverage |
| scripts/test-file.ps1 | Explicit disposable DB image override; report the image actually selected | Honest environment evidence |
| docs/BACKEND_API_INTEGRATION_CONTRACT.md | Current FE/AI endpoints, DTOs, policies, errors and reproduction | Required handoff |
| docs/BACKEND_HARDENING_REPORT.md | This evidence and residual-risk record | Required final report |

No frontend source/dependencies, AI algorithms/profiles, historical migrations,
license, CI exception baselines or the four pre-existing untracked documents
were changed. No commit, branch, tag, push, publication or production deploy.

## 3. Security findings

| Finding | Root cause | Fix / regression | Status |
| --- | --- | --- | --- |
| F04 document bypass | Public Core metadata/files checked membership/resource policy but not document:view | Handler annotation + shared policy; HTTP denial before storage; signed-JWT revocation | FIXED for these public routes |
| F04 worker task bypass | Core inbox/claim and assignee completion lacked current task:handle | HTTP guards; completion policy inside locked transaction; actual engine/audit unchanged on denial | FIXED for these actions |
| Requester confirmation | Unconditional worker permission would block legitimate initiator steps | Exact BPMN initiator step still requires owner and assignee; existing real workflow tests cover it | PRESERVED |
| Public audit parity | Core coordinator/platform-admin read could avoid HPDI audit:view | Public Core audit now requires audit:view and workspace membership, then existing service role policy | FIXED at HTTP boundary |
| Directory outage/revocation | A stale fallback or alternate guard cache could restore revoked rights | Same directory cache; expiry/outage test and shared policy | PASS with documented cache/token windows |
| Service-token escalation | Confusing private bearer with a user/workspace grant | No bypass added; no-group signed identity gets 403; private opaque token gets 401 | NEGATIVE TEST PASS |
| CVE-2026-22732 | Spring Security default header timing in affected dependency | Vendor eager HeaderWriterFilter mitigation; before-downstream header regression | MITIGATED, dependency still reported |
| Remaining manifest advisories | Existing dependency versions | See raw fresh source scan; no unreviewed major/minor framework migration | OPEN, release blocked |

Vendor reference verified during this task:
`https://spring.io/security/cve-2026-22732/`. The official advisory lists eager
header writing as a mitigation. The test proves configuration/header timing, not
an exhaustive reproduction of every servlet-container error-dispatch exploit.

Fresh Trivy 0.76.0 source scan (2026-10-25) reports:

| Target | HIGH | CRITICAL | Coverage caveat |
| --- | --- | --- | --- |
| backend/pom.xml | 15 | 2 | Manifest/dependency findings, not per-CVE exploit verification |
| frontend/package-lock.json | 1 | 0 | Frontend changes are outside the allowed patch scope |
| scripts/acceptance/requirements.txt | 0 | 0 | Does NOT cover installed AI runtime/profile locks |

Seven Dockerfile misconfiguration failures are also reported, including root-user
warnings for frontend/APISIX. Image-level healthchecks are absent in several
Dockerfiles but Compose defines healthchecks; assess context instead of inventing
a runtime outage from a static warning. Do not blindly switch APISIX users without
its filesystem/port/startup compatibility tests. Frontend ownership stays with FE.

The first online manifest scan FAILED on Maven HTTP 429 and was not accepted as
clean. A second offline-resolution scan with the populated Maven cache produced
JSON findings. It warns that `intelligence/Dockerfile.dockerignore` was misidentified
as a Dockerfile, and that Python installed-package/license information is absent.
Installed container images, OS packages, AI locks and optional cloud runtime still
require a fresh image scan. `scan-core.ps1 -FromCompose` supplies actual selected
Compose services instead of silently omitting frontend/Intelligence.

## 4. Database/workflow findings

| Component | Issue/check | Resolution / evidence |
| --- | --- | --- |
| Empty DB | Previously skipped integration evidence | Fresh private DBs apply and validate Flyway V1..V7 |
| Upgrade | Legacy file-name backfill and existing constraints | Existing real test migrates a separate schema to V2, inserts legacy row, upgrades and verifies backfill/NOT NULL |
| Ticket transactions | Ticket + engine + audit must commit together | Existing post-start/audit failure rollback tests execute against PostgreSQL |
| Workflow transactions | Engine and process_scope/audit consistency | Real shared datasource/transaction manager checks; start/completion rollback and concurrency tests |
| Concurrency | Duplicate request ID, concurrent starts, claim/double complete | Existing unique constraint + row-lock regressions run, not replaced with distributed locks |
| Task policy | Stale key, invalid outcome, unauthorized assignment, independent approver | Existing real BPMN tests remain; revoked task:handle test added |
| File consistency | DB rollback after physical write; bad blobs/symlinks | Linux POSIX/symlink, nested-savepoint and unknown-commit-outcome tests execute |
| File crash recovery | Filesystem + Postgres are not a distributed atomic transaction | Existing cleanup hooks tested; crash-time reconciliation / real backup restore NOT RUN here |
| Local runtime | Pinned 16.15 image has empty passwd / truncated libldap, cannot boot | Isolated failed container removed; explicit 16.6 fallback only for tests; no deployment pin changed |

No migration drift/schema fix was justified by the passing tests, so no existing
migration was rewritten and no speculative migration was added.

## 5. Test results

Evidence directory (ignored, machine-local): `.local/backend-hardening-20261025/`.
Final Java runs use Linux, Java 17.0.15, Maven 3.9.9, a read-only populated Maven
cache and fresh disposable PostgreSQL 16.6 databases. XML reports, full build logs
and both failed attempts are retained; selected acceptance runs have zero skips.

| Suite / attempt | Passed | Failed | Errors | Skipped | Status / notes |
| --- | --- | --- | --- | --- | --- |
| Original metadata regression, before fix | 0 | 1 | 0 | 0 | EXPECTED RED, exception not thrown |
| Identical final HTTP tests against baseline controllers | 0 | 2 | 0 | 0 | EXPECTED RED: both returned 200 instead of 403 |
| Security header timing, before mitigation | 0 | 1 | 0 | 0 | EXPECTED RED, nosniff absent before downstream |
| First Windows full DB run | 249 | 1 | 3 | 0 | Initial stale file expectation corrected; POSIX/symlink failures are Windows privilege/platform limits |
| Linux F04 full verify before header mitigation | 256 | 0 | 0 | 0 | PASS, real DB, packaged JAR |
| Final Linux full verify | 257 | 0 | 0 | 0 | PASS, includes header mitigation and every final Java change; packaged JAR |
| Core-only full verify | 89 | 0 | 0 | 0 | PASS; no upper tiers; private PostgreSQL |
| Second-domain over-broad default suite | 196 | 2 | 1 | 0 | FAIL: intentionally excluded sample BPMN absent; not a passing reuse gate |
| Second-domain supported CI proof + policy tests | 31 | 0 | 0 | 0 | PASS, explicit existing reuse scope plus policy tests; no Service Desk/sample modules |
| Source boundary mutations | 39 | 0 | 0 | 0 | PASS; also actual source guard PASS |
| Compose/static tooling validation | n/a | 0 | 0 | 0 | PASS: six-service config/inventory with temporary placeholders; three PowerShell ASTs and Python acceptance syntax |
| Real Keycloak -> APISIX -> Java -> Python | 0 | 0 | 0 | 0 | NOT RUN in this task; historical evidence not reused |
| Browser E2E | 0 | 0 | 0 | 0 | NOT RUN; frontend not changed |
| Production-image/OS scan | 0 | 0 | 0 | 0 | NOT RUN; source scan is separate evidence |

The broad `second-domain` invocation is not the repository's CI proof command:
that profile deliberately removes `apps/samples` while generic workflow suites
expect those fixtures. No test was disabled or weakened to hide this. The supported
reuse scope is the existing `tier-boundaries.yml` Room Request/architecture/HPDI
selection, plus the new policy regressions. Its exact command and XML reports are
retained separately from the failed broad attempt.

DB Spring contexts use explicit test-only directory mappings. Keycloak HTTP calls
are exercised by `MockRestServiceServer`, including the real 60-second cache.
RSA JWT validation uses a real local JWKS HTTP server. Intelligence transports use
real Java HTTP clients against controlled HTTP servers; that is NOT real Python
execution. Optional `*E2E` classes not selected by Maven's normal `*Test` discovery
are NOT RUN, even when the selected suites have zero skipped tests.

## 6. Integration status

| Boundary | Status |
| --- | --- |
| Keycloak | JWT/JWKS rejection and role mapping/cache tests PASS; live realm/Admin API NOT RUN |
| APISIX | Existing route configuration inspected; no live gateway acceptance in this task |
| Backend | Actual MVC, real RSA JWT, application transactions and compiled dependency guards tested |
| PostgreSQL | Real disposable Linux 16.6 DB tests; exact production-pinned 16.15 NOT VERIFIED |
| Flowable | Real engine, candidate/assignee/initiator, rollback, concurrent claim/complete tested |
| Intelligence | Existing transport/validator/ownership tests; live current Python/private-token stack NOT RUN |
| Room Request | Existing app integration plus separate no-Service-Desk proof; see final totals |

Runtime artifact: `backend/target/dx-platform-0.1.0-SNAPSHOT.jar`, built by the
final successful `clean verify`. Local logs are `linux-full.log`, `core-only.log`
and `second-domain-supported.log`; XML directories are `full-reports`,
`core-only-reports` and `second-domain-supported-reports`. The failed broad
second-domain log/reports and original red tests remain separate, not overwritten.

## 7. Frontend handoff

Use `docs/BACKEND_API_INTEGRATION_CONTRACT.md`: JWT/PKCE, current routes,
permissions, DTO fields/limits, error shapes and sample JSON are specified there.
URLs are retained; restricted Core reads now return the same denial as HPDI.
Never fall back to Core after 403, cache permission-sensitive data indefinitely,
embed a service token or treat UI visibility as enforcement. Keep initiator
confirmation accessible via progress/timeline rather than worker inbox.

FE Owner must verify actual login/session renewal, new 403/503 states, download
handling, refreshed permissions and advisory-only triage. Resolve the independently
reported frontend manifest advisory in the FE-owned change; no FE patch here.

## 8. AI handoff

No AI algorithm/profile/data/model change is included. Current Java triage uses
`service-desk@0.3.0`, contract 0.1.0; private context is server-derived. Preserve
the existing separate reference/cloud envelopes and guarded synthetic routing.
AI Owner must run real Python E2E with the current installed wheel, bearer and
profile pins, including wrong-token, unavailable, timeout and malformed payloads.
No real API calls, paid provider usage, cloud egress or dataset release were made.

## 9. Remaining risks

- P0/release: raw source scan includes CRITICAL findings; one header issue is
  mitigated, not a reason to mark the other dependencies safe. Compatibility-tested
  dependency remediation and actual image/OS scans remain mandatory.
- P1: full live Keycloak/APISIX/current Python and exact pinned-DB acceptance remain
  unverified. Repair the owner's local image storage in a separately approved step.
- P1: application-owned creation/read rules are not interchangeable with every
  generic process permission. This patch does not claim a universal permission
  audit of all app business operations; broaden only with an explicit policy matrix.
- P1: Core's generic start policy does not carry HPDI's selfService flag. The
  application-only-start restriction is enforced by HPDI, not asserted for every
  Core process start. Mapping that contract across tiers is a separate compatibility
  change; never use generic Core start as the UI path for creating business tickets.
- P2: directory cache mappings can remain effective for 60 seconds; signed role
  membership can remain until token renewal/expiry. No instantaneous-revocation claim.
- P2: Keycloak directory pagination limits (100 role children / 1000 group members)
  remain unchanged. Current small demo is within that ceiling; larger directory
  evidence is required before scaling.
- Unverified: browser E2E, production content scanning, crash/backup recovery,
  installed AI/profile/cloud dependency inventory and a clean source checkout build
  without this machine's populated Maven cache.
- Owner approval: license/source publication, public release/tag, third-party data
  rights and production deployment. None was decided or performed here.

## 10. Final verdict

F04's tested document-read and worker-handle alternate routes are closed, with the
requester-confirmation exception preserved and no Core -> HPDI dependency. Real
database/workflow evidence replaces the old compilation-only/skipped evidence.
API integration is suitable for controlled local development against the documented
contract, not unconditional production approval.

**Release verdict: NO-GO** until remaining security findings, exact pinned-image
runtime acceptance, live integration and owner release/licensing decisions are
resolved. `start.ps1 -Intelligence` is the explicit local full-demo command; its
actual end-to-end execution is NOT claimed as passed in this environment.

## 11. Core generic start integrity closure - 2026-10-25

This follow-up supersedes only the generic-start/selfService P1 item in section 9.
Sections 1-10 and their earlier successful, failed and unverified evidence remain
historical evidence, not fresh acceptance results. HEAD remains
`f94f0ef102cbc7d5737ca05270b0affab1fde1a6`; the previous uncommitted hardening
changes are retained. No commit, deployment or shared-data repair is performed.

### A. Root Cause

Confirmed application-initialization integrity bypass, not authentication bypass.
APISIX forwards `/api/core/v1/*` to the backend; its route does not establish a
private service-only boundary. The actual backend path was resource-server JWT
validation -> MVC `request:create` permission -> `WorkflowApi.start` -> registered
starter role/allowed variables -> Flowable transaction -> `process_scope` and
`process.start` audit. Membership and starter roles were enforced, but did not
express whether this HTTP entry point was allowed for the process.

HPDI's `ProcessDefinition.selfService` defaults false. Service Desk and Room
Request retain false; sample `document-review` and `equipment-approval` explicitly
call `allowSelfService()`. HPDI's generic catalog rejects false, but
`ContributionRegistry` discarded this metadata when constructing Core policies.
Consequently, `request:create` plus a starter role allowed Core HTTP callers to
start an application-only process without running the application service.

The legitimate path is application controller -> transactional application service
-> HPDI process capability -> Core workflow transaction -> business insert ->
business audit, all joining the outer transaction. Core cannot require an existing
business record at start: the application creates it after obtaining the process
ID. Core also must not query application tables or import application types.

### B. Exploit/Reproduction

Regression was written and executed before the production patch:

```http
POST /api/core/v1/workspaces/lab-a/processes/service-desk/instances
Authorization: Bearer <locally RSA-signed requester JWT>
Content-Type: application/json

{"businessKey":"01e6882f-8ffe-41d7-80bd-bb9af118e54a","variables":{}}
```

The real Spring resource-server decoder fetched the test public JWKS and validated
RS256, issuer, audience and expiry. The signed actor belongs to lab-a/requester;
the test directory grants requester `request:create`. Only directory mappings are
a test double; these four signed-start tests do not use `jwt()` authentication
injection or mock Flowable/database writes. A live Keycloak or gateway hop is not
claimed.

RED: HTTP **201**, with counters ordered as ticket / process_scope /
act_hi_procinst / act_ru_task / audit_event:
`[0,1,1,1,1] -> [0,2,2,2,2]`. There is a new real process, task, scope and technical
audit but still no ticket or application-create audit. The first existing process
belongs to the independently successful self-service control. Result: 3 tests,
1 expected regression failure, 0 errors, 0 skipped. This establishes orphan
creation and bypass of application initialization; it does not prove arbitrary
task completion, data exfiltration or every hypothesized business-validation flaw.

Evidence: `.local/core-workflow-start-20261025/red.log` and the
`CoreStartIntegrationTest` XML/text in `red-reports` (other reports in that archive
may predate this selected run and are not counted as RED evidence).

### C. Architecture Decision

Carry the existing boolean as neutral Core-owned registration metadata and enforce
it at the generic Core HTTP start boundary. HPDI maps the flag rather than inventing
a second policy. The permission interceptor still runs first; self-service does
not replace membership, current directory permission, starter-role or variable
validation. Rejection occurs before calling the workflow service.

Do not globally reject application-only starts in `WorkflowService.start`: it is
the trusted in-process capability needed by both transactional application create
flows. No HTTP header, JSON variable, business key or service-identity claim can
turn an HTTP start into this in-process path. No new permission framework,
Core-to-HPDI edge, application-name branch or business-table check is introduced.

### D. Source Changes

- `ProcessPolicy`: additive `selfService` record component; existing four/six-arg
  Java constructors retained and default false (fail closed).
- `ContributionRegistry`: propagate `ProcessDefinition.selfService` unchanged.
- `WorkflowApi`: reject application-only generic HTTP starts with 403 before writes;
  retain the earlier `request:create` interceptor annotation.
- `CoreStartIntegrationTest`: real signed-JWT tests for denied app-only start,
  allowed self-service, foreign workspace and linked application-owned creation.
- `CoreAuthorizationRegressionTest`: omitted metadata defaults false; a client
  variable named `selfService` cannot override it; workflow service not invoked.
- `ContributedProcessCatalogTest`: assert both true and false survive registration.
- `RoomRequestIntegrationTest`: generic room start denied without changes to room,
  scope, engine history/task or audit counters; existing reuse tests retained.
- Core-only `ApprovalProcesses` fixture: explicitly opt the two samples into HTTP
  self-service, preserving their intended behavior without a permissive default.
- `TicketIntegrationTest`: statistics corruption fixture now seeds its standalone
  application-only process through the authenticated in-process service, not the
  newly forbidden HTTP route. All statistics/scope-mismatch assertions remain.
- `BACKEND_API_INTEGRATION_CONTRACT.md`: document the HTTP restriction, additive
  metadata and Java registration compatibility impact.

### E. Test Evidence

Fresh evidence directory: `.local/core-workflow-start-20261025/`. Disposable Linux
PostgreSQL **16.6**, actual Flowable, Java 17.0.15 and offline Maven with the local
dependency cache. This is explicitly NOT pinned production PostgreSQL 16.15
acceptance. Separate source copies/databases avoid profile build-output races.

- RED: 3 tests, 1 expected failure, 0 errors/skips, on unpatched production code.
- First targeted GREEN attempt and first Core-only attempt: test compilation failed
  because the new test omitted the static `assertFalse` import. This was a harness
  error, fixed without changing the asserted policy; neither run is acceptance.
- First full run: 264 tests, 263 passed, 1 failure, 0 errors/skips. The failure was
  the old statistics fixture expecting HTTP 201 for exactly the prohibited orphan
  creation (`statisticsIgnoreStandaloneCoreProcessesAndDetectScopeMismatch`).
  Its setup now uses the trusted service; statistics assertions are unchanged.
  Evidence: `full-first-fixture.log` and `full-first-fixture-reports`.
- Final full `mvn -B -o clean verify`: **264 passed**, **0 failures**, **0 errors**,
  **0 skipped**, with a packaged application JAR; `full-final.log` and
  `full-final-reports`. Surefire XML totals were checked independently. The signed
  denied-start probe reports HTTP **403**, counters
  `[1,5,5,4,14] -> [1,5,5,4,14]`. This run includes 4 signed-start tests,
  16 Service Desk integration tests, 4 Room Request integration tests,
  17 workflow approval tests and the compiled architecture boundary test.
- Core-only `mvn -B -o -Pcore-only clean verify`: **90 passed**, 0 failures,
  0 errors, **0 skipped**; `core-only.log` and isolated-workspace Surefire XML.
- Supported second-domain `clean verify`: **39 passed**, 0 failures/errors/skips;
  `second-domain.log` and isolated-workspace Surefire XML. Scope:
  `RoomRequestIntegrationTest,TierBoundaryTest,CoreKnowledgeCatalogTest,CoreProcessCapabilitiesTest,WorkspaceActivityJournalTest,TrustedIntelligenceCapabilityTest,CoreAuthorizationRegressionTest,KeycloakPermissionGuardTest,KeycloakWorkspaceDirectoryTest,SecurityHeadersTest,ContributedProcessCatalogTest`.
- `py -3.11 scripts/check-tier-boundaries.py`: PASS; `source-boundaries.log`.
  The supported second-domain run also executes the compiled ArchUnit boundary
  check. Broad second-domain was not rerun or claimed green; its earlier missing
  sample-BPMN failure remains recorded in the previous evidence.

### F. Regression & Compatibility

Tests A-D exercise real decoded, signed JWTs and real persisted workflow state.
Denied generic app-only and foreign-workspace calls leave all observed counters
unchanged. Allowed sample start still returns 201 and adds exactly one scope,
history, task and technical audit, without creating a ticket. Valid Service Desk
creation adds one ticket, its linked workflow and both technical/business audits.
The full suite retains application rollback probes (failure after engine start and
business-audit trigger rejection), independent handling, rework and initiator
confirmation. Test E preserves Room Request's unchanged HPDI/Core reuse.

The HTTP URL and request DTO are unchanged; application-only generic calls now
intentionally return 403. Process-definition JSON gains `selfService`. Existing
Java registration source compiles, but four/six-arg registrations that need HTTP
start must explicitly pass true via the seven-arg constructor. This deliberately
tightens the old implicit-public default; it is not binary compatibility for
already compiled record consumers. The Core-only sample registration explicitly
declares its valid self-service policy. Internal capability signatures and app
transaction code remain unchanged.

### G. Remaining Risks

Earlier dependency vulnerabilities, exact pinned-image runtime acceptance, live
Keycloak/APISIX/Python integration, directory-cache/token revocation windows and
release/licensing approvals remain unresolved as described in section 9. No new
dependency scan or production readiness claim is made by this follow-up.

Registration metadata is trusted server code: mistakenly marking an app-owned
process self-service is still a configuration defect. Application permission
matrices remain separate from generic workflow permissions. This fix neither
finds nor repairs historical production orphans; reconciliation requires a
separately scoped owner-approved inventory, not automatic deletion. Reproduced
orphans exist only in disposable test data, which is discarded.

### H. Final Verdict

The confirmed generic-start application-initialization gap is closed by
server-owned process exposure metadata, without breaking in-process application
creation or adding a Core dependency on upper tiers. For this threat, generic
start is appropriate for controlled end-user Gateway exposure only for processes
explicitly registered self-service. It is not a route for business ticket/room
creation. Overall production release remains **NO-GO**, not upgraded by closing
this one finding; the earlier release blockers still apply.

### Current Lifecycle And Integration Readiness

BPMN files are unchanged. Service Desk goes from `handle` (workspace agent,
independent claimant/handler) to `confirm` (exact initiator); `accepted` ends the
process and `rework` returns to `handle`. Room Request uses the workspace
coordinator `approve` task and ends after its configured approval/rejection
outcome. Document review branches to approved/rejected; equipment approval either
rejects or proceeds to agent handover. No new start rule changes claim, completion,
visibility, workspace binding or initiator confirmation.

The business-link guarantee applies to successful application-service creates
inside their transaction, not to every trusted Java call or historical process.
Core deliberately has no database-level requirement that every process reference
an app record; self-service processes do not have one. Trusted application code
must continue using its transactional creation service for app-owned workflows.

Reviewed the supplied official PDF
`docs/references/De_thi_PMNM_HaUI_2026_BAN_CHINH_THUC.pdf`, especially sections 3,
4.1 and 4.2 (pages 2-4). Its three-tier/headless reusable Core requirement is
preserved: Core owns generic exposure metadata, HPDI translates contributions and
offers reusable capabilities, and Service Desk/Room Request own their records and
initialization. Source/ArchUnit isolation and second-domain reuse are concrete
technical evidence for that requirement, not a claim that the entire competition
rubric, PoF/license checklist or live demo has been accepted.

**Frontend/AI:** ready for controlled local integration against the updated API
contract. Use the Service Desk/Room Request application APIs for business create,
and generic Core/HPDI start only for advertised self-service processes. Existing
intelligence transport/validation tests remain green, but actual browser/AI
services, current Python runtime and Keycloak-to-APISIX end-to-end execution are
**NOT RUN** in this follow-up. The pinned PostgreSQL 16.15 runtime and broad
second-domain suite are also **NOT RUN** here. Final selected acceptance has
**0 FAIL / 0 SKIPPED**; earlier failed attempts remain documented above. Local
workflow readiness for the competition project is distinct from production
release approval.

Files changed by this workflow follow-up (separate from the earlier dirty work):

- `backend/src/main/java/vn/haui/dx/core/workflow/api/ProcessPolicy.java`
- `backend/src/main/java/vn/haui/dx/core/workflow/WorkflowApi.java`
- `backend/src/main/java/vn/haui/dx/hpdi/process/internal/ContributionRegistry.java`
- `backend/src/test/java/vn/haui/dx/apps/servicedesk/CoreStartIntegrationTest.java`
- `backend/src/test/java/vn/haui/dx/apps/servicedesk/TicketIntegrationTest.java`
- `backend/src/test/java/vn/haui/dx/apps/roomrequest/RoomRequestIntegrationTest.java`
- `backend/src/test/java/vn/haui/dx/core/CoreAuthorizationRegressionTest.java`
- `backend/src/test/java/vn/haui/dx/core/workflow/internal/ApprovalProcesses.java`
- `backend/src/test/java/vn/haui/dx/hpdi/process/ContributedProcessCatalogTest.java`
- `docs/BACKEND_API_INTEGRATION_CONTRACT.md`
- `docs/BACKEND_HARDENING_REPORT.md`
