# R3 - Shift Scheduling And End-Of-Shift Handover

Date: 2026-10-28, Asia/Saigon. Branch: `feature/r3-shift-scheduling`.
Base: `a6a92845c48eeefa86e5e261e382dbd096160cdb`, fetched from `origin/develop`.
The initial local HEAD was the R11 source parent; fast-forwarding to the specified
merge baseline changed no source files. Existing untracked documents are untouched.
`docs/ticket.md` was read as supplied local requirements, not committed as authority.
Delivery: Draft review only; no merge, force push, history rewrite or CI waiver.

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

## Test Evidence And Findings

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
