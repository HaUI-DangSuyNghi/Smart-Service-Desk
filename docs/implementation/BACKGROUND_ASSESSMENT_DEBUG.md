# Background assessment investigation (2026-10-27)

Branch: `feat/background-ticket-assessment`; base HEAD:
`4cbe38c95676f9a45ee4d56a47e229b930c89bec`.
This is local debugging evidence, not a deployment or hosted-CI certification.

## Root cause: invalid test service JWT

A test-only spy around the real JWT decoder and background processing boundary
captured the following before any production remediation:

```text
org.springframework.security.oauth2.jwt.JwtValidationException
invalid_token: The iss claim is not valid
invalid_token: The aud claim is not valid
  at NimbusJwtDecoder.validateJwt(NimbusJwtDecoder.java:193)
  at NimbusJwtDecoder.decode(NimbusJwtDecoder.java:139)
  at JwtServiceIdentity.run(JwtServiceIdentity.java:64)
```

The generated, correctly signed test JWT had neither issuer nor audience.
Application configuration requires the configured issuer and audience `dx-core`.
`JwtServiceIdentity` correctly rejected the token. Its safe unavailable error was
translated by `WorkspaceBackgroundProcessing` to `INTELLIGENCE_UNAVAILABLE`.
The AI adapter and success transaction were never reached.

After one claim, real PostgreSQL queries returned:

```sql
select status, attempts, available_at, lease_until, error_code
from servicedesk_assessment where ticket_id = ?;
-- PENDING | 1 | 2026-10-27 07:21:28.286361+00 | NULL | INTELLIGENCE_UNAVAILABLE

select attempt, event, error_code
from servicedesk_assessment_history where ticket_id = ? order by id;
-- 0 | QUEUED  | NULL
-- 1 | CLAIMED | NULL
-- 1 | PENDING | INTELLIGENCE_UNAVAILABLE
```

The claim transaction committed; the failure transaction committed the retry
and released the lease. `processOne() == true` means a job was claimed, not that
assessment succeeded. The job remained PENDING intentionally until retry.

Fix: the test issuer is explicitly configured to a local test identity, and the
test JWT carries that exact issuer and `dx-core` audience. Signature, expiry,
issuer, audience, service identity and workspace checks remain enabled.
Additional tests use signed wrong-issuer and wrong-audience tokens to prove
fail-closed behavior. No production security checks were relaxed.

Raw test-only diagnostics were removed after evidence capture. They never
printed ticket input, bearer tokens or credentials. No verbose production
logging was added. Original sanitized RED logs remain local:
`.local/review-api/background-red-jwt.log` and `background-red-outer.log`.

## Availability assertion

The old `Job should not yet be available` assertion followed an ordinary,
committed creation. It did not arrange future availability, retry or a lease.
Expecting `false` contradicted immediate background processing after commit.
No production delay was introduced.

Separate tests now prove:

- Another connection cannot claim the newly inserted job before commit.
- The job is claimable immediately after commit, without changing available_at.
- A future available_at and an active lease prevent claims.
- A transient failure schedules retry; success clears the error and persists READY.
- Exhaustion/permanent failure reaches DEGRADED without blocking manual review.
- Competing workers cannot both finish; expired leases recover and stale completion is fenced.
- Assessment uniqueness is enforced by the database primary key.
- Audit failure rolls back READY/result/history, then a later attempt can succeed.
- GET reads stored results without inference; foreign workspace/owner access is rejected.
- Worker/requester revocation prevents releasing a result; coordinator decisions are not overwritten.

Lease recovery tests recreate the worker against retained PostgreSQL state;
they do not claim to perform an operating-system/JVM restart.

## Reproduction and acceptance

```powershell
scripts/test-task-execution.ps1 -BuildImage dx-core-build:0.1.0 -Profile full -Test BackgroundTicketAssessmentTest
scripts/test-task-execution.ps1 -BuildImage dx-core-build:0.1.0 -Profile full
scripts/test-task-execution.ps1 -BuildImage dx-core-build:0.1.0 -Profile core-only
scripts/test-task-execution.ps1 -BuildImage dx-core-build:0.1.0 -Profile second-domain
python scripts/check-tier-boundaries.py
python scripts/test-tier-boundaries.py
```

The runner uses digest-pinned PostgreSQL 16.6 and real Flowable 7.1.0. Only the
AI transport and directory fixture are controlled test doubles; JWT decoding,
HTTP ticket operations, JDBC transactions, migrations and BPMN are real.
Targeted evidence has a separate directory, and a failed Maven run now writes
its actual XML totals rather than leaving the previous success summary intact.

RED: diagnostic success test 0 PASS / 1 FAIL / 0 ERROR / 0 SKIP with invalid JWT.
GREEN: final targeted suite 18 PASS / 0 FAIL / 0 ERROR / 0 SKIP.
Full regression totals and evidence paths are reported in the task handoff.

Final runs after the live-contract fix:

| Suite | PASS | FAIL | ERROR | SKIP |
| --- | ---: | ---: | ---: | ---: |
| Backend full | 519 | 0 | 0 | 0 |
| Background assessment (included in full) | 18 | 0 | 0 | 0 |
| Core-only | 103 | 0 | 0 | 0 |
| Supported second-domain selection | 30 | 0 | 0 | 0 |
| Source boundary regression | 40 | 0 | 0 | 0 |

ArchUnit is included in full (`TierBoundaryTest`, one test, no failures/skips).
Flyway validated and applied all 10 migrations on a fresh full-profile database.
Source boundary checking and core contract script also passed. HTTP schema checks
are 14 existing checks plus 9 assessment checks, not additional JUnit tests.

Final full XML and HTTP evidence:
`.local/task-execution-full/task-execution-b09114e06c6d/`.
Other suite summaries and Maven logs:
`.local/task-execution-core-only/`, `.local/task-execution-second-domain/`.
Contract evidence: `.local/review-api/background-http-contract-final.log`.
The runner also records `postgres-version.txt`; final relevant source hashes are
in `.local/review-api/background-final-source-hashes.json`.

## Additional contract defect

The full Java suite passed, but validating its actual HTTP response against the
live Service Desk OpenAPI failed: `Additional properties are not allowed
('assessment' was unexpected)`. Ticket had `additionalProperties: false` and the
new DTO property had not yet been declared. This was a real feature contract gap,
not an invalid validator expectation.

The live contract now explicitly describes persisted assessment and its result,
with strict properties, provenance, nullable model version, and READY versus
PENDING/DEGRADED result invariants. No endpoint was added or promoted. Existing
14 HTTP contract checks pass, plus 9 additional positive/negative checks using
real READY HTTP evidence. They reject fake confidence, unknown state, missing
version and incompatible result/state combinations.

```powershell
python scripts/test-servicedesk-execution-contract.py <run>/servicedesk-http-evidence.json --assessment-evidence <run>/background-assessment-http-evidence.json
python scripts/test-core-contract.py
```

## Remaining feature delivery

This investigation fixes incorrect test assumptions, not a demonstrated
production JWT/transaction defect. Deployment service-account provisioning,
background worker configuration, complete FE handoff and the
overall feature PR are separate unfinished delivery items. Background inference
currently uses the validated rules triage adapter, not hierarchical LLM inference.
No hosted GitHub Actions PASS, commit, push, merge or complete FE readiness is
claimed by this debugging note.
