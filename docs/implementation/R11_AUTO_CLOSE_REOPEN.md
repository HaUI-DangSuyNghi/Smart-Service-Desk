# R11 Auto-Close And Reopen Alerts

Date: 2026-10-28 (Asia/Saigon). Feature branch: `feature/r11-auto-close-reopen-alerts`.
Base: `develop` at verified baseline `d431c29a0f95de6689c21e11f1936318584e43e1`.
Delivery: Draft PR into `develop`. No self-merge, force-push or history rewrite.

## 1. GAP Matrix

| Component / Requirement | Baseline State | R11 Implementation |
| --- | --- | --- |
| Requester confirmation | Core validates membership, active task, assignee, initiator ownership and allowed outcome (`accepted`, `rework`) | Persists `closedBy=REQUESTER`, `closedAt`, immutable confirm generation, handle completion timestamp and confirm start timestamp |
| Auto-close execution | No timer or auto-close capability existed; generic completion rejected non-initiator | Dedicated `WorkflowTimers` capability in Core + `ProcessTimers` in HPDI; scoped to initiator user tasks under `process_scope` lock; records `closedBy=AUTO`, `closedAt` |
| Confirmation clock & deadline | Undefined clock policy | Owner ratified: exactly 72 continuous hours in UTC (`Duration.ofHours(72)`), never business days |
| Rework workflow & instance | Returns to `review` (`service-desk-reviewed`) or `intake` (`service-desk-intake-reviewed`) on the same Flowable process instance | Process instance, SLA resolution, worked time, ET, CAS context revision and receipts preserved unchanged |
| REOPENED alert | State enum had `REOPENED`; no persistent alert event existed | Emits persistent alert `REOPENED` (severity WARNING) to authorized Coordinators, deduplicated by confirm generation |
| REOPEN_UNCLAIMED alert | Target OpenAPI had type definition; no backend implementation | Durable monitor emits `REOPEN_UNCLAIMED` after 15 minutes if ticket remains unclaimed in intake, review or unassigned handle; stops on reviewed Agent assignment or canonical linkage |
| Concurrency & duplicate prevention | Single active task query in Core | Process-scope advisory lock, row-level confirmation lock, unique generation constraint, atomic state transition |
| Canonical follower propagation | Guard checked ended process without checking closure provenance; AUTO root could close follower | Fail-closed guard added to `CanonicalTicketService.rootClosed` preventing AUTO-closed roots from closing followers |
| Legacy active confirmation enrollment (review 5678302783 P1) | Pre-V29 active confirm tasks had no ledger | Reconciles native active Flowable confirm tasks under process-scope lock, verifies ticket/workspace/initiator/business key/definition and resolved handle history; native task creation +72h; transactional system audit, no ticket mutation; overdue enrollments held for manual review |
| Reopen polling fairness (review 5678302783 P2) | Already emitted REOPEN_UNCLAIMED generations remained eligible | V30 persistent reopen_alerted_at, atomically committed with durable publication; emitted generations excluded from the 100-row fair checked_at queue |
| Migrations | Migrations ended at V27 | Additive V28/V29 plus V30 enrollment hold and reopen publication cursor; V1-V29 untouched |
| OpenAPI / Contract | Fields only in target draft; live had no closure metadata | Promoted `closedAt`, `closedBy`, `handleResolvedAt`, `confirmStartedAt`, `confirmDeadlineAt`, `confirmGeneration`, `confirmationHistory`, and alert types to live contracts |

## 2. Workflow Transition Matrix

