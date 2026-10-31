# R3 - Shift Scheduling And End-Of-Shift Handover

Date: 2026-10-28, Asia/Saigon. Branch: `feature/r3-shift-scheduling`.
Base: `a6a92845c48eeefa86e5e261e382dbd096160cdb`, fetched from `origin/develop`.
The initial local HEAD was the R11 source parent; fast-forwarding to the specified
merge baseline changed no source files. Existing untracked documents are untouched.
`docs/ticket.md` was read as supplied local requirements, not committed as authority.
Delivery: Draft review only; no merge, force push, history rewrite or CI waiver.

Final acceptance follow-up is recorded at the end of this document. It supersedes
the initial test counts, disabled upgrade-test workaround, and open runtime/race
gaps below. PR #60 remains Draft pending independent approval and external gates.

## GAP Matrix

| Area | Before R3 | This change / scope |
| --- | --- | --- |
| Manual duty | Implemented: V14 persistence, own-Agent mutation, audit | Preserved unchanged; no V14 data rewrite |
| Shift schedule | Missing | HPDI date-specific absolute intervals, IANA timezone, explicit enable, per-Agent CAS version, retained prior shifts |
| Schedule RBAC | Missing | Coordinator `task:assign` manages/reads workspace; Agent `task:handle` reads own; fresh role/membership verification |
| Effective duty | Partial: manual only | Manual AND active enabled schedule; absent/disabled schedule keeps manual-only eligibility |
| Assignment enforcement | Implemented shared eligibility at review/assign | Same gate reads effective duty under transaction-scoped Agent lock |
| Suggestions | Existing ET/load/daily counts/round robin; shift minutes null | Preserve ranking; exclude effective OFF; real remaining minutes and explicit ET warning |
| R6 handover | Implemented START/PAUSE/RESUME/RELEASE/REVIEW | Reused; no timer mutation or second workflow |
| Persistent alerts | Implemented event/outbox, recipients, generations | Worker roster now evaluates schedules; existing off/empty generations deduplicate |
| Organization-wide idle group alert | Partial: only active reviewed handle monitors | Still partial; no new intake-wide/group monitor |
| Leave approval/weekly recurrence | No approved local scheduling contract | BUSINESS_DECISION_REQUIRED; explicit dated roster encodes approved results only |
| WorkSettings/Admin, R7 emergency auto-queue, R12 | Separate scope | Not implemented or promoted |

## Shift Policy And Provenance

- Baseline is an explicit finite dated roster, not an implicit recurring calendar.
  Many Agents may share an interval; an Agent may have multiple non-overlapping
  intervals. Adjacent intervals are allowed. Each interval is at most 24 hours;
  each complete replacement accepts at most 366 intervals.
- Business timezone defaults to `Asia/Ho_Chi_Minh`, configurable with
  `dx.scheduling.business-zone`. Recommended local templates remain morning
  07:00-11:30 and afternoon 13:00-17:00. No automatic weekday/holiday expansion.
  Coordinators configure actual dates/hours through the schedule payload.
- `startsAt` and `endsAt` are absolute offset-bearing instants, stored as PostgreSQL
  `timestamptz`, with a validated IANA timezone alongside. Start is inclusive; end
  is exclusive. API clients must convert selected local date/time in that timezone
  to an offset-bearing instant; never append Z to unconverted local time.
- New Agents and legacy Agents without a schedule remain manual-only. Creating a
  disabled schedule does not change eligibility. `enabled=true` is explicit opt-in.
  Enabled with no shifts means no scheduled availability. Cancel by omitting the
  interval in the next complete version, not by deleting history.
- Manual OFF always wins; the schedule never writes `hpdi_agent_duty`. Manual ON
  cannot bypass an enabled schedule outside its intervals. Disabled schedules return
  to the legacy manual-only policy. No automatic reset at shift boundaries.
- Provenance is the original manual duty row and its `updatedAt`, the schedule
  version, immutable versioned intervals, and the real actor in transactional audit.
  Effective response exposes manual/scheduled/effective values and reason.
  Manual toggles retain the existing last-writer-wins API; schedule writes require CAS.
