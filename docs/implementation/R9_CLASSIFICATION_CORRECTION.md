# R9 Classification and Priority Correction

Baseline: 84a479456b683ccb1d1bf745a909bcd1c41e1201. Date: 2026-10-28 (Asia/Saigon).

## Pre-Implementation GAP Matrix

| Requirement | Baseline gap | Planned implementation |
| --- | --- | --- |
| API and history | Target only | Validated PATCH, atomic immutable field history |
| Authorization | R6 assignee-only | Fresh directory intersection, explicit correction return hook |
| Category before START | No Coordinator return | Reuse native R6 transition and Intake handover |
| IN_PROGRESS / PENDING | New group invalidates incumbent ET | Preserve assignment and pending episode |
| Priority / SLA | No amendments | Preserve original policy and anchors, sticky breach, manual precedence |
| ET | Pending request remains valid | New generation and CLASSIFICATION_CHANGED supersession |
| Alerts | No category mismatch type | Durable Coordinator-only generic event |
| Concurrency | Process lock exists | Process then ticket then SLA/ET, atomic audit |
| Evidence | No R9 tests | Full/Core/Room, focused and runtime checks |

## Sources and Decisions

Source: supplied task and target OpenAPI. Local docs/ticket.md is untracked user material, not a canonical baseline; it is not staged.
R5, R7/R8 and R2/R6 reports define reused SLA, outbox and handover boundaries.
OWNER_APPROVED on 2026-10-28: cross-clock consumed-budget conversion, historical
breach preservation and manual priority precedence, per the owner's PR follow-up.
No hosted CI waiver is assumed. Hosted CI must not be described as passing without a run.

## Implemented Requirement Traceability

| Requirement | Implementation | Evidence |
| --- | --- | --- |
| R9 PATCH, partial change, reason, enums, no-op | TicketCorrection / TicketCorrectionService / TicketController | Runtime 200/400/401/403/404/409; parser and integration tests |
| Current Agent or Coordinator | HumanContext.currentMember plus role-specific PermissionGuard.requireCurrent | Revocation/policy tests and actual Keycloak-issued JWT runtime |
| Before START, changed support group | WorkExecution.returnForCorrection -> authenticated Core release orchestration | Coordinator is not assignee; exactly one new review in same process |
| IN_PROGRESS / PENDING category | Keep native handle, assignee, work interval and pending episode | Parameterized integration and runtime tests |
| Priority / SLA | ClassificationSlaAmendment and transactional SLA amendment ledger | Pure arithmetic checks, paused/breach/restart checks, native priority/due assertions |
| ET | Recalculate DEFAULT only, retain COORDINATOR, append estimate history | Default/manual tests; real pending request supersession |
| Stale ET | New estimate generation and CLASSIFICATION_CHANGED lifecycle | Correction vs ET approval race; unique pending slot released |
| Alerts | Generic CLASSIFICATION_CHANGED event via existing durable outbox | Same transaction; no CRITICAL_NEW misuse; group-duty race tests |
| Correction provenance | Immutable API history rows, operation UUID, both generations, actor/reason/time | Both field rows share operation; fromAi is false for human-reviewed prior values |
| Intake / final binding | Require REVIEWED and current sealed revision; reuse R6 handover receipt hook | Reject pre-review mutation; old version fails after return; final attempts unchanged |
| Room Request / tier boundaries | Generic mechanisms contain no Service Desk imports | Core-only, second-domain, architecture and Python boundary checks |

## Authorization Matrix

| Caller | Mutation | Correction history |
| --- | --- | --- |
| Current assigned Agent, signed agent role and current task:handle | Allowed on approved active handle | Allowed with current task:handle |
| Independent current Coordinator, signed role, current task:handle and task:assign | Allowed, even when not assignee | Allowed with current task:handle |
| Other Agent | 403 | Existing ticket visibility plus current staff permission |
| Requester / ticket creator, including dual-role creator | 403 | Property omitted |
| Revoked role or role permission | 403 at mutation boundary | No internal history disclosure |
| Cross-workspace token | 403 | No access |
| Valid workspace caller, missing ticket | 404 | No access |
| No/invalid bearer token | 401 | No access |
| Service identity without a matching current human workspace role and permission | 403 | No access |
| Intake, review, confirm, closed, suspended, unsupported process definition | 409 for otherwise eligible caller | Read rules unchanged |
| Active follower or root with active canonical links | 409 | Existing read rules; no disposition mutation |

