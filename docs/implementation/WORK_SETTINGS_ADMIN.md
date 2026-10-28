# WORK SETTINGS & SERVICE DESK ADMIN SETTINGS — IMPLEMENTATION & ARCHITECTURE SPECIFICATION

- **Date**: 2026-10-28 (Asia/Saigon)
- **Base**: `develop@246a8d6676f4030b474e0f809fbb9ce652339cb8`
- **Branch**: `feature/work-settings-admin`
- **Status**: **PHASE 1 REMEDIATED — READY FOR PHASE 2 IMPLEMENTATION**
- **Hosted CI**: **BLOCKED** (Billing/external infrastructure gate; no self-issued waiver)

---

## 1. Executive Summary & Architecture Overview

The system architecture partitions administrative and process functionality into three strict tiers:
- **Tier 1 (Core)**: Workflow Engine (Flowable BPMN 2.0), Identity (Keycloak JWT validation, workspace directories), File Storage, Metadata, and Transactional Audit (`audit_event`). Core enforces process locks and task execution states.
- **Tier 2 (HPDI)**: Common operational capabilities reusable across any process domain (Queues, Work execution lifecycle START/PAUSE/RESUME/RELEASE, Agent suggestions, Shifts/Duty rosters, SLA calculation engine, Work Alerts, and Common Work Settings). HPDI does NOT know about "tickets".
- **Tier 3 (Service Desk & Domain Apps)**: Ticket intake, AI assessment binding, classification correction (R9), SLA policy binding (R5), Confirmation lifecycle (R11), Statistics (R12), and Service Desk specific settings.

```
+-----------------------------------------------------------------------------------+
| Tier 3: Service Desk (/api/apps/servicedesk/v1)                                   |
|   - ServiceDeskSettings (/settings)                                               |
|   - categoryGroups, estimateDefaults                                              |
+-----------------------------------------------------------------------------------+
                                         │ depends on Tier 2
                                         ▼
+-----------------------------------------------------------------------------------+
| Tier 2: HPDI (/api/hpdi/v1)                                                       |
|   - WorkSettings (/settings/work)                                                 |
|   - assignment, shifts, sla, critical, alerts, closing, estimate                  |
+-----------------------------------------------------------------------------------+
                                         │ depends on Tier 1
                                         ▼
+-----------------------------------------------------------------------------------+
| Tier 1: Core (/api/core/v1)                                                       |
|   - Workflow (Flowable), Identity (Keycloak), AuditService (audit_event)          |
+-----------------------------------------------------------------------------------+
```

---

## 2. Corrected GAP Matrix

