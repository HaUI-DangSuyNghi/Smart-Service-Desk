# Core review and isolated workflow approval

Review date: 2026-10-15. Branch: `feat/workflow-approval`.

## Findings before this change

1. **High: independent approval was missing from runtime, not entirely missing from the plan.**
   `WorkflowApi` directly used the engine, restricted reads/completion to the creator,
   and the two `processes/` probes assigned every task to `${initiator}`.
   `WorkflowService` and `ProcessRegistration` existed as contracts only. There was
   no implementation, registry, candidate inbox or claim operation. BE-02/BE-03
   mention these concerns, but BE-03 made claim conditional. For shared candidate
   approval, claim and its concurrency checks must be an explicit acceptance gate.
2. **High: API gateway configuration alone cannot finish integration.**
   The old HTTP completion body has only `expectedTaskKey`, not outcome/comment;
   its authorization also excludes independent reviewers. Registering a route does
   not replace this controller, wire identity/audit/transactions or reconcile BPMN
   versions. Those are a separate, reviewed integration task after standalone proof.
3. **Medium: the Core gate mixes delivered contracts with runtime capabilities.**
   File has schema/contracts but no private upload/download implementation;
   Metadata has an API contract separate from the existing direct JDBC controller.
   Their presence is not proof that reusable services exist. Keep File explicitly
   blocked until authorization and failure-compensation tests pass. Do not describe
   delegate/cancel, notifications or UI as prerequisites unless scope requires them.
4. **Medium: evidence and status have drifted.**
   `core-baseline-status.md` says audit read is unimplemented, while
   `AuditController` and `JdbcAuditService.search` now exist. Its original test
   count is historical, not current acceptance evidence. Maintain per-feature
   commands/results and distinguish contract, implementation and verified runtime.
5. **Medium: N1-N4 capacity remains a target, not a validated estimate.**
   Independent approval adds roles, negative cases, races and rollback evidence.
   Do not silently add this work to the original 48 implementation hours or mark
   Core complete just because this branch passes. Keep the documented clean-machine,
   signed-token negatives, File and release-security gates with named owners.

Conclusion: the foundation and ownership plan are useful, but Core was not complete.
The friend's workflow suggestion addresses a real implementation gap, not proof
that every part of the Core architecture must be rewritten.

## What this branch supplies

- `FlowableWorkflowService` implements the existing Java contract: start, scoped
  candidate/assigned inbox, tasks, claim, complete and engine-backed history.
- `ProcessRegistry` rejects duplicate/missing registrations, reserved start
  variables, unregistered deployed definitions and mismatched task outcome policies.
- `workflow-approval/` contains independent-review BPMNs. Documents are approved
  or rejected by a coordinator. Equipment rejection ends immediately; approval
  creates a handover task for an agent. The initiator cannot claim/complete their
  own tasks, even when they also hold the relevant role.
- Identity is a supplied `CurrentUser` contract, not an HTTP call. The caller must
  supply trusted identity; test identities are fixtures, never an authentication API.
- Engine, scope and audit calls share one Spring transaction manager/datasource.
  Scope-row locks serialize claim/complete; unique workspace/process/business keys
  reject duplicate starts. Inputs/outcomes are bounded and allowlisted.
- Completion rechecks the assignee's current workspace candidate role, so losing
  that role after claim cannot authorize completion with a refreshed identity.
- The registry pins validated definition IDs. Starts never silently switch to a
  subsequently deployed version; claim/complete fail with CONFLICT for versions
  outside that snapshot. Historical reads remain available under normal ACLs.
  Direct-assignee probes and unscoped/empty candidate groups fail registration.
- Outcome/comment are persisted as Flowable task-local history variables; the
  routing outcome is also written as `outcome_<taskKey>`. Comments are not sent to
  a notification system or an external audit sink.

## Isolation and runnable proof

No new Spring component/bean, HTTP endpoint or gateway route is enabled. Existing
`WorkflowApi`, production BPMNs under `processes/`, Compose services, shared DB,
Keycloak and app modules remain untouched. New BPMNs are explicitly deployed only
by the standalone test harness. This is an independently executable service test,
not a second web server or a production authentication bypass.

Prerequisites: Docker running, `postgres:16.6-bookworm` and a Maven build image with
this project's dependencies cached. The local `dx-core-build:0.1.0` image is used;
on a clean machine prepare it with `docker build --target build -t dx-core-build:0.1.0 backend`.