The generic correction return is a separate authenticated operation, not an
assignee impersonation or a service-identity release. It independently verifies
current membership, role-specific permissions and NOT_STARTED under the native
process lock, then reuses the R6 engine change-state path and registered hooks.
R6's existing release authorization remains assignee-only.

## Category Transition Matrix

| Work state | Support group unchanged | Support group changed |
| --- | --- | --- |
| NOT_STARTED | Keep handle and assignment | Return to fresh Coordinator review; clear old assignment; no auto-assignment |
| IN_PROGRESS | Keep handle and running interval | Keep incumbent and interval; durable Coordinator alert |
| PENDING | Keep handle and pending reason/note/episode | Same preservation; durable Coordinator alert; never interpreted as NOT_STARTED |
| Not an approved active handle | Conflict | Conflict |

The baseline mapping is HARDWARE -> hardware, NETWORK -> network, SOFTWARE ->
software, ACCOUNT -> sysadmin, OTHER -> manual routing (no support group).
Every distinct baseline category changes this mapping. Consequently the supplied
acceptance case of a *different category in the same group* has no realizable
baseline pair; it is not reported as a fabricated passing category-change test.
Priority-only tests exercise the no-group-change path. The existing public
classification.group remains the native BPMN candidate role, `agent`.

For a running/pending incumbent, ET eligibility checks current Agent membership
and duty, not membership of the newly selected support group. Fresh assignments
still require the new support group. This exception requires the explicit
correction_incumbent marker bound to that assignee on the current review. A changed
assignment clears it; ordinary R2 requests without a correction retain the original
support-group eligibility check. Group duty and incumbent duty are evaluated
separately: a changed group must not fabricate ASSIGNEE_OFF_DUTY. Duty samples
taken before a category change are discarded under the process lock.

## Priority And SLA Amendment Policy

OWNER_APPROVED (2026-10-28): the owner explicitly selected the following amendment
policy in the PR follow-up instruction. This is a business decision, not test
evidence or an independent review approval.

1. Keep physical base_priority, original policy JSON, approved_at,
   first_started_at, paused_at, escalated_at and all old SLA/release rows.
2. Store manual_priority separately. API basePriority becomes the effective
   amended base. The original base remains in storage and amendment snapshots.
3. Measure consumed minutes in the previous effective priority's clock. Derive
   consumption from its budget and signed remaining deadline interval. Existing
   completed pause extensions therefore remain excluded. During an open pause,
   use paused_at, not now, as the resolution consumption anchor.
4. Subtract consumed duration from the new priority budget. Only the remaining
   duration uses the new clock: CRITICAL is continuous, others use the frozen
   business calendar. Never use now plus a new full budget. Historical weekends
   are not retroactively charged as continuous time.
5. Preserve an already-breached deadline instead of pushing it into the future.
   If a shorter budget is exhausted, clamp its deadline to the consumption anchor.
   Persist signed budget adjustments so clamping, switching clocks and repeated
   corrections cannot discard debt or create fresh minutes. Retain sticky
   historical start/resolve breach flags in the projection.
6. Preserve the existing constraint resolve_by >= start_by by capping the start
   deadline at the resolution deadline; the same adjustment ledger preserves its
   exact consumed budget. Never rewrite first_started_at.
7. Store independent before/after SLA JSON per correction operation. Manual
   priority wins over automatic escalation for this generation, including R6
   continuation. A genuine rework creates a new generation by the existing policy.
8. Reconcile native Flowable priority and projected due date in the transaction.
   Sub-millisecond precision is lost in Flowable's java.util.Date representation;
   synchronization assertions compare epoch milliseconds.

This is one existing SLA clock with versioned amendments, not a new two-clock SLA
engine. LOW/HIGH/CRITICAL increases and decreases, business/continuous conversion,
pause, existing breach and repeated amendments have runnable checks.

The API startBreached / resolveBreached flags mean **ever breached**, including
the current generation's handover lineage. They are not current-overdue flags:
an amendment cannot erase prior breach evidence. Rework follows the existing new
generation policy. FE must render these as historical violations, never infer
current overdue status from them. The live HPDI schema states this contract;
there is currently no FE consumer of these fields to migrate.