| Area | Target OpenAPI Specification | Baseline State (`develop@246a8d6`) | Remediated Design & Target Implementation |
|---|---|---|---|
| **HPDI WorkSettings Endpoint** | `GET/PUT /api/hpdi/v1/settings/work` | 404 Not Found | REST controller in Tier 2 (`vn.haui.dx.hpdi.process.web`); returns/updates complete `WorkSettings` |
| **ServiceDeskSettings Endpoint** | `GET/PUT /api/apps/servicedesk/v1/settings` | 404 Not Found | REST controller in Tier 3 (`vn.haui.dx.apps.servicedesk`); returns/updates complete `ServiceDeskSettings` |
| **RBAC Authorization** | Realm role `platform-admin` only. Non-admin -> 403 Forbidden | `CurrentUser.platformAdmin()` inspects JWT `realm_access.roles`. No dedicated settings guard exists | Dedicated `AdminGuard` checking caller identity; fresh revocation enforcement; 401 on unauthenticated, 403 on non-admin |
| **Optimistic Locking & CAS** | `version` integer; PUT requires matching client version; mismatch -> 409 Conflict | Missing | Database-level atomic CAS (`UPDATE ... WHERE version = ?`). Version increments on successful update; 409 on mismatch |
| **Transactional Audit** | Every PUT records audit in same transaction; audit failure rolls back settings | `JdbcAuditService.record` requires non-blank workspace parameter (`workspace.length() <= 100`) | Record in reserved system scope (`workspace = "system:platform"`), preventing collision with regular workspaces. Propagation `MANDATORY` |
| **Database Migrations** | Persistent schema via Flyway migrations | Migrations end at `V31` (HPDI) and `V30` (Service Desk) | **Distinct versions**: `hpdi/V32__work_settings.sql` (HPDI) and `apps/servicedesk/V33__servicedesk_settings.sql` (Service Desk). No version 32 collision! |
| **Bootstrap & Default Seeding** | Initial defaults available on fresh install | Missing | Migrations V32 and V33 seed default row (version 1) with OpenAPI target defaults. GET returns defaults immediately |
| **Assignment Settings** | `pendingLoadFactor`, `tieThresholdMinutes`, `maxInProgress`, `minRemainingMinutes` | Hardcoded: tie threshold 15m in `AssignmentSuggestionController`, maxInProgress 1 in `FlowableWorkflowService` | Persisted in WorkSettings. `maxInProgress` kept as Core constraint in Phase 2; marked `PERSISTED_NOT_ENFORCED` |
| **Shifts Settings** | Array of `Shift(name, start, end)`. Default 07:00–11:30, 13:00–17:00 | Hardcoded shift strings in `TicketSlaConfiguration` | Stored as system-wide default shift templates; active agent rosters in R3 remain authoritative |
| **SLA Settings** | `targets` (LOW, MEDIUM, HIGH, CRITICAL), `escalateAfterRatio`, `maxEscalatedPriority`, `waitingExternalCounts` | Configured via Spring `@Value` in `TicketSlaConfiguration` | Stored in WorkSettings. SLA snapshots on active tickets (`servicedesk_sla.policy`) preserved; applies only to new reviews |
| **Critical Settings** | `autoQueueAfterMinutes` (min 1, default 5) | R7 Emergency Auto-Queue is strictly **BLOCKED** | Stored and validated as configuration ONLY. Worker auto-queue remains disabled |
| **Alerts Settings** | `warnRemainingRatio` (0.2), `pendingRemindDays` (3), `reopenUnclaimedMinutes` (15) | Hardcoded: 0.2 in `SlaPolicy`, 3 days in `TicketAlerts`, 15m in `TicketConfirmation` | Stored in WorkSettings. Marked `PERSISTED_NOT_ENFORCED` for live worker polling loops pending BD-01 ratification |
| **Closing Settings** | `autoCloseAfterDays` (default 3) | 72 continuous hours (`Duration.ofHours(72)`) in `TicketConfirmation` | Preserves strict 72 continuous hours invariant (`3 * 24h`), NOT 3 business days. Active confirmation deadlines preserved |
| **Estimate Settings** | `minSamples` (20), `windowMonths` (6), `outlierMinMinutes` (2), `outlierMaxMinutes` (480) | Documented in R2 specification | Stored in WorkSettings. Median worker integration deferred to domain task |
| **Category Groups** | Mapping `HARDWARE`, `NETWORK`, `SOFTWARE`, `ACCOUNT`, `OTHER` (`OTHER` nullable) | Hardcoded `switch` in `TicketAssignmentEligibility.supportGroup` | Stored in ServiceDeskSettings. `OTHER = null` retains Coordinator manual selection |
| **Estimate Defaults** | Default minutes table for 5 categories × 4 priorities | Hardcoded `switch` in `TicketReview.defaultMinutes` | Stored in ServiceDeskSettings. Approved estimates (`basis = 'COORDINATOR'`) are NEVER rewritten |

---

## 3. Revised Migrations Specification

### The Version Collision Hazard
In the Full profile (`mvn clean test`), Flyway scans `classpath:db/migration` recursively. It loads migrations from:
- `db/migration/` (V1–V6)
- `db/migration/apps/roomrequest/` (V7)
- `db/migration/apps/servicedesk/` (V8–V13, V15–V20, V22, V24, V27, V29, V30)
- `db/migration/hpdi/` (V14, V21, V23, V25, V26, V28, V31)

If both HPDI and Service Desk were to add a migration named `V32__...`, Flyway in the Full profile fails on bootstrap with:
`FlywayException: Found more than one migration with version 32`.

### Corrected Numbering Strategy
1. **Tier 2 (HPDI)**:
   - File: `backend/src/main/resources/db/migration/hpdi/V32__work_settings.sql`
   - Content: Creates table `hpdi_work_settings`, constraints, and seeds default row (`id = 1`, `version = 1`).
2. **Tier 3 (Service Desk)**:
   - File: `backend/src/main/resources/db/migration/apps/servicedesk/V33__servicedesk_settings.sql`
   - Content: Creates table `servicedesk_settings`, constraints, and seeds default row (`id = 1`, `version = 1`).

### Profile Discovery & Upgrade Behavior
- **Backend Full**: Migrations execute sequentially: `... -> V30 -> V31 -> V32 -> V33`. Validation succeeds, zero collisions.
- **Core-only**: Excludes all app and HPDI migrations; unaffected.
- **Second-domain (`-Psecond-domain`)**: Excludes `db/migration/apps/servicedesk/**`. Discovers V1..V4, V7, V14, V21, V23, V25, V26, V28, V31, and `V32__work_settings.sql`. It does NOT load V33. Runs cleanly without gaps or errors.
- **Existing Migrations (V1–V31)**: Completely untouched. Checksums remain identical.

### DDL & Seed Blueprint

