# Work Settings / Service Desk Admin - Corrected Phase 1 Design

Date: 2026-10-28 (Asia/Saigon). Branch: `feature/work-settings-admin`.
Runtime baseline: `246a8d6676f4030b474e0f809fbb9ce652339cb8`.
Reviewed document: `06c3996cd159430bb4febd62b65bba780ba089f7`.
Sections 1-9 retain the historical Phase 1 design and baseline evidence; their
NOT_IMPLEMENTED/NOT_RUN labels are historical, not current delivery status.
Current Phase 2 implementation, evidence and remaining limits are in sections
10-13. Both settings APIs and V32/V33 are implemented as storage-only foundation.

> **Phase 3 – runtime wiring (2026-10-28, `feature/FE-08-ticket-workflow`).** Settings are now
> ENFORCED for new operations; stored snapshots stay unchanged. Supersedes every
> PERSISTED_NOT_ENFORCED statement below.
>
> | Setting | Runtime consumer | Applies to |
> | --- | --- | --- |
> | assignment.pendingLoadFactor, minRemainingMinutes | `TicketReviewService.remainingMinutes` | every queue / load read |
> | assignment.tieThresholdMinutes | `AssignmentSuggestionController.rank` | every suggestion |
> | assignment.maxInProgress | `core.workflow.api.ExecutionLimits` ← `WorkPolicyReader` (property is fallback) | every start / resume |
> | shifts, sla.*, alerts.warnRemainingRatio | `TicketSlaConfiguration.current()` → `servicedesk_sla.policy` snapshot (`r5-settings-v<version>`) | reviews after the change; reviewed work keeps its snapshot |
> | critical.autoQueueAfterMinutes | `TicketAlerts.remindUnreviewed` (`servicedesk_critical_watch`, V36) emits `CRITICAL_UNREVIEWED` (V35) to Coordinators once | AI-advised CRITICAL still awaiting review. **No auto-review / auto-queue** (AI keeps no operational authority) |
> | alerts.pendingRemindDays | `TicketAlerts.check` | every monitor pass |
> | alerts.reopenUnclaimedMinutes, closing.autoCloseAfterDays | `TicketConfirmation` (persisted deadlines; audit label `r11-<hours>h-v1`) | confirmations / reworks created after the change |
> | estimate.* | `TicketEstimates`: median `worked_millis` of closed tickets (same workspace, category, priority, within window, outliers dropped) when samples ≥ minSamples, basis `SIMILAR` + `sampleSize` (V34) | reviews and default-basis corrections after the change |
> | categoryGroups | `ServiceDeskPolicy` → eligibility, suggestion, alerts, assignment cursor, `classification.group` | immediately |
> | estimateDefaults | `ServiceDeskPolicy` → `TicketEstimates` fallback, `GET /tickets/{id}/estimate-default` | reviews after the change |
>
> Readers cache 5 s and are invalidated after commit. Working days, holidays and zone remain
> properties (`service-desk.sla.*`); `service-desk.sla.shifts`, `escalate-after-ratio`,
> `max-escalated-priority`, `waiting-external-counts` and `serviceDesk.confirmation.reopen-minutes` are no longer read.

## 1. Approved Scope And GAP Matrix

BD-01 is APPROVED by the owner's FINAL PHASE 1 CORRECTION request.
Phase 2 is Settings Management Foundation only: persistent storage, REST
GET/PUT, validation, atomic CAS, fresh platform-admin authorization,
transactional audit and restart persistence. No runtime consumer wiring.
PERSISTED_NOT_ENFORCED is the intended state AFTER implementation, not today's
status. R7 emergency auto-queue remains prohibited.

| Finding | Actual gap | Chosen design, not implemented |
| --- | --- | --- |
| P1-01 | JWT realm roles can be stale | Enabled-account + effective-role checks on BOTH methods of BOTH APIs |
| P1-02 | Draft seed assumed 480-minute day | LOW 510/2550, MEDIUM 240/1020; preserve r5-v1 |
| P1-03 | Persistence conflated with effective configuration | Foundation only; all consumers unwired |
| Migrations | Full loads HPDI and App in one sequence | HPDI V32, Service Desk V33; V1-V31 untouched |
| Audit | Workspace-oriented, JWT-only admin read bypass | Reserved scope protected by explicit fresh authorization |
| CAS/bootstrap | Missing singleton settings | Seed version 1; guarded UPDATE + mandatory audit |
| Core boundary | Core owns execution constraint and locks | Keep dx.workflow.max-in-progress; no Core -> HPDI dependency |
| Evidence | Baseline is not settings acceptance | Correct historical results; new acceptance NOT_RUN |

Routes: GET/PUT `/api/hpdi/v1/settings/work` (Tier 2), GET/PUT
`/api/apps/servicedesk/v1/settings` (Tier 3). Both global, not workspace-selected.
Global admin does not gain workspace ticket access.

## 2. Fresh Authorization (P1-01)

Version-pinned Keycloak 26.0.8 primary references inspected:

- https://www.keycloak.org/docs-api/26.0.8/rest-api/index.html
- https://github.com/keycloak/keycloak/blob/26.0.8/services/src/main/java/org/keycloak/services/resources/admin/RoleMapperResource.java
- https://github.com/keycloak/keycloak/blob/26.0.8/services/src/main/java/org/keycloak/services/resources/admin/UserResource.java
- https://github.com/keycloak/keycloak/blob/26.0.8/services/src/main/java/org/keycloak/services/resources/admin/permissions/UserPermissions.java

`/users/{user-id}/role-mappings/realm` returns getRealmRoleMappingsStream(),
not effective-role expansion. `/realm/composite` enumerates realm roles and
filters through roleMapper.hasRole; its documented purpose is effective realm
roles, recursively expanding composites. Use
`GET /admin/realms/{realm}/users/{sub}/role-mappings/realm/composite?briefRepresentation=true`.
Support direct, group-inherited, composite and nested-composite grants; prove
each on real 26.0.8, not JWT mocks.

Same policy for EVERY GET and PUT of BOTH settings APIs:

1. Authenticated: trusted issuer/audience/signature/expiry and nonblank subject;
   missing/invalid JWT -> 401.
2. Identity: ONLY authenticated JWT sub, path-encoded in fixed configured realm.
   Never trust body/query/header userId, username, actor or roles. Stale JWT admin
   claim neither overrides a live denial nor is the sole grant source.
3. Freshly authorized: fetch `GET /admin/realms/{realm}/users/{sub}`; require
   matching id, enabled=true, effective realm roles containing exact
   platform-admin, and positive live human-search membership by the same id.
   Absence of serviceAccountClientId does NOT establish a human account in
   Keycloak 26.0.8 (see security correction below). No positive account/
   role cache, stale directory fallback or cross-request decision reuse.

