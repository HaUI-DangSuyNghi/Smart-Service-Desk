# Coordinator review and Service Desk execution HTTP API

Date: 2026-10-27 (Asia/Saigon).
Branch: `feat/servicedesk-review-execution-api`.
Baseline: `develop` at `bb24425f2faa7a7e9ab6e13d8a97f55d2aa10844`.

PR #48 was verified through GitHub as merged at `2026-10-27T03:25:55Z` before
switching to develop, fast-forwarding and creating this feature branch. No merge
was performed by the agent.

Status: implemented and locally verified HTTP subset, usable by FE against this
backend. **M1 remains PARTIAL; the entire target API/R2-R12 is not implemented.**
Hosted CI and human review remain separate release gates.

## Implemented behavior and transaction boundaries

- New tickets start `service-desk-reviewed`: `review -> handle -> confirm`.
  Requester rework goes back to `review`, not directly to an unreviewed agent task.
- Coordinator review requires the coordinator role, current `task:assign` and
  `task:handle`. It validates category/priority/ET, persists a frozen decision,
  completes review, sets native priority and assigns the new handle task.
- A ticket row lock serializes competing reviews. The exact active review task ID
  is recorded; the Core owner callback rejects generic completion without that
  decision. A previous review cannot authorize a later rework review.
- Review, assignment, default/override history, Flowable changes and safe-reference
  audit share one transaction. Invalid assignment or audit failure rolls back all
  of them. Arbitrary text is not added to the shared activity journal.
- Only `service-desk-reviewed/handle` opts into WorkExecution. Neither `review` nor
  `confirm` is an execution task. Coordinator-only membership is not agent authority.
  The request creator cannot handle or be assigned their own ticket.
- Foundation still owns START/PAUSE/RESUME, active-work advisory locks, task-local
  intervals, authorization, reassignment checks and transactional audit.
- On handle completion, the owning application accumulates the final closed
  interval total in the ticket transaction. Completed rounds survive rework;
  retries/audit rollback do not double-count them.

## ET and queue semantics

The exact initial table from `docs/ticket.md` is used (minutes of active work):

| Category | LOW | MEDIUM | HIGH | CRITICAL |
| --- | ---: | ---: | ---: | ---: |
| HARDWARE | 45 | 60 | 90 | 120 |
| NETWORK | 30 | 45 | 60 | 90 |
| SOFTWARE | 30 | 30 | 45 | 60 |
| ACCOUNT | 15 | 15 | 20 | 30 |
| OTHER | 30 | 45 | 60 | 90 |

Omit/null `estimateMinutes` to freeze DEFAULT. An integer 1..10080 freezes a
COORDINATOR override. The default and each override are appended to
`servicedesk_estimate_history`, with review-task ID, category, priority, assignee,
actor, reason and time. A later review appends history rather than rewriting it.
Reason is always required, a deliberately stricter review input than conditional
AI-difference inference. Impact/urgency are optional explicit coordinator values.
There is no median, AI effort sizing, estimate-change request API or admin table
configuration API in this PR.

`workedMinutes` floors accumulated milliseconds, including prior completed rounds.
Remaining load is ET for NOT_STARTED; otherwise max(10, ET - workedMinutes).
PENDING contributes half of that remainder, rounded up to an integer minute.
Queue `loadMinutes` covers the entire assigned queue, not just the current page.
The HTTP queue uses REPEATABLE_READ while paging through Foundation for that sum.
Application projection is batched (one application query per 100 execution items),
including prior review-round counts from estimate history; no per-task workflow
timeline is read. Requested-page projections are reused from those batches.
For N assigned tasks, the Service Desk path makes 2 + 3 * ceil(N/100) queue/projection
SQL calls (4 for an empty queue), excluding identity/permission lookups. Memory is
bounded by a batch plus the requested page. Exact total load still scans O(N)
execution rows; this is not a constant-time workload projection or advanced R2.
Order is IN_PROGRESS first, native priority descending (25/50/75/100), native due
date null-last, creation and ID. No aging, SLA or expected-start values are fabricated.

Null assignee means approved but unclaimed; it is not in an agent queue until an
eligible independent agent claims it or a coordinator assigns it through the
existing HPDI task API. No automatic next-shift dispatch exists yet. The actual
candidate role remains `agent`; category-specific team routing and on-duty
eligibility are explicitly pending, not represented as completed R1/R3 behavior.

## Promoted HTTP subset

Base URL is the existing gateway (for example `http://localhost:9080`). Every
request carries `Authorization: Bearer <JWT>`; subject/workspace/roles come from
the authenticated identity, never the JSON body.