| Process Key | Current Task / Outcome | Next Task / Outcome | R11 Behavior |
| --- | --- | --- | --- |
| `service-desk-reviewed` | `handle` -> `resolved` | `confirm` | Records `handle_resolved_at`, `confirm_started_at`, `deadline_at` (+72h) |
| `service-desk-reviewed` | `confirm` -> `accepted` | `end` | Requester closure; `closedBy=REQUESTER`, `closedAt=now` |
| `service-desk-reviewed` | `confirm` -> `rework` | `review` | Reopen event published; `reopen_deadline_at` set (+15m) |
| `service-desk-reviewed` | `review` -> `approved` | `handle` | Review approved; assignment marks rework received |
| `service-desk-intake-reviewed` | `handle` -> `resolved` | `confirm` | Records `handle_resolved_at`, `confirm_started_at`, `deadline_at` (+72h) |
| `service-desk-intake-reviewed` | `confirm` -> `accepted` | `end` | Requester closure; `closedBy=REQUESTER`, `closedAt=now` |
| `service-desk-intake-reviewed` | `confirm` -> `rework` | `intake` | Reopen event published; intake admission resets to NEW round |
| `service-desk-intake-reviewed` | `intake` -> `duplicate` | `follower` | Canonical link established; marks reopen received (`CANONICAL`) |
| `service-desk-intake-reviewed` | `follower` -> `closed` | `end` | Allowed ONLY if root was confirmed by Requester; AUTO root fails closed |

## 3. Auto-Close Authorization Design

Auto-close cannot be performed via the generic task completion endpoint because:
1. The confirm task is bound to the initiator (`${initiator}`). Generic completion strictly enforces that the caller matches the task assignee/initiator.
2. Fabricating a Requester token or temporarily reassigning the task violates auditability and three-tier security boundaries.
3. Direct SQL manipulation of `ACT_RU_TASK` bypasses Flowable engine events and integrity checks.

Solution implemented:
- `FlowableWorkflowTimers`: an engine-level capability in Core.
- Requires service identity with `workflow:timer` permission in the workspace (`service-account-dx-assessment-worker` assigned to `/workspaces/{workspace}/workflow-timer`).
- Acquires the `process_scope` advisory transaction lock.
- Verifies that the process instance is active, not suspended, matches business key and owner.
- Confirms that the target user task is an initiator task (`${initiator}`, empty candidate groups).
- Invokes application-level `evaluateTimer` callback to check business rules (deadline expired, SLA resolved, matching generation).
- Completes the task through Flowable engine API with local variable `outcome=accepted`.
- Emits transactional system audit `task.timer.complete` with actor `system:workflow`.

## 4. Deadline Policy (Owner Ratified)

- **Clock**: 72 continuous hours in UTC (`Duration.ofHours(72)`), calculated from `confirm_started_at` (when Flowable transitions from `handle` to `confirm`).
- **Calendar**: Calendar time, NOT business hours/days.
- **Storage**: Stored in PostgreSQL with UTC timestamp with time zone (`timestamptz`).
- **Reopen Unclaimed Deadline**: Exactly 15 minutes (`Duration.ofMinutes(15)`) from the instant Requester completes rework.
- **Receipt / Acknowledgement Boundary**: Reopen monitoring ceases when:
  1. Ticket in review/intake workflow is assigned to an Agent (`ASSIGNED` reason).
  2. Follower ticket is linked to a canonical incident (`CANONICAL` reason).

## 5. Reopen Event Lifecycle

When Requester selects `rework`:
1. Core `FlowableWorkflowService` records completion and invokes `TicketConfirmation.completed`.
2. `servicedesk_confirmation` row updated with `decision='REWORK'`, `reopen_deadline_at = now + 15m`.
3. `REOPENED` persistent alert event published to `hpdi_alert_event` with generation key = confirm task ID.
4. Deduplicated by `(workspace, process_instance_id, generation, type)` unique constraint.
5. `AlertDeliveryWorker` delivers alert to authorized workspace Coordinators.
6. `TicketConfirmationWorker` evaluates pending rework tasks. If elapsed time exceeds `reopen_deadline_at` and ticket is still in `intake` or `review` (or unassigned `handle`), `REOPEN_UNCLAIMED` alert is published with the same generation key.
7. When Coordinator approves and assigns an Agent, `TicketConfirmation.received` stamps `reopen_received_at` with reason `ASSIGNED`, preventing further unclaimed alerts.

## 6. Canonical And Follower Consistency