## ET, History And Provenance

### Manual Reaffirmation After Automatic Escalation

Review 5677667210 identified the LOW business / MEDIUM effective / absent manual
priority edge case. An explicit PATCH priority LOW with a nonblank reason is now
a valid manual override, even though the confirmed business priority stays LOW.
Omitting priority is not reaffirmation. If business and effective priority already
equal the supplied value, an otherwise unchanged PATCH remains a 409 no-op.

The three values are distinct: servicedesk_review.priority is human-confirmed
business priority (DEFAULT ET basis); servicedesk_sla.priority is the effective
operational/queue priority; servicedesk_sla.manual_priority is nullable manual
precedence for this generation and its handover lineage. Automatic escalation
only changes effective priority. Reaffirmation sets effective/manual to LOW while
leaving business LOW. It creates no fictitious LOW -> LOW correction field row.

The existing SLA amendment ledger records operation ID and full before/after
snapshots. Audit action servicedesk.sla.priority.overridden records actor/time,
operationId, handleTaskId, estimateHistoryId, businessPriorityKey,
previousEffectivePriorityKey, previousManualPriorityKey (NONE if absent),
manualPriorityKey and reaffirmedKey. Its reason/provenance stays in the referenced
append-only private estimate history, not arbitrary text in shared audit details;
the existing safe-reference guard is unchanged. The generic correction audit,
SLA amendment, estimate history/generation invalidation and queue update commit
or roll back together. DEFAULT/COORDINATOR ET minutes/basis do not change solely
because priority is reaffirmed.

Reaffirmation uses the business-clock SLA base, not the escalated urgency, to
retain consumed budget, original approval/deadlines and historical breaches.
Flowable priority becomes 25 for LOW. Subsequent reconciliation respects manual
LOW. R6 handover copies it; real rework initializes a fresh generation without
manual precedence and retains old amendment/provenance rows. No schema or applied
migration V1-V27 changes are needed.

Runnable integration cases cover HANDOVER, REWORK, concurrent RECONCILE, and
override-audit ROLLBACK. The optional runtime --sla-fixture-container argument
must name an isolated test PostgreSQL container: it ages only a newly created
synthetic LOW ticket's anchors, waits for the actual scheduled worker to escalate
MEDIUM, then uses real Coordinator JWT HTTP PATCH LOW and real R6 handover.
It never directly sets escalation/manual/queue priority. Restart proof checks
the retained manual LOW in storage as well as ticket projections.

DEFAULT estimates use the existing category/human-confirmed review priority table,
not the effective priority after automatic SLA escalation. A manual correction
updates that confirmed priority and DEFAULT ET; history records the same basis.
R6 handover/re-review retains this distinction. COORDINATOR estimates
are retained, never silently overwritten. Both cases append estimate history and
invalidate the old estimate-request generation, including requests whose minutes
happen to remain equal. The generic lifecycle atomically marks every pending
request on the handle SUPERSEDED with CLASSIFICATION_CHANGED; assignment-only
invalidation remains ASSIGNMENT_CHANGED. New requests use the new UUID generation.
Queue/load/remainingMinutes and suggestions read the updated canonical review.

Correction history is append-only through public APIs. Each changed field has a
row with operation UUID, workspace/ticket/process, review and handle IDs, old/new
value, actor, reason and common timestamp. No-op requests return 409 without a row.
Both fields, ET, SLA, workflow and audit commit or roll back together. The previous
authoritative classification is human-reviewed, not proven
AI output: fromAi=false. Original AI final/advisory records are not rewritten.
Ordinary Requester responses omit corrections entirely, rather than exposing an
empty or redacted internal history. There is no public update/delete-history API.

## Migrations And Compatibility

- HPDI V26 extends persisted event compatibility and ET supersession reasons.
- Service Desk V27 adds field history, SLA amendment snapshots, sticky flags,
  signed budget adjustments, effective manual priority and estimate generation.
- V1-V25 are unchanged; no repair, checksum rewrite or existing-row backfill
  fabricates corrections. New columns are nullable/defaulted for active work.
- Upgrade tests include V25 and earlier baselines, retained work, checksums,
  constraints, clean startup and repeated migration. Runtime restarts preserve
  native tasks, correction history, classification, estimates and pending state.