This checks enabled-account and role state, not current login-session validity.
Temporary brute-force lockout, required actions and logout differ from enabled;
the user endpoint alone does not check those. Extra account admission rules
need explicit design/tests before claiming them. Keycloak reads and PostgreSQL
commit are not atomic: revocation can race an in-flight check; guarantee
next-request fresh evaluation, not distributed transactional revocation.

### Service identity and fail-closed behavior

Local `infra/keycloak/realm-template.json` grants dx-directory the
realm-management CLIENT roles view-users, query-users, query-groups.
UserResource.getRoleMappings supplies auth.users().requireView(user), invoked
by both RoleMapperResource reads. UserPermissions.canViewDefault accepts
view-users (also manage-users, which MUST NOT be granted). Query roles alone
are not user-view authorization. Template permissions are sufficient by source
inspection, NOT proof of actual permissions in an already-imported realm.

Use confidential dx-directory client credentials, never end-user or bootstrap
admin credentials. For by-sub reads view-users is the relevant built-in grant;
retain query roles for existing directory use. No realm-admin, manage-users,
impersonation or role/user mutations. Runtime verification remains NOT_RUN:
obtain its real token, prove user/effective-role reads, prove mutations denied,
then remove view-users in the isolated fixture and verify fail-closed behavior.
Do not expand privileges merely to make a test pass.

Chosen limits: 2s connect timeout, 5s entire authorization budget; no unbounded
retries. Service-token 401 may invalidate cached service token and refresh once
within budget. Service-token caching is NOT caching user authorization.

| Keycloak outcome | Settings response; no read/mutation |
| --- | --- |
| User lookup 404, enabled=false, missing effective role | 403 |
| Malformed response or mismatched identity | 503 |
| Service API 401 after bounded refresh, or 403 | 503, dependency credential/permission fault |
| Composite lookup 404 after successful user lookup | 503, cannot establish effective roles |
| Timeout, network/TLS error, token endpoint failure, 429, 5xx | 503, never use stale grants |

JWT-only TTL mode is NOT selected: it fails old-JWT revocation requirements.
Template accessTokenLifespan=300 seconds; JWT-only revocation could lag the
remaining lifetime plus configured clock skew. CAS/audit do not fix this.

Real Keycloak acceptance (NOT_RUN): both APIs/methods, direct/group/composite/
nested-composite grants -> 200; revoke each grant/group/child role, reuse SAME
unexpired JWT -> 403. Also disable/delete account, deny ordinary Requester/
Agent/Coordinator and service accounts, reject spoofed identity, test newly
effective grants independently of stale claims, service permission removal,
timeouts/403/404/5xx. Restore fixtures in finally. Mock JWT tests are unit-only
evidence, never proof of fresh revocation.

## 3. Runtime-Compatible SLA Defaults (P1-02)

Source: `backend/src/main/java/vn/haui/dx/apps/servicedesk/TicketSlaConfiguration.java`.
07:00-11:30 = 270 minutes; 13:00-17:00 = 240 minutes; working day = **510**.
Runtime SlaPolicy version **r5-v1**; default Asia/Ho_Chi_Minh, Monday-Friday,
configured holidays, escalation ratio 0.5, ceiling HIGH, external waiting counts.

| Priority | startWithinMinutes | resolveWithinMinutes | Clock |
| --- | ---: | ---: | --- |
| LOW | 510 | 2550 | working minutes: 1 / 5 configured working days |
| MEDIUM | 240 | 1020 | working minutes: 4h / 2 configured working days |
| HIGH | 60 | 480 | working minutes, not a full 510-minute day |
| CRITICAL | 15 | 120 | continuous elapsed minutes |

R5 in supplied docs/ticket.md and target OpenAPI describe MEDIUM two days and
LOW one/five days; neither authorizes replacing actual calendar with an 8-hour
day. V32 seed/examples MUST use these pairs; no separate policy change approved.
Customized deployment properties remain runtime truth despite Foundation seeds.
Config row version 1 is NOT policy version r5-v1.

Future regression checks: calendar sum=510, all four seed/bean pairs, continuous
flags, weekend/holiday/pause arithmetic, R9 amendments. Foundation PUT must leave
runtime bean and existing policy JSON/deadlines unchanged. New checks NOT_RUN.

## 4. Migrations, Bootstrap, Validation And CAS

Planned only: `backend/src/main/resources/db/migration/hpdi/V32__work_settings.sql`
and `backend/src/main/resources/db/migration/apps/servicedesk/V33__servicedesk_settings.sql`.
Never edit V1-V31. Default recursive classpath db/migration discovery shares
one version sequence; EngineAfterMigrations orders Flowable after Flyway.
Actual `backend/pom.xml` resource exclusions control packaged migrations.

| Profile | Clean install | Upgrade fixture | Assertions |
| --- | --- | --- | --- |
| Full | empty -> V33 | Full history at V31 -> V32 -> V33 | Both singleton tables/seeds; unique versions; prior checksums/data unchanged |
| Second-domain, unfiltered | empty -> V32 | Second-domain history at V31 -> V32 | HPDI V32 present; V33 and Service Desk resources/tables absent |
| Core-only | empty -> existing Core maximum | Core-only history -> unchanged | No HPDI/App migrations/classes/settings tables; V32/V33 absent |

Do not run Core-only against Full history or require V33 from Second-domain.
Use clean builds between profiles, inspect packaged resources, Flyway history/
validate and schema; repeat migration/restart idempotence. Expectations above
are NOT execution results: no V32/V33 exists yet.

Each table: singleton id=1 primary key CHECK(id=1); BIGINT version NOT NULL
CHECK(version>=1); nonnull JSONB payload with object/array type constraints;
nonnull timestamptz updated_at and nonblank updated_by <=100 characters.
Seed complete validated defaults at version 1 in migration; bootstrap actor
system:bootstrap denotes provenance, never a user PUT identity. GET before
first PUT is read-only and returns seeded row. Missing singleton is an integrity
error, not lazy reseeding. V32 SLA defaults are in section 3; other defaults
are in section 6. V33 defaults match current category/ET tables.

Full replacement request: version and all required nested fields. Separate
server-owned response updatedAt/updatedBy; reject submitted read-only metadata,
unknown fields at EVERY nesting depth, missing/null required values, invalid
enums, fractional integers, numeric overflow and nonfinite ratios with 400.
No silent coercion/ignoring; recursive @Valid plus cross-field validation.
Ratios [0,1]; exact category/priority key sets; positive targets/days/sample/
window counts; tie/minRemaining >=0; outlierMin>=0, max>=min and max>=1;
shifts minItems=1, nonblank unique name <=50, strict HH:mm, start<end and no
overlap; resolve>=start; ET 1..10080 compatible with existing review limit.
Document extra cross-field constraints beyond target minima in live contract.
Validate safe group names, reject reserved role groups; Foundation keeps OTHER
null to preserve Coordinator manual choice (document contract tightening).
No eligibility mapping changes. Future wiring requires per-workspace group
existence validation and resolved-group snapshots; global name is not membership.