- Approved leave is represented by excluding approved unavailable intervals from
  the dated roster. There is no leave approval capability in this PR.

## Assignment And Handover

`AgentDuty.onDuty` now means effective eligibility for NEW assignments. The original
`DutyStatus.onDuty` remains the manual switch; additive `effectiveDuty` is the value
suggestions use. Candidate `onDuty` is effective. Requesters are still excluded,
and current support-group membership is still required. No eligible candidate
returns a null recommendation; suggestion GET never publishes alerts.

Remaining minutes are whole minutes until the current shift end (including zero
in its last minute), or null outside a shift/manual-only mode. Ranking does not
change. `ESTIMATE_EXCEEDS_SHIFT` in suggestion reason tells the Coordinator to
compare ET with each candidate's minutes and explicitly decide overtime/R6 handover.
There is no automatic splitting, reassignment or cross-group bypass.

Existing work is not withdrawn at shift end, including NOT_STARTED assignments.
START, PAUSE, RESUME and completion retain existing authorization/state checks.
R2 ET operations and the R9 retained-incumbent exception preserve their original
manual-duty/group checks, not new-assignment schedule checks.

An Agent may use existing `POST .../tasks/{id}/release` with `reason=HANDOVER` and a
note explaining end of shift and work completed. The same process returns to real
Coordinator review. Existing R6 machinery conserves worked time, pending history,
SLA/ET/correction records and R11 lifecycle; this change does not alter BPMN or
confirmation tables. No worker impersonates an Agent or Coordinator. An absent
Agent cannot be forcibly released using this feature; a separately authorized
Coordinator-initiated handover remains BUSINESS_DECISION_REQUIRED.

## Alerts And Transactions

The existing worker reads current workspace/support-group Agent membership and
evaluates effective duty for those members. It does not infer an empty roster
from an empty recommendation or remove a legitimate roster Agent merely because
that Agent requested a particular ticket. OTHER still has no fictitious group.

An active handle's existing monitor emits `ASSIGNEE_OFF_DUTY` / `NO_ONE_ON_DUTY`
on the corresponding false-to-true condition. Persisted group/off generations and
event uniqueness survive polling/restart; returning on duty rearms a later episode.
Existing authorized delivery filters and durable outbox remain unchanged. Schedule
mutations and their audit commit atomically; alert mutations and their audit/outbox
commit atomically in the existing monitor transaction, not in suggestion GET.

Read projections use repeatable-read snapshots without Agent advisory locks.
Assignment lock order: existing process/task lock, then the
`agent-duty:workspace:subject` transaction advisory lock, then existing assignment
event/cursor writes. Review retains its pre-existing ticket-row lock; its redundant
early assignment eligibility preflight is removed, leaving the canonical check
under the process lock at actual assignment. A failed eligibility check rolls the
whole review transaction back, including task changes and audit.
Schedule and manual mutation take only the Agent lock before their rows/audit.
They never acquire process locks. No JVM synchronization is used.
The effective-duty check linearizes assignment at check time; later shift expiry
does not retrospectively revoke a committed assignment.

## Migration And Frontend Handoff

Only V31 is new; V1-V30 are not edited. V31 adds `hpdi_agent_schedule` and versioned
`hpdi_agent_shift`; it inserts no legacy enrollments and has no Room Request policy.
Historical rows are retained, bounded per replacement but not globally pruned.

Promoted paths in live HPDI OpenAPI:

| Endpoint | Authorization / FE behavior |
| --- | --- |
| `GET .../workspaces/{workspace}/schedules` | Coordinator workspace roster; Agent own entry only |
| `GET .../schedules/{subject}` | Coordinator or own Agent; `me` supported |
| `PUT .../schedules/{subject}` | Current Coordinator + `task:assign`; full replacement with `version`, `enabled`, `shifts` |
| `GET .../schedules/{subject}/effective-duty` | Coordinator or own current Agent; reasons and remaining shift |

