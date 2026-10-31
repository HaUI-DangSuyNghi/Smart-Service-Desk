# Frontend handoff: Service Desk demo blockers

Verified 2026-10-28 (Asia/Saigon), source
`ef047a31abfd8e35ce9b6155e3a06c224262269d`, project `ssd-p0-20261028`.
Scope: wire existing APIs, not new backend features or altered business rules.
**Three missing primary-flow UI actions; one fresh-environment duty prerequisite.**

## What already works: do not rebuild or weaken permissions

- Real Keycloak authorization-code/PKCE S256 login works for requester1/2/3/4.
- Ticket list/detail/timeline, agent handle completion, requester confirmation on
  the ticket timeline, dashboard, triage result and AI-outage message work in browser.
- CORRECTION to the earlier recovery note: requester confirmation is NOT missing.
  On the ticket timeline, click the current confirmation's `Xu ly` action, choose
  accepted, confirm. It returned 204, persisted CLOSED/REQUESTER and updated statistics.
  Requester denial at /inbox is intentional, not a reason to remove its role gate.
- Room Request browser create -> coordinator claim -> approve -> requester readback
  works. Its Intelligence route currently returns 502 on the Service Desk-only
  Python profile; no profile/runtime changes made to disguise this limitation.

## Shared contract and security rules

Browser origin http://localhost:5173 proxies `/api`; gateway http://localhost:9080.
Paths below are full HTTP paths. Existing apiClient base is `/api`, so its method
arguments omit that initial `/api` (do not produce `/api/api/...`).
All operations require actual OIDC bearer tokens; do not put credentials in FE env,
logs, fixtures or localStorage. Obtain workspace/subject from existing auth context.
Backend remains authoritative for fresh membership, role, state and concurrency.

Contracts: `contracts/openapi/servicedesk.yaml` (NewTicket, ReviewDecision, Ticket),
`contracts/hpdi/openapi.yaml` (QueueItem, DutyStatus, AssignmentSuggestion),
`contracts/intelligence/v1/triage-result.schema.json`.
Examples below are request bodies or explicitly partial response projections;
use referenced schemas for full types, not a reduced hand-written response contract.

## FE-P0-01: create Service Desk ticket

Missing: TicketListPage has no create action; generic request dialog cannot start
Service Desk (catalog canStart=false by design). Do not change that catalog flag or
use generic process start: app creation atomically creates ticket/process/audit.

- Actor: current workspace requester/agent/coordinator; demo requester1/lab-a. Backend
  app creation checks these roles; do not confuse it with generic process-start
  permission. Seeded accounts also carry request:create for the generic request UI.
- Before: authenticated workspace member; no client-supplied creator/owner/role.
- Endpoint: `POST /api/apps/servicedesk/v1/workspaces/{workspace}/tickets`.
- Required JSON: clientRequestId UUID, nonblank title <=200, nonblank description
  <=4000. Additional fields rejected; title forbids control characters, description
  permits tab/CR/LF. Generate submission UUID once per intended create; retain on retry.

```json
{"clientRequestId":"<new UUID>","title":"Synthetic hardware issue","description":"Computer cannot start."}
```

Success 201: full Ticket. Exercised projection:
`{id, workspace, creator, clientRequestId, title, description, createdAt,
process:{processInstanceId,processKey:"service-desk-reviewed",businessKey,workspace},
state:"AWAITING_REVIEW",taskId,assignee:null,workState:null,classification:null}`.
Navigate to `/servicedesk/workspaces/{workspace}/tickets/{id}` and invalidate list,
detail, request/process and statistics queries. Do not create another process.
Errors: 400 invalid input, 401 login, 403 permission, 404 workspace, 409 duplicate.
Disable duplicate submit; show server Problem.code/requestId without logging tokens.

Write scope: `frontend/src/apps/servicedesk/pages/TicketListPage.tsx`, app create
dialog, `frontend/src/apps/servicedesk/services/ticketService.ts`, related hooks.
Acceptance: real requester form -> 201 -> visible persisted review ticket; refresh
does not duplicate; duplicate clientRequestId 409 and validation/RBAC errors surfaced.

## FE-P0-02: coordinator standard review + atomic assignment

Missing: standard service-desk-reviewed ticket has no category/priority/assignee/
reason review form. Existing IntakePanel handles different, optional intake states;
do not enable intake/R7 flags just to obtain a form. Generic approved outcome alone
does not replace the app review decision and must not bypass its validation.

- Actor: independent current coordinator, task:assign AND task:handle; demo coordinator1.
- Before: active review, Ticket.state=AWAITING_REVIEW or rework review; current actor
  is not creator. Re-fetch state on conflict rather than replaying an old review.
- Endpoint: `POST /api/apps/servicedesk/v1/workspaces/{workspace}/tickets/{id}/review`.
- Required: category HARDWARE/NETWORK/SOFTWARE/ACCOUNT/OTHER; priority
  LOW/MEDIUM/HIGH/CRITICAL; explicit assignee subject or null; nonblank reason <=1000.
- Optional: estimateMinutes integer 1..10080 (omit/null freezes backend default),
  impact I1..I4, urgency U1..U4. Intake CAS fields expectedContextRevision and
  expectedIntakeVersion are for enrolled intake only, not this default demo flow.

```json
{"category":"HARDWARE","priority":"HIGH","assignee":"<agent1 subject>","reason":"Independent coordinator decision"}
```