Atomic mutation shape (payload is illustrative, not a forced column design):

```sql
UPDATE <settings_table>
SET payload=:validated_payload, version=version+1,
    updated_at=CURRENT_TIMESTAMP, updated_by=:authenticated_sub
WHERE id=1 AND version=:expected_version AND version<9223372036854775807
RETURNING *;
```

Use Java long, versions 1..Long.MAX_VALUE, never wrap/reset. Zero rows -> 409
(stale/exhausted version; report exhaustion explicitly). Same transaction writes
audit; no read-then-unconditional-update or independent audit commit. Two same-
version PUTs -> exactly one 200, one 409, one increment, one audit. Test max-1
to max then rejection with unchanged state. Response only after commit.

## 5. Reserved System Audit Scope

V1 audit_event.workspace is varchar(100) NOT NULL, no workspace foreign key.
JdbcAuditService requires nonblank workspace <=100. Choose `system:platform`,
which cannot be parsed as a workspace membership by JwtCurrentUser's
`[a-z0-9-]{1,50}` group pattern. But Spring @PathVariable does NOT reject colons:
neither URL syntax nor a special string constitutes authorization.

Planned enforcement: explicit reserved-scope branch in Core audit authorization
before the existing platformAdmin/coordinator branch. Reads of system:platform
require the same fresh enabled-account/effective-admin check as settings; no
JWT-only bypass. Reject reserved-scope reads through workspace mine/member
paths. Normal workspace audit still uses its existing permissions; an ordinary
workspace named system or global cannot see reserved events. Existing
AuditController @RequirePermission(audit:view) is a workspace guard, not a new
system-admin read API: do not claim a global admin can already browse the
reserved scope. Define a protected system-read entry point or explicit
interceptor handling in Phase 2, without bypassing other workspace checks.

Core owns generic fresh-admin authorization/audit support; HPDI invokes a Core
capability; App invokes an HPDI facade. Core never imports HPDI/App. Define the
small capability when implementing it, not speculative production code now.
PUT uses authenticated sub, RequestContext request ID (or generated UUID), real
server timestamp, action hpdi.work-settings.update / servicedesk.settings.update,
scope system:platform and before/after values + versions. AuditService.record
remains Propagation.MANDATORY in the SAME transaction as CAS. Bootstrap system
provenance must never replace a user actor. Do not put tokens/secrets in audit.

Acceptance NOT_RUN: direct and encoded colon paths; ordinary coordinators of
system/global and forged reserved-scope memberships -> denied; stale/revoked
admin -> denied; fresh admin -> only explicit system-read policy; real request
ID/sub match audit. Inject audit insert failure after mutation, assert settings
payload/version/metadata AND audit unchanged, retry same version succeeds;
assert one audit for CAS winner, none for denied/stale/failing requests.

## 6. Per-Setting Application Matrix

All rows: Storage/API today = NOT_IMPLEMENTED. Planned Foundation state =
PERSISTED_NOT_ENFORCED; runtime consumer wired = NO; settings PUT applies to
new operations = NO and affects active operations = NO. These facts apply to
EVERY row, not just background workers. R7 additionally remains BLOCKED.
Source below means CURRENT runtime truth, not the future administrative table.
Foundation stores a separate administrative value; it is not runtime truth.

| Field | Compatible initial default | Current source / snapshot or provenance | Decision |
| --- | --- | --- | --- |
| assignment.pendingLoadFactor | 0.5 | TicketReviewService.projected: (remaining+1)/2 for PENDING, ceiling rounding | Retain formula |
| assignment.tieThresholdMinutes | 15 | AssignmentSuggestionController.rank: strict <15 band, no settings snapshot | Retain ranking |
| assignment.maxInProgress | 1 | FlowableWorkflowService property dx.workflow.max-in-progress, global assignee advisory lock | Core unchanged |
| assignment.minRemainingMinutes | 10 | TicketReviewService.projected Math.max(10,minutes-worked), exception for untouched NOT_STARTED | Retain projection |
| shifts | Morning 07:00-11:30; Afternoon 13:00-17:00 | TicketSlaConfiguration calendar; R3 explicit dated Agent shifts independent | Stored templates only |
| sla.targets.LOW | 510 / 2550 minutes | r5-v1 template then servicedesk_sla.policy snapshot | No policy change |
| sla.targets.MEDIUM | 240 / 1020 minutes | r5-v1 template then servicedesk_sla.policy snapshot | No policy change |
| sla.targets.HIGH | 60 / 480 minutes | r5-v1 template then servicedesk_sla.policy snapshot | No policy change |
| sla.targets.CRITICAL | 15 / 120 continuous minutes | r5-v1 template then servicedesk_sla.policy snapshot | No policy change |
| sla.escalateAfterRatio | 0.5 | TicketSlaConfiguration property, serialized SlaPolicy | Preserve snapshot |
| sla.maxEscalatedPriority | HIGH | TicketSlaConfiguration property, serialized SlaPolicy | Preserve snapshot |
| sla.waitingExternalCounts | true | TicketSlaConfiguration property, serialized SlaPolicy | Preserve snapshot |
| critical.autoQueueAfterMinutes | 5 | No authorized auto-queue consumer | BLOCKED, storage only |
| alerts.warnRemainingRatio | 0.2 | SlaPolicy.near hardcodes ratio, NOT a serialized configurable ratio | Do not alter existing projection |
| alerts.pendingRemindDays | 3 | TicketAlerts.check Duration.ofDays(3), no settings snapshot | Keep worker behavior |
| alerts.reopenUnclaimedMinutes | 15 | TicketConfirmation property serviceDesk.confirmation.reopen-minutes; persisted reopen deadline | Keep existing deadline |
| closing.autoCloseAfterDays | 3 | TicketConfirmation +72 continuous hours; persisted deadline and r11-72h-v1 audit label | Never business days |
| estimate.minSamples | 20 | R2 target/business requirement, no settings consumer established | Do not claim median implemented |
| estimate.windowMonths | 6 | R2 target/business requirement, no settings consumer established | Deferred |
| estimate.outlierMinMinutes | 2 | R2 target/business requirement, no settings consumer established | Deferred |
| estimate.outlierMaxMinutes | 480 | R2 target/business requirement, no settings consumer established | Deferred |
| categoryGroups.HARDWARE | hardware | TicketAssignmentEligibility.supportGroup switch | Keep eligibility |
| categoryGroups.NETWORK | network | Same hardcoded switch | Keep eligibility |
| categoryGroups.SOFTWARE | software | Same hardcoded switch | Keep eligibility |
| categoryGroups.ACCOUNT | sysadmin | Same hardcoded switch | Keep eligibility |
| categoryGroups.OTHER | null | Same switch; Coordinator manually chooses independent eligible Agent | Keep manual choice |
| estimateDefaults.HARDWARE | LOW/MEDIUM/HIGH/CRITICAL: 45/60/90/120 | TicketReview.defaultMinutes; persisted minutes/basis/history at approval | No live wiring |
| estimateDefaults.NETWORK | 30/45/60/90 | Same lookup and persisted review/history | No live wiring |
| estimateDefaults.SOFTWARE | 30/30/45/60 | Same lookup and persisted review/history | No live wiring |
| estimateDefaults.ACCOUNT | 15/15/20/30 | Same lookup and persisted review/history | No live wiring |
| estimateDefaults.OTHER | 30/45/60/90 | Same lookup and persisted review/history | No live wiring |