- BPMN definitions are not edited. Existing supported deployed instances use
  the same native R6 state-change path. Unsupported definitions fail closed.
- Recovery is roll-forward: keep additive history and manual SLA amendments.
  An older binary does not understand manual-priority precedence or estimate
  generations; a blind binary downgrade is not a supported rollback.

## Concurrency And Atomicity

All corrections start with the existing WorkScheduling process-scope row lock,
then lock ticket/review and SLA rows. ET supersession, outbox event, immutable
history, engine mutation and business audit share the Spring DB transaction.
No JVM synchronized, direct Flowable table mutation, forged JWT, fabricated
completion or unchecked background release is introduced.

Parameterized races cover two corrections and correction vs START, PAUSE, RESUME,
RELEASE, COMPLETE, assignment, ET approval and SLA reconciliation. Triggered audit
failure proves rollback of history, amendment, review, native return, SLA and alert.
Intake's existing CAS and task-bound receipt remain authoritative; new rounds use
a fresh receipt/version. Existing AI-D2 suites remain required.

## Latest Verification For Review 5677667210

Read review 5677667210 and source at c3b336d before implementation. The remaining
P2 manual-precedence edge is fixed in
`a9b61c918cae28159fc4dc4f37897948b518aed6`. No new P0/P1 is confirmed. This
documentation/evidence commit changes no tested production source; all four
profiles are rerun on this final commit before push. The exact final tested SHA,
artifact directories and runtime request totals are recorded in the PR receipt.

| Final verification | PASS | FAIL | ERROR | SKIP |
| --- | ---: | ---: | ---: | ---: |
| Backend Full | 807 | 0 | 0 | 0 |
| Core-only | 106 | 0 | 0 | 0 |
| Second-domain | 43 | 0 | 0 | 0 |
| R2/R5/R6/R7/R8/R9 + Intake + migration targeted | 149 | 0 | 0 | 0 |
| Python tier-boundary regression | 43 | 0 | 0 | 0 |

Targeted overlaps Full. TicketIntegration now has 98 cases; upgrade coverage
remains 21. Full and targeted passed on the implementation commit before the
final-commit rerun. The real Flowable/PostgreSQL/Keycloak-issued JWT runtime also
passed: actual scheduled LOW -> MEDIUM escalation, Coordinator reaffirm LOW,
unchanged anchors/debt, retained breaches, empty field corrections, LOW queue,
real R6 handover and manual-priority persistence. Restart verifies four retained
tickets, two critical correction alert IDs and stored manual LOW. Runtime HTTP
totals vary with delivery/escalation polling: use the final JSON receipt in PR,
not an earlier run's request count as the final count. The previous c3b336d receipt
was 29 main + 4 restart requests; its earlier 31-request run included extra polls.
These are PR-reported local results, not an independent reviewer rerun.

OpenAPI validates five real successful corrections (including reaffirmation),
nine request checks and eight SLA responses; baseline whole-document errors stay
HPDI live 3 / target 1 and Service Desk live/target 0. No whole-HPDI PASS claim.
No V1-V27 migration, schema, audit safe-reference guard or overall SLA engine
change. Runtime uses the isolated PostgreSQL 16.6 / Keycloak 26.0.7 override, not
the broken default pinned PostgreSQL image. Local artifacts use
.local/r9-reaffirm-* and .local/task-execution-*; no secrets are staged.

Recommendation: READY FOR FINAL INDEPENDENT REVIEW; DO NOT MERGE until an actual
independent approval (or explicit owner review-policy exception) and owner
handling of HOSTED_CI_BLOCKED_BILLING are recorded. Owner SLA policy approval is
not test evidence, independent approval or a CI waiver. Keep this Draft PR #58;
MERGE COMMIT ONLY after gates are satisfied. No merge or history rewrite here.

## Historical Follow-Up Verification For Review 5677475009

Reviewed GitHub review 5677475009 and source at
`3c33a99c50006204009aa91ad7d75c1beeba4b14` before changing code. The two P2 follow-ups
are fixed: priority-only CRITICAL corrections now emit one durable correction
alert, and DEFAULT ET follows confirmed business priority rather than escalation.
No new P0/P1 is confirmed by this verification. No migration V1-V27 is modified;
no schema change, new event type or overall two-clock SLA engine is introduced.