```sql
-- V32__work_settings.sql
CREATE TABLE hpdi_work_settings (
    id integer PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    version bigint NOT NULL,
    assignment jsonb NOT NULL,
    shifts jsonb NOT NULL,
    sla jsonb NOT NULL,
    critical jsonb NOT NULL,
    alerts jsonb NOT NULL,
    closing jsonb NOT NULL,
    estimate jsonb NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT current_timestamp,
    updated_by varchar(100) NOT NULL
);

INSERT INTO hpdi_work_settings (id, version, assignment, shifts, sla, critical, alerts, closing, estimate, updated_at, updated_by)
VALUES (
    1,
    1,
    '{"pendingLoadFactor": 0.5, "tieThresholdMinutes": 15, "maxInProgress": 1, "minRemainingMinutes": 10}'::jsonb,
    '[{"name": "Sáng", "start": "07:00", "end": "11:30"}, {"name": "Chiều", "start": "13:00", "end": "17:00"}]'::jsonb,
    '{"targets": {"LOW": {"startWithinMinutes": 480, "resolveWithinMinutes": 2400}, "MEDIUM": {"startWithinMinutes": 240, "resolveWithinMinutes": 960}, "HIGH": {"startWithinMinutes": 60, "resolveWithinMinutes": 480}, "CRITICAL": {"startWithinMinutes": 15, "resolveWithinMinutes": 120}}, "escalateAfterRatio": 0.5, "maxEscalatedPriority": "HIGH", "waitingExternalCounts": true}'::jsonb,
    '{"autoQueueAfterMinutes": 5}'::jsonb,
    '{"warnRemainingRatio": 0.2, "pendingRemindDays": 3, "reopenUnclaimedMinutes": 15}'::jsonb,
    '{"autoCloseAfterDays": 3}'::jsonb,
    '{"minSamples": 20, "windowMonths": 6, "outlierMinMinutes": 2, "outlierMaxMinutes": 480}'::jsonb,
    current_timestamp,
    'system:bootstrap'
);
```

```sql
-- V33__servicedesk_settings.sql
CREATE TABLE servicedesk_settings (
    id integer PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    version bigint NOT NULL,
    category_groups jsonb NOT NULL,
    estimate_defaults jsonb NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT current_timestamp,
    updated_by varchar(100) NOT NULL
);

INSERT INTO servicedesk_settings (id, version, category_groups, estimate_defaults, updated_at, updated_by)
VALUES (
    1,
    1,
    '{"HARDWARE": "hardware", "NETWORK": "network", "SOFTWARE": "software", "ACCOUNT": "sysadmin", "OTHER": null}'::jsonb,
    '{"HARDWARE": {"LOW": 45, "MEDIUM": 60, "HIGH": 90, "CRITICAL": 120}, "NETWORK": {"LOW": 30, "MEDIUM": 45, "HIGH": 60, "CRITICAL": 90}, "SOFTWARE": {"LOW": 30, "MEDIUM": 30, "HIGH": 45, "CRITICAL": 60}, "ACCOUNT": {"LOW": 15, "MEDIUM": 15, "HIGH": 20, "CRITICAL": 30}, "OTHER": {"LOW": 30, "MEDIUM": 45, "HIGH": 60, "CRITICAL": 90}}'::jsonb,
    current_timestamp,
    'system:bootstrap'
);
```

---

## 4. Security & Role Revocation Policy

### Three Security States
1. **Authenticated**: The incoming request presents a cryptographically valid, non-expired Bearer JWT signed by the trusted Keycloak realm (`dx-lab`).
2. **Statically Authorized**: The validated JWT claims contain `realm_access.roles` including `"platform-admin"`.
3. **Freshly Authorized**: Live authorization status verified at request processing time to detect whether the realm role was revoked after the JWT was issued.

### Fresh Revocation Design Options

#### Option A: Online Fresh Role Validation (Preferred Security Architecture)
- **Mechanism**: On every administrative mutation (PUT), the backend verifies the caller's active realm roles against Keycloak Admin API using the existing backend service identity (`dx-directory`).
- **Endpoint**: `GET /admin/realms/{realm}/users/{userId}/role-mappings/realm`.
- **Fail-Closed Principle**: If Keycloak is unreachable, times out, or returns 5xx, the request fails closed with HTTP 503 Service Unavailable. Revoked administrators are blocked immediately, even with a valid unexpired JWT.
- **Service Identity Scope**: Requires `dx-directory` service account to possess `view-users` / `query-users` client role on `realm-management` (already present in `realm-template.json`).

