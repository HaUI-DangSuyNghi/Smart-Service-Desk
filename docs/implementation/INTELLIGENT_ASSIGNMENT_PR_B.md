# R2 assignment suggestion (PR B)

Baseline: latest fetched `origin/develop`, `217296d7bc2381b0df98a16c05b1012f007f338f` (PR #52).
Branch: `feat/r2-assignment-suggestion`. No automatic assignment or review is introduced.

## GAP before / after

| Capability | Before | PR B |
| --- | --- | --- |
| R1 category/group/current enabled agent permissions | EXISTING | REUSE `TicketAssignmentEligibility.supportGroup` and `AgentDirectory.currentAgents` |
| R3 workspace duty | EXISTING | REUSE `AgentDuty.list`; missing state remains off-duty |
| Creator exclusion and final assignment revalidation | EXISTING | REUSE; exclusions also explained in suggestions |
| ET defaults, overrides and history | EXISTING | REUSE unchanged; ACCOUNT/HIGH remains 20 |
| Execution intervals and remaining workload | EXISTING | REUSE queue and `WorkProjection.projectBatch`; no second formula |
| Queue aggregation across agents | Per-assignee only | EXTEND with coordinator-only paged `workspaceQueue` |
| Daily assignment history | Core audit, no dedicated counter source | NEW immutable assignment events, including claim and reassignment |
| Persistent group tie cursor | Missing | NEW V15 cursor; update only on actual assignment |
| Task suggestion | Target only | NEW implemented/tested live endpoint |
| Review form suggestion before handle exists | Missing | NEW read-only application preview with explicit form inputs |
| Flowable assignment lifecycle | EXISTING | EXTEND default-no-op `afterAssign` hook, inside existing transaction |
| AI assessment/category/priority | EXISTING | REUSE unchanged; suggestions never consume or write AI decisions |

Historical median ET (`SIMILAR`, `SIMILAR_BY_SIZE`, sample size) and estimate-change approval remain NEW work for a separate PR.
The target describes them as R2, but neither is needed to rank with the already-reviewed/default/overridden ET.
They need separate historical-data and approval policies; implementing them here would rewrite the explicitly preserved estimate flow.
No scheduling, SLA R5, R8 alerts, CRITICAL dispatch or unrelated OpenAPI cleanup is included.

## Lifecycle and HTTP / FE handoff

`TicketReviewService.review` persists the classification/ET, completes `review`, and only then receives the new `handle` task.
The target handle-task route alone therefore cannot populate the review form.

Before submitting review, call:

```http
GET /api/apps/servicedesk/v1/workspaces/lab-a/tickets/{ticketId}/assignment-suggestion?category=HARDWARE&priority=HIGH&estimateMinutes=90
```

Category and priority are required explicit coordinator form values; estimateMinutes is optional, 1..10080.
Omitting ET uses the existing default table. Preview requires a pending reviewed-workflow `review` task and an independent reviewer.
Its `taskId` is the EXISTING REVIEW task ID, never an invented future handle ID. Legacy and non-review states return 409.

After review, call the unchanged target path:

```http
GET /api/hpdi/v1/workspaces/lab-a/tasks/{handleTaskId}/assignment-suggestion
```

This route requires an active reviewed Service Desk handle task; other processes return 409.
Both endpoints require Coordinator and `task:assign`, enforce workspace access, and return the same schema:

```json
{
  "taskId": "existing-review-or-handle-id",
  "group": "hardware",
  "estimateMinutes": 90,
  "recommended": "agent-a",
  "reason": "Minimum-load band (<15 minutes from minimum; considered task excluded), then fewest assignments today, then committed-assignment round-robin; selected agent-a with 30 load minutes and 1 assignments today",
  "candidates": [{"subject":"agent-a","displayName":"Agent A","onDuty":true,"loadMinutes":30,"assignedToday":1,"shiftRemainingMinutes":null,"recommended":true,"excludedReason":null}]
}
```

FE must not overwrite a user-selected assignee on refresh; display this as advice, not a reservation or final decision.
Submit the existing review/assign API only on explicit coordinator action. Re-fetch after conflicts or classification changes.
No eligible on-duty candidate returns `recommended:null` and an explanation, not a fallback to another group.
OTHER returns `group:"manual"`, no recommendation, and otherwise-current agents for manual selection.
Off-duty/requester candidates use target enums `OFF_DUTY`/`IS_REQUESTER`; revoked, disabled, foreign-workspace and wrong-group agents are omitted.
`shiftRemainingMinutes` stays null: no scheduling is implemented.
Live HPDI and application OpenAPI describe only this implemented subset; remaining target APIs are still aspirational.
The HPDI route is contributed by the Service Desk assembly, not installed in core-only or Room Request-only assemblies.

## Workload and query budget

The new Core queue capability reuses the exact existing queue SQL/execution mapping, but selects the workspace's assigned execution tasks in bulk.
It checks Coordinator/`task:assign` itself. HPDI adapts Core types; application code uses only public HPDI contracts.
Read-only REPEATABLE_READ transactions keep DB queue pages/counts/projections/history/cursor on one snapshot.
Each page is at most 100 tasks, projected once per process using the existing provider's batch projection.
Other registered execution work across processes contributes to the assignee load. Only the exact considered task ID is excluded before batch projection.
For an already-assigned handle this removes self-workload bias; preview passes a review task ID, so every existing handle remains included.
This is a comparison load, not a replacement for the queue's actual total. `assignedToday` is never discounted for the considered ticket.
More than 10,000 active execution tasks fails explicitly with 409 rather than returning a partial ranking.

The existing reviewed-work projection supplies NOT_STARTED full ET, IN_PROGRESS max(ET-worked,10), and PENDING half remaining (rounded up).
No per-agent queue/duty/count queries: duty is read in bulk, assignment counts use one grouped query, cursor one query.
For N execution tasks, queue cost is two SQL statements per 100 tasks plus a batch projection per process present on that page.
Directory refreshes are a constant number per request, independent of agent count; real Keycloak pagination remains owned by R1.
No load benchmark or EXPLAIN/production capacity claim is made; 10,000 is a fail-closed implementation ceiling, not an SLA.

## Business day, history, ranking and concurrency

Configure `dx.assignment.business-zone` as an IANA timezone (Spring property); default is `Asia/Ho_Chi_Minh`.
The business day is local start-of-day inclusive to next local start-of-day exclusive, converted to UTC instants; not a fixed 24-hour window.
`assignedToday` counts actual assignment/claim transitions from `servicedesk_assignment_event`, including completed/reassigned work.
Reassigning the same task to the same subject is a no-op for counting/cursor. Reassigning to a different subject is another received assignment.
V15 backfills identifiable historical handle `task.assign` audit events, excluding no-op reassignment. It does not substitute current task counts.
Pre-V15 `task.claim` audit lacks taskKey: historical manual claims cannot be safely reconstructed by this application migration.
Thus the upgrade day's daily counts may omit pre-upgrade manual claims; after the next business-day boundary all new claims/assignments are captured.
Operators requiring an exact upgrade-day total must reconcile those historical claims before enabling recommendations; no invented data is inserted.

Ranking partitions eligible agents into successive bands anchored at the lowest remaining load: `[minimum, minimum+15)`.
Within a band sort by assignedToday, then subject circularly after the group's last committed assignee, with lexical wraparound.
This avoids the non-transitive pairwise comparator for loads 0, 14, 28: 28 is never allowed to join the first band through 14.
Candidate order is deterministic and independent of directory order. Recommendation is the first eligible ranked agent, except OTHER.

The cursor primary key is `(workspace,support_group)` and PostgreSQL upsert row locks serialize concurrent updates.
The default-no-op public `afterAssign` hook runs after Flowable claim/assign, under the existing locked task transaction.
Only reviewed Service Desk handle tasks insert an event/update the appropriate cursor. Audit failure rolls everything back.
Room Request, legacy workflows, review and requester-confirmation tasks do not touch these tables.
Polling never writes a cursor/event or advances workflow. Concurrent reads may recommend the same agent; there is deliberately no reservation or automatic dispatch.
Concurrent explicit coordinator assignments can choose any eligible agents; the cursor records the last serialized committed decision, not an override of it.

## Validation and evidence

Detailed machine-readable results are in `R2_ASSIGNMENT_EVIDENCE.json`; local raw logs are under `.local/task-execution-*` and `.local/r2-*`.
The historical Windows full run on f6283af reports 640 tests, zero failures, three OS-specific filesystem errors; it is NOT final-HEAD evidence or PASS.
Linux containers avoid Windows POSIX/symlink limitations. The runner excludes `backend/.local` so copied host permissions cannot poison private storage initialization.
The reviewed Linux full suite runs 645 tests, core-only 106, second-domain 33, each with zero failures/errors/skips.
ArchUnit runs in these suites. Source boundary gate and all 43 boundary mutation regressions pass.
Real PostgreSQL and Flowable integration covers preview/handle APIs, read-only polling, real projection load, history counters, override, revocation, off-duty,
wrong group, creator, empty candidates, workspace access, bounded bulk calls, concurrent assignment cursor updates, and migration upgrades from V14 and earlier.
HTTP responses validate against live schemas with negative mutations; existing execution/assessment HTTP contracts are rerun unchanged.
Keycloak/JWT validation is reported separately from mocked-directory integration; neither is represented as gateway E2E or a GitHub Actions run.
The real direct-HTTP smoke passes eight checks: off-duty, current support group, read-only polling, membership revocation,
assignment-time revalidation, disabled account exclusion, explicit coordinator assignment, and persisted daily workload/count.

Reproduce the database suites (substitute `core-only` or `second-domain`):

```powershell
./scripts/test-task-execution.ps1 -BuildImage maven:3.9.11-eclipse-temurin-21 -Profile full
python scripts/check-tier-boundaries.py
python scripts/test-tier-boundaries.py
```

Use Python with the existing acceptance OpenAPI dependencies for `scripts/test-servicedesk-execution-contract.py`, passing the runner's real HTTP evidence and `--assignment-evidence`.
After packaging the full backend JAR, `python scripts/acceptance/assignment-suggestion.py` runs the disposable real Keycloak/PostgreSQL/JWT smoke.
It imports only an isolated realm, generates temporary credentials, binds random loopback ports and removes its containers/network on exit.
No new runtime dependency is added. V11-V14 are not modified and their applied checksums remain intact.

## Limitations / disposition

Keycloak freshness is a validation-time snapshot, not a distributed transaction: revocation immediately after validation remains possible.
Suggestions are advisory and may be stale before assignment; the existing R1/R3 check is still authoritative at commit-time validation.
No new cursor reservation, user override, workflow state mutation or AI authority is introduced.
Pre-existing whole-spec OpenAPI errors are compared with the fetched develop baseline rather than repaired or waived.
Disposition: **BLOCKED** for internal merge until the upgrade-day historical-claim limitation has an accepted deployment/reconciliation decision and required hosted checks can actually run.
Baseline OpenAPI debt remains unchanged and separately reported; this review does not expand scope to repair it.
Keep the PR Draft, do not merge. Hosted CI status must be checked independently; local suite success does not certify GitHub Actions.

## Final merge-readiness review (2026-10-27)

The initial PR B incorrectly counted the handle being considered against its current assignee.
Example: A holds target T=90 minutes plus another task=20; B holds 60. The old totals (A=110, B=60) incorrectly recommended B for T.
For a fair reassignment comparison the other-work totals are A=20, B=60, so A wins even though A has received two assignments today versus B's one.
Only T is removed; A's other 20 minutes remain. A separate review preview still sees A=110/B=60 and recommends B for a NEW ticket.
Do not remove T's historical receipt from `assignedToday`: when other workloads tie, fewer real assignments today may still legitimately favor B.

The new regression was first run against f6283af production code and FAILED on the NOT_STARTED recommendation (expected A, got B).
After the fix it passes for NOT_STARTED, IN_PROGRESS and PENDING, while the actual queue still includes T and GET leaves events/cursor untouched.
Additional regressions exercise no-op reassignment, transfer to another agent, completed work, agent claim, local-day boundaries and workspace isolation.
The concurrency regression now holds a real PostgreSQL cursor row lock, observes two overlapping assignment transactions waiting,
verifies GET can read the old committed cursor without blocking/writing, then releases both and checks committed counts and circular recommendation.
An audit-trigger failure proves event, cursor and Flowable assignee all roll back atomically. Other workspace/support-group cursors remain unchanged.

The new V14-to-V15 fixture verifies changed handle assignments are backfilled, no-op/review/legacy/missing-assignee events are excluded,
timestamps and applied checksums/duty/audit rows are preserved, workspace isolation holds, and a second migrate is a no-op.
It also explicitly confirms the known omission of ambiguous pre-V15 `task.claim` audit records; V15 SQL is unchanged in this review.
This leaves an upgrade-day accuracy gap, not a regression hidden by a green test. Reconcile old claims or obtain approval to enable after the next business-day boundary.
PR #52 eligibility, duty, remaining-time formula, execution lifecycle, Room Request and legacy behavior remain unchanged and are covered by the full/isolated regressions.
Hosted checks on f6283af failed to start with GitHub's account-payment/spending-limit annotation. This is not a hosted test PASS or a demonstrated code failure.