Production change: `6ad237a35e63a6e5a0fa5da46565f6ecf6119ffb`. Test-only handover
assertion correction: `eb8781d996098bdfeb8d2733c3a0e1d7d9b20cd5` (native release
returns 204, not 200). The initial run caught that assertion mismatch; it is not
reported as a passing run. The corrected source passed Full and targeted suites.
The final documentation/evidence commit containing this section is rerun before
push; its exact tested SHA and check receipt are recorded in PR #58.

| Follow-up verification | PASS | FAIL | ERROR | SKIP |
| --- | ---: | ---: | ---: | ---: |
| Backend Full | 803 | 0 | 0 | 0 |
| Core-only | 106 | 0 | 0 | 0 |
| Second-domain | 43 | 0 | 0 | 0 |
| R2/R5/R6/R7/R8/R9 + Intake + migration targeted | 145 | 0 | 0 | 0 |
| Python tier-boundary tests | 43 | 0 | 0 | 0 |
| Real Keycloak JWT HTTP runtime | Initial run 31; final c3b336d run 29 + 4 restart requests | 0 | 0 | 0 |
| Actual application restart | 3 tickets + 2 critical correction alerts | 0 | 0 | 0 |
| Isolated smoke | 88 PASS lines | 0 | 0 | 0 |
| DB consistency | 5 zero-violation queries | 0 | 0 | 0 |
| R9 promoted contract | 4 actual responses + 9 request checks | 0 | 0 | 0 |

Targeted overlaps Full; do not add the counts as unique tests. TicketIntegration
now has 94 cases; upgrade coverage remains 21 cases. The new parameterized
business-priority test covers LOW -> automatic MEDIUM -> category correction;
manual HIGH -> category correction; COORDINATOR estimate preservation across
priority/category correction and re-review; and escalation -> category correction
while running -> actual R6 HANDOVER -> Coordinator re-review. The R2 pending
supersession test now also creates a fresh request after the R9 generation change.
Critical alert tests cover priority-only and combined corrections, repeated no-op
deduplication, audit-trigger rollback and concurrent correction operation/event
counts. Real JWT HTTP checks reject requester alert access and exclude the Agent
from Coordinator-only critical events; actual application restart retains both
critical event IDs and all three ticket snapshots.

Reproducible commands use scripts/test-task-execution.ps1 with profiles full,
core-only and second-domain, and Maven image maven:3.9.11-eclipse-temurin-21.
The targeted -Test list is TicketIntegrationTest,IntakeWorkflowIntegrationTest,
ServiceDeskMigrationIntegrationTest,ClassificationSlaAmendmentTest,
TicketAlertRulesTest,KeycloakPermissionGuardTest. Evidence is retained under
.local/task-execution-* and .local/r9-followup-*; no tokens or secrets are staged.
Runtime uses isolated project r9-followup-20261028, PostgreSQL 16.6 and Keycloak
26.0.7 via the documented image override, not the broken default pinned image.
OpenAPI baseline comparison remains HPDI live 3 / target 1 unchanged errors,
Service Desk live/target 0; this is not whole-HPDI validation PASS.

Hosted CI: **HOSTED_CI_BLOCKED_BILLING**. The reviewed HEAD had 17 failures,
2 cancellations and 2 skips; an actual check annotation states that jobs did not
start because account payments failed or spending limits need increasing. New
HEAD checks are inspected after push. No hosted PASS or owner waiver is claimed.
Owner SLA approval is a business decision only; it is not CI evidence or an
independent APPROVED review. Final recommendation: READY FOR FINAL INDEPENDENT
REVIEW; DO NOT MERGE until approval and owner handling of the hosted-CI gate are
recorded, then MERGE COMMIT ONLY. Do not merge or rewrite shared history here.

## Historical Test Evidence Before Review Follow-Up

Tested implementation SHA: `f22267ae4cb3b5a8101bcc2eb61f1b9c030600a8`.
The following documentation-only delivery commit does not change tested source.