#### Option B: Token TTL & Windowed Revocation (Standard OIDC Architecture)
- **Mechanism**: The backend verifies authenticity and `platform-admin` presence via signed claims in `CurrentUser.platformAdmin()`. Revocation takes effect upon token expiration.
- **Token TTL**: Keycloak default access token lifespan is 5 minutes (300 seconds).
- **Revocation Lag**: Up to 300 seconds window where a revoked admin with an active token could mutate settings.
- **Mitigation**: Critical settings mutations require CAS versioning and mandatory transactional audit logging, ensuring attribution to the caller's subject UUID and immediate forensic visibility.
- **Owner Sign-off**: Requires explicit owner decision (BD-05) if online verification latency overhead (~20-50ms) is deemed undesirable for administrative endpoints.

### Real Keycloak Integration Test Strategy
Unit tests mocking `Jwt` or `CurrentUser` do NOT prove real Keycloak role revocation.
Acceptance must include integration tests against a real Keycloak instance (similar to `scripts/test-r3-runtime.py`):
1. Obtain real JWT for `superadmin` (has `platform-admin`).
2. Verify GET and PUT `/api/hpdi/v1/settings/work` succeed (HTTP 200).
3. Via Keycloak Admin API, revoke `platform-admin` from test user.
4. Attempt PUT `/api/hpdi/v1/settings/work` with the old unexpired JWT:
   - Under Option A: Immediately returns HTTP 403 Forbidden.
   - Under Option B: Validates that after token TTL expiry and refresh attempt, Keycloak refuses to issue `platform-admin` claim, and subsequent requests return HTTP 403 Forbidden.
5. Restore test user roles in cleanup.

---

## 5. System Audit Scope Specification

### The Workspace Collision Vulnerability
In `V1__core.sql`, `audit_event` has column `workspace varchar(100) NOT NULL`.
In `JwtCurrentUser.java`, workspace group regex is:
`^/workspaces/([a-z0-9-]{1,50})/([a-z0-9-]{1,30})$`
Regular workspaces consist of lowercase letters, numbers, and hyphens.

If the audit implementation were to use `"system"` or `"global"` as the workspace identifier:
1. An administrator could create a Keycloak workspace named `system` (`/workspaces/system/coordinator`).
2. A coordinator of that workspace could call `GET /api/core/v1/workspaces/system/audit`.
3. `JdbcAuditService.search` would return the system-wide platform settings audit events to that workspace coordinator, violating tenant isolation!

### Reserved System Scope Design
The system-level audit scope must use a reserved namespace that **cannot be formed by any valid workspace pattern**:
- **Designated Scope**: `system:platform` (contains a colon `:`, which is strictly prohibited in workspace group regex `[a-z0-9-]{1,50}`).
- **Validation Compliance**: `JdbcAuditService.require("system:platform", 100, "workspace")` passes (`length = 15 <= 100`).
- **Tenant Protection**: No workspace URL `/workspaces/{workspace}/audit` can ever query `system:platform` because the path regex rejects colons.
- **Coordinator Isolation**: No Keycloak group `/workspaces/system:platform/*` can be parsed by `JwtCurrentUser`.
- **Administrative Query**: Only a caller with `CurrentUser.platformAdmin() == true` can query platform audit logs.

### Audit Transactional Invariant
- **Actor Attribution**: Real authenticated user ID (`access.current().sub()`), never synthetic `"system"`.
- **Request Tracing**: `RequestContext.requestId()` preserves the caller's `X-Request-ID`.
- **Mandatory Rollback**: Audit insertion uses `Propagation.MANDATORY`. If audit insertion fails (e.g., database constraint or connection drop), the entire settings update transaction rolls back. Neither settings nor audit changes persist.

---

## 6. Settings Application Matrix

