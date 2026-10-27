# Intelligent assignment: PR A (R1 + R3)

Baseline: develop b9e2a5537cf047a634f2e98698f217ea587f1575. PR B must build on this change; do not merge automatically.

## GAP matrix

| Requirement | Baseline | PR A |
| --- | --- | --- |
| Reviewed workflow, stored assessment, coordinator review | ALREADY IMPLEMENTED | Reused unchanged |
| Default ET, coordinator override and estimate history | ALREADY IMPLEMENTED | Preserved, ACCOUNT/HIGH remains 20 |
| Workspace membership, candidate roles, creator exclusion | PARTIAL | Fresh agent role + task:handle + support membership checks |
| Category to actual Keycloak support group | MISSING | HARDWARE/hardware, NETWORK/network, SOFTWARE/software, ACCOUNT/sysadmin |
| Workspace duty persistence and self-service | MISSING | V11, GET/PUT duty, transactional audit |
| WorkExecution / WorkProjection batch queue and elapsed time | ALREADY IMPLEMENTED | Reused, no queue rewrite |
| R2 ranking, daily assignment counts, persistent concurrent round-robin | MISSING | Deferred to dependent PR B |
| Assignment-suggestion HTTP endpoint | MISSING | Deferred to PR B; target only, not live |
| Historical median ET and estimate basis | MISSING | Deferred; current default/override history is retained |

## R1 security and assignment

Support groups live at `/support-groups/{workspace}/{hardware|network|software|sysadmin}`.
They deliberately do not live directly below `/workspaces/{workspace}`: that namespace is parsed as workspace roles.
Support membership alone grants neither workspace membership nor agent permission.
Eligibility intersects enabled current workspace agent members, current agent `task:handle` permission, category support membership,
creator exclusion and persisted on-duty status. Directory refresh bypasses the normal 60-second snapshot cache for this decision.
Keycloak errors fail closed (503); missing groups produce no eligible agents. OTHER permits manual coordinator selection of any otherwise eligible agent.
Unassigned review remains supported. Both generic Core and HPDI assign/claim routes invoke a default-no-op contribution hook
inside the existing locked workflow transaction. Only reviewed Service Desk handle tasks apply R1/R3; legacy Service Desk and Room Request retain their policies.
No recommendation or automatic assignment is introduced. Existing explicit review/assignment actions remain coordinator decisions.

## R3 migration and HTTP handoff

Flyway V11 is app-scoped and creates `servicedesk_agent_duty`, primary key `(workspace, subject)`.
No row means off-duty. Existing agents must explicitly opt in after deployment.
Duty and `servicedesk.duty.changed` audit commit or roll back together. Notes are stored but excluded from audit references.
Concurrent updates serialize through PostgreSQL's upsert row locking; last committed update wins.
Off-duty changes do not pause, cancel, or reassign any existing task.

`GET /api/hpdi/v1/workspaces/lab-a/duty` returns an array of
`{"subject":"agent-subject","onDuty":false,"note":null,"updatedAt":null}` for a never-set agent.
Agents with task:handle see only themselves; coordinators with task:assign see current workspace agents.
`PUT /api/hpdi/v1/workspaces/lab-a/duty/me` with `{"onDuty":true,"note":"Available"}` returns the persisted status.
An agent may substitute their own subject for `me`. No actor may change somebody else's duty in this phase.
Null/missing onDuty, oversized notes and unknown JSON fields are rejected. A membership/permission revocation prevents subsequent changes and assignments.
The live OpenAPI is authoritative; the target's previous coordinator-edit and schedule behavior is not implemented.
FE should show explicit duty opt-in and a conflict message when the chosen agent becomes off-duty before assignment.
Assignment rechecks are snapshots of Keycloak at validation time, not a distributed transaction with Keycloak; revocation immediately after validation remains possible.

## Keycloak deployment (operator action only)

Fresh realm imports include empty support groups for lab-a and lab-b in `infra/keycloak/realm-template.json`.
No member is auto-enrolled and no permission role is assigned to support groups.
For an existing realm, use the Admin Console with an authorized operator:

1. Create top-level `support-groups`, without role mappings.
2. Add the exact workspace code as a child, then hardware, network, software and sysadmin children.
3. Enroll enabled agents in the appropriate leaf groups, retaining their separate `/workspaces/{workspace}/agent` membership.
4. Verify the read-only dx-directory service account can query these groups and members.
5. Have each agent opt in using PUT duty/me; verify a coordinator sees them in GET duty.