First PUT sends `version=0`; every successful PUT increments it. On 409, refetch and
ask the user to reconcile; never silently retry a stale replacement. On cancellation,
retain other dated shifts in the replacement. Display manual and effective duty
separately, show the ET warning, and retain normal R6 handover actions after shift end.

## Initial Delivery Evidence And Findings (Historical)

Evidence files are local `.local/r3-*` logs and Maven Surefire XML, not committed
generated artifacts. Exact final counts and publication status are recorded at
delivery. Tests use synthetic identities/data, PostgreSQL and real Flowable; they
are not a claim of deployed Keycloak or production acceptance.

| Requested race | Evidence / remaining limit |
| --- | --- |
| Schedule edit vs manual toggle | `concurrentScheduleActivationCannotUndoManualOff` |
| Shift end vs assign | `concurrentScheduleCancellationAndAssignmentLinearizeWithoutLosingTask`; clock boundary policy unit test |
| Shift end vs START | Existing work remains assigned; dedicated simultaneous wall-clock expiry/START not separately evidenced |
| Shift end vs PAUSE/RESUME | `shiftEndDoesNotStopWorkAndExistingR6ReleaseKeepsProcessAndHistory` exercises both after expiry |
| Shift end vs R6 RELEASE | Same real PostgreSQL/Flowable handover test after expiry; no automatic release |
| Reassign vs handover | Existing process locking retained; schedule-specific simultaneous stress remains open |
| R9 correction vs eligibility | Full R9 regression and unchanged manual incumbent policy; dedicated schedule/correction stress remains open |
| R11 worker concurrency | Full R11 regression; dedicated combined schedule/R11 worker stress remains open |
| Worker restart/shift transition | PostgreSQL restart preserves schedule/manual/monitor/event digests; full signed-worker replay across shift boundary remains open |
| Two Coordinators | `twoCoordinatorsCannotOverwriteOneVersion`: one 200, one 409, one audit |
| Audit failure | `scheduleAuditFailureRollsBackVersionAndShifts` plus existing manual rollback test |
| Role/workspace revocation | `revokedCoordinatorAndAgentCannotUseStaleJwtScheduleAccess` and foreign-workspace HTTP denial |

- Initial Windows Full attempted 835 tests: 23 failures, 4 errors, 21 skipped.
  Findings included V31 migration-count expectations (updated), one unconfigured
  background identity in a new test (changed to real HTTP duty projection plus
  real alert persistence), mocked R9 manual-duty fixture (updated), POSIX/symlink
  limitations on Windows, and a PostgreSQL 16.6-specific assertion. Linux rerun
  uses the existing build image and PostgreSQL 16.6; initial failures are not hidden.
- Boundary source gate passes; boundary checker self-tests: 43 pass.
- AI-D2 team release regression: 4 pass.
- AI-D2 Compose boundary regression: 1 pass.
- Final Backend Full clean verify on fresh PostgreSQL 16.6, Linux/JDK 17:
  **838 total, 817 executed/pass, 21 skipped, zero failures/errors; BUILD SUCCESS**
  (`.local/r3-full-clean-verified.log`, final Surefire XML in
  `.local/r3-final-evidence/surefire-reports`). Includes R2/R3/R4/R5/R6/R7/R8/R9/R11,
  Intake, clean/upgrade migrations, real Flowable, alert/JWT and architecture tests.
  The final executable source snapshot is unchanged between this run and the
  delivery commit; only this evidence document is finalized afterward.
- Run Full with `mvn -B -o clean verify`; Core-only with
  `mvn -B -o -Pcore-only clean verify`; the selected second-domain command adds
  `-Psecond-domain -Dtest=RoomRequestIntegrationTest,AgentDutyIntegrationTest,AgentSchedulePolicyTest,WorkAlertsIntegrationTest,TierBoundaryTest`.
  Use fresh databases `file_storage`, `workflow_approval`, `duty_upgrade`, the
  `FILE_TEST_JDBC_URL`/`FILE_TEST_DB_PASSWORD` and
  `WORKFLOW_TEST_JDBC_URL`/`WORKFLOW_TEST_DB_PASSWORD` environment variables, and a
  native POSIX `DX_STORAGE_ROOT` (not a Windows-mounted directory). Some legacy
  tests intentionally require PostgreSQL 16.6 and single-use database fixtures;
  reusing a completed suite's databases is not a valid Full acceptance run.
