# M1: task execution and queue foundation

Date: 2026-10-27 (Asia/Saigon). Branch: `feat/ticket-execution-queue`.
Base: `origin/develop` at `122519e`, fetched before implementation.
Status: **PARTIAL; Java capabilities only, not a completed M1 or FE-ready API.**

## 1. Requirement-to-code gap matrix

| Rule | Reused baseline | This change / remaining gap |
| --- | --- | --- |
| R0 | Transactional ticket + Flowable process + audit creation | Unchanged; no client-supplied actor |
| R1 | Existing triage/group suggestions and AI07 assessment | No persisted review decision/group routing |
| R2 | None for estimates/workload | ET defaults, overrides, median and assignment suggestions remain |
| R3 | Workspace identity/directory | Duty persistence and assignment eligibility remain |
| R4 | Core claim, assign and HPDI inbox | New opt-in Java queue; review BPMN and public projection remain |
| R5 | Flowable task priority/due-date primitives | Queue reads native priority/deadline; no business SLA/aging calculation |
| R6 | Existing workflow completion | New start/pause/resume, elapsed work, audit and concurrency checks; HTTP/release remain |
| R7 | None | Urgent handling/timers remain |
| R8 | Transactional audit | Alert persistence, generation and read APIs remain |
| R9 | Existing AI views | Persisted human corrections remain |
| R10 | Not approved | Intentionally deferred; no auto-merge |
| R11 | Existing confirm/accepted/rework in the same process | Auto-close and reopen notifications remain |
| R12 | Creation-time and visibility-scoped statistics | New execution/SLA/review buckets remain |

## 2. Scope and dependencies

The baseline has `handle -> confirm -> accepted/rework`, not a coordinator review
stage. Claiming a task is not an approval. Consequently Service Desk is NOT opted
into the new execution policy yet: enabling it without review would violate R4.
Room Request is also unchanged; its approval task is not silently treated as work.
The equipment handover test process demonstrates an app-independent work step.

The target REST `QueueItem` requires `estimate`, `remainingMinutes`, business
priority, label and reopen information. The target `QueuePage` requires workload.
Those projections depend on approved application review data and the later ET
implementation. No fake estimate, SLA, calibrated confidence or priority mapping
has been invented to satisfy the schema.

The milestone boundary is approved: this is a separate Java-only Foundation PR.
Review/ET prerequisites must land before promoting the target REST operations.
No target operation has been moved to the live OpenAPI contract. M1 remains
PARTIAL; this PR is not a Frontend integration contract.

## 3. Architecture and ownership

Core `WorkflowService.execute/queue` implements engine-local mechanisms. A new
`TaskExecution` record carries the active task, execution substate, native numeric
priority, native due date and elapsed milliseconds. No ticket entity/status or
Service Desk dependency is added to Core.

HPDI `WorkExecution` maps Core results to HPDI-owned records and validates shared
pending reasons. `ProcessContribution.executionTasks()` is the application-owned
opt-in, adapted by `ContributionRegistry` to `ProcessRegistration.executionTasks()`.
Both defaults are empty, preserving existing applications. The registry rejects
unknown/duplicate registrations and initiator-only execution tasks. Only the
registration/contribution owning a process may declare its execution task keys.
The new `WorkflowService` methods default to fail-closed CONFLICT responses, so
existing external Java implementations do not need to implement them immediately.
Existing process registration/contribution defaults remain empty.

## 4. State and persistence

Flowable remains the lifecycle authority: only an existing, active, supported
candidate task can execute. Task-local variables store `executionState`,
`executionStarted`, `executionMillis`, `pendingReason` and `pendingNote`.
Missing state means NOT_STARTED; START opens an interval, PAUSE closes it and
sets PENDING, RESUME opens a fresh interval. Repeated transitions return CONFLICT.
Each closed interval and transition is written through transactional Core audit.
Historical task-local variables retain accumulated work after completion.

No application table, Flyway migration, new state machine or BPMN deployment is
needed for this foundation. No applied migration or old process instance changes.
The existing supported-definition guard remains in force. A future BPMN upgrade
must address active older instances explicitly; this patch does not migrate them.

## 5. Authorization matrix

| Operation | Required checks inside the service boundary |
| --- | --- |
| Execute | Workspace member, exact assignee, current task:handle, current candidate membership, not process owner, opted-in active task |
| Own queue | Workspace member and current task:handle |
| Another queue | Workspace coordinator and current task:assign |
| Complete opted-in work | Existing completion checks plus IN_PROGRESS; final interval closes in the same transaction |
| Reassign active work | CONFLICT; coordinator must first arrange an explicit pause |
| Reassign paused work | Existing assignment checks; preserve elapsed work and reset to NOT_STARTED for the new assignee |