| Method/path | Behavior |
| --- | --- |
| POST `/api/apps/servicedesk/v1/workspaces/{workspace}/tickets` | Create; new ticket is AWAITING_REVIEW |
| GET `/api/apps/servicedesk/v1/workspaces/{workspace}/tickets?state=AWAITING_REVIEW` | Coordinator review queue, q/from/to/page/size; optional sort=createdAt or priority |
| POST `/api/apps/servicedesk/v1/workspaces/{workspace}/tickets/{id}/review` | Atomic review, estimate and assignment |
| GET `/api/apps/servicedesk/v1/workspaces/{workspace}/tickets/{id}` | Ticket with review/estimate/work state for the new flow |
| GET `/api/hpdi/v1/workspaces/{workspace}/queue` | Own agent queue; another assignee needs coordinator + task:assign |
| POST `/api/hpdi/v1/workspaces/{workspace}/tasks/{id}/start` | NOT_STARTED -> IN_PROGRESS |
| POST `/api/hpdi/v1/workspaces/{workspace}/tasks/{id}/pause` | IN_PROGRESS -> PENDING; reason required, OTHER requires note |
| POST `/api/hpdi/v1/workspaces/{workspace}/tasks/{id}/resume` | PENDING -> IN_PROGRESS |
| POST `/api/hpdi/v1/workspaces/{workspace}/tasks/{id}/complete` | Existing completion endpoint, also used for requester confirmation |

Only AWAITING_REVIEW ticket-state filtering is implemented. Unsupported state/sort
values return 400. Existing unfiltered search remains backward-compatible and
returns the original ticket rows. Review queue ordering uses the last reviewed
priority on rework; first-review tickets have no invented pre-review priority.

The wire execution field is `workState`, as in the target contract. Ticket `state`
uses AWAITING_REVIEW, ASSIGNED, IN_PROGRESS, PENDING, AWAITING_CONFIRMATION, REOPENED,
CLOSED or UNCLASSIFIED. Rework is REOPENED until the new handle task starts,
including while awaiting its fresh coordinator review. The AWAITING_REVIEW search
filter selects the review stage, so it includes REOPENED tickets needing review.
Legacy ticket GET retains its original shape.
HPDI owns the queue DTO; no Core or Flowable internal DTO is serialized to FE.

## One real FE flow

1. Requester POSTs `/api/apps/servicedesk/v1/workspaces/lab-a/tickets`:
   `{"clientRequestId":"<new-uuid>","title":"Printer will not start","description":"Lab printer has no power"}`.
   Retain the ticket ID. Response state is AWAITING_REVIEW; no agent work is queued.
2. Coordinator GETs `/tickets?state=AWAITING_REVIEW&page=0&size=20` under the same
   application/workspace prefix, then POSTs `/{ticketId}/review`:
   `{"category":"HARDWARE","priority":"HIGH","assignee":"agent-subject","reason":"Checked power failure"}`.
   ET is frozen at 90, basis DEFAULT. To override, add `"estimateMinutes":120`.
   Response includes taskId, assignee, classification, estimate and workState.
3. Agent GETs `/api/hpdi/v1/workspaces/lab-a/queue?page=0&size=20`; use the item's
   task ID, not ticket ID, for task endpoints. The item is NOT_STARTED.
4. Agent POSTs `/api/hpdi/v1/workspaces/lab-a/tasks/{taskId}/start`, without a body.
   Response workState is IN_PROGRESS; the active interval begins.
5. Agent POSTs `.../{taskId}/pause` with
   `{"reason":"WAITING_PARTS","note":"Waiting for replacement supply"}`.
   Response workState is PENDING; elapsed active time stops accumulating.
6. Agent POSTs `.../{taskId}/resume`, without a body. Response is IN_PROGRESS;
   a new interval begins without counting the waiting period.
7. Agent POSTs `.../{taskId}/complete` with
   `{"expectedTaskKey":"handle","outcome":"resolved","comment":"Power supply replaced"}`.
   Response is 204; final elapsed work is committed and the ticket awaits confirmation.
8. Requester GETs `.../tickets/{ticketId}/progress` to obtain the new confirm task
   ID, then POSTs its HPDI `/complete` with
   `{"expectedTaskKey":"confirm","outcome":"accepted"}`. Response is 204 and
   ticket GET reports CLOSED. `"rework"` instead starts a fresh coordinator review;
   previous review does not bypass that gate, and prior worked time is retained.

