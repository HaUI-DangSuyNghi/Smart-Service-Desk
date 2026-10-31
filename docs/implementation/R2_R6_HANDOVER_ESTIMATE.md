# R2/R6 Work Handover and Estimate Adjustment

Date: 2026-10-28 (Asia/Saigon).
Base: `e9966a89ef6956c84432dfc646e10c5188030579` (`origin/develop`, fetched before implementation).
Branch: `feature/r2-r6-work-handover-estimate`.
Delivery status: verification in progress; not approved for merge or production.
Hosted CI: `HOSTED_CI_WAIVED` for internal review, by the task's explicit waiver; not CI PASS.

## Requirements and GAP Matrix

`docs/ticket.md` was present locally but untracked when work began. It was read as
user-supplied requirements, not added or represented as version-controlled canonical
documentation. A separate documentation PR should establish its provenance.

| Requirement | Baseline gap | Implementation / evidence |
| --- | --- | --- |
| R6 release API | Target only | Validated reason/note, assignee authorization, 204 |
| R6 workflow | No handle-to-review BPMN edge | Engine change-state, same process, exactly one fresh native review |
| R6 work conservation | Completion accumulates work, release absent | Close active interval once, persist handover and cumulative ticket work |
| R6 pending | Pending monitor survives until resolution | Retire old monitor, suppress undelivered agent recipients without deleting events |
| R6 Intake | Review receipt bound to old task | New round/version and HANDOVER receipt, unchanged sealed context |
| R6 SLA | Initialization always grants a new deadline | Carry old policy, approval/start/resolve timestamps and escalation to new handle |
| R2 request/list/decision | Target only | HPDI persistent lifecycle and workspace-filtered pages |
| R2 ET/history/load | Existing canonical review estimate/history | Service Desk adapter updates those stores, existing queue reads new ET |
| Concurrency | No request serialization | Existing process-scope transaction lock plus unique pending index |
| Isolation/rollback | Existing primitives | Reused permissions, current-role intersection, audit and PostgreSQL transaction |

## Architecture

Core owns authenticated, atomic Flowable release and process registration hooks.
HPDI owns estimate request orchestration, DTOs, storage, HTTP endpoints and the
`WorkEstimate` application extension. Service Desk owns estimate storage,
handover facts, review policy, Intake, SLA and alert monitoring. No generic
Core/HPDI class imports Service Desk. Room Request has no release target or
estimate adapter and retains its existing workflow.

## Release Workflow and Definition Compatibility

No BPMN resource is edited or redeployed with a different graph. Release uses
Flowable `createChangeActivityStateBuilder().moveActivityIdTo(...).changeState()`
under the same process-scope lock used by completion and assignment. It is not
completion with outcome `resolved`. The old task is ended by the engine with a
`released` historic outcome and handover comment; the native process remains open.
Only the registered reviewed Service Desk handle can return to `review`.
Multiple active tasks, suspension and unsupported definition IDs fail closed.

| Instance position | Behavior |
| --- | --- |
| New reviewed/intake-reviewed | Existing definition and admission behavior unchanged |
| Existing review | Existing review API; release not supported |
| Existing handle on supported definition | Can release from NOT_STARTED, IN_PROGRESS or PENDING |
| Confirm | Confirmation/rework unchanged; cannot release |
| Intake/follower/safety hold | No release target; admission cannot be bypassed |
| Rework | Existing rework route and fresh SLA policy remain unchanged |
| Older unsupported definition ID / legacy service-desk | Conflict, no automatic migration |
| Failure/recovery | Workflow change, history, Intake, SLA, alerts and audit roll back together |

Because definitions are unchanged, active instances on the currently supported
deployed definitions need no migration. This does not widen the baseline's
support for older definition IDs. Never delete definitions to force compatibility.
Application rollback must retain these additive tables and inspect returned
review rounds before using an older binary: the old binary would reset SLA on
approval. Recovery is roll-forward, not blind binary downgrade.

## Work History

An IN_PROGRESS release closes only its own current interval. NOT_STARTED adds
zero; PENDING reuses already accumulated time. The task history and START/PAUSE/
RESUME audits remain in Flowable/audit storage. `servicedesk_work_release` records
actor, reason, note, old handle, new review and accumulated milliseconds. The
ticket's previous work is included in later queue projections. Handover does not
increment the public rework count and never starts/stops another task.

