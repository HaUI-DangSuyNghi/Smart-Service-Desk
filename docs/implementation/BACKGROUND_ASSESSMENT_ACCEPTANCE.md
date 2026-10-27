# Background ticket assessment acceptance - 2026-10-27

Baseline: remote develop / HEAD `4cbe38c`; branch
`feat/background-ticket-assessment`. No Frontend source changes and no Spring
dependency, Duty, SLA, R2 or R3 expansion.

## Implementation delivered

- TicketService enqueues in the existing ticket/process/audit transaction.
- BackgroundTicketAssessment uses a durable PostgreSQL assessment row as outbox,
  workspace allowlist, SKIP LOCKED claims, 30-second lease, unique lease token,
  bounded attempts and 5/30-second retry delays. Stale or expired completions are
  fenced; result and READY audit are transactional. Inference is outside the DB
  transaction. Polling only claims rows already eligible after commit.
- TicketReview/View expose persisted advice separately from coordinator
  classification. GET only reads; no synchronous AI dependency is introduced.
- V10__servicedesk_background_assessment.sql adds assessment/history tables,
  primary-key deduplication, state/lease/result constraints and a pending index.
  No additional migration was necessary during acceptance.
- JwtServiceIdentity obtains fresh Keycloak client-credentials JWTs and uses the
  existing issuer/audience/signature decoder plus service-account/azp/expiry checks.
  Requester subject is not the service principal and no requester JWT is persisted.
- Realm/setup/compose provision the worker only for lab-a intelligence:process,
  using generated secrets. No realm admin, ticket-create or review grant is given.
  Existing realms require explicit administrative provisioning, not a shared reset.
- WorkspaceDirectory.refresh reloads live grants/memberships before inference and
  completion, so the normal directory cache cannot authorize a revoked worker or
  requester. Directory outages fail closed. External authorization and DB commit
  are not a distributed atomic transaction.
- Rejected Keycloak credentials/signatures/issuer/audience are permanent
  AUTHORIZATION_REVOKED, not retryable INTELLIGENCE_UNAVAILABLE. Keycloak outages
  remain retryable. Python's separate private credential remains independent;
  Python 401/403 are permanent INTELLIGENCE_INTEGRATION_FAILED.
- StoredAssessment now exposes safe errorCode without upstream text. Worker logs
  contain neither ticket contents nor secrets.

Principal source files: BackgroundTicketAssessment.java, StoredAssessment.java,
TicketService.java, TicketReview.java, TicketReviewService.java, ServiceIdentity.java,
JwtServiceIdentity.java, WorkspaceDirectory.java, KeycloakWorkspaceDirectory.java,
BackgroundProcessing.java and WorkspaceBackgroundProcessing.java. Regression changes
are in BackgroundTicketAssessmentTest.java, KeycloakWorkspaceDirectoryTest.java,
the SQL boundary checker/regression and the existing disposable test runner.

## Current-source regression

| Suite | PASS | FAIL | ERROR | SKIP |
| --- | ---: | ---: | ---: | ---: |
| Backend full | 522 | 0 | 0 | 0 |
| Background-specific, also included in full | 20 | 0 | 0 | 0 |
| Core-only | 104 | 0 | 0 | 0 |
| Supported second-domain selection | 30 | 0 | 0 | 0 |
| Source boundary regression | 40 | 0 | 0 | 0 |
| Python HTTP/triage contract and AI07 regression | 100 | 0 | 0 | 0 |
| Real Keycloak/Java/Python acceptance | 13 | 0 | 0 | 0 |

ArchUnit TierBoundaryTest is included in the full Backend run. PostgreSQL-backed
background tests cover transaction rollback/visibility, availability immediately
after commit, active lease exclusion, concurrent claims, stale fencing, bounded
backoff, terminal errors, audit rollback, revoked identities and workspace scope.

OpenAPI checks PASS: existing 14 HTTP request/response checks, 9 background
positive/negative checks, 3 live GET examples and 3 actual real-service lifecycle
responses. Core contract and exact source debt checks PASS. The existing checker
explicitly does not waive known whole-HPDI live/target baseline schema errors.

Current evidence, not the earlier 519-test report:

- `.local/task-execution-full/summary.json` and `task-execution-d04c6768b24b/` XML.
- `.local/task-execution-full-targeted/summary.json`: 20 tests.
- `.local/task-execution-core-only/summary.json`: 104 tests.
- `.local/task-execution-second-domain/summary.json`: 30 tests.
- `.local/background-python-http-with-git.log`: 100 Python tests.

## Real integration and restart evidence

Successful isolated project: `assessment-1cfd5665694a`.
Private evidence is under `.local/assessment-1cfd5665694a/`:
summary.json, pending.json, ready.json, degraded.json, runtime.log,
database-evidence.json and gateway-evidence.json. No secrets from this directory
belong in Git or PR attachments. The generated realm and compose files contain
random acceptance-only secrets and must remain ignored.

