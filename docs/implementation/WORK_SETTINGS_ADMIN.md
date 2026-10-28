# WORK SETTINGS & SERVICE DESK ADMIN SETTINGS

Date: 2026-10-28 (Asia/Saigon).
Branch: `feature/work-settings-admin`
Base commit: `develop@246a8d6676f4030b474e0f809fbb9ce652339cb8`
Status: **PHASE 1 COMPLETE (Survey, GAP Matrix, Hardcoded Values, Snapshot Invariants, Baseline Acceptance)**. Ready for Phase 2.

---

## 1. Survey & Architecture Overview

The system architecture separates concerns into three tiers:
- **Tier 1 (Core)**: Workflow Engine (Flowable), Identity (Keycloak), File Storage, Audit, Metadata.
- **Tier 2 (HPDI)**: Common capabilities reusable by any process application (Queues, Start/Pause/Resume/Release work intervals, Suggestions, Duty/Shift scheduling, SLA engine, Work Alerts, Common Work Settings). HPDI does NOT know about "tickets".
- **Tier 3 (Service Desk & Domain Apps)**: Ticket intake, domain assessment, domain classification, SLA calendar/policy snapshot binding, Service Desk specific settings.

### Contracts Inspected
1. `contracts/hpdi/openapi-target.yaml`:
   - Target route: `GET /api/hpdi/v1/settings/work` and `PUT /api/hpdi/v1/settings/work`
   - Scope: System-wide ("Cấu hình chung cho mọi workspace. Một bộ cho cả hệ thống"). No workspace parameter.
   - Schema: `WorkSettings` (version, updatedAt, updatedBy, assignment, shifts, sla, critical, alerts, closing, estimate).
   - Security: Bearer auth, requires `platform-admin`.
2. `contracts/openapi/servicedesk-target.yaml`:
   - Target route: `GET /settings` and `PUT /settings` under server `/api/apps/servicedesk/v1` (`/api/apps/servicedesk/v1/settings`).
   - Scope: Service Desk specific ("Mục 6, phần riêng Service Desk (platform-admin)"). No workspace parameter.
   - Schema: `ServiceDeskSettings` (version, updatedAt, updatedBy, categoryGroups, estimateDefaults).
   - Security: Bearer auth, requires `platform-admin`.

---

## 2. GAP Matrix