## Estimate Lifecycle and Authorization

Requests snapshot the task, process, review generation, requester and current ET.
Creation does not change ET. APPROVED sets the existing review ET and COORDINATOR
basis, and appends existing estimate history. REJECTED changes only the request.
Either decision permits a later request on the still-active task. Duplicate
pending creation and duplicate decisions conflict. Ended/released/reworked tasks,
changed assignment, changed generation or changed ET make a request stale.
Changing an active task's assignee atomically moves the previous assignee's PENDING
request to SUPERSEDED under the same process-scope lock. The unique pending index
is retained. Actor, timestamp and ASSIGNMENT_CHANGED reason are recorded separately
from decisions, with an estimate.supersede audit. Audit failure rolls back both
assignment and supersession. Same-assignee assignment is a no-op; A -> B -> A never
revives an old request. B can immediately create a new request; A cannot propose
or decide for B. Approve-before-reassign is valid; reassign-before-approve returns
409 without changing ET. Creation also reconciles pre-upgrade pending requests
whose requester no longer holds the task, transactionally and with audit.
Requests on ended/released tasks remain historical PENDING and cannot be decided;
new handles have distinct IDs and are not blocked by them.

On handover reapproval, DEFAULT is recalculated from the new category and priority
(even after WRONG_GROUP). COORDINATOR estimates carry forward unchanged, including
across category changes, unless the reviewer explicitly replaces the estimate.
This preserves deliberate human sizing rather than silently reinterpreting it as
a default. Previous history is immutable; the new review records the new category,
basis and effective estimate, which feeds queue remaining time and workload.
Priority changes remain blocked by the existing SLA policy (409); this PR does not
relax that guard or grant a new SLA budget.

| Operation | Authorization |
| --- | --- |
| Request ET | Current Agent + task:handle + actual independent assignee + current group/Duty |
| List ET | Current Coordinator + task:assign sees workspace; current Agent + task:handle sees own requests |
| Decide ET | Current Coordinator + task:assign; requester still eligible/on-duty and holds same active task |
| Release | Signed candidate role + task:handle + current membership + actual independent assignee |
| Requester / foreign workspace | Denied; no internal list leakage |

Current directory roles only restrict signed roles, never grant extra roles.
Membership and eligibility are rechecked at the mutation boundary. Keycloak and
PostgreSQL do not share a distributed transaction; revocation after the final
directory check takes effect on the next operation. Going off duty does not
prevent an agent from returning work they still hold.

## Concurrency and Transactions

`WorkScheduling.reconcile` locks the existing process scope before ET validation
and mutation. Completion, assignment, execution and release use the same lock.
All request writers serialize on that scope; PostgreSQL also enforces one
PENDING request per workspace/task. No JVM synchronization is used. Audit
failure rolls back request decision, canonical ET/history and native workflow
mutations. Pages sort by created timestamp and UUID descending; offset paging
is not a multi-request snapshot while new rows arrive.

## SLA and Alerts Policy

Handover is not resolution. The old SLA row gets `released_at`, not `resolved_at`.
The old monitor is inactive and the old pending episode ends. If SLA was paused
under the existing R5 policy, only already accrued eligible pause time is settled.
The Coordinator waiting period then consumes the original deadline; no new budget
is granted. Reapproval copies the original policy, approval/start/resolve clocks,
first START and escalation. Base-priority changes on handover reapproval return
409 pending an explicit business policy for such changes. ET edits never change SLA.

No CRITICAL_NEW is generated solely because an existing ticket was handed over.
Already delivered alerts/read state remain intact. Undelivered agent recipients
are suppressed durably, including a worker that captured recipients before release;
Coordinator delivery remains eligible. Agent-only suppressed events are retained
but excluded from worker retries. While awaiting review, no agent monitor runs;
breaches are not erased and are observed again after reassignment. Overall SLA
and new Coordinator-wait alert policy are intentionally outside this PR.

## Database Migrations

- HPDI V23: estimate requests, checked bounds/state, stable-page indexes, unique
  PENDING task index, durable alert recipient-suppression flag.