- Core-only clean verify: 106 tests, zero failures/errors/skips, BUILD SUCCESS
  (`.local/r3-core-only.log`).
- Second-domain selected Room Request/shared-capability proof: 28 tests, zero
  failures/errors/skips, BUILD SUCCESS (`.local/r3-second-domain-targeted.log`).
  The unfiltered second-domain attempt ran 260 tests with 2 failures/2 errors:
  generic Core tests expect the intentionally excluded sample document-review
  process, and the V30-only R3 upgrade test assumed a Service Desk test database.
  The R3 upgrade test is now explicitly disabled in this unrelated-domain proof;
  the sample-process assumptions are unchanged. Do not call unfiltered second-domain
  verification PASS on the strength of the selected proof.
- Real PostgreSQL process restart: counts and sorted-row digests match for all
  five stores: 85 manual duty rows, 22 schedule heads, 24 historical shifts,
  119 persistent alert events, 479 monitors (`.local/r3-restart-evidence.txt`).
  This is database persistence evidence, not a claim of deployed worker replay.
- HTTP contracts: real ticket examples, assignment response and four negative
  checks, eight SLA responses with nine negative checks each, and 14 promoted
  response/request checks pass (`.local/r3-http-contract.log`).
- New R3 OpenAPI paths/schemas validate independently. Complete HPDI OpenAPI is
  already invalid at the baseline: `KnowledgePage` has a description string inside
  `properties`. This unrelated defect is not repaired here. Service Desk OpenAPI
  validates using its CLI file resolver. The unchanged target OpenAPI also fails
  validation (an alert response has `cho badge` parsed as a schema keyword).
- Hosted baseline Core acceptance run 40648794421 did not start: GitHub annotation
  reports failed payments/spending limit. Status: `HOSTED_CI_BLOCKED_BILLING`.
  This is neither CI PASS nor a waiver.

P0: no confirmed data-loss/security defect at this stage; not a blanket clearance.
P1 acceptance gaps: unfiltered second-domain assumptions, complete HPDI OpenAPI baseline validation, deployed Keycloak
authorization/runtime acceptance, and the complete requested twelve-way
concurrency/restart matrix must be evidenced before merge readiness is claimed.
P2/business decisions: recurrence/holidays/approved-leave integration, overtime
approval granularity, Coordinator-initiated release, independent idle-group
monitoring, and history retention policy.

Merge recommendation: keep Draft; DO NOT MERGE until acceptance gaps are resolved,
review is approved and required checks are available. Eventual merge must be a
merge commit into `develop`, never squash/rebase or automatic merge.

## Final Acceptance Follow-Up - 2026-10-28

Acceptance source is the delivery commit containing this section. Its exact SHA
and pushed HEAD are recorded in the PR acceptance comment. Machine-readable
evidence is in `docs/implementation/evidence/r3-final-acceptance/`:
`acceptance.json` includes the backend/contracts source fingerprint, artifact
SHA-256, Surefire XML/log hashes, profile totals and baseline contract errors;
`runtime-http.json` contains real HTTP responses without bearer tokens;
`historical-skips.json` enumerates all 21 class/method/reason/origin/impact rows;
`migrations.json` contains clean-runtime checksums and exact upgrade evidence.

### Unfiltered Second-Domain Diagnosis And Fix

| Failure/error | Develop baseline | Classification and resolution |
| --- | --- | --- |
| `CoreRuntimeIntegrationTest.independentWorkflowAndParentBinding` expects sample definitions | Reproduced | Test composition: supply existing Core BPMN fixtures and Core-only registrations in the second-domain test classpath |
| `CoreRuntimeIntegrationTest.workflowAuditFailureRollsBackEngineAndScope` gets 404 rather than 409 | Reproduced | Same missing fixture; audit transaction is not reached without the registered sample process |
| `WorkflowApprovalTest.startEngine` cannot find sample BPMN | Reproduced | Same test-resource omission; production samples remain excluded |
| R3 upgrade test assumes `duty_upgrade`, then assumes Service Desk V30 in second-domain | New PR test-fixture defect | Use the configured test database with an isolated schema; upgrade the highest available pre-V31 migration, preserving all earlier checksums |