| Component / Setting | Target Specification | Current Baseline State (`develop@246a8d6`) | Planned Implementation |
|---|---|---|---|
| **HPDI WorkSettings Endpoint** | `GET /api/hpdi/v1/settings/work`, `PUT /api/hpdi/v1/settings/work` | 404 Not Found (Missing) | Implement controller, service, and persistence in Tier 2 (`vn.haui.dx.hpdi`) |
| **ServiceDeskSettings Endpoint** | `GET /api/apps/servicedesk/v1/settings`, `PUT /api/apps/servicedesk/v1/settings` | 404 Not Found (Missing) | Implement controller, service, and persistence in Tier 3 (`vn.haui.dx.apps.servicedesk`) |
| **RBAC Authorization** | Realm role `platform-admin` only. Non-admin -> 403 Forbidden | `CurrentUser.platformAdmin()` checks `realm_access.roles["platform-admin"]`. Used in `JdbcAuditService.search`, but no dedicated settings guard exists | Dedicated `AdminGuard` / security check verifying `CurrentUser.platformAdmin()`; rejects Requester/Agent/Coordinator |
| **CAS & Versioning** | Version integer; PUT requires matching version; conflict -> 409 Conflict | Not implemented for settings | Atomic compare-and-swap on `version` column; increment on write; 409 on mismatch |
| **Audit Logging** | Transactional audit on every PUT; failure rolls back settings change | `AuditService.record` requires non-blank workspace parameter (length <= 100) | Record audit with `workspace = "system"` or `"global"`, action `hpdi.work-settings.update` / `servicedesk.settings.update`, mandatory transaction |
| **Persistence Across Restart** | Database persistence with Flyway migrations | Migrations currently at `V31` (HPDI) and `V30` (Service Desk) | Add Flyway migration `V32__work_settings.sql` in HPDI and `V32__servicedesk_settings.sql` in Service Desk |
| **Assignment Settings** | `pendingLoadFactor`, `tieThresholdMinutes`, `maxInProgress`, `minRemainingMinutes` | Hardcoded: tie threshold = 15m in `AssignmentSuggestionController`, maxInProgress = 1 in `WorkExecution` | Persistent configuration schema with target defaults (0.5, 15, 1, 10) |
| **Shifts Settings** | `shifts`: array of `Shift(name, start, end)`. Default 07:00–11:30, 13:00–17:00 | Hardcoded shift strings in `TicketSlaConfiguration` | Stores default shift window templates for workspaces |
| **SLA Settings** | `targets` (LOW, MEDIUM, HIGH, CRITICAL), `escalateAfterRatio`, `maxEscalatedPriority`, `waitingExternalCounts` | Defined in `TicketSlaConfiguration` Spring properties | Stored in `WorkSettings.sla`; applies to new ticket initialization |
| **Critical Settings** | `autoQueueAfterMinutes` (min 1, default 5) | R7 Emergency Auto-Queue is strictly **BLOCKED** | Configuration stored and validated ONLY. Worker auto-queue remains disabled |
| **Alerts Settings** | `warnRemainingRatio` (0.2), `pendingRemindDays` (3), `reopenUnclaimedMinutes` (15) | Hardcoded: 0.2 in `SlaPolicy`, 3 days in `TicketAlerts`, 15m in `TicketConfirmation` | Configurable in `WorkSettings.alerts` |
| **Closing Settings** | `autoCloseAfterDays` (default 3) | 72 continuous hours (`Duration.ofHours(72)`) in `TicketConfirmation` | Preserves 72 continuous hours policy (3 * 24 hours), NOT 3 business days |
| **Estimate Settings** | `minSamples` (20), `windowMonths` (6), `outlierMinMinutes` (2), `outlierMaxMinutes` (480) | Documented in R2 specification | Stored in `WorkSettings.estimate` |
| **ServiceDesk CategoryGroups** | Mapping for `HARDWARE`, `NETWORK`, `SOFTWARE`, `ACCOUNT`, `OTHER` (`OTHER` nullable) | Hardcoded `switch` in `TicketAssignmentEligibility.supportGroup` | Dynamic mapping from `categoryGroups`; `OTHER = null` retains Coordinator manual choice |
| **ServiceDesk EstimateDefaults** | Default minutes table for 5 categories × 4 priorities | Hardcoded `switch` in `TicketReview.defaultMinutes` | Dynamic defaults from `estimateDefaults`; approved estimates (`COORDINATOR`) never modified |

---

## 3. Hardcoded Values & Configurability Analysis