| Setting Field | Target Default | Source of Truth | Storage/API in Phase 2 | Runtime Consumer Wired? | Applies to New Ops? | Affects Active Ops? | Snapshot & Provenance Policy | Required Decision |
|---|---|---|---|---|---|---|---|---|
| `assignment.pendingLoadFactor` | `0.5` | `hpdi_work_settings` | Yes | `PERSISTED_NOT_ENFORCED` | No | No | Dynamic weighting deferred to suggestions engine | BD-01 |
| `assignment.tieThresholdMinutes`| `15` | `hpdi_work_settings` | Yes | `PERSISTED_NOT_ENFORCED` | No | No | Currently hardcoded 15m in `AssignmentSuggestionController` | BD-01 |
| `assignment.maxInProgress` | `1` | Core `@Value` / DB | Yes | `PERSISTED_NOT_ENFORCED` | No | No | Core enforces max 1 in `FlowableWorkflowService` | BD-01 |
| `assignment.minRemainingMinutes`| `10` | `hpdi_work_settings` | Yes | `PERSISTED_NOT_ENFORCED` | No | No | Queue filter threshold deferred | BD-01 |
| `shifts.shifts` | Sáng/Chiều | `hpdi_work_settings` | Yes | `PERSISTED_NOT_ENFORCED` | No | No | Configures default template; R3 dated rosters authoritative | BD-04 |
| `sla.targets.LOW` | 480m / 2400m | `servicedesk_sla` / DB | Yes | `PERSISTED_NOT_ENFORCED` | No | No | Snapshotted on ticket approval in `servicedesk_sla.policy` | BD-01 |
| `sla.targets.MEDIUM` | 240m / 960m | `servicedesk_sla` / DB | Yes | `PERSISTED_NOT_ENFORCED` | No | No | Snapshotted on ticket approval in `servicedesk_sla.policy` | BD-01 |
| `sla.targets.HIGH` | 60m / 480m | `servicedesk_sla` / DB | Yes | `PERSISTED_NOT_ENFORCED` | No | No | Snapshotted on ticket approval in `servicedesk_sla.policy` | BD-01 |
| `sla.targets.CRITICAL` | 15m / 120m | `servicedesk_sla` / DB | Yes | `PERSISTED_NOT_ENFORCED` | No | No | Snapshotted on ticket approval in `servicedesk_sla.policy` | BD-01 |
| `sla.escalateAfterRatio` | `0.5` | `servicedesk_sla` / DB | Yes | `PERSISTED_NOT_ENFORCED` | No | No | Snapshotted on ticket approval | BD-01 |
| `sla.maxEscalatedPriority` | `HIGH` | `servicedesk_sla` / DB | Yes | `PERSISTED_NOT_ENFORCED` | No | No | Snapshotted on ticket approval | BD-01 |
| `sla.waitingExternalCounts` | `true` | `servicedesk_sla` / DB | Yes | `PERSISTED_NOT_ENFORCED` | No | No | Snapshotted on ticket approval | BD-01 |
| `critical.autoQueueAfterMinutes`| `5` | `hpdi_work_settings` | Yes | **BLOCKED** | No | No | Configuration ONLY. R7 auto-queue worker NOT wired | BD-02 |
| `alerts.warnRemainingRatio` | `0.2` | `hpdi_work_settings` | Yes | `PERSISTED_NOT_ENFORCED` | No | No | Reused in `SlaPolicy.near`; worker polling kept stable | BD-01 |
| `alerts.pendingRemindDays` | `3` | `hpdi_work_settings` | Yes | `PERSISTED_NOT_ENFORCED` | No | No | Hardcoded `Duration.ofDays(3)` in `TicketAlerts` | BD-01 |
| `alerts.reopenUnclaimedMinutes`| `15` | `hpdi_work_settings` | Yes | `PERSISTED_NOT_ENFORCED` | No | No | Hardcoded 15m in `TicketConfirmation` | BD-01 |
| `closing.autoCloseAfterDays` | `3` | `hpdi_work_settings` | Yes | `PERSISTED_NOT_ENFORCED` | No | No | Represents 72 continuous hours. Confirmation deadline fixed | BD-03 |
| `estimate.minSamples` | `20` | `hpdi_work_settings` | Yes | `PERSISTED_NOT_ENFORCED` | No | No | R2 sample threshold; calculation worker not modified | BD-01 |
| `estimate.windowMonths` | `6` | `hpdi_work_settings` | Yes | `PERSISTED_NOT_ENFORCED` | No | No | R2 sample window; calculation worker not modified | BD-01 |
| `estimate.outlierMinMinutes` | `2` | `hpdi_work_settings` | Yes | `PERSISTED_NOT_ENFORCED` | No | No | Outlier trimming; calculation worker not modified | BD-01 |
| `estimate.outlierMaxMinutes` | `480` | `hpdi_work_settings` | Yes | `PERSISTED_NOT_ENFORCED` | No | No | Outlier trimming; calculation worker not modified | BD-01 |
| `categoryGroups.HARDWARE` | `hardware` | `servicedesk_settings` | Yes | `PERSISTED_NOT_ENFORCED` | No | No | Mapping category -> group. Hardcoded switch kept in Phase 2 | BD-01 |
| `categoryGroups.NETWORK` | `network` | `servicedesk_settings` | Yes | `PERSISTED_NOT_ENFORCED` | No | No | Mapping category -> group. Hardcoded switch kept in Phase 2 | BD-01 |
| `categoryGroups.SOFTWARE` | `software` | `servicedesk_settings` | Yes | `PERSISTED_NOT_ENFORCED` | No | No | Mapping category -> group. Hardcoded switch kept in Phase 2 | BD-01 |
| `categoryGroups.ACCOUNT` | `sysadmin` | `servicedesk_settings` | Yes | `PERSISTED_NOT_ENFORCED` | No | No | Mapping category -> group. Hardcoded switch kept in Phase 2 | BD-01 |
| `categoryGroups.OTHER` | `null` | `servicedesk_settings` | Yes | `PERSISTED_NOT_ENFORCED` | No | No | OTHER is nullable; Coordinator manual selection preserved | BD-01 |
| `estimateDefaults.HARDWARE` | 45..120m | `servicedesk_settings` | Yes | `PERSISTED_NOT_ENFORCED` | No | No | Table R2 defaults. Approved estimates NEVER rewritten | BD-01 |
| `estimateDefaults.NETWORK` | 30..90m | `servicedesk_settings` | Yes | `PERSISTED_NOT_ENFORCED` | No | No | Table R2 defaults. Approved estimates NEVER rewritten | BD-01 |
| `estimateDefaults.SOFTWARE` | 30..60m | `servicedesk_settings` | Yes | `PERSISTED_NOT_ENFORCED` | No | No | Table R2 defaults. Approved estimates NEVER rewritten | BD-01 |
| `estimateDefaults.ACCOUNT` | 15..30m | `servicedesk_settings` | Yes | `PERSISTED_NOT_ENFORCED` | No | No | Table R2 defaults. Approved estimates NEVER rewritten | BD-01 |
| `estimateDefaults.OTHER` | 30..90m | `servicedesk_settings` | Yes | `PERSISTED_NOT_ENFORCED` | No | No | Table R2 defaults. Approved estimates NEVER rewritten | BD-01 |

