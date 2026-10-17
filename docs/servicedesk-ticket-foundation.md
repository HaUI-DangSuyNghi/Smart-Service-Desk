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

Implementation/execution evidence will be recorded before human review.