The acceptance jar SHA-256 is
`78a2516d315010c458f30091c9784db44bfed81e2234ebc8e02744d44ef4a6bf`.
It was built from this task's Java source using
`int01-bedemo01-20261018195717-java-build:ci`; build evidence is
`.local/assessment-4d0a02954a19/build.log`. Later attempts reused exactly that jar,
not an old packaged Backend. Python executes current repository source read-only,
with actual installed dependencies and actual profile/contract files.

Real services: PostgreSQL 16.6, imported Keycloak realm with real signatures and
service accounts, current Java jar/embedded Flowable, real Python rules HTTP
service and APISIX Gateway. Runtime image overrides are recorded in summary.json.
No mock identity server, mock transport or canned Python assessment is used.

All 13 runner assertions PASS:

1. Python stopped: ticket POST succeeds with persisted PENDING and no AI wait.
2. PENDING survives Backend replacement on the same PostgreSQL volume; a new JVM
   produces READY with rules provenance, exact evidence and NETWORK/LOW/I1/U2.
3. READY survives another JVM restart with unchanged attempt count/result.
4. Coordinator override persists HARDWARE/HIGH separately; AI never auto-reviews.
5. Insufficient evidence preserves needsReview and null impact/urgency.
6. Real requester tokens enforce workspace isolation.
7. Stopped Python produces a retryable outage, then recovery to READY.
8. Paused Python exceeds the real HTTP deadline, then recovery to READY.
9. Backend SIGKILL after a durable claim, an actual 31-second wait and a new JVM
   recover the same job with attempt=2. PostgreSQL/volume are not replaced.
10. Two healthy Backend JVMs concurrently hold two different leases while Python
    is paused; six total jobs finish with attempt=1 and exactly one READY each.
11. Invalid Python credential degrades on the first attempt without retries.
12. Invalid real Keycloak client secret degrades on the first attempt, correctly
    classified as authorization failure, not Intelligence unavailability.
13. Removing intelligence:process through real Keycloak Admin API causes immediate
    permanent degradation despite earlier cached grants.

Additional reinspection restarted the same isolated stack/volume and verified GET
through the real Gateway preserved READY and the coordinator override. Durable SQL
evidence has 14 jobs (11 READY, 3 DEGRADED), 47 history events, 17 claims, no active
leases and exactly one READY event per completed ticket. Containers/networks are
stopped/removed; the acceptance project's named volumes remain for inspection.

## Harness corrections and limits

Earlier isolated attempts are retained as ERROR, not counted as PASS. The local
baseline PostgreSQL 16.15 digest exits with `postgres is not the name of a known
user`; acceptance uses the same verified digest-pinned PostgreSQL 16.6 as the test
runner. No unrelated Compose dependency pin was changed. This is not certification
of the unmodified 16.15 runtime image or of a production deployment.

The initial supplemental Python run was 98 PASS / 2 FAIL because its disposable
test image lacked the Git executable required by two immutable-dataset checks.
Installing Git only inside a disposable test container, using HTTPS Debian
repositories, allowed the identical unchanged test selection to finish 100 PASS /
0 FAIL / 0 ERROR / 0 SKIP. No application/runtime dependency was changed. Both
the initial failure log and final successful log remain in .local.

The runner also removes Compose-config-generated global resource names before
launch, so its final volumes/networks are project-scoped. An earlier failed run
created previously absent dx-platform-named volumes; labels identify them as owned
by assessment-4d0a02954a19. It did not reuse or delete another member's volume.

Stopping a container can yield a connect timeout rather than immediate refusal;
the initial runner overconstrained this to INTELLIGENCE_UNAVAILABLE and timed out
waiting for that code. Both timeout and unavailable are valid transient outages.
The final run accepts either, separately verifies an actual paused-service timeout,
and verifies successful recovery. No production error mapping was weakened.

Literal ABSTAIN is NOT SUPPORTED by service-desk@0.3.0 rules. Its supported
INSUFFICIENT_INFORMATION semantics are verified instead. There is no fabricated
confidence, model score, risk level, hierarchy or retrieved document citation.
At-least-once inference after lease loss is intentional; completion is fenced.

## FE and merge handoff

Live OpenAPI includes PENDING/READY/DEGRADED GET examples and the persisted DTO.
See BACKGROUND_ASSESSMENT_FE_HANDOFF.md for polling, safe errors, provenance,
authorization, review-form mapping, unsupported fields, deployment and rotation.
No Frontend source is changed.

GitHub Actions status is separate from these local results; no hosted CI PASS is
claimed. Commit SHA and PR URL are supplied in the final handoff after publication.

Feature conclusion: **READY FOR INTERNAL DEVELOPMENT MERGE** on the verified
integration environment. The separate baseline PostgreSQL image issue and any
hosted-CI quota/availability limitation remain explicitly outside this conclusion.
This is not unconditional production-deployment readiness.