*Note: In Phase 2, all 31 fields are fully implemented in persistence, validation, CAS updates, and REST APIs. Fields marked `PERSISTED_NOT_ENFORCED` store and serve valid administrative configuration but are intentionally not wired into background workers to prevent unauthorized disruption to verified R3/R5/R7/R9/R11/R12 workflows.*

---

## 7. Runtime Compatibility & Snapshot Boundaries

### 1. SLA Policy Snapshotting
- **Creation Anchor**: When a ticket review is approved in `TicketReviewService.review`, `TicketSlaService.initialize` captures the complete active `SlaPolicy` object and serializes it as JSONB into `servicedesk_sla.policy`.
- **Immutability Invariant**: Active tickets evaluate all deadline checks, pause intervals, and R9 amendments against their own persisted `state.policy()`.
- **Forward Compatibility**: Updates to `WorkSettings.sla` will configure the SLA template for future reviews. They NEVER modify existing tickets, their calculated deadlines (`start_by`, `resolve_by`), or historical breach flags.

### 2. Work Estimates (ET) Semantics
- **Dual Basis Model**:
  - `basis = 'DEFAULT'`: Assigned automatically during ticket review from standard lookup tables when the Coordinator inputs no custom estimate.
  - `basis = 'COORDINATOR'`: Set when the Coordinator explicitly provides an estimate, approves an ET adjustment request (`TicketWorkEstimate.approve`), or when modified during review.
- **R9 Classification Correction**: If a ticket category changes, an estimate with basis `'DEFAULT'` is recalculated according to the new category defaults. An estimate with basis `'COORDINATOR'` is strictly PRESERVED.
- **Settings Invariant**: Modifying `ServiceDeskSettings.estimateDefaults` updates the default table for subsequent reviews. It NEVER overwrites approved estimates (`COORDINATOR`).

### 3. Shift Schedules
- **Explicit R3 Rosters**: Agent shifts are stored as explicit absolute instants in `hpdi_agent_shift` (`starts_at`, `ends_at`, `timezone`).
- **Templates vs Assignments**: `WorkSettings.shifts` defines system-wide template presets (e.g. Sáng 07:00–11:30, Chiều 13:00–17:00). Modifying `WorkSettings.shifts` does NOT alter already-created agent shifts or cancel active on-duty statuses.

### 4. Confirmation & Auto-Close
- **Continuous 72-Hour Clock**: R11 establishes that confirmation deadlines expire after exactly 72 continuous UTC hours (`Duration.ofHours(72)`), NOT 3 business days.
- **Enrollment Anchor**: `confirm_deadline_at = confirm_started_at + 72 hours` is written to `servicedesk_confirmation` when the handle task completes.
- **In-flight Safety**: Changing `WorkSettings.closing.autoCloseAfterDays` will not recalculate or shorten the deadline of existing active confirmation tasks.

### 5. R7 Emergency Auto-Queue Safeguard
- **Blocked Status**: R7 Emergency Auto-Queue remains **BLOCKED** because independent human authorization and admission protocols are not ratified.
- **Safe Storage**: Setting `critical.autoQueueAfterMinutes` is validated (integer >= 1) and persisted. No background timer, worker loop, or Flowable event is attached to trigger auto-queue.

---

## 8. Core / HPDI Tier Boundary Design