- Exact unmodified develop reproduction: 249 total, 246 PASS, 2 FAIL, 1 ERROR,
  0 SKIP. Original PR HEAD reproduction: 260 total, 256 PASS, 2 FAIL, 1 ERROR,
  1 SKIP (the old conditional disable). The earlier missing-database run's
  additional migration error was a fixture configuration issue, not production.
- Removed the R3 second-domain conditional disable. The upgrade test now executes
  in both domains: Full upgrades exact V30 to V31; isolated second-domain upgrades
  its highest shared pre-V31 version (V28) to V31, without importing Service Desk.
- Fixed profile runs 286 tests because the previously failing class setup now
  allows all Core workflow tests to execute. No new excludes/skips were added.
- Inspected the packaged second-domain JAR: no Service Desk or sample application
  classes/resources. Core probe contributions/BPMN are test-only. Room Request
  production code and its dependency direction are unchanged.

### Final Regression Results

All Maven proofs run on native Linux/JDK 17 with fresh PostgreSQL 16.6 fixtures.
Counts are parsed from Surefire XML, not inferred from exit status.

| Profile/gate | Total | PASS | FAIL | ERROR | SKIP |
| --- | ---: | ---: | ---: | ---: | ---: |
| Backend Full, `mvn -B -o clean verify` | 842 | 842 | 0 | 0 | 0 |
| Core-only, `-Pcore-only clean verify` | 106 | 106 | 0 | 0 | 0 |
| Unfiltered Second-domain, `-Psecond-domain clean verify` | 286 | 286 | 0 | 0 | 0 |
| Explicit R2/R3/R4/R5/R6/R7/R8/R9/R11 regression selection | 198 | 198 | 0 | 0 | 0 |
| AI-D2 team release, Python 3.12.12 | 4 | 4 | 0 | 0 | 0 |
| AI-D2 Compose boundaries | 1 | 1 | 0 | 0 | 0 |
| Tier source-boundary checker self-tests | 43 | 43 | 0 | 0 | 0 |

The targeted selection is `AgentDutyIntegrationTest,AgentSchedulePolicyTest,AgentScheduleMigrationTest,AssignmentSuggestionIntegrationTest,AssignmentSuggestionTest,TicketIntegrationTest,WorkAlertsIntegrationTest,AgentDutyUpgradeIntegrationTest,ConfirmationUpgradeIntegrationTest,ServiceDeskMigrationIntegrationTest,TicketAssignmentEligibilityTest,SlaPolicyTest,ClassificationSlaAmendmentTest`.

All historical 21 skips belong to `BackgroundTicketAssessmentTest`. Each method
and its `BACKGROUND_TEST_JDBC_URL` condition exists at the exact develop baseline.
They cover Service Desk background/outbox/worker security, not direct R3/Core/Room
Request behavior; shared service-identity regression coverage is nevertheless
important. They were NOT counted as PASS. Supplying a fresh `background_assessment`
database and `BACKGROUND_TEST_JDBC_URL` executes all 21 successfully in final Full.
Python 3.11 initially produced two AI-D2 environment errors (`Path.is_junction`
unavailable); the unchanged tests pass on the installed Python 3.12.12.

### Real Runtime And Concurrency Evidence

- `scripts/apps/servicedesk/acceptance/test-r3-runtime.py --postgres r3-accept-pg --backend r3-accept-backend --evidence .local/r3-real-runtime-final.json`
  runs against an isolated packaged JAR, PostgreSQL 16.6, actual Flowable schema
  7.1.0.2 and Keycloak 26.0.7. `.env` supplies demo/admin credentials; directory and
  background clients use their actual configured service accounts. No MockMvc
  JWT substitutes for these runtime checks. Never point this destructive fixture
  probe at a non-acceptance environment: it temporarily changes test membership,
  injects a database audit-failure trigger and stops/restarts the backend.