## 7. Compatibility, Provenance And Tier Boundaries

TicketReviewService.review uses DEFAULT when no custom ET is supplied and
COORDINATOR for explicit ET; TicketWorkEstimate.approve records approved manual
ET. A review-approved DEFAULT is still persisted ET: settings changes cannot
silently rewrite EITHER basis. R6 handover preserves prior manual ET when no
replacement is supplied; R9 explicit correction can recalculate DEFAULT under
its existing rules and preserves COORDINATOR. Foundation changes neither path.

TicketSlaService.initialize snapshots r5-v1 at approved review, NOT at ticket
creation. R6 handover continues original policy/anchors. correctPriority uses
state.policy(), existing pause/budget logic and amendment history; preserved
policy does not mean legitimate R9 deadlines can never change. Settings PUT
must not cause any such change. R11 stores confirm deadline at native confirm
creation +72 continuous hours, including legacy enrollment; existing confirm
and reopen deadlines are never recomputed from Foundation storage.

Future wiring requires an independently reviewed policy boundary: snapshot
config scope/version + effective payload/hash + policy version + application
time at initial approved review; persist resolved group and ET provenance;
carry snapshots across handover and explicit R9 amendments. Existing records
without config version retain legacy provenance, never fabricated version 1.
The mutable settings row alone is NOT historical policy proof. Preserve payload
in the business snapshot or immutable history before claiming version provenance.

Core execution locks and dx.workflow.max-in-progress remain unchanged in
Foundation. If dynamic policy is later approved, define a Core-owned generic
execution policy SPI with Core-only property fallback and an HPDI/assembly
adapter. Preserve global assignee counting/locking across workspaces; a
workspace-specific SPI must not silently change that scope. No SPI or Core
locking rewrite is included now. Tier 2 must compile without Service Desk.

## 8. Evidence Ledger And Acceptance Gates

Historical baseline source SHA: 246a8d6676f4030b474e0f809fbb9ce652339cb8.
The previous report's unfiltered Second-domain 286 PASS claim was incorrect.
Its run lacked PostgreSQL environment variables and skipped 96 tests.

| Historical run | Total | PASS | FAIL | ERROR | SKIP | Evidence/status |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| Backend Full clean test, Linux/PG | 849 | 849 | 0 | 0 | 0 | .local/task-execution-full/summary.json; baseline PASS |
| Core-only clean test, Linux/PG | 106 | 106 | 0 | 0 | 0 | .local/task-execution-core-only/summary.json; baseline PASS |
| Targeted rules selection, Linux/PG | 215 | 215 | 0 | 0 | 0 | .local/task-execution-full-targeted/summary.json; baseline PASS |
| Filtered Second-domain, Linux/PG | 48 | 48 | 0 | 0 | 0 | .local/task-execution-second-domain/summary.json; NOT unfiltered acceptance |
| Unfiltered Second-domain, no DB fixture | 286 | 190 | 0 | 0 | 96 | Historical terminal output; SKIP, NOT acceptance PASS |
| Core-only Windows/PG attempt | 106 | 103 | 0 | 3 | 0 | Historical OS symlink/POSIX permission ERROR; superseded only by Linux run |

Historical Python boundary self-tests: 43 PASS; source boundary checker PASS;
Service Desk target validator and R12 contract check PASS. HPDI target validator
FAILED: unquoted description comma at alerts unread produces extra YAML key
cho badge; test-core-contract.py FAILED because old fixture lacks R12 required
awaitingReview. These are baseline issues, not fixed in this documentation task.
check-core-contract.py and execution-contract script calls missing required
arguments were invocation ERROR, not contract acceptance. Do not count those.

Summaries are local historical artifacts, not immutable source-SHA attestations;
do not turn them into post-implementation proof. New settings migration/runtime/
restart/audit rollback/live Keycloak revocation acceptance: **NOT_RUN**.
This documentation revision does not rerun backend suites or claim clean
install/upgrade success for nonexistent V32/V33. Documentation checks below
are separate from runtime tests. Hosted CI: **NOT_VERIFIED** for this revision;
previous BLOCKED billing statement had no inspected run evidence. If an actual
hosted run reports billing blockage, record BLOCKED with run evidence, never
PASS or a self-issued waiver.

### Future exact gates (all NOT_RUN)

1. Clean `mvn -B -o clean verify`, `mvn -B -o -Pcore-only clean verify`,
   `mvn -B -o -Psecond-domain clean verify` on separate profile fixtures, with
   FILE_TEST_JDBC_URL, WORKFLOW_TEST_JDBC_URL, BACKGROUND_TEST_JDBC_URL and
   matching DB credentials where applicable. Unfiltered means NO -Dtest.
   Parse Surefire XML PASS/FAIL/ERROR/SKIP; unexpected skips fail the gate.
2. Targeted R3/R5/R7/R9/R11/R12: run AgentDutyIntegrationTest,
   AgentSchedulePolicyTest, AgentScheduleMigrationTest,
   AssignmentSuggestionIntegrationTest, AssignmentSuggestionTest,
   TicketIntegrationTest, WorkAlertsIntegrationTest, AgentDutyUpgradeIntegrationTest,
   ConfirmationUpgradeIntegrationTest, ServiceDeskMigrationIntegrationTest,
   TicketAssignmentEligibilityTest, SlaPolicyTest, ClassificationSlaAmendmentTest,
   TicketStatisticsServiceTest, CanonicalTicketIntegrationTest plus new settings
   tests. Passing R7 fail-closed regression is NOT implementing auto-queue.
3. Migration matrix in section 4: inspect packaged resources, clean install,
   profile-specific upgrade, unchanged V1-V31 checksums/data, seed GET before
   PUT, Flyway validate and restart idempotence. No cross-profile history reuse.