### The `maxInProgress` Boundary Problem
In `FlowableWorkflowService.java` (Tier 1 Core):
```java
@org.springframework.beans.factory.annotation.Value("${dx.workflow.max-in-progress:1}")
private int maxInProgress = 1;
...
long active = tasks.createTaskQuery().taskAssignee(user.sub())
    .taskVariableValueEquals(WORK_STATE, TaskExecution.State.IN_PROGRESS.name()).count();
if (active >= maxInProgress) throw CoreException.conflict("Active work limit reached");
```

Core sits at Tier 1. HPDI sits at Tier 2.
- **Architectural Rule**: Strict downward dependency: `Tier 3 -> Tier 2 -> Tier 1`. Tier 1 Core MUST NOT depend on Tier 2 HPDI.
- **Violation Risk**: If `FlowableWorkflowService` were to inject `WorkSettings` from HPDI, Core would depend directly on HPDI. This violates architectural constraints and breaks the `-Pcore-only` Maven build.

### Inversion Design vs Phase 2 Safety
- **Dependency Inversion Pattern**: To make `maxInProgress` dynamic without breaking boundaries:
  1. Core declares an interface: `public interface WorkExecutionPolicy { int maxInProgress(String workspace); }`.
  2. Core provides a default fallback implementation reading `@Value("${dx.workflow.max-in-progress:1}")`.
  3. HPDI or assembly module provides an optional bean implementing `WorkExecutionPolicy` reading from `WorkSettings`.
- **Phase 2 Safe Decision**: Modifying Core execution locking in Phase 2 introduces risk to already-accepted task execution tests. Therefore, in Phase 2:
  - Core constraint remains unchanged (`@Value("${dx.workflow.max-in-progress:1}")` with PostgreSQL transaction advisory lock).
  - WorkSettings field `assignment.maxInProgress` is persisted and served via API, but marked `PERSISTED_NOT_ENFORCED`.
  - Core dependency inversion is deferred to a dedicated workflow configuration initiative.

---

## 9. Open Business Decisions & Chosen Safe Defaults

| ID | Priority | Topic | Description & Impact | Chosen Safe Default | Owner Sign-Off Status |
|---|---|---|---|---|---|
| **BD-01** | P1 | Storage-Only vs Dynamic Worker Wiring | Wiring settings into background workers (SLA timers, alert monitors) could alter verified R5/R7/R8/R11 runtime behavior. | **PERSISTED_NOT_ENFORCED**: Store and serve settings via REST; leave active workers on verified baselines. | Pending Owner Review |
| **BD-02** | P1 | R7 Emergency Auto-Queue Execution | `critical.autoQueueAfterMinutes` exists in target OpenAPI, but automated ticket admission without human review is prohibited. | **BLOCKED**: Save setting to DB; do NOT activate auto-queue worker. | Ratified in ADR / Task Spec |
| **BD-03** | P2 | Auto-Close Unit & Business Calendar | OpenAPI specifies `autoCloseAfterDays` as integer days. R11 mandates 72 continuous UTC hours. | Strictly treat `days` as `days * 24` continuous hours. Never evaluate against business calendar. | Ratified in R11 Spec |
| **BD-04** | P2 | Shift Templates vs Dated Rosters | `WorkSettings.shifts` provides recurring time strings. R3 uses explicit dated intervals. | `WorkSettings.shifts` acts as default templates; R3 dated rosters remain authoritative for agent duty. | Ratified in R3 Spec |
| **BD-05** | P1 | Platform-Admin Role Revocation Strategy | Choose between Option A (online Keycloak role verification per PUT, fail-closed) and Option B (token TTL windowed revocation). | Option A planned for Phase 2 implementation; fail-closed on Keycloak outage. | Awaiting Owner Ratification |

---

## 10. Test Strategy & Exact Acceptance Gates

### Baseline Test Evidence (Commit `246a8d6676f4030b474e0f809fbb9ce652339cb8`)
Before making code changes, the baseline test suite was executed against disposable PostgreSQL 16.6 fixtures:

| Test Profile / Suite | Command | Total | PASS | FAIL | ERROR | SKIP | Result |
|---|---|---:|---:|---:|---:|---:|---|
| **Backend Full** | `mvn -B -o clean test` (with PG16.6) | 849 | 849 | 0 | 0 | 0 | **PASS** |
| **Core-only** | `mvn -B -o -Pcore-only clean test` | 106 | 106 | 0 | 0 | 0 | **PASS** |
| **Targeted R3/R5/R7/R9/R11/R12** | `test-task-execution.ps1 -Profile full -Test ...` | 215 | 215 | 0 | 0 | 0 | **PASS** |
| **Unfiltered Second-domain** | `mvn -B -o -Psecond-domain clean test` (with PG16.6) | 286 | 286 | 0 | 0 | 0 | **PASS** |
| **Tier Source Boundaries** | `python scripts/test-tier-boundaries.py` | 43 | 43 | 0 | 0 | 0 | **PASS** |
| **Tier Boundary Check** | `python scripts/check-tier-boundaries.py` | — | — | — | — | — | **PASS** |
| **Target ServiceDesk Contract** | `openapi_spec_validator contracts/openapi/servicedesk-target.yaml` | — | — | — | — | — | **PASS** |