| Final verification | Tests/checks | Failures | Errors | Skips |
| --- | ---: | ---: | ---: | ---: |
| Backend Full | 797 | 0 | 0 | 0 |
| Core-only | 106 | 0 | 0 | 0 |
| Second-domain selected suite | 43 | 0 | 0 | 0 |
| R9/R2/R5/R6/R7/R8 + Intake + migration targeted | 139 | 0 | 0 | 0 |
| Python tier-boundary regression | 43 | 0 | 0 | 0 |
| Boundary checker / Python syntax / git diff --check | PASS | 0 | 0 | 0 |
| Real Keycloak HTTP runtime | 25 requests | 0 | 0 | 0 |
| Actual application restart | 3 retained tickets | 0 | 0 | 0 |
| Final isolated scripts/smoke.ps1 | 88 PASS lines | 0 | 0 | 0 |
| DB consistency | 5 zero-violation queries | 0 | 0 | 0 |
| R9 promoted OpenAPI | 4 real responses + 9 request checks | 0 | 0 | 0 |

Targeted results are a subset, not additional unique tests to add to Full.
Full includes TicketIntegrationTest (88, R2/R5/R6/R7/R8/R9), RoomRequestIntegrationTest
(4), ServiceDeskMigrationIntegrationTest (21), FinalAssessmentIntegrationTest (13),
IntakeAdmissionIntegrationTest (15), IntakeWorkflowIntegrationTest (11),
WorkAlertsIntegrationTest (10), and SlaPolicyTest (8). All have zero failures,
errors and skips. The new Intake tests include old-receipt rejection and a
correction race against actual completion/confirmation/AI-D2 rework back to intake.
The separate boundary command reports PASS; its Python regression suite has 43 tests.

Runtime proof uses real network HTTP, real Flowable, PostgreSQL, and tokens issued
by the running Keycloak, not MockMvc JWT injection. It checks error distinctions,
partial updates, native queue priority/removal, non-assignee Coordinator return,
incumbent ET after group change, SUPERSEDED reason, durable Coordinator alert,
pending clock and requester history privacy. After an actual platform restart,
process/task IDs, work state, assignment, classifications, estimates and immutable
corrections match all three retained ticket snapshots.

Database checks find zero scope mismatches, incomplete SLA amendment snapshots,
missing correction audits, native/manual priority mismatches and duplicate active
work tasks. Runtime application image ID:
`sha256:8bbd11007988fff89c0886a36f5e660904b8062e21c053ff7cfd733a8d9e71c1`.

Commands:

```powershell
scripts/test-task-execution.ps1 -Profile full -BuildImage maven:3.9.11-eclipse-temurin-21
scripts/test-task-execution.ps1 -Profile core-only -BuildImage maven:3.9.11-eclipse-temurin-21
scripts/test-task-execution.ps1 -Profile second-domain -BuildImage maven:3.9.11-eclipse-temurin-21
scripts/test-task-execution.ps1 -Profile full -Test 'TicketIntegrationTest,IntakeWorkflowIntegrationTest,ServiceDeskMigrationIntegrationTest,ClassificationSlaAmendmentTest,TicketAlertRulesTest,KeycloakPermissionGuardTest' -BuildImage maven:3.9.11-eclipse-temurin-21
python scripts/check-tier-boundaries.py
python scripts/test-tier-boundaries.py
python scripts/apps/servicedesk/acceptance/test-r9-runtime.py --evidence .local/r9-final-runtime-evidence.json
# Restart the isolated platform, wait for health, then:
python scripts/apps/servicedesk/acceptance/test-r9-runtime.py --evidence .local/r9-final-runtime-evidence.json --verify-restart
scripts/smoke.ps1 -ComposeProject r9-correction-final-20261028
```

Contract validation uses Python 3.11 with the existing OpenAPI validation packages:
`scripts/test-servicedesk-execution-contract.py <servicedesk-http-evidence.json>
--baseline origin/develop --correction-evidence .local/r9-final-runtime-evidence.json`.
Whole-document structural-error comparison remains unchanged: HPDI live 3,
HPDI target 1, Service Desk live 0, Service Desk target 0. This is not a claim
that the whole HPDI documents pass validation.