Duplicate/invalid transitions return 409. Wrong actor/role is rejected; a member
querying a task in another workspace gets 404 without revealing the foreign task.
An expired/invalid/missing JWT is rejected by the existing resource-server filter.

## Migration and old process instances

V8 adds application-owned review/history tables and ticket columns. Old rows get
`process_key=service-desk`, `awaiting_review=false`, and zero new accumulated work.
No existing BPMN or applied migration is edited. The original `service-desk` key
and resource stay registered with their old handle/confirm behavior and remain
outside execution queue opt-in. New creation uses the distinct reviewed key.
Old review requests return 409; old instances finish via existing claim/complete/
confirm APIs. No instance migration, cancellation or deletion is performed.
Core-only/second-domain builds exclude the Service Desk V8 migration.
V9 adds only a (ticket_id, review_task_id) history index for batched projection;
it does not change V8 checksums or existing instance data. Those builds exclude V9 too.

Rolling back after new reviewed instances exist requires retaining their BPMN,
registration, V8 data and review guard until they drain, or an explicitly reviewed
migration plan. Do not delete V8 or deploy the old-only binary over active reviewed
instances. Removing an applied Flyway migration is not a supported rollback.

## Verification and evidence

Actual PostgreSQL 16.6 / Flowable; Linux Java 17.0.15 and Maven 3.9.9:

```powershell
./scripts/test-task-execution.ps1 -BuildImage dx-core-build:0.1.0 -Profile full
./scripts/test-task-execution.ps1 -BuildImage dx-core-build:0.1.0 -Profile core-only
./scripts/test-task-execution.ps1 -BuildImage dx-core-build:0.1.0 -Profile second-domain
python scripts/check-tier-boundaries.py
python scripts/test-tier-boundaries.py
python scripts/test-core-contract.py
python scripts/test-servicedesk-execution-contract.py <latest-full-run>/servicedesk-http-evidence.json
python -m openapi_spec_validator contracts/openapi/servicedesk.yaml
python -m openapi_spec_validator contracts/openapi/servicedesk-target.yaml
```

Final-source full backend: 495 PASS; Core-only: 103 PASS; supported second-domain:
30 PASS. Each run has 0 FAIL/ERROR/SKIP; profile counts overlap, not unique totals.
ArchUnit runs in full and supported second-domain. Source boundaries PASS; 39
boundary mutation checks PASS; core contract checker PASS. The real MockMvc
responses are exported by the full runner and validated against promoted schemas,
with negative mutations; 14 positive/negative request/response checks PASS.
Service Desk live and target specifications validate successfully.
Signed RS256/JWKS tests also verify F04 for both process keys and denial of
review/queue/execution to a real signed requester. A deterministic concurrent
review regression verifies that review-queue filtering and item projection share
one snapshot, even when another coordinator commits a review between those reads.

Evidence stays ignored under `.local/task-execution-<profile>/` (summary, Maven
log, per-run JUnit XML and exported HTTP responses) and `.local/review-api/`
(source/mutation/contract/validator logs and baseline comparison). Earlier failed
iterations are not used as acceptance of the final source.

Known baseline blockers remain separate: the malformed HPDI KnowledgePage schema
and alerts-target `cho badge` property. The exact malformed nodes are unchanged
from merged develop, and both baseline/current whole specifications fail there.
Only the promoted HPDI subset is validated successfully; this is not a claim that
whole HPDI validation is green. No unrelated API repair is bundled.

## Remaining business scope and risks

| Rule | Still pending |
| --- | --- |
| R1 | Category-specific group routing/configuration; current candidate role is agent |
| R2 | Median/effort-size estimates, re-estimate approval API, least-load assignment recommendation and tie-breaking |
| R3 | On-duty persistence, shift eligibility and automatic next-shift dispatch |
| R4 | Shift-aware expected start, wider ticket search filters and queue aging policy |
| R5 | SLA clocks/projection, escalation and deadline calculation |
| R6 | Return-work API and estimate-adjustment request workflow |
| R7-R8 | Urgent timers/auto-dispatch and generated alerts |
| R9 | Public classification correction workflow and persisted AI/human comparison |
| R10 | Related-ticket merging and settings/release approval |
| R11 | Auto-close, reopen notifications and escalation; explicit confirm/rework works |
| R12 | Extended status/SLA statistics; legacy handling bucket includes review and handle |

Queue load currently scans all assigned work in bounded batches and projects each
item; large-workspace query/index/performance acceptance is still pending. Core
native SQL remains coupled to Flowable's schema. No FE/Python AI source is changed.
Neither this PR nor Foundation should be advertised as completion of all M1.