Investigation revealed:
- Follower tickets poll for root closure via `CanonicalTicketService.propagate`.
- `CanonicalTicketService.rootClosed` previously checked only process end status and revisions.
- Merely withholding `rootConfirmed` update upon AUTO-close was insufficient to prevent follower closure, because `rootClosed` evaluated history directly.

R11 guard implemented:
- `CanonicalTicketService.rootClosed` explicitly queries `servicedesk_confirmation` for `decision='AUTO'`.
- If the root was closed by `AUTO`, `rootClosed` returns `false` (fail-closed).
- Follower remains in `FOLLOWING` state; automatic closure is blocked.
- Follower closure requires human coordinator decision/split or root confirmed by Requester.

## 7. Concurrency, Locking And Transaction Rollback

- **Process Scope Lock**: `select id from process_scope where id=? and workspace=? for update` serializes all execution and timer operations per process instance.
- **Confirmation Ledger Lock**: `select ... from servicedesk_confirmation ... for update` ensures single-evaluator mutation.
- **Requester vs Auto-Close Race**: Atomic conditional update `where decision is null`. Winner commits decision (`REQUESTER` or `AUTO`); loser receives 409 Conflict.
- **Multiple Workers Race**: Verified with concurrent threads executing `evaluateTimer`. Exactly one worker succeeds; audit and database records are not duplicated.
- **Audit Rollback**: System audit failure in `audit_event` trigger rolls back both Flowable task completion and `servicedesk_confirmation` updates. Worker successfully recovers and completes on next run.

## 8. Migrations

- `hpdi/V28__reopen_alert_types.sql`: Additive constraint update adding `REOPENED` and `REOPEN_UNCLAIMED` to `hpdi_alert_event.type`.
- `apps/servicedesk/V29__confirmation_lifecycle.sql`: Creates `servicedesk_confirmation` table with generation unique constraint, 72-hour check constraint, and indexing for pending evaluations.

## 9. OpenAPI And Contracts

- `contracts/hpdi/openapi.yaml`: Promoted `REOPENED` and `REOPEN_UNCLAIMED` to `Alert.type` enum.
- `contracts/openapi/servicedesk.yaml`: Promoted `closedAt`, `closedBy`, `handleResolvedAt`, `confirmStartedAt`, `confirmDeadlineAt`, `confirmGeneration`, `confirmationHistory` to `Ticket` and `TicketProgress`.
- Verified with `OAS30Validator` against captured real HTTP traffic: 17 responses pass.
- Verified with `test-servicedesk-execution-contract.py`: 0 new errors versus baseline.

## 10. Test Evidence

| Suite / Gate | Tests | PASS | FAIL | ERROR | SKIP | Details |
| --- | --- | --- | --- | --- | --- | --- |
| Backend Full (Linux container, real PostgreSQL) | 823 | 823 | 0 | 0 | 0 | Includes ArchUnit tier boundaries, SLA, AI-D2, R5-R9 regressions |
| Targeted R11/R5/R6/R7/R8/R9, Intake/Canonical and migrations | 159 | 159 | 0 | 0 | 0 | Includes legacy enrollment races, rollback, native upgrade, stale scope and 105-generation queue fairness |
| Core-only Profile | 106 | 106 | 0 | 0 | 0 | Validates Core isolation from app dependencies |
| Second-domain Profile | 43 | 43 | 0 | 0 | 0 | Validates Room Request without Service Desk |
| R11 Real Runtime Probe (Keycloak JWT, Flowable, Postgres) | 14 | 14 | 0 | 0 | 0 | Validates 72h continuous clock, signed service worker, restart persistence, alert delivery |
| OpenAPI Contract Validation | 17 | 17 | 0 | 0 | 0 | Real HTTP traffic matches OpenAPI 3.0 specification |
| Whole OpenAPI Baseline Comparison | 4 docs | 4 docs | 0 | 0 | 0 | Zero regressions vs develop baseline |
| Tier Boundaries | 43 checks | 43 | 0 | 0 | 0 | Python architectural gates pass |