Platform-admin alone gains no workspace access. Actor identity comes only from
the current authenticated user. Revoked permissions and directory outages fail
closed. Confirmation tasks and all non-opted-in tasks retain existing behavior.

## 6. Concurrency and queue

The existing `process_scope FOR UPDATE` lock serializes competing operations on
one process. START/RESUME additionally take a transaction-scoped PostgreSQL
advisory lock keyed by authenticated subject across workspaces before counting
active intervals. The technical limit defaults to 1 via
`dx.workflow.max-in-progress` (1..100); it is not the future admin settings API.
Every writer of IN_PROGRESS goes through this lock. Audit failure rolls back
Flowable variables and work totals in the same Spring transaction.

Queue filtering and pagination execute in SQL before materializing a page:
workspace, assignee, supported process definition and opted-in task key. Owner
tasks and suspended tasks are excluded. Order: IN_PROGRESS first, native priority
descending, native due date ascending (null last), creation time, task ID.
Approval/confirmation tasks are excluded unless incorrectly declared as work by
an application; initiator declarations are rejected during registry validation.
The queue does not calculate ET, workload, shift-aware start times or priority
aging. Page projection, locals and candidate groups share one SQL statement
snapshot, bounded by the existing maximum page size of 100. A regression first
reproduced the old ID-query/Flowable-hydration reassignment leak (RED evidence:
`.local/foundation-review/queue-red.log`); the snapshot projection removes that
race and per-item hydration. Count and page are separate READ_COMMITTED snapshots:
the total may drift during concurrent changes. Native Flowable SQL schema coupling
and large-workspace query/index performance remain technical risks.

## 7. APIs and request flow

There are **no new HTTP endpoints** in this patch. Internal usage:

```java
workExecution.execute(workspace, taskId, WorkExecution.Action.START, null, null);
workExecution.execute(workspace, taskId, WorkExecution.Action.PAUSE, "OTHER", "Awaiting supplier");
workExecution.execute(workspace, taskId, WorkExecution.Action.RESUME, null, null);
workExecution.queue(workspace, null, new PageRequest(0, 20));
```

Existing actual flow remains: Frontend bearer request -> APISIX -> Spring JWT
validation -> Service Desk API -> HPDI process capability -> Core workflow ->
PostgreSQL/Flowable + audit -> application response. AI07/triage remain separate
existing calls; creation does not gain a background assessment job in this patch.
For the new internal capability, HPDI -> Core performs authorization, locks,
variable/audit writes, then returns an HPDI-owned DTO. A future HTTP controller
must return the target QueueItem, not serialize this intermediate Java DTO.

## 8. AI/FE handoff