Local raw output is under `.local/task-execution-{full,core-only,second-domain,full-targeted}`,
`.local/r9-final-runtime-evidence.json`, `.local/r9-final-contract.txt`,
`.local/r9-final-smoke.log`, and `.local/r9-final-db-consistency.txt`.
Those artifacts, secrets, tokens and generated realms are deliberately not committed.
Full reports: `.local/task-execution-full/task-execution-25965c947c3f`.
Hosted CI: HOSTED_CI_BLOCKED_BILLING, not PASS and not HOSTED_CI_WAIVED.
On delivery commit `d1890fbc8a65bb0a7c748f178fb0abb205bd77f0`, GitHub reports
21 check runs: 18 failures, 2 skipped and 1 cancelled. Annotations on sampled
failed jobs (assistance-integration, second-domain-python and combined-head)
state that the job was not started because account payments failed or the
spending limit must be increased. No hosted test execution is claimed from
those failures. Account/billing remediation requires the owner; no billing
settings, spending limits or repository protections are changed by this PR.

The default scripts/start.ps1 build succeeds but startup encounters the existing
local pinned PostgreSQL image error: postgres is not a known user. No existing
volume is deleted or repaired. Runtime proof uses the isolated Compose project
r9-correction-final-20261028 with PostgreSQL 16.6 and Keycloak 26.0.7, fresh project-scoped
volumes and the built application. This is not proof of the default pinned image.

## FE Handoff

Use the promoted PATCH path and live Correction schema, not the retired target
operation. Send only changed category/priority and a nonblank reason; refetch ticket
and queues after success. 409 means stale work/no-op/invalid state; do not retry
blindly. Category correction may return AWAITING_REVIEW with a new taskId. Running
and pending corrections keep taskId/assignee and must not trigger a client stop.
Render corrections only when the property is present. Read SLA from the backend;
do not recalculate deadlines, infer AI provenance or overwrite manual ET in FE.
CLASSIFICATION_CHANGED is a Coordinator correction event, not a new-ticket event.
A priority increase to CRITICAL emits severity CRITICAL and the distinct label
"Priority corrected to CRITICAL; Coordinator review required", even without a
category change. A combined group/priority change emits only one event for the
operation UUID. Other group corrections retain WARNING. The existing transactional
outbox commits with correction/audit, then delivers to authorized Coordinators;
neither CRITICAL_NEW nor a direct pre-commit notification is used.

## Known Limitations And Merge Readiness

- OWNER_APPROVED: amendment policy, debt conversion, historical breach flags and
  manual escalation precedence were selected on 2026-10-28.
- Active canonical roots/followers fail closed until disposition-aware correction
  semantics are approved; no canonical link or Intake final assessment is changed.
- Same-group/different-category acceptance is not representable by baseline mapping.
- Actual AI-D2 model execution is not claimed by deterministic integration tests.
- Existing whole-document HPDI validation debt remains (3 live, 1 target errors);
  the promoted subset and Service Desk documents must add no structural errors.
- No hosted CI waiver has been granted here. Review and owner approval remain
  necessary; no merge, force push, shared-history rewrite or squash is performed.

## Findings And Delivery Decision

- P0/P1 newly confirmed implementation defects: none remaining after the stated
  verification. This is test evidence, not a formal proof of all race orderings.
- Business policy gate resolved by owner instruction on 2026-10-28; this does not
  waive independent approval or hosted CI requirements.
- Existing environment gate: default pinned PostgreSQL startup fails locally;
  isolated-image proof is not a release waiver for that image.
- Hosted CI gate: GitHub jobs are blocked by account billing/spending limits,
  not a local test failure. Owner remediation and rerun, or an explicit recorded
  owner waiver, are required before treating this merge gate as satisfied.
- P2 baseline contract debt: unchanged HPDI structural errors described above.
- READY FOR INTERNAL REVIEW as a Draft PR; not approved for merge or production.
- Draft PR: https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/pull/58.
- Baseline re-fetched before delivery and remains
  `84a479456b683ccb1d1bf745a909bcd1c41e1201`. Branch:
  `feature/r9-classification-correction`, base `develop`. MERGE COMMIT ONLY.
- Source changes: Service Desk correction DTO/service/controller and projection;
  existing SLA/ET/assignment/alert adapters; explicit Core/HPDI correction return
  and current-permission primitives; V26/V27; live/target contracts; focused and
  regression tests; runtime and promoted-contract checks. Full changed-file list:
  `git diff --name-only 84a479456b683ccb1d1bf745a909bcd1c41e1201..HEAD`.