### Probe Check Breakdown (`scripts/apps/servicedesk/acceptance/r11-confirmation.py`)
1. `deadline is exactly 72 continuous hours`: PASS
2. `requester provenance is persisted`: PASS
3. `real JWT Agent cannot confirm`: PASS (HTTP 403)
4. `restart before deadline preserves generation and deadline`: PASS
5. `real scheduled worker closes due generation with signed service identity`: PASS
6. `auto-close preserves process and resolved SLA`: PASS
7. `accepted generation remains REQUESTER`: PASS
8. `rework before deadline is never auto-closed`: PASS
9. `both persistent reopen events reach Coordinator exactly once`: PASS
10. `old Agent does not receive reopen events`: PASS
11. `restart after commit preserves AUTO provenance`: PASS
12. `restart and retries do not duplicate completion audit`: PASS
13. `restart enrolls legacy active confirm from native task time`: PASS
14. `overdue legacy generation stays active on manual rollout hold`: PASS

## 11. Findings And Business Decisions

- **P0 / Security**: Resolved. Timer evaluation requires real Keycloak service account token with `workflow:timer` permission. No requester impersonation.
- **P1 / Canonical Follower Safety**: Resolved. `CanonicalTicketService.rootClosed` incorporates `servicedesk_confirmation.decision = 'AUTO'` check to fail closed.
- **P2 / Local Documentation Discrepancy**: Local `docs/ticket.md` notes rework returning directly to Agent handle; production BPMN definitions (`service-desk-reviewed` and `service-desk-intake-reviewed`) enforce rework returning to `review` and `intake` respectively. The production BPMN and AI-D2 intake specifications take precedence.
- **Hosted CI Status**: `BLOCKED` on GitHub Actions due to repository account spending limit / billing failure (confirmed via check-run `122633977187` annotation). Not waived.

## 12. Delivery Decision

- Ready for internal review as Draft PR into `develop`.
- Do NOT merge until hosted CI billing is resolved and owner conducts independent review.


## 13. Review 5678302783 Follow-Up

- P1: enrollment runs before each scheduled evaluation and recovers after restart. Only live native tasks are considered; stale/corrupt correlations fail closed. Scope-row locking serializes enrollment with Requester completion and other workers. Existing generation enrollment is a no-op. Resolved native task-local outcome history and the matching SLA handle receipt are required; no timestamps or prior work are invented.
- No reader/Requester impersonation: Core executes under ServiceIdentity with workflow:timer, reads native history itself, and passes verified task identity/time through the HPDI callback. App SQL touches only its own tables.
- Rollout policy (owner decision pending): a missing-ledger generation already due at enrollment (including exact deadline equality) is persisted with rollout_hold=true, rollout_reason=OVERDUE_AT_ROLLOUT. Deadline remains the native start +72h, never an invented grace period. Scheduled and direct timer evaluation both refuse AUTO-close while held. Requester accepted/rework remains available. No automatic hold release is provided; owner approval and an audited operational procedure are required before any future release policy.
- Operator inventory: `select workspace,ticket_id,process_instance_id,generation,confirm_started_at,deadline_at,rollout_reason from servicedesk_confirmation where rollout_hold and decision is null order by deadline_at;`. Missing verified handle/SLA evidence is not enrolled or auto-closed; investigate native history manually. Historical already-completed rework rounds are not reconstructed or assigned invented alert deadlines.
- P2: reopen_alerted_at is written in the same transaction as persistent REOPEN_UNCLAIMED insertion and its system audit. A crash/audit failure before commit rolls back both; a retry after commit sees the cursor. Existing unique alert identity still deduplicates V29 events on first reconciliation. REOPENED, current recipient authorization, Intake CAS and assignment/receipt rules are unchanged.
- Fairness: successfully alerted rows leave the pending query; failures/stale work rotate by checked_at,workspace,generation with a 100-row batch. Enrollment failure is isolated per process and logged, not silently accepted.
- Migration/runtime evidence includes V27 native active confirmation, V29 empty ledger, then V30 startup/enrollment; original migration checksums and ticket state are asserted unchanged. No V1-V29 migration is edited.
- Merge policy: Draft PR #59 only; independent review and owner decision remain required. MERGE COMMIT ONLY. Hosted CI billing remains BLOCKED, never CI PASS.