| Setting Group | Field | Default in Target Contract | Current Hardcoded Location in Baseline | Configurability Impact |
|---|---|---|---|---|
| `assignment` | `pendingLoadFactor` | `0.5` | Not dynamically weighted in suggestions | Configurable in WorkSettings |
| `assignment` | `tieThresholdMinutes` | `15` | `AssignmentSuggestionController.rank` (`< 15`) | Configurable in WorkSettings |
| `assignment` | `maxInProgress` | `1` | Core execution constraint | Configurable in WorkSettings |
| `assignment` | `minRemainingMinutes` | `10` | Not parameterized | Configurable in WorkSettings |
| `shifts` | `shifts` | Sáng 07:00–11:30, Chiều 13:00–17:00 | `TicketSlaConfiguration` `@Value` property | Stored in WorkSettings |
| `sla` | `targets` | CRITICAL 15m/2h, HIGH 1h/8h, MEDIUM 4h/2d, LOW 1d/5d | `TicketSlaConfiguration` bean | Stored in WorkSettings |
| `sla` | `escalateAfterRatio`| `0.5` | `TicketSlaConfiguration` `@Value` | Stored in WorkSettings |
| `sla` | `maxEscalatedPriority`| `HIGH` | `TicketSlaConfiguration` `@Value` | Stored in WorkSettings |
| `sla` | `waitingExternalCounts`| `true` | `TicketSlaConfiguration` `@Value` | Stored in WorkSettings |
| `critical` | `autoQueueAfterMinutes`| `5` | None (R7 blocked) | Stored in WorkSettings; auto-queue disabled |
| `alerts` | `warnRemainingRatio`| `0.2` | `SlaPolicy.near` (`< budgetMinutes * 60000.0 * 0.2`) | Stored in WorkSettings |
| `alerts` | `pendingRemindDays`| `3` | `TicketAlerts.check` (`Duration.ofDays(3)`) | Stored in WorkSettings |
| `alerts` | `reopenUnclaimedMinutes`| `15` | `TicketConfirmation` (`reopenMinutes = 15`) | Stored in WorkSettings |
| `closing` | `autoCloseAfterDays`| `3` | `TicketConfirmation` (`Duration.ofHours(72)`) | Stored in WorkSettings (represents 72 continuous hours) |
| `estimate` | `minSamples` | `20` | `docs/ticket.md` R2 | Stored in WorkSettings |
| `estimate` | `windowMonths` | `6` | `docs/ticket.md` R2 | Stored in WorkSettings |
| `estimate` | `outlierMinMinutes` | `2` | `docs/ticket.md` R2 | Stored in WorkSettings |
| `estimate` | `outlierMaxMinutes` | `480` | `docs/ticket.md` R2 | Stored in WorkSettings |
| `categoryGroups` | `HARDWARE..OTHER` | `hardware`, `network`, `software`, `sysadmin`, `null` | `TicketAssignmentEligibility.supportGroup` | Stored in ServiceDeskSettings |
| `estimateDefaults` | `HARDWARE..OTHER` | Table R2 (45..120, 30..90, 30..60, 15..30, 30..90) | `TicketReview.defaultMinutes` | Stored in ServiceDeskSettings |

---

## 4. Snapshot Invariants & Business Safeguards

1. **SLA Snapshot Policy**:
   - Every ticket snapshots its complete `SlaPolicy` into PostgreSQL column `servicedesk_sla.policy` (JSONB) at review approval (`TicketSlaService.initialize`).
   - Active tickets compute remaining budgets, pauses, and breach projections against their own snapshotted policy.
   - Any modification to `WorkSettings.sla` takes effect **only for new ticket initializations**. Active tickets must NEVER have their deadlines, calendar, or targets modified retroactively.
2. **Work Estimate (ET) Snapshot Policy**:
   - Approved estimates are marked with `basis = 'COORDINATOR'` in `servicedesk_review`.
   - Modifying `ServiceDeskSettings.estimateDefaults` affects only new tickets entering review, or R9 category corrections when prior basis was `'DEFAULT'`.
   - Approved estimates (`COORDINATOR`) are NEVER silently rewritten.
3. **Shift Schedules Snapshot Policy**:
   - R3 schedules (`hpdi_agent_schedule` and `hpdi_agent_shift`) represent explicit dated intervals for individual agents.
   - Modifying `WorkSettings.shifts` configures default system templates; it does NOT alter existing agent shifts or cancel on-duty rosters.
4. **Auto-Close 72h Continuous Clock**:
   - `closing.autoCloseAfterDays = 3` must strictly maintain the existing R11 policy of 72 continuous UTC hours (`Duration.ofHours(72)`).
   - It MUST NOT be converted to 3 business days or depend on business calendars.
   - Active confirmation tasks maintain their persisted `confirm_deadline_at`.