4. Real Keycloak 26.0.8 issued JWTs and dx-directory permission proof per section
   2, signed HTTP requests to packaged backend on PostgreSQL/Flowable. Both
   settings APIs must pass the same authorization matrix. No mock substitutes.
5. Two concurrent same-version PUTs; audit failure after CAS; version overflow;
   malformed/unknown/nested/null/read-only fields; reserved audit scope and
   workspace isolation; restart backend on SAME persistent DB and verify exact
   payload/version/metadata. Record sub and request ID, never tokens/secrets.
6. Foundation settings updates must leave reviewed ET, active SLA policies,
   deadlines, R3 rosters, R11 confirmation/reopen deadlines, eligibility and
   Core concurrency constraint unchanged, including NEW operations. R7 stays
   blocked. Separate restart persistence from runtime-enforcement claims.
7. Validate both live/target OpenAPI plus actual HTTP responses; boundary source
   checker, its self-tests and Java TierBoundaryTest. Track baseline failures
   explicitly; do not silently omit or waive them. Record exact tested source
   SHA, artifact digest, commands, fixture versions and raw results for final
   acceptance. A later documentation commit does not become tested code SHA.

## 9. Phase 2 Commit Plan And Decisions

No Phase 2 code is authorized by this documentation-only turn. On explicit
instruction to begin, each commit includes its focused tests:

1. Generic Core fresh-admin capability + HPDI facade, bounded Keycloak client,
   reserved system-audit authorization/entry point. Unit error matrix and real
   26.0.8 service-identity/effective-role proof BEFORE exposing settings routes.
2. HPDI V32 seed/schema, validated DTOs, GET/PUT, atomic CAS and mandatory audit;
   correct SLA seeds, migration/validation/CAS/rollback tests. No workflow wiring.
3. Service Desk V33 + GET/PUT, category/ET validation and same authorization/CAS/
   audit; Full/Second-domain/Core-only migration-resource checks.
4. Contract promotion with explicit Foundation semantics, packaged runtime and
   restart proofs, full unfiltered/profile/targeted/OpenAPI/boundary acceptance,
   exact tested SHA and evidence. Do not describe JSON as effective policy.

P0: no newly identified source exploit established by this docs-only review.
P1 design findings P1-01/P1-02/P1-03 addressed; implementation/security/migration
acceptance remains pending, NOT a production closure or merge readiness claim.
BD-01 scope is approved, no longer an open owner choice. Online fresh checks
are the chosen security requirement; JWT-only TTL fallback is not an alternative
compliant implementation. Owner already prohibited R7 and mandated compatible
SLA defaults / 72 continuous-hour R11 behavior. P2 future decisions: runtime
activation boundaries/version provenance, custom deployment settings bootstrap,
per-workspace group validation and OTHER nonnull support, and any extra current-
session/lockout admission requirement. Default: no runtime wiring, no policy
change, OTHER null. New timeouts/cross-field validation must be documented in
implementation contracts and tested, not presented as already enforced.

Phase 2 readiness: design ready for the owner to explicitly start Foundation;
no runtime acceptance or merge readiness yet. Stop after documentation commit/
push on the existing branch. No new PR, merge, force-push or Phase 2 production code.

Documentation-only checks on 2026-10-28: git diff --check PASS; runnable inline
assertions PASS for effective-role/disabled-account design, corrected SLA pairs
and 510-minute arithmetic, scope approval, overflow bound, evidence separation
and sole changed tracked file. No backend/runtime test run in this revision.

## 10. Phase 2 Commit 1 - Core Fresh Admin Authorization

Date: 2026-10-28 (Asia/Saigon). Base b41e013c94939d3da70018911bf4eaef5db1dbbd.
This section supersedes NOT_IMPLEMENTED/NOT_RUN for this capability ONLY, not
the settings, audit-namespace, migration or runtime-wiring acceptance above.

Public Core API: FreshAdminAuthorization.requirePlatformAdmin() returns the
current authenticated subject, or throws CoreException. Its only identity input
is WorkspaceAccess.current().sub(); no caller-supplied user id, role or actor.
KeycloakFreshAdminAuthorization uses existing dx.directory URL/realm/client/
secret configuration. It obtains a NEW dx-directory client-credentials token
per invocation, reads current user, rejects missing/disabled/service account,
then reads effective realm/composite roles. No account/role/decision cache and
no service-token cache, so no refresh/retry path is needed; service 401 fails
closed immediately. Production HttpClient disables redirects, connects within
2s and shares a monotonic 5s budget across token/user/role/human-search requests and body
completion. Timeout/interruption cancels the pending request; interrupt status
is preserved. Strict JSON rejects duplicate keys/trailing content, malformed
account/roles and mismatched identity. Errors do not disclose tokens/secrets.

401: missing authenticated subject. 403: user absent/disabled/service account
or effective admin role absent. 503: missing configuration, dependency errors,
invalid payload, credential/permission failures, role endpoint 404 or timeout.
No JWT platform-admin claim is used as a grant or prerequisite. Direct,
group-inherited, composite and nested-composite expansion is delegated to the
real Keycloak effective-role endpoint, not reimplemented in Java.

No existing endpoint, WorkspaceAccess/JWT behavior, AuditService, directory
cache/client, Keycloak template permission, migration or Maven profile is
modified. No HTTP settings endpoint, HPDI facade, system-audit read branch or
V32/V33 is implemented. The current task narrows the earlier commit plan:
reserved-scope audit enforcement remains future work. Future settings GET AND
PUT must explicitly invoke this capability through the correct tier facade;
there is no global interceptor changing authorization of existing APIs.
The capability itself has no database mutation or transaction propagation.

### Runnable evidence

`scripts/test-fresh-admin.ps1` creates a disposable realm from the repository
template on pinned Keycloak 26.0.8, with random ephemeral credentials; it never
targets an existing realm or changes production grants. Directory gets exactly
the template view-users/query-users/query-groups grants. The live test removes
and restores view-users in its own fixture to prove dependency fail-closed.
User/role mutations by dx-directory return 403; user/effective-role reads work.
Test-only users/groups/composites and a disposable service client are created
using bootstrap admin ONLY in the disposable test realm. No privileged roles
are added to dx-directory. Import credential file and containers are removed
in finally; logs/XML/summaries remain under .local/fresh-admin-* (not secrets).

The live test verifies Keycloak-issued JWT signature/issuer/expiry using its
real JWK endpoint and asserts dx-core audience before installing the caller in
the test SecurityContext. It proves newly granted effective roles work without
refreshing claims, then obtains an admin-bearing JWT, revokes the direct/group/
composite/nested grant and reuses the SAME JWT to prove denial. Also tests
disabled/deleted accounts, actual Requester/Agent/Coordinator users and a signed
service-account JWT (privileged-account coverage was added by the correction
below). This is capability integration, NOT nonexistent Settings
HTTP/Gateway acceptance. Unit tests use a local HTTP fixture for failure status,
strict JSON, subject encoding, no cache, time budget and interruption cases.

