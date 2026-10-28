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
| Migrations | Migrations ended at V27 | Additive migrations V28 (HPDI alert types) and V29 (Service Desk confirmation ledger) |
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
- Verified with `OAS30Validator` against captured real HTTP traffic: 13 requests pass.
- Verified with `test-servicedesk-execution-contract.py`: 0 new errors versus baseline.

## 10. Test Evidence

| Suite / Gate | Tests | PASS | FAIL | ERROR | SKIP | Details |
| --- | --- | --- | --- | --- | --- | --- |
| Backend Full (Linux container, real PostgreSQL) | 814 | 814 | 0 | 0 | 0 | Includes ArchUnit tier boundaries, SLA, AI-D2, R5-R9 regressions |
| Core-only Profile | 106 | 106 | 0 | 0 | 0 | Validates Core isolation from app dependencies |
| Second-domain Profile | 43 | 43 | 0 | 0 | 0 | Validates Room Request without Service Desk |
| R11 Real Runtime Probe (Keycloak JWT, Flowable, Postgres) | 12 | 12 | 0 | 0 | 0 | Validates 72h continuous clock, signed service worker, restart persistence, alert delivery |
| OpenAPI Contract Validation | 13 | 13 | 0 | 0 | 0 | Real HTTP traffic matches OpenAPI 3.0 specification |
| Whole OpenAPI Baseline Comparison | 4 docs | 4 docs | 0 | 0 | 0 | Zero regressions vs develop baseline |
| Tier Boundaries | 43 checks | 43 | 0 | 0 | 0 | Python architectural gates pass |

### Probe Check Breakdown (`scripts/acceptance/r11-confirmation.py`)
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

## 11. Findings And Business Decisions

- **P0 / Security**: Resolved. Timer evaluation requires real Keycloak service account token with `workflow:timer` permission. No requester impersonation.
- **P1 / Canonical Follower Safety**: Resolved. `CanonicalTicketService.rootClosed` incorporates `servicedesk_confirmation.decision = 'AUTO'` check to fail closed.
- **P2 / Local Documentation Discrepancy**: Local `docs/ticket.md` notes rework returning directly to Agent handle; production BPMN definitions (`service-desk-reviewed` and `service-desk-intake-reviewed`) enforce rework returning to `review` and `intake` respectively. The production BPMN and AI-D2 intake specifications take precedence.
- **Hosted CI Status**: `BLOCKED` on GitHub Actions due to repository account spending limit / billing failure (confirmed via check-run `122633977187` annotation). Not waived.

## 12. Delivery Decision

- Ready for internal review as Draft PR into `develop`.
- Do NOT merge until hosted CI billing is resolved and owner conducts independent review.