- Service Desk V24: handover facts with workspace/ticket FK, unique old handle
  and new review, SLA release timestamp.
- HPDI V25: additive SUPERSEDED lifecycle and separate supersession provenance.
  V23/V24 are unchanged; upgrade tests include populated V23/V24 request storage.
- V1-V22 unchanged. Clean install, upgrade through V22 and repeat migrate are tested.
- HPDI process IDs are capability references, not cross-owner foreign keys to Core
  storage (the repository's strict boundary gate prohibits such FKs). Domain-owned
  handover FKs and request constraints remain database-enforced.

## API / FE Handoff

Prefix: `/api/hpdi/v1/workspaces/{workspace}`. Bearer workspace authorization applies.

| Action | Method/path | Body / result |
| --- | --- | --- |
| Return work | POST `/tasks/{id}/release` | `{"reason":"HANDOVER","note":"Hardware checked; continue diagnostics"}` -> 204 |
| Propose ET | POST `/tasks/{id}/estimate-requests` | `{"proposedMinutes":120,"reason":"Replace mainboard"}` -> 201 EstimateRequest |
| Pending decisions | GET `/estimate-requests?status=PENDING&page=0&size=20` | Page of EstimateRequest |
| Approve | POST `/estimate-requests/{requestId}/decision` | `{"approved":true,"note":"Approved extra work"}` -> 200 EstimateRequest |
| Reject | Same decision endpoint | `{"approved":false,"note":"Keep current estimate"}` -> 200 EstimateRequest |

After release refresh the old agent queue and Coordinator ticket list; discard the
old task ID. Reapproval returns a new handle. After an ET decision refresh the
request, ticket, queue/load and assignment suggestion. A 409 requires refreshing
state, not silently retrying approval. Only these four target endpoints are
promoted into the live HPDI contract; Service Desk HTTP shapes remain unchanged.

## Verification and Merge Readiness

PR #57's final verification comment records the exact final commit SHA, commands,
XML-derived test counts and runtime checks. The verification matrix is:

| Gate | Command / evidence |
| --- | --- |
| Backend full | `scripts/test-task-execution.ps1 -BuildImage dx-core-build:0.1.0 -Profile full` |
| Core-only | Same runner with `-Profile core-only` |
| Second-domain / Room Request | Same runner with `-Profile second-domain` |
| R2/R6 + upgrade | Full profile, `-Test 'TicketIntegrationTest#r2*+r6*,ServiceDeskMigrationIntegrationTest,AgentDutyUpgradeIntegrationTest,IntakeWorkflowIntegrationTest#handover*'` |
| SLA / Alerts | Full profile, `-Test 'TicketIntegrationTest#sla*,WorkAlertsIntegrationTest,SlaPolicyTest'` |
| Real Flowable + signed JWT + restarts | `python scripts/apps/servicedesk/acceptance/sla-engine.py --handover --alerts` and again with `--intake` |
| Contract | `scripts/test-servicedesk-execution-contract.py` with real full-suite response evidence, `--estimate-evidence .local/r2-r6-runtime.json --baseline origin/develop` |
| Tier boundaries | `python scripts/check-tier-boundaries.py` and `python scripts/test-tier-boundaries.py` |

Local Maven evidence is `.local/task-execution-<profile>[-targeted]/summary.json`
and per-run `TEST-*.xml`; real-runtime evidence is `.local/r2-r6-runtime.json`
and `.local/r2-r6-intake-runtime.json`. Evidence is not committed or presented as
hosted CI success. Baseline whole-document OpenAPI issues are reported separately
from the promoted-contract regression check. Hosted CI waiver means WAIVED, not PASS.
The implementation commit is made before the final matrix runs; no source edits
are permitted between that verification and push without rerunning affected gates.

The initial native Windows full run exposed existing POSIX permission/symlink
test incompatibilities; final full/core/second-domain runs use disposable Linux
containers and PostgreSQL rather than skipping those checks. Runtime probes use
real signed Keycloak JWTs, persisted Flowable instances and actual backend restarts.
No shared environment deployment, forced push, merge or production-ready claim.
Use a MERGE COMMIT only (not squash or rebase merge). Draft removal and independent
review approval remain human decisions; this agent does not merge PR #57.