Do not run realm reset/import over a shared existing realm. Repeat additive creation for each existing workspace.
Rollback application changes before dropping V11; preserve duty/audit data for investigation. No automated destructive rollback is provided.

## Performance and scope ceilings

Support membership reads are paginated, with no per-agent Keycloak request. Workspace directory refresh currently uses the existing
bounded role/group reads (100 role groups, 1000 members per role); larger workspaces need directory pagination in a follow-up.
Directory refresh is deliberately uncached for eligibility, trading latency for revocation correctness. No N+1 query is added per listed agent.
R2 should bulk aggregate active WorkExecution/projection data, apply workload and daily-count ranking, then serialize persisted
round-robin selection per workspace/support-group. Keep coordinator override and do not invent AI confidence.

## Acceptance evidence

Local runs on 2026-10-27 use disposable PostgreSQL 16.6 and real Flowable, not H2. No shared realm was changed.

| Check | Result | Evidence |
| --- | --- | --- |
| Backend full, final tree | PASS: 532 tests, 0 failures/errors/skips | `.local/task-execution-full/summary.json` |
| Core-only | PASS: 106 tests, 0 failures/errors/skips | `.local/task-execution-core-only/summary.json` |
| Supported second-domain | PASS: 30 tests, 0 failures/errors/skips | `.local/task-execution-second-domain/summary.json` |
| Targeted R1/R3 + review/directory | PASS: 52 tests, 0 failures/errors/skips | `.local/task-execution-full-targeted/summary.json` |
| Source boundary guard + mutation tests | PASS: 41 tests; no new debt | `python scripts/test-tier-boundaries.py`; `python scripts/check-tier-boundaries.py` |
| Java architecture boundaries | PASS in full and second-domain | Maven TierBoundaryTest reports |
| Existing execution HTTP contract | PASS: 14 positive/negative request/response checks and stored assessment examples | `scripts/test-servicedesk-execution-contract.py` with generated servicedesk-http-evidence.json |
| Duty HTTP authorization/isolation, persistence and audit rollback | PASS | AgentDutyIntegrationTest and TicketIntegrationTest |
| Concurrent duty updates and coordinator review | PASS | AgentDutyIntegrationTest and existing concurrentCoordinatorReviewsHaveOneWinnerAndOneEstimateHistory |
| Flyway upgrade / validation | PASS in real PostgreSQL suite, V11 applied | Maven reports and migration logs |
| Whole live OpenAPI | FAIL: same 3 baseline validation errors | `.local/routing-duty-contract-validation.json` |
| Whole target OpenAPI | FAIL: same 1 baseline validation error | `.local/routing-duty-contract-validation.json` |
| Hosted GitHub Actions | NOT VERIFIED locally | Inspect the PR checks; do not infer success from local tests |
| R2 suggestion/ranking/concurrency and historical median | SKIP: not part of PR A | Dependent PR B / later ET enhancement |

Whole-spec failures were independently reproduced against `git show origin/develop:<contract>` and the current tree;
validation error paths are unchanged. Live KnowledgePage nests a plain description string inside properties;
target alerts has a malformed unread property description. These unrelated defects are not silently fixed or waived.
The source-boundary scanner now recognizes PostgreSQL ON CONFLICT DO UPDATE SET rather than treating SET as a table;
its new mutation test still detects a forbidden table update following an upsert.
The Maven runner caps each Hikari pool at 3 connections so multiple Spring test contexts do not exhaust the disposable PostgreSQL connection limit.

Reproduce database suites with `powershell -NoProfile -File scripts/test-task-execution.ps1 -BuildImage maven:3.9.11-eclipse-temurin-21 -Profile full`
and substitute `core-only` or `second-domain` for the profile. JSON summaries refer only to the final completed run of each profile.

Acceptance status: **BLOCKED for unconditional internal merge** by the pre-existing whole-spec errors.
R1/R3 implementation and local regression suites pass. R2 is deliberately not claimed complete; proceed on a dependency-ready PR A baseline,
without automatically merging this PR. The draft PR should retain these blockers until maintainers resolve or explicitly accept the baseline contract debt.