## 14. Final Local Verification Receipt

Final tested implementation SHA: `42c3c87db413c09fa85a832badca71a1cab7713c` (2026-10-28).
A subsequent documentation-only receipt commit does not change the tested Java, SQL, contracts, acceptance script or test sources.

- Backend Full: 823/823; Core-only: 106/106; Second-domain/Room Request: 43/43; targeted: 159/159. Every Maven run has 0 failures, 0 errors and 0 skipped tests. PostgreSQL 16.6, Flowable 7.1, Linux Maven/Temurin 21 runner.
- Real runtime: 14/14 checks with signed Keycloak JWTs and a rebuilt packaged backend. Four lifecycle/restart phases include missing-ledger enrollment and overdue rollout hold. UTC offset is injected only through the test classpath; no production time-travel endpoint exists.
- Clean migrations and V27 -> V29 -> V30 active-native-confirm upgrade pass. Existing checksums, ticket state and process instance are preserved. Only new V30 is added relative to reviewed HEAD 99dc489; V1-V29 are unchanged.
- Queue fairness: a real PostgreSQL 105-generation backlog drains 100 then 5; the next poll never reselects the cursor-marked rows. Separate real-engine tests verify alert publication/audit/cursor rollback and exactly-once event identity.
- Tier source boundary gate PASS; 43/43 boundary checker self-tests PASS; ArchUnit passes within Maven. OpenAPI validates 17 actual runtime responses plus the targeted MockMvc evidence and unchanged whole-document error baselines for all four promoted/target documents.
- Local raw evidence: `.local/task-execution-full/summary.json`, `.local/task-execution-core-only/summary.json`, `.local/task-execution-second-domain/summary.json`, `.local/task-execution-full-targeted/summary.json`, `.local/r11-runtime.json`, `.local/r11-http.json`, `.local/r11-review-final-contract.log`. Logs are local artifacts, not hosted CI results.
- P0: no confirmed issue in this follow-up. P1 legacy active-confirm gap: fixed for verified supported active generations; overdue or unverifiable history is fail-closed/manual handling, not retroactive closure. P2 repeated reopen polling: fixed with transactional persistent cursor and batch-fairness coverage.
- Hosted CI: BLOCKED. Re-read check-run 122633977187 annotation: jobs were not started because account payments failed or spending limit must be increased. Local PASS is not hosted CI PASS.
- Merge readiness: NOT READY. PR #59 stays Draft pending independent review and owner decision on CI/release gates and manual legacy handling. MERGE COMMIT ONLY; no merge or force-push performed.

Reproduction:
```powershell
scripts/test-task-execution.ps1 -Profile full -BuildImage maven:3.9.11-eclipse-temurin-21
scripts/test-task-execution.ps1 -Profile core-only -BuildImage maven:3.9.11-eclipse-temurin-21
scripts/test-task-execution.ps1 -Profile second-domain -BuildImage maven:3.9.11-eclipse-temurin-21
scripts/test-task-execution.ps1 -Profile full -Test 'TicketIntegrationTest,ConfirmationUpgradeIntegrationTest,ServiceDeskMigrationIntegrationTest,AgentDutyUpgradeIntegrationTest,CanonicalTicketIntegrationTest,IntakeAdmissionIntegrationTest' -BuildImage maven:3.9.11-eclipse-temurin-21
python scripts/apps/servicedesk/acceptance/r11-confirmation.py
python scripts/check-core-contract.py .local/r11-http.json contracts/openapi/servicedesk.yaml
python scripts/check-tier-boundaries.py
python scripts/test-tier-boundaries.py
```
Runtime probe requires the full-profile packaged jar and compiled TimerRuntimeProbe test classes (build with `mvn -DskipTests package` before launching the probe). Contract scripts require the existing Python OpenAPI validator dependencies.
