# R5 SLA Engine

Date: 2026-10-27 (Asia/Saigon). Feature branch: `feat/r5-sla-engine`.
Rebaseline: `origin/develop` at `83e19ec312bf4515c89d8f2277545a17269c72db` (AI-D2 PR #54).
Delivery: existing Draft PR #55 on `feat/r5-sla-engine`; no merge or deployment.
Current acceptance and tested integration SHA: `evidence/R5_SLA_ENGINE.json`.
The historical acceptance section below describes pre-AI-D2 source only.

Tested integration commit: `c751681ca3dc6e791ec96e9e16f99dc15db08ace`.
The evidence follow-up changes documentation only; tested executable source is unchanged.

| Fresh gate | Result |
| --- | --- |
| Backend full / ArchUnit | 709 tests, zero failures/errors/skips |
| Core-only | 106 tests, zero failures/errors/skips |
| Second-domain | 33 tests, zero failures/errors/skips |
| SLA + AI-D2 targeted | 103 tests, zero failures/errors/skips |
| Source boundary / mutations | PASS / 43 checks |
| SLA HTTP contracts | 8 real responses, 9 negatives each |
| Real Keycloak JWT | 10 reviewed + 12 intake-reviewed checks, PASS |
| Whole OpenAPI versus develop | No new errors; HPDI live 3/target 1 existing errors |
| Hosted GitHub Actions | Separate gate, not certified by local acceptance |

## AI-D2 compatibility

| Workflow | SLA participation | Lifecycle |
| --- | --- | --- |
| service-desk | None | Legacy unchanged |
| service-desk-reviewed | Approved handle only | Existing reviewed lifecycle |
| service-desk-intake-reviewed | Approved handle only | No intake/release/review/follower SLA |
| Room Request | None | Second-domain isolation |

Both reviewed keys share transactional execution/resolution hooks. The merge
retains intake CAS, expectedContextRevision, expectedIntakeVersion, final binding,
human feedback, canonical/follower guards, root confirmation, eligibility, duty,
assignment history and all process definitions/grants. R5 does not overwrite
frontend, model-delivery or Python service changes from upstream.

SLA initializes only after the approved handle exists, in the review transaction.
A failing insert rolls back admission, feedback, review, estimate and Flowable.
The existing unique review key prevents duplicate SLA rounds. Worker reconciliation
retains process-scope-before-SLA lock ordering and only persisted unresolved handles
participate; intake/follower tasks never receive SLA rows.

True-wait rework returns to intake with awaiting_review=false. SLA reads therefore
also hide an old round while its admission is not REVIEWED. Old resolved records
remain immutable; a new valid release/review creates the next round. Batch work
projection still uses actual handle IDs without adding per-item queries.

Only SLA migration is renamed to V20; upstream V16-V19 remain byte-identical.
Upgrade fixtures cover V9 through V19 to V20, including V15 through the entire
AI-D2 chain. They retain all applied checksums and app rows, assert no backfill,
validate and rerun migrate idempotently. V19-to-V20 executes exactly one migration.
No Flyway repair/history rewrite is used.

Fresh full/core/second-domain/targeted counts supersede the historical counts.
Added regressions cover pre-approval/follower null SLA, SLA-insert rollback,
true-wait START/PAUSE/RESUME/resolution and rework with a preserved historical
round and exactly one new SLA. A failed targeted run caught a changed test-helper
replay payload; the helper now preserves replay version and accepts a new version
only for a new operation. Failed runs are not acceptance evidence.
The new resume assertion checks persisted paused_at rather than clockPaused,
which correctly remains true outside business hours. Checksum comparison excludes
Flyway's versionless schema-creation row while preserving every applied version.

Runtime reproduction additionally supports:

```powershell
py -3.11 scripts/acceptance/sla-engine.py --intake
```

Both real JWT probes use direct HTTP, not Gateway E2E. Intake smoke uses human
release without a real final-assessment model; final-assessment/canonical behavior
is covered separately by PostgreSQL/Flowable integration, not claimed as one
combined production-model Gateway E2E. Hosted CI is a separate gate, never inferred
PASS from local results. Seven existing untracked documents remain excluded.

## GAP before / after

| Requirement | Existing | Reuse | Extend | New / after |
| --- | --- | --- | --- | --- |
| SLA deadlines | Native Flowable priority/dueDate, no R5 policy initialization | Existing reviewed handle task | Transactional native scheduling | Approval-based start/resolve deadlines |
| Business hours | Target shifts, staff on-duty flag (not a calendar) | Target shift defaults | No staff scheduling rewrite | Generic immutable BusinessCalendar |
| SLA pause/resume | WorkExecution intervals and pending reason | Existing authorized transitions | Owner callback in the same transaction | Persisted requester-wait timestamp, separate from workedMillis |
| Start/resolve breach | Existing START and handle completion | Existing task lifecycle | Persist first START / handle resolved | Read-only flags frozen at their respective stop times |
| Priority escalation | Native task priority / queue ordering | Existing native queue sort | Trusted transactional scheduling callback | One-step, retry-safe escalation from approved base |
| Queue SLA projection | Batched WorkProjection | Existing batching and load calculation | One SLA batch query per projection batch | Nullable Sla and effective/base priorities |
| Ticket SLA projection | Review view / classification / assessment | Existing authorized detail endpoint | Additive nullable sla property | Current approved round only |
| Transactional audit | ActivityJournal, Core audit, owner beforeComplete | Existing transaction / safe reference validation | Explicit trusted system activity method | Initialization, START, PAUSE, RESUME, resolution, escalation audits |
| Migration/upgrade | Flyway V1-V19, workspace composite key from V11 | Existing workspace/ticket constraint | V20 only | Persistent round history; no synthetic backfill |

## Verified rules and design decisions

Sources: existing reviewed BPMN, review/execution implementation and handoff,
R1/R2 handoffs, HPDI and Service Desk live/target contracts, and the local business
brief `docs/ticket.md` sections R3-R5. That brief was **untracked on entry** and is
not added to this PR; the decisions needed to review this implementation are
restated here. AI suggestion is never authoritative.

| Approved priority | Start budget | Resolve budget | Time basis |
| --- | --- | --- | --- |
| CRITICAL | 15 minutes | 120 minutes | Continuous, including nights/weekends |
| HIGH | 60 minutes | 480 minutes | Business calendar |
| MEDIUM | 240 minutes | 2 business days | Business calendar |
| LOW | 1 business day | 5 business days | Business calendar |

- The local R3 brief explicitly exempts CRITICAL from shift hours. It also sets
  escalation's default ceiling to HIGH. This is not inferred from AI output.
- The clock starts when the coordinator approval creates the handle task, even
  when no agent is assigned. Pre-review time is not charged retrospectively.
- First successful START stops the response target permanently. Handle `resolved`
  stops the resolution target; waiting for requester confirmation is not charged.
- Rework enters review with `sla: null`. The next approval creates a fresh round
  and snapshot. Completed old rounds remain in `servicedesk_sla`; accumulated
  workedMillis/ET semantics are unchanged.
- Exact deadline equality is on time. Breach means strictly after the deadline.
  A later GET cannot turn a completed on-time target into a breach.
- Warning means less than 20% of either unfinished, non-breached target remains,
  measured in that target's calendar. It is not a persisted R8 alert.
- The business brief's illustrative 08:00 + four hours = 12:00 conflicts with its
  explicit lunch exclusion. Shift intervals take precedence: four business hours
  from 08:00 finish at 13:30, not noon. This choice is covered by calendar checks.

Unspecified rules are deliberately explicit decisions, not claimed approvals:

- Default working days are Monday-Friday. Default holidays are empty: there is
  no invented statutory holiday source. Configure verified closures explicitly.
- A business day is the sum of configured shifts: default 510 minutes (8.5h),
  not a 24-hour duration. MEDIUM resolution is 1020 business minutes, LOW is 2550.
- Escalation occurs only while the approved handle task has never STARTed, after
  strictly more than 50% of the base start budget. It is **one step per round**,
  not repeated escalation on each scan. LOW -> MEDIUM, MEDIUM -> HIGH; HIGH and
  CRITICAL do not rise with the default ceiling. No automatic dispatch occurs.
- Escalation changes effective/native priority only. Base review priority,
  original calendar, start budget, resolution budget and deadlines do not reset.
- OTHER never pauses SLA. WAITING_PARTS and WAITING_EXTERNAL count by default;
  a snapshotted `waiting-external-counts=false` makes them pause like requester wait.

## Architecture and trust boundary

Core has no SLA or Service Desk business policy. It supplies a default no-op
owner callback after a successful authorized work transition, and a trusted
`TaskScheduling` mechanism. The mechanism locks the existing `process_scope`
row, checks workspace/instance/task identity, execution-task opt-in, definition
support and suspension, then executes the policy callback and updates native
priority/dueDate in the same transaction. It is **not an HTTP capability** and
does not grant a user the ability to bypass assignment or handling permission.

HPDI exposes `BusinessCalendar`, typed immutable `SlaPolicy`, `Sla`, and the
public `WorkScheduling` adapter. These contain no application tables, categories,
Flowable models or dependency on Service Desk. Room Request does not opt into R5.

Service Desk maps coordinator priority, freezes policy JSON, owns round state,
projects ticket/queue SLA, and reconciles approved open handles. Existing BPMN,
WorkExecution, assignment, workload and estimation remain their source of truth.
The existing injected UTC Clock bean is reused, without adding a competing Clock.

`recordSystem` is an explicit trusted server-side audit operation; it never
impersonates a JWT user. Core fixes its actor to `system:workflow`. The same safe
reference validation and MANDATORY transaction rules apply as to user activity.
No controller exposes system journaling or raw scheduling callbacks.

## Calendar configuration / snapshots

Configuration keys, applied only to future approvals:

```properties
service-desk.sla.zone=Asia/Ho_Chi_Minh
service-desk.sla.working-days=MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY
service-desk.sla.shifts=07:00-11:30,13:00-17:00
service-desk.sla.holidays=
service-desk.sla.waiting-external-counts=true
service-desk.sla.escalate-after-ratio=0.5
service-desk.sla.max-escalated-priority=HIGH
service-desk.sla.scheduled=true
service-desk.sla.poll-millis=60000
```

Holidays are comma-separated ISO dates. Shifts cannot wrap midnight, be empty,
negative or overlap. Adjacent shifts are allowed. Calendar arithmetic uses
explicit IANA zones and Instant intervals, including DST gaps/overlaps; it never
uses the JVM default timezone. DST boundaries follow `ZonedDateTime.atZone`:
gap boundaries move forward and overlap boundaries use the earlier offset.
Normalized overlapping intervals are merged before arithmetic, so a DST gap
cannot double-count two locally disjoint shifts. Targets are bounded to ten years, calendar scans
to 100 years. Unsupported duration/calendar configurations fail closed.

Each approval snapshots the entire policy/calendar/targets with algorithm
version `r5-v1`. Changing properties cannot rewrite old deadlines. Future
WorkSettings should supply a new policy for new approvals, not re-evaluate old
snapshots using current settings. No WorkSettings endpoint is promoted here.

## Pause / resume persistence

WAITING_REQUESTER stores `paused_at` in the same transaction as WorkExecution
PAUSE and audit. Reads calculate a provisional extended resolveBy without writing
it. RESUME consumes exactly the business/continuous interval since that timestamp,
extends the stored deadline once and clears `paused_at`. Retries of the same work
transition are rejected by the existing state machine. Restart loses no interval.
The response target is already frozen at first START. Non-working hours pause
the calendar automatically, without creating technician work intervals.

`clockPaused` is true for an unresolved SLA while explicitly paused or outside
business hours (except continuous CRITICAL). Completed resolution is not paused.
It is independent from `workState=PENDING`: OTHER/external waits can be pending
while SLA continues. SLA processing never changes workedMillis or estimates.

## Reconciliation / concurrency / performance

- Default scan: oldest 100 unresolved rounds every 60 seconds, one transaction
  per candidate. Multiple instances serialize on the **same existing process
  scope lock** as START, PAUSE, RESUME, assignment and completion, then lock SLA.
  Priority is compared again under that lock; a repeated scan cannot escalate twice.
- `checked_at` is scan-attempt metadata, not a breach/notification event. The
  worker advances it before trying a candidate, including suspended/stale/failing
  handles, so they cannot monopolize the first page forever. A failed business
  transaction rolls back policy/native-priority/audit changes, not attempt metadata.
- Native dueDate is initialized to resolveBy and synchronized at transitions and
  reconciliation, including provisional paused deadlines. Queue priority and
  existing dueDate sort therefore use the same effective SLA state. No JVM timer
  is the only source of truth; GET never escalates or writes state.
- Queue adds one batched SLA query per existing batch of at most 100 executions,
  not one query/timeline per item. Full queue load still scans O(N) existing work
  rows; this PR does not claim a constant-time workload projection.
- Ceiling: 100 attempts per poll per instance; lag increases above that backlog.
  Native dueDate can lag a currently paused projection by a poll interval. No
  production throughput/latency benchmark or global batch lock is claimed.

## Database upgrade / recovery

V20 creates only `servicedesk_sla`, referencing V11's existing composite
workspace/ticket key. The primary key identifies an approval round, the unique
workspace/handle key prevents duplicate clocks. Constraints check priority names,
timestamp ordering, pause state and JSON object snapshots. Partial reconciliation
and workspace/ticket lookup indexes bound the worker/read access paths.

No row is inserted for an existing pre-R5 round: its historical approval timestamp
and policy are not invented. Such a round returns null until a future genuine
review. V1-V19 are unchanged. Flyway validate and re-running migrate must succeed.
Before upgrade, take a PostgreSQL backup. If application rollback is necessary,
stop all R5 workers and roll back the application while retaining V20/table data;
old code ignores the additive table. Do not delete Flyway history or silently
discard clocks. A future forward recovery migration must handle any state repair.

## API / FE handoff

No new endpoint. Existing ticket detail/review and queue/START/PAUSE/RESUME
responses gain nullable `sla`:

```json
{
  "sla": {
    "startBy": "2026-10-26T02:00:00Z",
    "resolveBy": "2026-10-27T00:30:00Z",
    "startBreached": false,
    "resolveBreached": false,
    "warning": false,
    "clockPaused": false
  }
}
```

This illustrative HIGH snapshot corresponds to approval at 08:00 Vietnam time
on Monday 2026-10-26; it is not fabricated runtime evidence. Wire timestamps are
UTC. FE may localize their display, but must not recalculate deadlines, infer SLA
pause from workState, or replace coordinator basePriority with an AI suggestion.
Classification `priority` is the persisted effective level, `basePriority` remains
the approved level, and `escalated` distinguishes them. Unreviewed tickets,
awaiting-rework-review tickets, pre-R5 rounds and non-participating domains have
null SLA. Legacy Service Desk keeps its existing response shape.

Only Sla and these implemented response properties are live OpenAPI. Alerts,
settings, auto-close, classification correction, dispatch and frontend remain out
of scope. Whole HPDI OpenAPI already has unrelated structural debt; compare
baseline/current errors separately from validated execution/SLA subsets.

## Historical pre-AI-D2 evidence (not current acceptance)

Executed local gates are recorded in `evidence/R5_SLA_ENGINE.json`. Counts are
per suite/profile and must **not** be added together: targeted tests overlap the
full suite, and profile proofs deliberately rerun shared tests.

| Gate | PASS | FAIL | ERROR | SKIP |
| --- | ---: | ---: | ---: | ---: |
| Backend full (660 tests) | 1 | 0 | 0 | 0 |
| Core-only (106 tests) | 1 | 0 | 0 | 0 |
| Second-domain (33 tests) | 1 | 0 | 0 | 0 |
| SLA targeted + migrations (23 tests) | 1 | 0 | 0 | 0 |
| HTTP contracts (8 SLA responses + negative checks) | 1 | 0 | 0 | 0 |
| Boundaries (source, ArchUnit, 43 mutation checks) | 1 | 0 | 0 | 0 |
| Real runtime acceptance (10 checks, direct HTTP) | 1 | 0 | 0 | 0 |
| Hosted CI (publication pending) | 0 | 0 | 0 | 1 |

All four Java runs have zero failures, errors and skipped tests. Full regression
includes R1/R2/R3, review/execution, legacy workflows, Room Request and ArchUnit.
Targeted integration exercises late/on-time target freezing, audit rollback of
START/escalation, concurrent START/review/escalation, alternate-policy restart
reads, all pause reasons, and V9-V15 upgrades through V16 without backfill.

Reproduction (offline Maven cache and listed Docker images must already exist):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-task-execution.ps1 -BuildImage maven:3.9.11-eclipse-temurin-21 -Profile full
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-task-execution.ps1 -BuildImage maven:3.9.11-eclipse-temurin-21 -Profile core-only
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-task-execution.ps1 -BuildImage maven:3.9.11-eclipse-temurin-21 -Profile second-domain
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-task-execution.ps1 -BuildImage maven:3.9.11-eclipse-temurin-21 -Profile full -Test 'SlaPolicyTest,TicketIntegrationTest#sla*,ServiceDeskMigrationIntegrationTest,AgentDutyUpgradeIntegrationTest'
py -3.11 scripts/check-tier-boundaries.py
py -3.11 scripts/test-tier-boundaries.py
py -3.11 scripts/test-servicedesk-execution-contract.py <servicedesk-http-evidence.json> --sla-evidence <r5-sla-http-evidence.json> --baseline origin/develop
py -3.11 scripts/acceptance/sla-engine.py
git diff --check
```

The Java runner saves raw XML/HTTP evidence under `.local/task-execution-*`,
with a separate disposable PostgreSQL per run and no H2 substitution. The runtime
probe requires this branch's executable jar at `.local/r5-runtime.jar`, built
with `mvn -B -o -DskipTests package` in the Maven image and copied from
`backend/target/dx-platform-0.1.0-SNAPSHOT.jar`; it creates and removes its own
disposable PostgreSQL/Keycloak/backend containers and generates ephemeral secrets.
It restarts the backend while requester-wait is persisted and tests real
scheduled escalation; fixture aging occurs only in its disposable test database.

Whole-document comparison found exactly the existing 3 live HPDI structural
errors and 1 target HPDI structural error, unchanged from fetched develop. Both
Service Desk documents have zero structural errors. The live execution/SLA
subset and real HTTP bodies pass validation; unrelated HPDI debt is not waived.

During development, validation caught a duplicate constraint already provided
by V11, a stale V15-only upgrade assertion, and a rollback test's missing SQLSTATE.
These were fixed and the complete suites rerun; failing evidence was not labeled
PASS. The runtime runner also re-resolves Docker's ephemeral port after restart.
The tracked tree was clean on entry; seven existing untracked documents are
preserved and excluded from commits. No baseline migration is edited.
MockMvc is PostgreSQL/Flowable integration, **not** real JWT/gateway E2E.
The optional runtime probe uses real Keycloak JWT and HTTP, explicitly no gateway.
Hosted CI is a separate gate; it is not called PASS merely because local tests pass.

Known limits: no automatic holiday feed, per-agent scheduling, notification
persistence, full WorkSettings, historical SLA backfill, repeated escalation or
production capacity claim. Human approval of the documented calendar/day/rework
decisions is still required before merge. No production deployment or merge is
performed by this task.