### Phase 2 Acceptance Gates (Target DoD)
Upon implementing Phase 2, the following exact gates must be satisfied with concrete execution evidence:
1. **Migration Verification**: Clean install and upgrade from V31 -> V32 -> V33 across all three profiles (Full, Core-only, Second-domain). Zero duplicate version errors.
2. **REST API Contract Verification**:
   - `GET /api/hpdi/v1/settings/work` returns 200 with complete seeded `WorkSettings`.
   - `PUT /api/hpdi/v1/settings/work` with valid version updates row, increments version, records audit.
   - `GET /api/apps/servicedesk/v1/settings` returns 200 with complete seeded `ServiceDeskSettings`.
   - `PUT /api/apps/servicedesk/v1/settings` with valid version updates row, increments version, records audit.
3. **Optimistic Locking (CAS) Verification**: Two concurrent PUT requests with the same version result in exactly one HTTP 200 and one HTTP 409 Conflict.
4. **RBAC & Fresh Revocation**:
   - Unauthenticated request returns 401 Unauthorized.
   - Requester, Agent, Coordinator tokens return 403 Forbidden.
   - User with revoked `platform-admin` role returns 403 Forbidden.
5. **Transactional Audit Rollback**: Simulating an audit failure during settings PUT rolls back database modifications (settings retain previous version and values).
6. **Restart Persistence**: Updating settings, stopping backend, and restarting verifies updated settings persist in database.
7. **Regression Suite**: Backend Full (849+ tests), Core-only (106+ tests), and Second-domain (286+ tests) must pass with zero failures and zero unexpected skips.

---

## 11. Phase 2 Implementation Plan (Commit-by-Commit)

The implementation of Phase 2 will proceed strictly across discrete, test-verified commits:

### Commit 1: HPDI WorkSettings (Tier 2)
- Add Flyway migration `backend/src/main/resources/db/migration/hpdi/V32__work_settings.sql`.
- Implement `WorkSettings` record and nested records (`AssignmentSettings`, `ShiftSettings`, `SlaSettings`, `CriticalSettings`, `AlertSettings`, `ClosingSettings`, `EstimateSettings`) in `vn.haui.dx.hpdi.process.api`.
- Implement `WorkSettingsService` and `JdbcWorkSettingsService` with atomic CAS update (`UPDATE ... WHERE version = ?`).
- Implement `WorkSettingsController` (`GET/PUT /api/hpdi/v1/settings/work`) with `AdminGuard` role check and transactional audit to `system:platform`.
- Add unit tests: `WorkSettingsValidationTest` and `WorkSettingsCasTest`.

### Commit 2: ServiceDeskSettings (Tier 3)
- Add Flyway migration `backend/src/main/resources/db/migration/apps/servicedesk/V33__servicedesk_settings.sql`.
- Implement `ServiceDeskSettings` record, `CategoryGroups`, and `EstimateDefaults` in `vn.haui.dx.apps.servicedesk`.
- Implement `ServiceDeskSettingsService` with atomic CAS update.
- Implement `ServiceDeskSettingsController` (`GET/PUT /api/apps/servicedesk/v1/settings`) with `AdminGuard` check and transactional audit to `system:platform`.
- Add unit tests: `ServiceDeskSettingsValidationTest` and `ServiceDeskSettingsCasTest`.

### Commit 3: Integration Tests, RBAC & Runtime Verification
- Add integration tests verifying:
  - RBAC: 401 for anonymous, 403 for requester/agent/coordinator, 200 for platform-admin.
  - CAS concurrency: simultaneous updates test.
  - Audit rollback: injected audit failure test.
  - Restart persistence: verified via containerized test harness.
  - Migration upgrade: test upgrading from V31 through V33.
- Run complete test suite across Backend Full, Core-only, and Second-domain.

### Commit 4: OpenAPI Contract Promotion & Final Evidence
- Promote `/api/hpdi/v1/settings/work` to `contracts/hpdi/openapi.yaml`.
- Promote `/settings` to `contracts/openapi/servicedesk.yaml`.
- Update `docs/implementation/WORK_SETTINGS_ADMIN.md` with final test execution logs, test counts, and tested commit SHA.