Commands: `scripts/test-fresh-admin.ps1 -BuildImage dx-platform-build:0.1.0
-Profile core-only` (also full / second-domain); no -Dtest filter on these
regression profiles. Runner copies current source, clean verifies on Linux/JDK17
with PostgreSQL 16.6 and real Keycloak on a private network. Native focused
command: `scripts/test-fresh-admin.ps1 -Maven <mvn.cmd>`.

| Current source run | Total | PASS | FAIL | ERROR | SKIP | Evidence |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| Native focused unit + real Keycloak | 38 | 38 | 0 | 0 | 0 | .local/fresh-admin-aa8446c608df |
| Linux Core-only clean verify + real Keycloak | 144 | 144 | 0 | 0 | 0 | .local/fresh-admin-9bf6741cf7ad |
| Linux Full clean verify + real Keycloak | 887 | 887 | 0 | 0 | 0 | .local/fresh-admin-0072bd573017 |
| Linux unfiltered Second-domain clean verify + real Keycloak | 324 | 324 | 0 | 0 | 0 | .local/fresh-admin-419e68e09f30 |

Tier boundary checker PASS; its regression self-checks PASS (43 tests).
These are post-implementation working-tree runs, separate from Phase 1 baseline
evidence. All three Linux profiles used the same final Java/script sources;
only this evidence documentation changed afterward. Final committed-SHA focused
proof is reported in the delivery message rather than claiming a pre-commit run
tested a commit that did not yet exist.

Earlier attempts are not hidden: first live fixture failed six tests because
Keycloak required complete user profile; fixed fixture name/email, not realm
security. Later one live test failed because dx-directory token lacked dx-core
audience; fixed by creating an unprivileged test-only service client with that
audience, without changing template or relaxing JWT validation. Legacy profile
runner without FRESH_ADMIN_TEST_URL skipped four conditional test nodes (the
parameterized grant test plus three methods), and its zero-skip gate failed;
these are not counted as live PASS. New runner supplies real Keycloak for
unfiltered regression. Container readiness/address-pool setup errors were harness
errors, not production authorization PASS; readiness now sends explicit Host
and cleanup targets only owned disposable containers/networks.

Self-review: identity cannot come from request data; no token-claim admin bypass,
stale grant fallback or workspace membership requirement. Core imports no HPDI/
App source. No changes to existing endpoint security or business transactions.
No P0/P1 found in this local diff review; independent technical review pending.
P2/limits remain: account-enabled/effective-role checks are not login-session or
temporary lockout validation; revocation can race an in-flight check; deployments
must verify actual service identity grants match template. Hosted CI NOT_VERIFIED.
Stop after Commit 1; no Commit 2, new PR, merge or force-push.

### Commit 1 security correction - privileged service account

Independent review correctly identified that the original live service-account
test had no platform-admin grant. Its denial did not establish human-only
authorization. The strengthened test grants the disposable service account the
realm role using bootstrap admin, verifies the effective composite endpoint,
and validates the real signed service JWT and dx-core audience before denial.
Client deletion runs in finally; a subsequent GET verifies account deletion.
No existing accounts or production/directory grants are changed.

The first strengthened run exposed a production defect: the user representation
returned to dx-directory by Keycloak 26.0.8 does NOT expose serviceAccountClientId.
An assertion expecting the client id failed. The original guard trusted that
absence and would admit an enabled privileged service account. This is a real
correction, not merely a test-only change. The original live-test evidence above
must not be read as proof that privileged service accounts were excluded.

Pinned source inspection: Keycloak tag 26.0.8, UserResource.getUser returns a
user-profile representation; UsersResource.getUsers with a non-id search calls
searchForUser with includeServiceAccounts=false. In contrast, search=id: and
username/exact parameter paths must NOT be substituted: they can include service
accounts. The guard now uses a quoted, URL-encoded search derived from the LIVE
account username and requires the authenticated subject among returned ids.
It still rejects an explicit serviceAccountClientId if present. No inference
from username prefixes or JWT role/client claims establishes human status.
The live test explicitly asserts the observed field omission, proves the
privileged service account is excluded by this search using dx-directory,
and then requires FORBIDDEN from the production guard.

Human search shares the existing 5-second deadline and fail-closed status/JSON
handling; its result is never cached. Eight failure cases plus fresh-proof and
timeout tests cover the additional trust boundary. The search is bounded to two
results: a username containing a quote is rejected with 503, and rare ambiguous
exact matches where the subject is outside the first two results are denied,
never granted. This is an availability limitation, not a fallback grant; broader
username/search support requires explicit bounded pagination design and tests.

Initial focused/Core-only/Full runs each had one FAIL on the absent field,
zero ERROR/SKIP. The initial Second-domain setup hit Docker address-pool
exhaustion (ERROR before tests); no unrelated networks were pruned. These runs
are diagnostic evidence, not final acceptance.

Final correction evidence (same final Java sources, documentation finalized
after these runs; all use real pinned Keycloak 26.0.8):

| Run | Total/PASS | FAIL | ERROR | SKIP | Evidence |
| --- | ---: | ---: | ---: | ---: | --- |
| Focused: 41 unit + 7 live | 48/48 | 0 | 0 | 0 | .local/fresh-admin-cc08591e819a |
| Core-only clean verify | 154/154 | 0 | 0 | 0 | .local/fresh-admin-40f4bc2ae93a |
| Full clean verify | 897/897 | 0 | 0 | 0 | .local/fresh-admin-fd4402aa0103 |
| Unfiltered Second-domain clean verify | 334/334 | 0 | 0 | 0 | .local/fresh-admin-2c2d93537a68 |

Boundary checker PASS; boundary regression 43/43 PASS. No production API,
Keycloak grant, migration, settings controller or transaction boundary changed.
The correction is limited to privileged service-account exclusion and its
regression evidence. Self-review found no remaining P0/P1 in this diff; the
bounded-search availability limitation above remains P2. Independent review is
still required; hosted CI NOT_VERIFIED. A final focused run on the committed
SHA and its evidence directory are reported with delivery. Stop after this fix.

## 11. Phase 2 Commit 2 - Work Settings Foundation

