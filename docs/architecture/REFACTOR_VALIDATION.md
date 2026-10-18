# Strict boundary recovery: completed local validation

Date: 2026-10-19 (Asia/Saigon). Branch:
`refactor/bnd-01-07-strict-boundary-recovery`.
Functional base: `develop@728cdc3`; BND-00 base: `ebf7037`.
Runtime code tested: `ce7d422fc4ba62f85626489ccd830bab40242738`.
The closure commit adds generic ownership guard coverage and this evidence;
production Core, HPDI and hdpi runtime code remains identical to the tested code.

## Result

BND-00 through BND-07 are implemented. Source and compiled Java inventories are
empty, with `mode: strict`; migration exceptions are rejected. Applications own
business policy, persistence, DTOs, BPMN and AI profiles and consume HPDI public
capabilities. Assembly owns bootstrap. Original V1-V5 migrations and every original
Service Desk profile artifact are byte-identical to develop.

| Verification | Result |
| --- | --- |
| Source boundaries and strict metadata | PASS, zero exceptions |
| Mutation/ratchet checks | PASS, 31 tests |
| Assembled Java regression, Linux/JDK 17/PostgreSQL 16.6 | PASS, 151 tests, no failures/errors/skips |
| Core-only classpath/resources/database proof | PASS, 69 tests, no failures/errors/skips |
| Second domain with Service Desk excluded | PASS, 11 tests, no failures/errors/skips |
| Real second-domain Java -> HPDI -> private Python | PASS, 1 test, no failures/errors/skips |
| Python source and installed-wheel regression | PASS, 272 tests in each mode |
| Immutable profile/foundation checks | PASS, including 145 negative foundation cases |
| Generic wheel and image isolation | PASS, no app profile assets bundled; external read-only profiles verified |
| Real Java/Python transport success and fault cases | PASS, 5 scenarios / 6 Java tests |
| Real Keycloak/APISIX/Java/Python Service Desk triage | PASS, 11 acceptance checks |
| Real Core authorized retrieval/provenance | PASS, 8 acceptance cases |
| Real Room Request H/P/D/I gateway reuse | PASS, 5 acceptance scenarios |
| Room Request and HPDI HTTP contracts | PASS, 6 app responses and 4 HPDI responses |
| Service Desk CRUD/statistics/workflow HTTP contracts | PASS, 73 real responses |
| Application/HPDI OpenAPI and validator regression | PASS |
| Historical migration/profile byte preservation | PASS |
| Second-app production changes in Core / HPDI / hdpi | 0 / 0 / 0 |
| Whitespace and final packaged bootstrap | Checked before final commit |

## What the integration checks prove

The isolated second-domain profile excludes Service Desk classes, migrations and
BPMN; the real Python test loads only Room Request's external profile and returns
BOOKING/NORMAL without modifying generic implementations. The complete gateway
proof covers trusted actors, authorized knowledge bytes/checksum, app persistence,
real Python triage, shared inbox, independent completion and cross-workspace denial.

Service Desk's existing ticket/progress/triage wire shape is retained using
application-owned DTOs. Real acceptance covers rework, initiator confirmation,
concurrent duplicate submission, statistics and audit. Content-aware deployment
preserves running-instance compatibility when identical BPMN bytes change paths.

The same-revision Java/Python test ran from a clean disposable worktree. Its
scenarios were success, wrong service credential, unavailable service, timeout and
malformed response. Timeout/malformed faults were injected after a real Python
SUCCESS. That suite uses MockMvc JWT identities; the separate 11-check gateway
suite uses actual Keycloak login, JWT verification and APISIX. Browser UI testing
is outside this backend recovery.

Retrieval acceptance verifies authorization before scoring, refreshed-JWT denial
after membership revocation, corpus/version isolation, exact downloaded checksums,
missing authority records, unavailable authority and independent triage behavior.

## Evidence and execution scope

Safe synthetic summaries/logs are retained locally under ignored `.local/`,
including `bnd07-full.log`, `bnd07-core-only-final.log`,
`bnd07-second-domain.log`, `bnd07-room-python-e2e.log`,
`ai04-e2e/summary.json`, `boundary-gateway-triage.json`,
`boundary-core-retrieval.json`, `boundary-tier-reuse.json` and the captured
contract responses. Local secrets and disposable database credentials remain
ignored and are not delivery artifacts.

The Tier boundaries workflow now runs source/bytecode checks, isolated module
proofs and real second-domain Python integration. Local results do not assert
remote GitHub CI, branch protection or integration approval. Existing G9 security
scan failure remains a release blocker; this refactor preserves that release status.
No frontend restructuring or frontend artifacts are committed. No remote branch,
tag or PR was published by this task.

See [RECOVERY_PROGRESS.md](RECOVERY_PROGRESS.md) for phase ownership and
[STRICT_BOUNDARY_OPERATIONS.md](STRICT_BOUNDARY_OPERATIONS.md) for configuration.
