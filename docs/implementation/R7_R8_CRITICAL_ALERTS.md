# R7/R8 - Critical Handling and Persistent Alerts

## 1. Baseline And Status

- Business authority: `docs/ticket.md`, sections 1, R3-R8, 6-8. That supplied,
  untracked document is read but is not added to this change.
- Fetched `origin/develop`: `28489ee7beef369827b0c87750be6bb48ee4992c` (PR #55).
- Branch: `feature/r7-r8-critical-alerts`; destination: `develop`; Draft only.
- **R7 AUTO-QUEUE BLOCKED.** No emergency admission, synthetic queue, fabricated
  Coordinator approval, `CRITICAL_AUTO_QUEUED` event, merge or production deployment.
- Partial implementation for internal review, **not READY FOR INTERNAL MERGE**.
  Final committed-SHA test evidence and CI status belong in the Draft PR.

## 2. GAP Matrix And Requirement Traceability

| Rule | Baseline source / contract gap | Implementation / evidence | Status |
| --- | --- | --- | --- |
| R7.1 immediate Coordinator warning | SLA starts at human review; no persistent alert producer | Approved CRITICAL outbox is inserted in review transaction. Fresh final AI-D2/legacy assessment can publish clearly UNAPPROVED AI advice. Polling delivery does not depend on queue GET. `TicketIntegrationTest`, signed-worker integration and runtime probe | Partial: pre-review suggested-agent recipient unavailable |
| R7.1 proposed technician | R1/R2/R3 recommendation is computed by an authenticated GET, not a durable authorized recommendation receipt | Approved assignee receives the reviewed CRITICAL event. No worker impersonates a Coordinator or calls a side-effectful GET. Persisted pre-review recommendation/binding is still needed | Partial |
| R7.2 no forced preemption | WorkExecution already controls START/PAUSE/maxInProgress | Alerts never call claim, assign, START, PAUSE, review or admission. An actual IN_PROGRESS ticket remains IN_PROGRESS when another CRITICAL is approved | Implemented; existing execution/duty regressions retained |
| R7.3 unreviewed >5 minutes enters real queue | R4 review, intake CAS, final binding, independent human authorization and safety holds conflict with unconditional timeout admission | Fail closed before and after five minutes; advisory cannot approve. No handle created by alerts. See decision below | **BLOCKED** |
| R8 START_OVERDUE | SlaPolicy projects start breach, including historical late START | Emit only if `firstStarted == null` and existing projection says start breached; once per review handle | Implemented for monitored rounds |
| R8 SLA_WARNING | Existing SlaPolicy owns the strict <20% calculation, calendars and pause policy | Reuse `State.project(now)`, suppress paused/nonworking-clock warnings, one warning per review | Implemented for monitored rounds |
| R8 SLA_BREACHED | Existing projection owns deadline, but no notification persistence | CRITICAL-severity Coordinator/assignee event, once per review. No replacement SLA math | Partial: no new Coordinator queue-head sorting / FE change |
| R8 PENDING_TOO_LONG | SLA pause and execution pending are different clocks | Persist execution PAUSE instant, clear on START/RESUME, strict >3 elapsed days; key includes pending episode | Implemented for monitored rounds |
| NO_ONE_ON_DUTY | Empty eligible suggestion is not evidence of an empty duty roster | Read actual current agent/support-group membership and duty in HPDI, without requester/load/task-handle eligibility filters; OTHER is manual, not a fictitious empty group | Partial: active reviewed work only, not an intake-wide group monitor |
| ASSIGNEE_OFF_DUTY | Duty self-service exists; no persistent alert | Coordinator event for an observed off-duty episode, current assignee kept unchanged; recovery resets episode state | Implemented for monitored rounds |
| Persistence/API/security | Target-only alert paths; no tables or recipient read state | HPDI V21, recipient-private GET/read, current membership + permission + process observer-role filtering, transactional audit; real PostgreSQL/JWT tests | Implemented |
| Scheduler/recovery | TicketSlaWorker reconciles SLA, not delivery | Separate bounded domain evaluator and generic durable delivery worker, stable event keys, short transaction locks, retry leases | Implemented with rollout limits below |

Reviewed source includes live/target HPDI and Service Desk OpenAPI, R5/AI-D2
delivery documentation, ServiceDeskProcess, TicketReviewService, TicketSlaService,
TicketSlaWorker, SlaPolicy, AssignmentSuggestionController, duty/eligibility,
IntakeAdmission, CanonicalTicketService, workflow scheduling and audit APIs.
The target Alert schema's flow-style unread description contains an unquoted
comma; promoting it verbatim adds an invalid property. The live subset fixes that
description without changing the target document or unrelated baseline errors.

## 3. Emergency Auto-Queue Decision

There is no authorized pre-review CRITICAL decision or emergency admission
capability in the baseline. An AI priority is advisory, even after final snapshot
binding. A scheduler cannot call review as a fabricated Coordinator, silently
release intake, ignore duplicate disposition, or reinterpret a notification as a
Flowable handle. Therefore no timeout dispatch path is introduced.

Business/authorization decisions still required:

1. Name an authorized human/rule/policy issuer whose CRITICAL declaration permits
   emergency admission, distinct from AI suggestions; define revocation.
2. Ratify the five-minute origin. Proposed first round: ticket submission instant;
   proposed rework: a new explicitly persisted round-start instant, never the
   original ticket timestamp. This proposal is **not an implemented timeout**.
3. Specify an emergency receipt bound to context revision, final assessment,
   review round, recommendation and policy version. Specify how human safety and
   duplicate holds veto it without weakening admission/CAS.
4. Ratify the real queue state: an assigned, not-started handle in the existing
   process, with a fresh SLA decision provenance and no fake human approval.
5. Persist/revalidate the R1/R2/R3 selected subject: current agent permission,
   support group, duty, non-requester and recommendation revision. No eligible
   agent means escalation, not invented assignment or `NO_ONE_ON_DUTY` by inference.
6. Serialize timeout/review on the existing process/ticket order and prove one
   handle, audit rollback, restart recovery and off-duty races before enabling it.

The blocked path has no event enum in the live API or database for fabricated
`CRITICAL_AUTO_QUEUED`. Target OpenAPI retains the future contract.

## 4. Architecture

- Core is unchanged: identity, signature validation, workspace permissions,
  process serialization and audit remain technical mechanisms.
- HPDI public APIs `WorkAlerts` and `AlertProcessing` own reusable publication,
  recipient-private inbox/read, signed background execution and actual duty
  observation. Generic delivery has no Service Desk imports or SQL.
- Service Desk owns TicketAlerts, its monitor projection, business event rules,
  classification provenance and support-group mapping. It only uses public HPDI
  APIs; it does not read Core/HPDI/Flowable tables.
- Room Request is unchanged. The second-domain proof includes the same real
  HPDI alert API tests using its process key, with Service Desk excluded.
- HPDI enables its own scheduler; it does not rely on a Service Desk configuration.

## 5. Event / Recipient Matrix

| Event | Coordinator | Agent | Generation |
| --- | --- | --- | --- |
| CRITICAL_NEW approved | All current authorized Coordinators | Approved assignee, if currently authorized | Review task ID |
| CRITICAL_NEW AI advisory | Current authorized Coordinators | Not invented from a transient suggestion | Final attempt identity, or legacy assessment identity |
| START_OVERDUE | Yes | No | Review task ID |
| SLA_WARNING | Yes | Current assignee | Review task ID |
| SLA_BREACHED | Yes, CRITICAL severity | Current assignee | Review task ID |
| PENDING_TOO_LONG | No | Current assignee | Review + PAUSE instant |
| NO_ONE_ON_DUTY | Yes | No | Review + observed empty-group episode |
| ASSIGNEE_OFF_DUTY | Yes | No | Review + observed off-duty episode |

Labels do not copy ticket titles, descriptions, evidence or contact information.
AI labels explicitly say UNAPPROVED; reviewed labels explicitly say Coordinator
approved. Late legacy advice is serialized with review and suppressed once the
ticket is no longer awaiting a fresh decision. Degraded/ABSTAIN results do not
manufacture CRITICAL. Delivery resolves current directory membership/permissions;
it never turns directory results into caller roles or workflow authority.

## 6. Persistence Design

V21 adds `hpdi_alert_event` (transactional outbox) and `hpdi_alert_recipient`.
Event identity is unique on workspace/process/generation/type. Workspace/id is a
composite foreign key for recipient rows. UUIDs, references, severity, safe label,
created/retry/delivered timestamps and independent recipient read timestamps are
persisted in PostgreSQL. The same alert UUID can have multiple private receipts.

V22 adds app-owned `servicedesk_alert_monitor`: review/handle IDs, assignee,
pending instant, active flag, duty episodes and fair polling timestamp. Nothing
is backfilled: no synthetic alerts, historical pending time or fake SLA.
Consequently **only reviews performed after this migration are enrolled**.
Existing historical/active V20 rounds keep working but do not acquire R8 alerts
until a new reviewed round. Rollout/backfill of verified current work is a
separate readiness gate, not silently treated as full legacy coverage.

## 7. API / FE Handoff

Live HPDI OpenAPI promotes only the implemented paths and seven event types:

- `GET /api/hpdi/v1/workspaces/{workspace}/alerts`
- `POST /api/hpdi/v1/workspaces/{workspace}/alerts/{alertId}/read`

GET supports `since` (exclusive created-at bound), `unreadOnly`, zero-based page
and size 1-100. It returns items/page/size/total/unread; unread is the total visible
unread badge, independent of since/page filters. Ordering is createdAt DESC, UUID
DESC, with repeatable-read page/count consistency and no GET writes. Since is
not a lossless timestamp-only sync cursor: FE should poll the first unread page
or overlap timestamps and deduplicate UUIDs, rather than advancing past tied
timestamps. Offset pagination has ordinary concurrent-insert page drift.

Read is recipient-private, transactionally idempotent 204, and returns 404 for
another recipient/workspace/invisible domain reference. Fresh directory membership,
signed roles, current permission and process observer roles apply to **both**
paths, including badge totals. Existing FE APIs and error mapping are unchanged;
database constraint rejection uses the existing 409 mapping.

FE can poll without opening a queue, show CRITICAL severity prominently, preserve
UNAPPROVED wording and independently acknowledge alerts. No frontend/banner or
new Coordinator queue sorting is claimed in this backend delivery.

## 8. Scheduler And Concurrency

TicketSlaWorker remains unchanged and remains SLA reconciliation source of truth.
TicketAlertWorker checks at most 100 active monitor rows per pass, fairly ordered
by checked time. It obtains the actual duty snapshot outside the business row
transaction. TicketAlerts then uses existing WorkScheduling reconciliation to
serialize on process scope **before** monitor state, checks the current SLA round
and active native handle, and returns null: it does not change native scheduling.
START, PAUSE, RESUME, assignment and resolve update the monitor in the existing
business transaction. Rework gets a different review/handle generation.

AlertDeliveryWorker leases at most 100 due events using PostgreSQL SKIP LOCKED;
directory/token I/O happens outside recipient-delivery transactions. Delivery
locks one event, inserts recipients and audits atomically. Concurrent/restarted
workers are deduplicated. Failure/empty recipient sets retry after one minute;
rolled-back business events are never visible to the worker. Read locks only a
recipient receipt and audits only its first successful transition.

Defaults: `hpdi.alerts.scheduled=true`, `hpdi.alerts.poll-millis=5000`,
`service-desk.alerts.scheduled=true`, `service-desk.alerts.poll-millis=60000`.
This is bounded polling, not push or a hard real-time delivery guarantee. Token,
directory outage, backlog and retries can delay visible warnings. No Redis,
Kafka, background inference from GET, or replacement SLA calculations are added.

## 9. Migration Evidence

Latest fetched baseline is V20; additive V21 is in HPDI, V22 in Service Desk.
Clean install and upgrades V9-V20 are covered by migration integration tests:
validate old checksums/rows, empty new alert tables and repeat migrate=0. The
V14 duty upgrade assertion now correctly expects V22. Applied V1-V20 files are
untouched; no Flyway repair/history manipulation. Core-only excludes both new
capabilities/migrations; second-domain includes HPDI V21 but excludes app V22.

## 10. Security And Audit

Publication and business mutation share a transaction; created/delivered events
use existing system audit, read uses the authenticated subject. Trigger-induced
audit failures roll back events, recipients/read state, and reviewed handle/SLA.
Current workspace members cannot mark another recipient read; platform-admin
alone grants no inbox access. Domain observer-role removal hides old references
and unread totals even when the user remains an agent in the workspace.

Background delivery/evaluation uses the existing signature-validated ServiceIdentity
mechanism with a separate `alerts:process` capability. The local realm template
defines an `alert-worker` group carrying **only** that capability and enrolls the
existing service account in lab-a. No approve/assign/START permission is added to
that group. Existing issuers require an explicit operator-controlled realm/grant
update and configured `dx.background.token-url/client-id/client-secret`; otherwise
events remain pending, fail closed and retry. Do not reset a production realm.

## 11. Tests And Reproduction

Checked before final publication: full backend 728 tests, core-only 106,
second-domain 42, all zero failures/errors/skips, on an intermediate working
snapshot. Later hardening/tests require final committed-SHA reruns; those exact
results are recorded in the Draft PR, not inferred from these preliminary counts.

Reproduce with `scripts/test-task-execution.ps1` using profiles full, core-only,
second-domain. The full run covers ArchUnit, existing R5, AI-D2 final/intake,
canonical/follower, assignment/duty and real PostgreSQL/Flowable. Targeted classes:
WorkAlertsIntegrationTest, TicketAlertRulesTest, TicketIntegrationTest,
BackgroundTicketAssessmentTest, FinalAssessmentIntegrationTest and
ServiceDeskMigrationIntegrationTest. Rule tests use Clock.fixed; SQL integration
uses isolated PostgreSQL databases, not H2 or mocks of transaction semantics.

New checks include deadline equality/+1ns, no late-START false unstarted alert,
resolved suppression, pending >3 days, paused SLA, before/after five-minute
fail-closed advisory, stale/ABSTAIN advice, active work preservation, concurrent
evaluation/publication/delivery/read, independent recipients, cross-workspace
isolation, domain/membership revocation, actual signed user/service JWT, worker
retry/recreation, audit rollback and Room Request reuse. Auto-queue-specific
timeout/review/off-duty races remain **unimplemented and BLOCKED**, not passed.

`scripts/acceptance/sla-engine.py --alerts` (and `--alerts --intake`) runs isolated
PostgreSQL/Flowable/Keycloak plus signed JWT and actual workers. Build the current
jar to `.local/r7-r8-runtime.jar` first. This extends the existing R5 probe only
under a flag, retains its old checks and adds recipient/read/restart/403/CRITICAL
delivery checks. These are direct HTTP checks, **not Gateway E2E**. Logs, jars,
temporary credentials and HTTP captures stay in ignored `.local/`.

OpenAPI checking compares all four documents against the fetched baseline:
three existing HPDI-live validation errors, one HPDI-target error, no Service Desk
errors. New alert responses are separately validated against the live schema.
These baseline errors are not reported as a globally valid specification.

## 12. R5_R11_BUSINESS_ALIGNMENT

**R5:** `docs/ticket.md` illustrates deadlines from submission; TicketSlaService
initializes from Coordinator approval. Alerts deliberately reuse approved SLA
snapshots. Intake/unreviewed tickets therefore have no invented start/resolve SLA.
To standardize, product owners must ratify submission vs review origin and
legacy migration policy, then separately accept revised SLA math. This PR does
not change R5. The five-minute emergency clock is a separate unresolved authority
decision; it must not be implemented by moving the existing R5 start time.

**R11:** business text returns rework to processing in the old process; AI-D2
true-wait returns the same process to intake/review before the next handle. Alerts
retain that behavior, hide obsolete SLA rounds and key each new round separately.
To standardize, choose re-admission vs direct handling and approve the necessary
workflow/assessment/security changes independently. No rework auto-close or new
R11 state transition is added here.

## 13. Known Limitations / Findings

- P1: R7 auto-queue requires the authority/admission decisions in section 3.
- P1: pre-review suggested-agent alerts await a persisted authorized R1-R3 binding;
  advisory currently alerts Coordinators, not a guessed agent.
- P1: V20 active rounds are not enrolled/backfilled; the safe rollout must be
  explicitly accepted or implemented before claiming deployment-wide R8 coverage.
- P2: no new Coordinator queue-head ordering or FE prominent banner; event
  severity/contract enables a later frontend implementation.
- P2: duty monitoring is per active reviewed ticket and per observed episode;
  quick duty toggles between polls are not reconstructed, and group outages can
  create one event per affected monitored ticket. Intake-only groups are not scanned.
- P2: bounded polling can lag under directory/token outages/backlog; add operational
  delivery-latency monitoring and an explicit retention policy before broad rollout.
- P2: pre-existing whole-document HPDI OpenAPI validation debt remains unchanged.
- No P0 found in implemented paths; that is not a security certification.

## 14. Merge Readiness

The safe alert subset is offered for **READY FOR INTERNAL REVIEW** after final
committed verification. Overall business acceptance is **BLOCKED**, not READY FOR
INTERNAL MERGE, because R7 emergency admission, pre-review recipient binding and
legacy enrollment remain incomplete. Hosted CI is an independent gate: failures,
billing blocks or missing runs must be reported, never relabeled PASS. Gateway
E2E is not claimed unless separately executed. No merge or production deploy.