5. **R7 Emergency Auto-Queue Safeguard**:
   - `critical.autoQueueAfterMinutes` is stored and validated as configuration ONLY.
   - Per architecture safety rules, emergency admission / auto-queue remains BLOCKED until human admission/authorization protocols are ratified. No background worker may automatically queue tickets based on this setting.
6. **Tier Decoupling**:
   - Tier 2 (`vn.haui.dx.hpdi`) must not import or reference any Tier 3 classes (`vn.haui.dx.apps.servicedesk`).
   - `mvn -Psecond-domain test` must compile and pass without any dependency on Service Desk.

---

## 5. Security & Platform-Admin RBAC Analysis

1. **Authentication Boundary**:
   - Endpoints require a valid signed JWT bearer token. Unauthenticated requests receive HTTP 401 Unauthorized.
2. **Role Verification**:
   - Core parses realm roles from JWT claim `realm_access.roles`.
   - `CurrentUser.platformAdmin()` returns `true` if and only if `platform-admin` is present in `realm_access.roles`.
   - Requesters, Agents, and Coordinators lack this realm role and receive HTTP 403 Forbidden.
3. **System Scope Audit**:
   - System-wide settings do not belong to a specific workspace.
   - `JdbcAuditService.record` requires a non-blank workspace string (`workspace.length() <= 100`).
   - Platform settings audit records use `workspace = "system"` or `"global"`.
   - All PUT operations execute within a mandatory database transaction. If audit event insertion fails, the entire settings transaction rolls back.

---

## 6. Business Decisions Required

| ID | Priority | Description | Proposed Resolution |
|---|---|---|---|
| BD-01 | P1 | Dynamic Worker Integration vs Safe Settings Storage | Store and serve settings via REST with CAS and audit; do not alter live worker loops in R5/R7/R8/R11 without independent regression sign-off. |
| BD-02 | P1 | R7 Emergency Auto-Queue Admission Hold | Retain explicit BLOCKED status for R7 emergency auto-queue worker execution. Settings field `autoQueueAfterMinutes` is stored only. |
| BD-03 | P2 | Auto-Close Unit Conversion | Strictly treat `autoCloseAfterDays = N` as `Duration.ofHours(N * 24)`, preserving continuous UTC hours without business day conversion. |
| BD-04 | P2 | Default Shift Templates vs Active Schedules | `WorkSettings.shifts` serves as default system shift templates; R3 dated agent shifts remain authoritative for duty evaluation. |

---

## 7. Baseline Test Acceptance Evidence

Run on baseline `develop@246a8d6676f4030b474e0f809fbb9ce652339cb8` prior to code modifications:

| Test Gate / Profile | Command | Total | PASS | FAIL | ERROR | SKIP | Result |
|---|---|---:|---:|---:|---:|---:|---|
| **Backend Full** | `mvn -B -o clean test` (with PG16.6) | 849 | 849 | 0 | 0 | 0 | **PASS** |
| **Core-only** | `mvn -B -o -Pcore-only clean test` | 106 | 106 | 0 | 0 | 0 | **PASS** |
| **Targeted R3/R5/R7/R9/R11/R12** | `test-task-execution.ps1 -Profile full -Test ...` | 215 | 215 | 0 | 0 | 0 | **PASS** |
| **Unfiltered Second-domain** | `mvn -B -o -Psecond-domain clean test` (with PG16.6) | 286 | 286 | 0 | 0 | 0 | **PASS** |
| **Tier Source Boundaries** | `python scripts/test-tier-boundaries.py` | 43 | 43 | 0 | 0 | 0 | **PASS** |
| **Tier Boundary Check** | `python scripts/check-tier-boundaries.py` | — | — | — | — | — | **PASS** |
| **Target ServiceDesk Contract** | `openapi_spec_validator contracts/openapi/servicedesk-target.yaml` | — | — | — | — | — | **PASS** |

Tested SHA: `246a8d6676f4030b474e0f809fbb9ce652339cb8`.
