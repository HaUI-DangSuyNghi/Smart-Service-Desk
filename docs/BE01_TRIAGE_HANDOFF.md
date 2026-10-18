# BE-01 Service Desk triage bridge

## Scope and approval

Base: `e39356cc44ef50421a9b81199756ec9784fe161a` (PRs #17/#18 merged).
Branch/worktree: `feat/servicedesk-triage-bridge`, `D:/Smart-Service-Desk-triage-bridge`.
G0-A: user-attested BE + AI technical approval recorded in
`Smart_Service_Desk_BE_AI_Contract_v0.1.md`, revision `BE-AI-TECH-0.1`.
G0-B: PENDING Process Owner approval; the `service-desk@0.2.0` profile remains PROPOSED.
No Python, shared logical schema, frontend or generic Core implementation changes.

## Boundary

`TicketTriageController -> TicketTriageService -> IntelligenceTriageClient -> HttpIntelligenceTriageClient`.
The service calls existing `TicketService.get` first, using persisted title/description.
That authorized read transaction ends before HTTP I/O. No recommendation persistence,
Ticket mutation, workflow transition or automatic retry occurs.
The shared Draft 2020-12 JSON schemas are packaged directly from `contracts/intelligence/v1`
by Maven; networknt 1.5.9 validates them, not a hand-maintained subset validator.
Java additionally checks correlation, pinned profile and literal original-input evidence.

## FE contract

`POST /api/apps/servicedesk/v1/workspaces/{workspace}/tickets/{id}/triage`
with the user's bearer JWT and **no body**. Any bytes, including `{}` or whitespace, return 400.
No client-selected profile, subject, roles, workspace context or ticket text is accepted.
The 200 response is the unchanged logical triage envelope, not a new FE wrapper.

```javascript
const response = await fetch(`/api/apps/servicedesk/v1/workspaces/${workspace}/tickets/${id}/triage`, {
  method: 'POST', headers: { Authorization: `Bearer ${accessToken}` }
});
const payload = await response.json();
if (!response.ok) {
  // Keep manual workflow controls independent; 502/503/504 are not a JWT refresh signal.
} else if (payload.status === 'DEGRADED') {
  // result/explanation/runtime are null, evidence is [], needsReview is true.
} else {
  // Display result, literal INPUT evidence, explanation and needsReview. Never auto-complete tasks.
}
```

| HTTP | Meaning / problem code |
| --- | --- |
| 200 | Valid SUCCESS or logical DEGRADED, including SUCCESS requiring human review |
| 400/401/403/404 | Existing validation/auth/Ticket visibility policy, before AI invocation |
| 502 | `INTELLIGENCE_CONTRACT_FAILED` or `INTELLIGENCE_INTEGRATION_FAILED` |
| 503 | `INTELLIGENCE_UNAVAILABLE`: disabled/unconfigured endpoint, connection failure, upstream 429/5xx |
| 504 | `INTELLIGENCE_TIMEOUT`: entire HTTP operation including body exceeds deadline |

Errors use the Backend ProblemDetail shape with `code` and request correlation when available.
Internal 401/403 are 502, never end-user authentication responses or successful fallback.
The problem `requestId` is the public HTTP trace; the logical envelope `requestId` is a separately
generated server correlation for the AI invocation.

## Python handoff / configuration

Configure `INTELLIGENCE_TRIAGE_BASE_URL` (origin only, e.g. `http://intelligence:8000` on a private
Compose network) and `INTELLIGENCE_TRIAGE_TOKEN` via environment or secret injection. No default token.
The target is fixed to `POST /internal/intelligence/v1/triage`, using
`Authorization: Bearer <internal-service-token>`, never the user JWT.
Do not expose this service through public APISIX routes. HTTP is for the trusted demo network;
cross-host deployment requires HTTPS. Redirects are rejected, including same-host redirects.
Connect deadline 1s; overall HTTP deadline 4s; UTF-8 request cap 32 KiB; response cap 256 KiB.
No sensitive content, credentials or raw upstream errors are logged or returned.

Python must authenticate the service before constructing TrustedContext and invoking existing
AI-02 with `profile_version='0.2.0'`. The wire profile remains `service-desk`, contract `0.1.0`;
SUCCESS must return `runtime.profileVersion='service-desk@0.2.0'`.

## Verification and real E2E

Default tests include deterministic fake-port tests, a real Java HTTP server stub for transport
negatives (not Python), shared-schema validation, and PostgreSQL application tests using a mocked
AI port. PostgreSQL tests assert no active transaction inside the AI call, visibility, no-body
requests, unchanged state and manual task handling after AI failure.

Local Docker daemon was unavailable during initial implementation. PostgreSQL/full stack results
must be distinguished from local unit results and from PR CI evidence in the delivery report.
The dedicated Service Desk triage workflow runs all default Java tests on disposable PostgreSQL.

REAL PYTHON E2E: BLOCKED until the AI teammate supplies the authenticated HTTP wrapper and deployment.
The opt-in Java test uses a real Python connection, persisted Ticket and actual HTTP adapter;
its MockMvc user JWT fixture is not real Keycloak login or browser E2E.
With a disposable `file_storage` PostgreSQL database/user `workflow`, set:
`FILE_TEST_JDBC_URL`, `FILE_TEST_DB_PASSWORD`, `INTELLIGENCE_TRIAGE_BASE_URL`, `INTELLIGENCE_TRIAGE_TOKEN`.
Run `mvn -B -f backend/pom.xml -Dtest=TicketTriagePythonE2E test`.
Never point this write-capable test at production or another worktree's database.
Run real timeout, wrong-service-token and manual-fallback rehearsals separately before declaring G2 passed.

Build container from repository root: `docker build -f backend/Dockerfile .` or `docker compose build core`.
After Python becomes available, run the opt-in test, then real Keycloak/gateway and browser rehearsals;
do not reinterpret the Java stub tests as G2/browser PASS.