Do not send username, group, arbitrary SLA or AI-authority fields. Tested subject:
`b5a318c4-d38c-4acd-ab78-cc3bd7f1efc1` (local seeded agent1, not a production constant).
Non-null assignee must be independent current agent, eligible support-group member
and on duty. Category groups: HARDWARE hardware, NETWORK network, SOFTWARE software,
ACCOUNT sysadmin; OTHER manual. Read fresh candidates; never infer eligibility from
workspace membership alone. Null means approved but unclaimed, not auto-dispatch.

Success 200 is full Ticket, NOT a separate assignment DTO. Tested projection:
`{state:"ASSIGNED",taskId:"<new handle ID>",assignee:"<subject>",
workState:"NOT_STARTED",estimate:{minutes:90,basis:"DEFAULT"},classification:{...}}`.
Use returned handle ID, not the previous review ID. Review+assign are atomic here;
do not add a second assignment call to finish the basic demo.

Optional read-only preview:
`GET /api/apps/servicedesk/v1/workspaces/{workspace}/tickets/{id}/assignment-suggestion?category=HARDWARE&priority=HIGH`.
200 AssignmentSuggestion: taskId (REVIEW id), group, estimateMinutes, candidates,
reason and optional/null recommended subject. Candidate includes subject,
displayName, onDuty, loadMinutes, assignedToday, recommended and exclusions. Preview
does not reserve/assign; null recommendation is not permission to invent an agent.

Errors: 400 invalid decision/group eligibility, 401 login, 403 non-coordinator/
self-review, 404 hidden/missing ticket, 409 stale/non-review/off-duty conflict.
Observed default seed rejected agent1 with 400 `Current support group agent membership
required`; fixed only in disposable Keycloak seed, not by bypassing eligibility.
OpenAPI's older prose says no duty/SLA; live source enforces eligibility/duty and
returns SLA data. Implement actual schema/observed behavior; do not rely on that sentence.

Write scope: app ticket detail/review panel, ticketService and hooks. Extend Ticket
types with live state/taskId/assignee/workState/estimate/classification/confirmation
fields actually consumed. Existing service interface only declares identity/content.
Acceptance: coordinator form -> assigned handle in agent1 inbox; requester1/agent1 review
denied; invalid agent visible as error; state conflict refreshes, no duplicate review.

## FE-P0-03: agent explicitly starts assigned work

Missing: generic inbox/timeline offers claim/complete/assign but no START action.
Current handle completion UI already passes once START was performed via API.

- Actor: current independent assignee with task:handle; demo agent1.
- Before: active supported handle, server workState=NOT_STARTED; backend enforces
  candidate membership/active-work limits. Do not equate assigned with in progress.
- Endpoint: `POST /api/hpdi/v1/workspaces/{workspace}/tasks/{taskId}/start`, no body.
- 200 QueueItem: id, processInstanceId, key=handle, assignee, workState=IN_PROGRESS,
  priority, estimate, remaining/worked minutes, SLA, etc. Ticket.state then IN_PROGRESS.
- Errors: 401/403/404; 409 wrong execution state, unsupported task, suspension or
  active-work limit. Do not silently auto-start or retry a conflict as success.

Write scope: existing task service/actions and inbox/timeline action rendering,
using returned server state. Add a distinct start button, then enable existing
completion for the started handle. Invalidate inbox/timeline/ticket/statistics.
Acceptance: assigned NOT_STARTED -> one START 200 -> server IN_PROGRESS -> existing
complete 204 -> requester confirmation. Wrong actor and stale START must fail safely.

## Seed prerequisite: duty control, not a fourth blocker on this preserved demo

No FE duty endpoint wiring located. Current agent1 is already persisted on duty;
retain it. Fresh environments require own-agent
`PUT /api/hpdi/v1/workspaces/{workspace}/duty/me` with `{"onDuty":true}`.
Optional note <=500; 200 DutyStatus `{subject,onDuty,note,updatedAt,effectiveDuty}`.
Agent with current membership/task:handle changes only their own duty; coordinators
cannot toggle somebody else's. 400/401/403/503 as documented. Existing demo support
membership `/support-groups/lab-a/hardware` is also required; admin seed, not FE bypass.
If providing self-service fresh-demo setup, wire this existing endpoint; no backend
feature required. Roster/group administration is not added to this P0 handoff.

## Existing completion/confirmation contracts and final browser acceptance

Both use `POST /api/hpdi/v1/workspaces/{workspace}/tasks/{taskId}/complete`:

```json
{"expectedTaskKey":"handle","outcome":"resolved"}
```

```json
{"expectedTaskKey":"confirm","outcome":"accepted"}
```

Optional comment string; success 204 has NO JSON body. Agent completes current
IN_PROGRESS handle; requester completes their assigned current confirmation via
ticket timeline, using confirmGeneration/current task ID. Confirm -> CLOSED,
closedBy=REQUESTER, process ended. Other actor 403; replay/stale task 409 observed.
Rework outcome exists but is not necessary for this minimal happy-path delivery.

After three blockers are wired: run real browser requester1 create -> coordinator1 review/assign
-> agent1 START/complete -> requester1 timeline confirm -> coordinator1 statistics, with no
API substitutions/mocks. Keep observed triage success/outage UI, and rerun restart
and RBAC checks. Browser E2E remains NOT READY until that exact FE candidate passes.
Do not replace scoped cache invalidation with optimistic workflow authority.

Evidence (private, redact before sharing): `D:/ssd-demo-recovery-20261028/`:
create-dialog.png, coordinator-review-ui.png, agent-inbox-before-start.png,
agent-completed.png, browser-triage-success.png, browser-ai-outage.png,
`confirm-ui/result.json`, `confirm-ui/requester-confirmed.png`,
`room/result.json`, all-contract-validation.log. Earlier missing-confirm claim
in demo-checks.json is historical and superseded by the actual confirm-ui test.