V32 adds a seeded singleton HPDI JSONB document (version 1). GET/PUT use the
Core fresh-admin capability through SettingsAdministration. Replacement validates
all nested fields, rejects coercion/unknown/read-only/duplicate fields, locks
only the settings row for an accurate audit-before image, performs guarded CAS,
and records audit in the same transaction. No Core workflow lock was changed.
Conflict/exhaustion is 409; audit insert failure rolls back payload and metadata.
Core reserved system audit reads now require fresh admin, mine rejects reserved
scope. The initially added /api/core/v1/audit/system read route is removed in
final merge preparation (section 14); no new HTTP audit-read API is required.
Normal workspace audit rules are unchanged.

All fields are PERSISTED_NOT_ENFORCED: PUT has NO effect on new or active work.
Seeds match r5-v1 LOW 510/2550 and MEDIUM 240/1020 working minutes; CRITICAL
15/120 continuous minutes. Closing seed 3 does not alter R11's continuous 72h.
R7 remains BLOCKED; maxInProgress does not modify Core execution configuration.
Live/target settings contracts now document the implemented storage-only API.

Targeted Second-domain run PASS: 40 tests, zero FAIL/ERROR/SKIP;
.local/fresh-admin-ec5042d93c13. Includes PostgreSQL clean/upgrade migration,
checksums/data preservation, CAS race, overflow and transactional audit failure.
Earlier run had one test ERROR from Mockito restubbing an already-throwing mock;
fixed test with doThrow, not production authorization. Full acceptance follows.

## 12. Phase 2 Commit 3 - Service Desk Settings Foundation

V33 adds Service Desk's singleton JSONB settings with runtime-compatible ET
seeds. The application owns its table/controller/validation; it calls only the
public HPDI SettingsAdministration facade, with no App-to-Core imports and no
HPDI dependency on Service Desk. Both methods require fresh administrator
authorization; CAS, overflow and transactional before/after audit match V32.
OTHER remains null and requires Coordinator selection. Specialty names are
validated syntactically, not falsely asserted to exist in every workspace.

All values remain PERSISTED_NOT_ENFORCED, including defaults for NEW tickets.
Existing TicketReview.defaultMinutes, eligibility and approved estimate rows
are unchanged. There is no runtime wiring or policy re-interpretation.
Full targeted PASS: 20 tests, zero FAIL/ERROR/SKIP;
.local/fresh-admin-a2b1459fd373 (all category/priority seeds, validation,
HTTP/CAS/concurrency/overflow/audit rollback and clean/upgrade V31->V33).
Tier boundaries PASS after using the HPDI error response contract for 503,
rather than a forbidden direct App-to-Core DTO reference. Full acceptance next.

## 13. Phase 2 Commit 4 - Acceptance And Delivery

### Current application/snapshot status

| Configuration | Persistent API | New business operations | Active/snapshotted work |
| --- | --- | --- | --- |
| Work assignment, shifts, sla, critical, alerts, closing, estimate | V32, GET/PUT | PERSISTED_NOT_ENFORCED | Unchanged |
| Service Desk categoryGroups, estimateDefaults | V33, GET/PUT | PERSISTED_NOT_ENFORCED | Unchanged |

Administrative storage is NOT runtime truth, even for newly created work.
Runtime SlaPolicy r5-v1, deployment properties, date-specific schedules, approved
ET, ticket SLA/deadline snapshots, R11's continuous 72-hour confirmation policy
and Core execution locks remain unchanged. R7 Emergency Auto-Queue is BLOCKED.
No new runtime policy version is introduced. Foundation version is a CAS row
version, not a ticket policy version. Future consumer wiring needs its own
approved application point, workspace group validation and snapshot decisions;
none are silently implemented here. BD-01 is sufficient for Foundation only.

### Executed pre-commit acceptance evidence

These runs are separate from the historical baseline and earlier failed runs.
All database/runtime suites use Linux/JDK17, PostgreSQL 16.6 and real Keycloak
26.0.8. Raw Maven log and Surefire XML are retained in each evidence directory.

| Gate | PASS | FAIL | ERROR | SKIP | Evidence under .local |
| --- | ---: | ---: | ---: | ---: | --- |
| Backend Full, unfiltered | 953 | 0 | 0 | 0 | fresh-admin-70e8ae51b99b |
| Packaged executable JAR HTTP/restart (separate run) | 1 | 0 | 0 | 0 | fresh-admin-70e8ae51b99b/packaged |
| Core-only, unfiltered | 157 | 0 | 0 | 0 | fresh-admin-57cf28c0f0d9 |
| Second-domain, unfiltered | 370 | 0 | 0 | 0 | fresh-admin-af680787f81a |
| Targeted R3/R5/R7/R9/R11/R12 + settings + fresh auth | 324 | 0 | 0 | 0 | fresh-admin-2ac11a190eb1 |
| Boundary source / regression checks | 43 checks + checker | 0 | 0 | 0 | scripts/check-tier-boundaries.py; scripts/test-tier-boundaries.py |

Final committed-SHA reruns are reported in delivery; source.json records SHA,
profile/filter/packaged mode and worktree state. This avoids claiming that a
pre-commit working-tree run tested a not-yet-existing SHA. The runner records
the executable JAR SHA-256 for the final packaged proof. No tracked source is
changed between final commit and final verification.

Reproduce: scripts/test-fresh-admin.ps1 -BuildImage dx-platform-build:0.1.0
-Profile full -PackagedProof; repeat -Profile core-only and second-domain with
NO -Tests filter. Targeted selection is the section 8 list plus *Settings*Test,
KeycloakFreshAdmin*Test and JdbcAuditServiceTest, using the same runner -Tests.
No legacy migration is modified. Migration tests assert clean install and
explicit Full V31->V32->V33, Second-domain V31->V32 without V33, Core-only V4
without HPDI/App resources; prior checksums/data and singleton constraints are
preserved. Full regression migration assertions now expect V33, not obsolete
V31; this changes test expectations only, not historical SQL or runtime policy.

### Real HTTP and restart proof

SettingsHttpAcceptanceTest runs production JVMs and performs actual socket HTTP
requests, not MockMvc/JWT mocks. A bootstrap administrator creates disposable
human/service identities in the runner's private realm. A privileged service
account has a real dx-core-audience JWT; both API methods deny it. Both methods
also deny actual Requester/Agent/Coordinator JWTs, disabled and revoked admin
with the SAME previously valid JWT. Missing/invalid JWT is 401; authorization
denial 403; unavailable fresh directory connection 503. The outage fixture
closes a forwarding listener used only for directory reads while the real
Keycloak issuer remains live: it tests unavailable fresh lookup, not Keycloak
process termination or current-session validity. No grants are expanded.

Successful GET/PUT, nested/read-only/duplicate/fractional validation 400, stale
and concurrent CAS 409, audit actor/request-ID/before-after version linkage,
injected audit INSERT failure 500 with unchanged payload/version/metadata/audit
and successful retry are asserted. MockMvc tests additionally cover overflow
to Long.MAX_VALUE and rejection without wrap. Reserved system audit requires
fresh admin; both plain and encoded-colon workspace paths deny ordinary users.
The explicit system read route permits a freshly authorized human admin.