AI07 is merged in the fetched baseline (PR #45). Its actual assessment view is
`ticket-intelligence-assessment@0.2.0`, with hierarchical classification,
assessment, attributes, evidence, missingInfo/questions, disposition/provenance.
Do not assume every field in the earlier ticket document is the same as this
merged contract. No confidence semantics are reinterpreted here. Existing AI06
grounded assistance is reused unchanged; no Python or frontend source changes.

FE must continue using the target contract as mock-only for the new operations.
No release, SLA clock pause, duty control, review API, durable assessment worker,
auto-close scheduler or new statistics is claimed. Interval detail is persisted
in audit; an application-authorized public execution timeline still remains.

## 9. Verification and running

Run the task-owned disposable PostgreSQL 16.6 runner with Maven on PATH (or pass
`-Maven <absolute mvn.cmd>`). It uses random credentials, a loopback-only random
port, tmpfs storage and cleanup limited to its newly created container:

```powershell
./scripts/test-task-execution.ps1 -Profile execution
./scripts/test-task-execution.ps1 -Profile full
./scripts/test-task-execution.ps1 -Profile core-only
./scripts/test-task-execution.ps1 -Profile second-domain
python scripts/check-tier-boundaries.py
python scripts/test-tier-boundaries.py
```

On Windows use `-BuildImage dx-core-build:0.1.0` for full/profile acceptance:
the existing storage tests require Linux POSIX permissions and symlinks. This
option copies source/contracts/boundary metadata into a disposable Linux build
container, reads the host Maven repository read-only and uses a private internal
Docker network. It neither writes the source mount nor updates a shared cache.
The image must already provide Java 17/Maven and the host cache must contain the
project dependencies. For example:

```powershell
./scripts/test-task-execution.ps1 -BuildImage dx-core-build:0.1.0 -Profile full
```

Maven runs offline, so populate the existing Maven cache first if necessary.
The runner fails for any skipped test and writes logs plus a count summary under
`.local/task-execution-<profile>/`. New tests exercise elapsed intervals,
start/resume contention, transactional audit rollback, current permission
revocation/outage, wrong workspace, independent handling, queue ordering and
pagination. This does not replace hosted CI, HTTP contract acceptance or real
Java-Python model acceptance.

Final verification against the current code, Linux Java 17.0.15/Maven 3.9.9 and
the runner's digest-pinned disposable PostgreSQL 16.6:

| Gate | Result | Evidence |
| --- | --- | --- |
| Full backend | 485 tests; zero failures/errors/skips | `.local/task-execution-full/summary.json` and `maven.log` |
| Core-only | 103 tests; zero failures/errors/skips | `.local/task-execution-core-only/summary.json` and `maven.log` |
| Supported second-domain selection + new HPDI tests | 30 tests; zero failures/errors/skips | `.local/task-execution-second-domain/summary.json` and `maven.log` |
| Focused execution regressions | 33 tests; zero failures/errors/skips | `.local/task-execution-execution/summary.json` and `maven.log` |
| Source tier boundary | PASS | `python scripts/check-tier-boundaries.py` |
| Boundary mutation checks | 39 PASS | `python scripts/test-tier-boundaries.py` |
| Existing contract checker self-test | PASS | `python scripts/test-core-contract.py` |
| Service Desk target OpenAPI | PASS | `.local/foundation-review/servicedesk-openapi.log` |
| HPDI live/target OpenAPI | FAIL: existing structural errors, unchanged | `.local/foundation-review/openapi-baseline.log` |

These are fresh runs after the queue snapshot, owner-only registration and Java
interface compatibility fixes. The earlier 479/98/29 counts do not certify this
source. Full and second-domain include the actual ArchUnit `TierBoundaryTest`.
Source/mutation/contract checker logs are retained in `.local/foundation-review/`.
The four profile commands above were run with `-BuildImage dx-core-build:0.1.0`.
Validator command: `python -m openapi_spec_validator contracts/openapi/servicedesk-target.yaml`.

The second-domain command follows the explicit selection in
`.github/workflows/tier-boundaries.yml`, plus `WorkExecutionTest`. It is NOT an
assertion that every test works with the second-domain profile. The broad run
failed because that profile excludes sample BPMN needed by unrelated Core
fixtures; its log is retained as `broad-maven.log`. No production guard or CI
workflow was weakened. The supported command and its new regression are explicit
in the runner, not hidden skips. Test counts differ from the historical 265/91/40
because this branch starts after merged AI07 and uses the current CI selection.

The first Windows full run also exposed a cleanup leak in the new ownership test;
the test now restores process ownership before cleanup. Its other three errors
were the existing POSIX/symlink tests on Windows, not removed assertions. Linux
full acceptance reruns those tests successfully. Earlier mock/unit-only results
with skipped DB suites are NOT used as acceptance.

The existing live HPDI `KnowledgePage.properties.description` is a string instead
of a schema (`contracts/hpdi/openapi.yaml:1157`); the alerts target has an unquoted
comma introducing an invalid `cho badge` property
(`contracts/hpdi/openapi-target.yaml:324`). Both files are unchanged from the base.
Baseline validation was rerun on the `git show origin/develop:<path>` bytes with
the original relative-reference base URI. Unchanged blob IDs are respectively
`d03a163421f77cc7ceaaffa017cd6d018ea4bcad` and
`11bcb1b10cc87d0606ae6aab442dab735c24f87a`; both fail on the baseline too.
These are recorded contract blockers, not a claim of complete contract validation.

## 10. Rollback, Git and outstanding work

No migration rollback is needed. Keep execution opt-in disabled while reviewing
the Java API; reverting this patch cannot alter existing Service Desk/Room Request
flows because neither app was opted in. If an application later enables it,
removing enforcement requires an explicit plan for outstanding active intervals.

The initial six untracked documents, including `docs/ticket.md`, are preserved.
No frontend/Python production edit, push to protected branches, merge, tag or
release occurs. Commit and PR cover only the approved Java Foundation, tests,
runner and this document. Milestones 2..5 are not implemented here.

## 11. Next PR: Coordinator Review + Service Desk Execution HTTP API

Design/order only; none of the following is enabled by this Foundation:

1. Define persisted coordinator review decisions and immutable reviewed/default ET
   snapshots, including provenance and the QueueItem/QueuePage projection rules.
   Resolve estimate/remaining/workload dependencies rather than inventing values.
2. Add coordinator review BPMN and transactional reviewed assignment, with explicit
   independent-agent eligibility. Plan versioned deployment and routing for active
   old process instances before changing the supported-definition policy.
3. Implement the required ET projection and opt in only approved Service Desk work
   steps. Approval and requester confirmation must never enter the execution queue.
4. Expose HTTP through HPDI-owned application DTOs mapped to the target contract;
   do not serialize intermediate Java execution records as the final public API.
5. Add real HTTP authorization, review/assignment concurrency, rollback and queue
   contract tests; assess workspace-scale query plans. Promote only implemented
   target operations after these dependencies pass. M1 stays PARTIAL until then.