Run from the repository root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-workflow.ps1
```

The script creates a unique internal Docker network, a temporary PostgreSQL database
in tmpfs, no host ports and no shared volumes. It runs Maven offline against current
source using `mvn -B -o clean verify` and removes only its own containers/network
in `finally`. It never connects
to the running Core database. Reports are in `backend/target/surefire-reports/`.
Without `WORKFLOW_TEST_JDBC_URL`, regular Maven runs skip the engine suite; a green
ordinary build alone is therefore NOT workflow acceptance evidence.

The suite exercises both approval/rejection paths, handover, actor/time/outcome
history, cross-workspace denial, creator self-approval denial, role denial, inbox
filtering, invalid outcomes/variables, replay, registry errors, duplicate starts,
concurrent start/claim/complete and transaction rollback after audit failure.
Audit uses a test adapter writing to the same isolated DB: this proves transaction
boundaries, not integration with the production audit bean. No JWT/gateway or
production HTTP smoke result is claimed by this suite.

Version acceptance additionally deploys a second definition, proves pinned starts,
rejects cross-version mutations, and proves legacy probe history stays readable
without enabling its tasks. There is no automatic migration: integration must
explicitly drain/migrate old instances and review policies before enabling a new
registry snapshot. Candidate/assigned inboxes can still list scoped older tasks;
seeing a task is not permission to transition an unsupported version.

Verified on 2026-10-15: `scripts/test-workflow.ps1` completed an offline clean
compile/test successfully: 19 tests, 0 failures, 0 errors, 0 skipped, including
8 workflow engine tests. `git diff --check` also passed. Test containers/network
were cleaned up; no production stack was started or modified.

Completion pass on 2026-10-15 (implementation commit `9b74b8e`): the original
19 tests passed again before changes. Added definition-ID pinning, registration
checks for candidate-role BPMNs, role revalidation on completion and four more
engine tests for versions, legacy probes, role revocation and invalid start input.
Final command `scripts/test-workflow.ps1` ran offline `mvn -B -o clean verify`:
**23 tests, 0 failures, 0 errors, 0 skipped**, including **12 real-engine tests**;
JAR creation and Spring Boot repackaging succeeded. Engine runtime/history, scope
and test audit rows were checked for rollback and duplicate-start consistency.
The script now waits for TCP readiness, not the temporary PostgreSQL init socket.
No dependency, Flyway migration, production controller or gateway change is part
of this feature. The staged token/private-key heuristic found no matches; it is
not a substitute for the planned full repository/dependency/image scans.

Git handoff: implementation `9b74b8e` and scope/evidence docs `8428dc4` were
normally pushed to the existing `feat/workflow-approval` branch. GitHub
[PR #8](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/pull/8) targets
`develop` and is OPEN, not merged. Human review is required before starting File;
the remaining branches have not been created. No force-push, history rewrite,
automatic merge or direct update of `develop`/`main` was performed.

Official scope was checked directly against all seven pages of
`references/De_thi_PMNM_HaUI_2026_BAN_CHINH_THUC.pdf`: pp1-3 mandate Core-first,
headless reuse and a workflow engine; pp3-4 distinguish Core from H-P-D-I/apps;
pp4-5 require source reproducibility, open-source provenance, RBAC, secret hygiene
and tests; pp6-7 retain the final release/demo gates. No UI or business-app code
was added to satisfy a Core-only milestone. Full phase ownership/progress is in
`core-team-plan.md`, not a second competing roadmap.

## Integration gate (intentionally not performed)

1. Agree HTTP inbox/claim/complete/history schemas and update OpenAPI + consumers.
2. Wire this service to verified CurrentUser and production AuditService with the
   same engine datasource/transaction manager; replace old controller delegation.
3. Plan BPMN deployment/version compatibility for existing probe instances; do not
   overwrite deployed definitions or assume latest policies cover old instances.
4. Test real JWTs, HTTP errors, workspace isolation and atomic audit with that wiring.
5. Only then adjust gateway routing if needed and run end-to-end smoke tests.

Deliberate ceiling: synchronous local human approval only. No delegate/cancel,
timers, external integrations, UI, policy hot reload or production schema upgrade.
Add these only with a concrete requirement and matching acceptance tests.
