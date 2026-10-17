# Service Desk ticket foundation

Base: `6f312eb`. Scope: backend only, not completed H-P-D-I.

## Contract

Base: `/api/apps/servicedesk/v1/workspaces/{workspace}/tickets`.

| Method | Path | Result |
| --- | --- | --- |
| POST | base | 201 Ticket; body: clientRequestId UUID, title (1-200), description (1-4000) |
| GET | base | Page of Tickets; q (literal title/description substring, max 200), page, size (1-100) |
| GET | /{id} | Ticket with canonical process reference |
| GET | /{id}/progress | Core ProcessHistory; no second stored status |

Requester reads own tickets; agent/coordinator reads workspace tickets. All three
may create. Other roles and platform-admin have no implicit workspace access.
Creator and workspace are server-authorized, never supplied by the body. Unknown
fields fail. Hidden ticket: 404; missing workspace/role: 403; unauthenticated: 401.

clientRequestId is a submission UUID, distinct from X-Request-ID tracing. A repeat
for the same creator/workspace returns 409, even concurrently or with a changed
payload. Different creators/workspaces have independent submission namespaces.

Application process service-desk: handle (independent workspace agent, outcome
resolved) -> confirm (initiator, accepted ends; rework loops to handle). A creator
with the agent role cannot handle their own ticket. Coordinator needs agent role
to claim. Use existing Core claim/complete endpoints with expectedTaskKey.

Core history authorization is unchanged: before claim an unrelated agent can read
the ticket but progress returns 404. Use candidate inbox to discover work, claim,
then read history. Creator/coordinator/prior assignees retain Core history access.

WorkflowService.start, ticket insert and servicedesk.ticket.create audit share one
REQUIRED transaction/datasource. Any failure rolls back engine, scope, ticket and
audit. Database uniqueness controls concurrent duplicate submissions. Audit uses
IDs only, not title/description. Core never imports application types.

Generic Core start remains governed by ProcessPolicy: directly starting a process
does not create a ticket. Only the application endpoint creates linked tickets.

## Handoff and limits

FE consumes application OpenAPI and Core inbox/claim/complete/history with PKCE.
The process businessKey is the ticket UUID; Flowable is the progress authority.
AI bridge is deferred: later adapter authorizes ticket access, derives trusted
context, pins profiles server-side and permits manual handling during AI outage.
No UI, AI bridge, private attachments, assignment, SLA or cancellation in this PR.
No new dependencies or license grant. Production/security/release gates remain.

## Verification

From repository root on Windows, with Docker's Linux engine running:

```powershell
py -3.11 -m venv .local/acceptance-venv
& .local/acceptance-venv/Scripts/python.exe -m pip install -r scripts/acceptance/requirements.txt
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-core-acceptance.ps1 -Project acceptance-ticket-review-01 -Python .local/acceptance-venv/Scripts/python.exe
```

Choose a new project name for each full run. The runner refuses existing projects,
volumes or evidence directories. It retains confidential DB/file backups and stops
only its named projects. Do not delete shared volumes to make verification pass.
The existing Core acceptance CI also invokes ticket HTTP and restore checks.

The HTTP probe can run independently against the local synthetic deployment:

```powershell
& .local/acceptance-venv/Scripts/python.exe scripts/acceptance/servicedesk.py .local/servicedesk-http.json
& .local/acceptance-venv/Scripts/python.exe scripts/check-core-contract.py .local/servicedesk-http.json contracts/openapi/servicedesk.yaml
```

Only use synthetic demo data with these probes. Treat ticket text as untrusted
plain text in the frontend; do not render it as HTML. OpenAPI, API and process
policies are reviewable application decisions, not changes to Core authority.

### Recorded execution: 2026-10-17 (Asia/Saigon)

Final local run: `.local/acceptance-ticket-20261017-c/summary.json` and
`.local/ticket-acceptance-c.log`. Exit 0; all nine functional gates PASS.
Tested code commit: `0483c7e`; workingTreeDirty=true records documentation-only
README/CHANGELOG/handoff edits. The final documentation commit changes no runtime,
contract, infrastructure or test code. Raw evidence/backups remain ignored/private.

| Check | Actual result |
| --- | --- |
| PostgreSQL 16.6 clean Maven verify | 77 passed, 0 failures/errors/skipped; includes 10 ticket integration tests |
| OpenAPI | Core, Core target and Service Desk specifications PASS |
| Real Keycloak -> APISIX -> application | Create/detail/list, scope/role negatives, duplicate race, rework and confirmation PASS |
| Contract-validated JSON | 164 existing Core responses + 26 application responses + 6 restored application responses |
| Failure injection | Workflow failure before/after real start and audit failure roll back ticket/engine/scope/audit |
| Restart / storage inventory / logical restore | PASS; three restored tickets preserve content, owner, process reference and readable progress |
| Intelligence regression, unchanged sources | Foundation/history checks and 90 tests PASS on Linux Python 3.12.14, Unicode 15.0.0; not an exact Python 3.12.7 CI-pin reproduction |
| Contract negative check | Removing creator from a captured successful response is rejected |

The first attempt was interrupted by Docker disappearance; the second exposed a
cross-document validator scope error. Neither is counted as a successful full run.
The validator now uses the installed referencing registry, and the final full run
passes Core and application schemas, including restored records.

Human sign-off, actual PR-head CI, production security, browser E2E and AI bridge
remain NOT VERIFIED/not delivered by this local result. No public release or
license grant is made. Before merge reviewers must approve the documented
visibility, submission-conflict, coordinator/agent and confirmation/rework policies.
