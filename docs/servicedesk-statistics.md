# Service Desk statistics backend

Base: develop `f348beb` (PR #17). Backend only; dashboard UI and Intelligence
HTTP adapter belong to FE and AI/Data respectively.

## Requirement and existing capabilities

Official HaUI PDF, section 4.2 D (page 4): unified data, metadata/provenance,
search/filter/statistics, dashboard, REST or GraphQL. Core already supplies
PostgreSQL, metadata with source/version/rights/checksum, file links and audit;
Ticket Foundation supplies workspace/creator/createdAt, literal search and a
canonical Core process reference. This change adds dashboard-ready REST counts
and shared creation-time filters. It does not complete the Data UI or general
knowledge search. No new database, search engine, migration or dependency.

## Frontend contract

`GET /api/apps/servicedesk/v1/workspaces/{workspace}/tickets/statistics`

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
One SQL statement gives a consistent snapshot. `servicedesk_ticket` is the sole
ticket population, using identical scoped filters as list. Core `process_scope`
must match process ID, workspace, owner, business key and `service-desk` key.
Flowable 7.1.0 `act_hi_procinst` and `act_ru_task` are the state authority:

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
This query deliberately depends on the current Flowable schema/BPMN; rerun its
PostgreSQL and lifecycle tests on engine/process upgrades. It does not calculate
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