- Runtime covers all 15 requested scenarios: Coordinator schedule creation, Agent
  self-read, Requester write denial, stale Coordinator JWT denial after removing
  current role membership (restored afterward), manual ON, eligible assignment,
  outside-shift denial, manual OFF precedence, exact server-time remaining-minute
  range, ET warning, natural expiry preserving IN_PROGRESS, R6 HANDOVER, persistent
  alert recipient isolation/generation dedup, restart persistence, foreign-workspace
  denial and real audit-failure rollback. The artifact digest is in acceptance JSON.
- Backend is stopped BEFORE a second assigned task's shift ends and started AFTER
  the boundary. Schedule/head/history/manual-duty/schedule-audit digests remain
  identical; prior alert survives and the worker produces exactly one new
  ASSIGNEE_OFF_DUTY alert for the second task. A real background JWT is required
  for alert evaluation/delivery. Duplicate means same generation, not distinct
  legitimate manual-OFF and later natural-expiry generations.
- Added deterministic PostgreSQL/Flowable tests: expiry while START waits at the
  process row lock; schedule cancellation commits while assignment waits (409);
  Coordinator reassign and R6 release queue behind the same process lock (one
  valid ownership outcome); R9 correction waits while eligibility expires; actual
  R11 auto-close worker waits while schedule cancellation commits independently.
  The existing two-Coordinator CAS and transactional audit rollback tests pass.
  A test-observation issue initially counted only direct blockers; the final
  observer follows PostgreSQL's recursive wait chain rather than using sleeps
  or weakening the overlap assertion. Full then passes all 842 tests.
- Existing-assignment START after expiry remains allowed; scheduling gates NEW
  assignment, not already-owned work. This preserves the R3 non-interruption rule.
  No production lock ordering or process/task/Agent lock implementation changed.

### Migration, Contracts And Remaining Gates

- Clean packaged runtime applies all 31 versioned migrations successfully. Full
  proves exact V30 -> V31, unchanged V1-V30 checksums/manual duty, no implicit
  enrollment, schedule persistence across connections. Runtime restart is also
  verified across a real shift boundary, not just a database count comparison.
- Source tier-boundary gate PASS; ArchUnit PASS in Maven. Service Desk whole-document
  OpenAPI PASS. R3 schedule paths/additive schemas PASS independently; 27 actual
  runtime R3 success/Problem responses validate against those contracts.
- Whole HPDI `openapi.yaml` and `openapi-target.yaml` remain FAIL on BOTH baseline
  and delivery: `KnowledgePage.properties.description` contains a string schema;
  target alert `unread` has an invalid `cho badge` keyword from an unquoted comma.
  These are baseline contract debt, not R3 regressions. No whole-HPDI PASS or waiver
  is claimed. No WorkSettings/R7/R12 expansion or N+1 optimization is included.
- P0 code: no confirmed critical defect found by these checks; not an exhaustive
  security/concurrency proof. P1 external acceptance: independent approval and
  owner resolution of required hosted CI are still outstanding. Hosted jobs have
  explicit payment/spending-limit annotations: `HOSTED_CI_BLOCKED_BILLING`, not PASS.
- P2 baseline debt: complete HPDI OpenAPI structural errors; existing effective-duty
  N+1 evaluation. Business decisions remain recurrence/leave/holidays, overtime,
  Coordinator release and retention. None is silently promoted into this PR.

Exact merge recommendation: R3 is ready for final independent technical review;
KEEP DRAFT / DO NOT MERGE now. Required hosted CI is blocked, independent approval
is absent, and whole-document baseline contract failures require owner disposition
if that contract gate is mandatory. No waiver/exception is recorded by this work.
After required approvals/gates are satisfied, merge into `develop` with a MERGE
COMMIT ONLY. Do not squash, rebase, auto-merge or open another PR.
