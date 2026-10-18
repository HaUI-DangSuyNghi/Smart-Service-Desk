# Service Desk statistics backend

Base: develop `f348beb` (PR #17). Backend only; dashboard UI and Intelligence
HTTP adapter belong to FE and AI/Data respectively.

## Requirement and existing capabilities

Official HaUI PDF, section 4.2 D (page 4): unified data, metadata/provenance,
search/filter/statistics, dashboard, REST or GraphQL. Core already supplies
PostgreSQL, metadata with source/version/rights/checksum, file links and audit;
Ticket Foundation supplies workspace/creator/createdAt, literal search and a
canonical H-P-D-I process handle. This change adds dashboard-ready REST counts
and shared creation-time filters. It does not complete the Data UI or general
knowledge search. No new database, search engine, migration or dependency.

## Frontend contract

`GET /api/apps/servicedesk/v1/workspaces/{workspace}/tickets/statistics`

No request body; filters are query parameters.

Authorization: bearer JWT via the existing Gateway. Requester sees own tickets;
agent/coordinator sees workspace tickets. A requester with either elevated role
gets workspace visibility, matching Ticket List. No platform-admin bypass.
The workspace is mandatory: call separately for each authorized workspace. No
global totals or membership enumeration. Aggregate ticket visibility does not
grant permission to read another person's process history.

Optional filters (also available on `GET .../tickets`):

- `q`: same literal case-insensitive title/description search, max 200 characters,
  no control characters; `%` and `_` are literal, not wildcards.
- `from`, `to`: RFC3339 timezone-bearing instants, inclusive/exclusive boundaries
  on **ticket createdAt**, not completion time. Omitted bounds are unbounded;
  when both supplied, from < to. Years 0001-9999 and microsecond precision only.
  Offsets normalize to the same instant. Use URL encoding for `+` offsets.

```sh
curl -G "$GATEWAY/api/apps/servicedesk/v1/workspaces/lab-a/tickets/statistics" \
  -H "Authorization: Bearer $TOKEN" \
  --data-urlencode 'q=network' \
  --data-urlencode 'from=2026-10-01T00:00:00+07:00' \
  --data-urlencode 'to=2026-11-01T00:00:00+07:00'
```

```json
{"workspace":"lab-a","visibility":"own","total":8,"awaitingHandling":3,"awaitingConfirmation":2,"completed":2,"unclassified":1}
```

## Metric provenance and accuracy boundary

Counts are application-owned read-time aggregation, not a stored status column.
A REPEATABLE_READ transaction gives a consistent snapshot. `servicedesk_ticket`
is the sole ticket population, using identical scoped filters as list. The app reads
only its own process links in bounded keyset batches, then uses H-P-D-I
ProcessObservations. It matches process ID, workspace, owner, business key and
`service-desk` key. Core alone resolves technical engine state; the app classifies
the public snapshot into these ticket-specific metrics:

| Metric | Exact meaning |
| --- | --- |
| total | All matching visible persisted tickets, including unclassified |
| awaitingHandling | Unended process, exactly one unsuspended `handle` task, claimed or unclaimed |
| awaitingConfirmation | Unended process, exactly one unsuspended `confirm` task |
| completed | Historic normal `end` event, no delete reason, no runtime tasks |
| unclassified | Everything else: missing/mismatched scope/history, suspended/deleted process, unexpected or multiple tasks |

The four state counts partition total. Rework returns a ticket to handling;
claim alone does not change its bucket. Standalone Core processes are not tickets.
Do not label unclassified as completed; display a data-quality warning instead.
Only the Core observation adapter depends on the engine schema. Ticket metric
semantics depend on the app-owned BPMN; rerun PostgreSQL and lifecycle tests on
engine/process upgrades. It does not calculate
SLA, completion throughput, historical status-at-date, AI labels or daily series.
Use adjacent creation windows for period counts; these show current states of
that creation cohort, not events that happened during the window.

Existing workspace/creator/createdAt indexes scope the ticket scan; task lookup
uses Flowable's process-instance index. No N+1 API calls or task-join fan-out.
Large unbounded cohorts still require a scan: add measured query-plan evidence
before considering materialized aggregates; no performance claim is made here.

Empty selection: HTTP 200, all counts zero. Invalid filters: 400; unauthenticated:
401; missing/unknown workspace role: 403. Errors use existing Core problem JSON.
Statistics is not paginated. Use existing page/size for the drill-down Ticket List
with identical q/from/to; separately issued calls can differ if data changes.

## Verification and review

Run `scripts/test-file.ps1` for real PostgreSQL integration and Core regression.
Run `scripts/test-core-acceptance.ps1 -Project acceptance-statistics-review-01
-Python <python-with-scripts/acceptance/requirements.txt>` for real Gateway HTTP,
OpenAPI, restart and backup/restore. Use an unused project name and synthetic data.
Evidence directories/backups are ignored/private. Human approval and PR CI are
separate gates. No production readiness, full H-P-D-I or AI integration claim.

### Recorded execution: 2026-10-17

Final run: `.local/acceptance-statistics-20261017-d/summary.json`, full log
`.local/statistics-acceptance-d.log`. Exit 0; all nine functional gates PASS.
Tested source: `5b11106`, workingTreeDirty=false. The subsequent evidence-only
documentation commit changes no backend, contract, infrastructure or test code.

- PostgreSQL 16.6: 83 Java tests, zero failures/errors/skips, including 16 ticket
  integration tests and real handle/claim/confirm/rework statistics assertions.
- Three OpenAPI specs and `python scripts/test-core-contract.py` PASS; the latter
  checks concrete-route precedence, UUID detail and missing-field rejection.
- 249 actual JSON responses validated: 164 Core, 73 application (25 statistics),
  12 restored application (3 statistics). Excludes the duplicate verify capture.
- Restart, storage inventory and logical backup/restore PASS. Three tickets and
  their current-state counts survive restore. Exact repository SQL was also
  EXPLAIN ANALYZE checked on three synthetic tickets; not a load benchmark.

This Docker Desktop/Compose 5 host used `DOCKER_CONTEXT=desktop-linux` explicitly.
Earlier attempts are retained, not counted as full passes: initial redirected
PowerShell test interrupted, then rerun passed; acceptance a hit Docker Hub TLS
timeout, b hit builder/context selection, c passed HTTP but exposed validator
route ambiguity. The corrected validator passes c's captured responses and the
entire final d run. Raw evidence/backups remain ignored/private.
PR-head CI, human sign-off and production security remain NOT VERIFIED.
