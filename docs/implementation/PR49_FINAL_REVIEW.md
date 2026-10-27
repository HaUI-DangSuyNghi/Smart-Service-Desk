# PR #49 final review and remediation

Date: 2026-10-27. Reviewed base: `6aecc207cc4d44667d81df6b56612e7278d52e82`.
Develop baseline: `bb24425f2faa7a7e9ab6e13d8a97f55d2aa10844` (merged PR #48).
Scope: review/fixes only; no FE, Python AI, M2-M5 or workflow-check bypass changes.

## Findings and disposition

1. **P1, fixed: create response projection could fail after commit.** The app
   service transaction ended before the controller projected the new ticket.
   A projection error returned a failure while persisting ticket/process/audit,
   making client retries unsafe. The controller now includes creation and response
   projection in one transaction. The regression verifies all three row counts.
2. **P2, fixed: queue projection N+1 and duplicate projection.** All assigned tasks
   were projected individually for workload, each reading application SQL and a
   full Flowable timeline, then the requested page was projected again. HPDI now
   dispatches batches to application-owned projection; Service Desk batches its
   own SQL and counts distinct approved review rounds from immutable estimate
   history. A default method preserves existing WorkProjection implementations.
   The requested page reuses batch results. Core remains application-independent.
3. **P2, fixed: rework state disagreed with the target contract.** Ticket views
   now return REOPENED while awaiting fresh review and before the next handle
   START. IN_PROGRESS/PENDING/confirmation/CLOSED retain their meanings. Live
   OpenAPI includes REOPENED. The review-stage filter still selects reopened
   tickets needing coordinator action; it is not a new general state filter.
4. **A, false positive: coordinator requires an agent role to review.** Both
   Keycloak's realm template and permission mapping grant task:handle to the
   coordinator role itself. This permission is not the agent role. Review still
   requires coordinator + task:assign + task:handle; no guard is removed. Handle
   candidate membership separately requires agent, even for a coordinator. The
   HTTP matrix covers coordinator-only, coordinator+agent, agent-only, requester,
   and coordinator-only denial of claim/START/PAUSE/RESUME/COMPLETE.
5. **D, not blocked: nullable assignee has existing recovery paths.** Successful
   first review returns ASSIGNED with assignee=null and a NOT_STARTED handle
   task: approved, not yet claimed. This does not enter anyone's agent queue.
   An independent agent can POST the existing HPDI task claim endpoint; a
   coordinator can POST its assign endpoint with a valid workspace agent.
   Both paths are exercised through HTTP through START/COMPLETE. The requester
   cannot claim their own work. ASSIGNED alone does not mean assignee is non-null.

## Lifecycle, security and compatibility evidence

- Three handle rounds, two reworks, one ticket/process: fresh reviews, cumulative
  work, four default/override history entries and nine task.complete audits.
  Queue excludes the ticket during review/confirmation/after close. Existing
  coarse statistics remain one ticket, never one count per handling round.
- A persisted review_task_id cannot approve the next review task. Competing
  reviews serialize under the ticket lock; review/ET/assignment/audit rollback
  remains covered by real database trigger failures.
- Existing RS256 JWT, F04, selfService, workspace/ownership, concurrent execution,
  elapsed-work and completion-audit rollback tests remain green.
- Legacy service-desk BPMN is unchanged and not execution-enabled. No instance
  is recreated, migrated or deleted. V8 is unchanged; V9 only adds the history
  (ticket_id, review_task_id) index. Flyway validation/application, legacy data
  probes and the explicit PostgreSQL version/index assertions pass.
- No new public endpoint or permission is introduced by remediation. The only
  wire change is the target-defined REOPENED value added to the live ticket enum.

## Final local acceptance

Real PostgreSQL 16.6 (digest-pinned runner), real Flowable, Java 17, Linux Docker
build, clean test runs. PASS means executed; no skipped test is counted as PASS.

| Suite | PASS | FAIL/error | SKIP |
| --- | ---: | ---: | ---: |
| Backend full | 501 | 0 | 0 |
| Core-only | 103 | 0 | 0 |
| Supported second-domain selection | 30 | 0 | 0 |
| HTTP schema positive/negative checks on actual responses | 14 | 0 | 0 |
| Source boundary check | 1 | 0 | 0 |
| Boundary mutation tests | 39 | 0 | 0 |
| Core route contract self-check | 1 | 0 | 0 |
| Service Desk live/target OpenAPI documents | 2 | 0 | 0 |
| Whole HPDI live/target OpenAPI documents | 0 | 2 baseline | 0 |

Full-suite subsets (not additional tests): TicketIntegrationTest 31,
CoreStartIntegrationTest 5, WorkflowApprovalTest 27,
WorkflowExecutionCompatibilityTest 2, WorkExecutionTest 4, ArchUnit TierBoundaryTest 1.
ArchUnit also runs in the supported second-domain selection.

The 101-task queue regression proves **2 application projection queries,
3 Core count queries, 3 Core page queries, zero timeline reads** for a 100-item
page request. ET, floor-to-minute elapsed work, minimum remainder and rounded
half-weight PENDING arithmetic are unchanged. REPEATABLE_READ prevents page
membership changes during the request. This is batching, not advanced R2.

Commands, from repository root (PowerShell):

```powershell
& scripts/test-task-execution.ps1 -BuildImage dx-core-build:0.1.0 -Profile full
& scripts/test-task-execution.ps1 -BuildImage dx-core-build:0.1.0 -Profile core-only
& scripts/test-task-execution.ps1 -BuildImage dx-core-build:0.1.0 -Profile second-domain
python scripts/check-tier-boundaries.py
python scripts/test-tier-boundaries.py
python scripts/test-core-contract.py
python scripts/test-servicedesk-execution-contract.py <full-run>/servicedesk-http-evidence.json
python -m openapi_spec_validator contracts/openapi/servicedesk.yaml
python -m openapi_spec_validator contracts/openapi/servicedesk-target.yaml
```

On this machine python is
`C:/Users/admin/AppData/Local/Programs/Python/Python311/python.exe`.
Runner summaries/logs/XML and actual HTTP evidence are under
`.local/task-execution-{full,core-only,second-domain}/`; these are intentionally
not committed. Final run paths are recorded in the PR description.

RED evidence is separate: the first added queue assertion failed on per-item
timeline calls (494 PASS/1 FAIL/0 SKIP). The create rollback regression failed
with one extra persisted ticket before the controller transaction fix. That
intermediate run also exposed an invalid test payload (400 instead of 403),
corrected to use each endpoint's actual request shape; it was not an auth bug.
Neither RED run is presented as final-source acceptance.

Whole HPDI validation blockers are independently compared against merged develop:
KnowledgePage.properties.description is a string rather than a Schema Object;
the target alerts response contains an unintended `cho badge` property from an
unquoted comma. Both malformed nodes are unchanged, with baseline/current
validator errors in `.local/review-api/openapi-baseline-final.json`. They are not
waived by promoted-subset validation and are not repaired outside this scope.

## FE handoff and remaining limits

FE can use the documented live create/detail/review/review-queue APIs, HPDI agent
queue, START/PAUSE/RESUME, and existing claim/assign/complete/timeline endpoints.
See SERVICEDESK_REVIEW_EXECUTION_API.md for paths, examples and JWT/workspace roles.
For a null assignee, use claim/assign, not a repeated review request. Handle must
START before COMPLETE; requester confirms accepted/rework on the same process.

Exact workload still scans every assigned execution row in bounded batches;
there is no constant-time aggregate, load benchmark/SLO certification or advanced
R2 algorithm. V9 uses a normal transactional index build; a large deployment
must account for index-build write locking during rollout. R1 team routing,
median/AI estimates, SLA/aging, configuration, reassignment policy extensions,
and detailed R12 projections remain outside this implemented subset. M1 is PARTIAL.

Hosted GitHub Actions are unavailable due billing/quota. Local acceptance is not
hosted CI PASS and does not establish required-check satisfaction. Human review
and release gates remain separate. No merge is performed by this review.

Conclusion: **READY FOR INTERNAL DEVELOPMENT MERGE**, with the explicit baseline
OpenAPI and hosted-CI limitations above; not a full-target or production sign-off.