Two distinct application JVMs start against the SAME database/schema; the first
is terminated before the second starts. Payload/version/updatedAt/updatedBy and
audit survive exactly, without deleting/reseeding the database between starts.
Cleanup occurs only after assertions. The additional packaged run executes the
repackaged production JAR, twice. Application classpath runs exclude test-classes
so sample test BPMNs cannot contaminate Second-domain's production registry.
Raw responses.json and jvm-1.log/jvm-2.log are retained in settings-http-* below
the evidence directory. No bearer tokens or credentials are written to responses.

### OpenAPI and failures (not waived)

scripts/test-settings-contract.py validates 62 non-500 settings HTTP responses
per Full run against live schemas, checks target/live schema parity and promoted
route references. Second-domain validates 31 and marks Service Desk absent as
NOT_APPLICABLE, not a skipped backend test. The two expected audit-failure 500
responses are not falsely claimed to conform to the Problem schema.
Whole Service Desk live and target OpenAPI PASS. R12 contract checks PASS.

Whole HPDI live OpenAPI remains BASELINE_FAIL: KnowledgePage has a description
string nested under properties instead of a property schema (three validator
diagnostics). HPDI target remains BASELINE_FAIL: the alerts unread description
contains an unquoted comma, parsed as an extra 'cho badge' key (one diagnostic).
Both error sets match 7dfb2be exactly; settings introduces no new spec errors.
Their impact is whole-document tool rejection despite valid settings subsets.
scripts/test-core-contract.py also retains its baseline FAIL: an old statistics
fixture omits required R12 awaitingReview. Raw logs: .local/settings-core-contract.log,
.local/settings-r12-contract.log and responses.json.contract.log alongside HTTP
evidence. These are NOT an all-OpenAPI PASS and receive no self-issued waiver.

Earlier Full acceptance had 22 FAIL (obsolete V31 migration assertions plus
child Flowable schema isolation), zero ERROR/SKIP. Earlier Second-domain runs
had one child fixture startup FAIL each: first Flowable schema metadata, then
test sample BPMNs contaminating the production process registry. Fixed only
test expectations/fixtures; production workflow and registration are untouched.
All these failed runs remain in fresh-admin-8714b72d453d,
fresh-admin-0130327d638b and fresh-admin-36b5172943ef, not counted as PASS.

### Self-review and merge readiness

Reviewed the entire branch diff against develop@246a8d6, including the accepted
Core capability. No remaining P0/P1 found in the scoped implementation. Atomic
CAS plus settings-row lock makes audit-before accurate under concurrency; audit
MANDATORY joins the caller's transaction. Fresh auth precedes any settings read
or mutation. Core imports no upper tier; HPDI imports no Service Desk; App only
uses HPDI public APIs. Existing runtime policy/eligibility/locking source and
V1-V31 are unchanged. Boundary source and Java ArchUnit gates pass.

P2: accepted Core bounded human-search/lockout/session limitations remain;
Foundation does not verify global specialty names exist in every workspace;
runtime integrations and snapshot application decisions remain future scope.
Known baseline OpenAPI/fixture failures remain explicit. Hosted CI is tracked
on the final Draft PR and is NOT claimed PASS from local runs. Billing failure,
if observed, is BLOCKED, never waived. Merge readiness is conditional on final
independent review, hosted checks and explicit handling of baseline failures.
Draft PR targets develop; MERGE COMMIT ONLY after final review. No merge,
squash/rebase, force-push or runtime/R7 implementation is performed here.

## 14. Final merge preparation - PR 62

This section supersedes section 13's endpoint and baseline-debt conclusions;
section 13 remains historical evidence for 01b38f8, not final acceptance.

### Minimal correction and scope review

Removed /api/core/v1/audit/system. Settings Foundation requires transactional
audit recording, not a new global audit-read HTTP API. Neither Core live/target
OpenAPI nor the approved Foundation scope requires that route. The existing
workspace AuditController, AuditService API and MANDATORY recording transaction
are unchanged. Internal reserved-scope searches still require fresh admin;
mine still denies reserved scopes. Authorization, not a Spring path-variable
syntax assumption, protects scopes containing ':'. HTTP acceptance asserts the
removed route returns 404 for a valid authenticated admin and still checks
plain/encoded reserved workspace paths deny ordinary users. Existing audit
service tests cover authorized search, reserved denial and workspace isolation.

Corrected only contract/fixture defects: removed the stray description string
under KnowledgePage.properties (not an actual response property), quoted the
alerts.unread description containing a comma, and completed the statistics
fixture with all required R12 counters. The fixture uses assigned=1 so the
legacy awaitingHandling=1 and total=1 remain consistent with the partition.
No application behavior or schema required-field rules are relaxed.
The settings validator now requires zero whole-document errors for all six
Core/HPDI/Service Desk live/target documents; baseline equality is not a waiver.

All settings remain PERSISTED_NOT_ENFORCED. No runtime consumer wiring, SLA,
deadline, shift, ET, eligibility, Core workflow locking or V1-V33 migration
changes are included in merge preparation. R7 remains BLOCKED, not implemented
and not represented as a passing Emergency Auto-Queue acceptance gate.

### Final acceptance evidence protocol

Run scripts/test-fresh-admin.ps1 with BuildImage dx-platform-build:0.1.0 for
unfiltered full (with PackagedProof), core-only, second-domain and the section
13 targeted filter. Each evidence directory contains source.json with exact
committed SHA, raw Maven log, JUnit XML and summary.json. HTTP responses and
both application JVM logs demonstrate CAS, rollback and restart without
database deletion. PostgreSQL tests check Full V31 -> V32 -> V33, Second-domain
V31 -> V32 without V33, and Core-only without HPDI/App migrations. Live Keycloak
is pinned to 26.0.8; the outage test disconnects the directory proxy, not the
issuer process. No mock JWT is counted as fresh-revocation evidence.

Run test-settings-contract.py against each final responses.json, plus
test-core-contract.py, test-r12-statistics-contract.py, check-tier-boundaries.py
and the 43 tier-boundary self-tests. Exact final results and raw evidence paths
are recorded in the PR acceptance update; earlier baseline FAILs are preserved
above and must not be relabeled PASS retroactively.

Hosted Actions annotations on the previous HEAD explicitly state jobs were
not started because recent account payments failed or spending limit requires
increase. This is BLOCKED (billing), not code FAIL or CI PASS. Dependent matrix
cancellations and conditional skips are reported separately. Reinspect final
HEAD annotations after push. No waiver or branch-protection bypass is granted.
Keep Draft while hosted gates are blocked, pending final independent review.
