# Strict boundary recovery: completed local validation

Date: 2026-10-19 (Asia/Saigon). Branch:
`refactor/bnd-01-07-strict-boundary-recovery`.
Functional base: `develop@728cdc3`; BND-00 base: `ebf7037`.
Runtime code tested: `ce7d422fc4ba62f85626489ccd830bab40242738`.
The closure commit adds generic ownership guard coverage and this evidence;
production Core, HPDI and hdpi runtime code remains identical to the tested code.
The reference-structure follow-up also leaves those production sources unchanged;
its additional checks for the new asset roots and frontend are recorded below.

## Result

BND-00 through BND-07 are implemented. Source and compiled Java inventories are
empty, with `mode: strict`; migration exceptions are rejected. Applications own
business policy, persistence, DTOs, BPMN and AI profiles and consume HPDI public
capabilities. Assembly owns bootstrap. Original V1-V5 migrations and every original
Service Desk profile artifact are byte-identical to develop.

| Verification | Result |
| --- | --- |
| Source boundaries and strict metadata | PASS, zero exceptions |
| Mutation/ratchet checks | PASS, 33 tests |
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
The structure follow-up restores the strict TypeScript frontend scaffold,
platform-only build and CI from the legacy reference. Application profiles now
live under `intelligence/src/apps`; generic `hdpi` packaging remains isolated.
No build output or installed dependencies are committed. No remote branch, tag
or PR was published by this task.

See [RECOVERY_PROGRESS.md](RECOVERY_PROGRESS.md) for phase ownership and
[STRICT_BOUNDARY_OPERATIONS.md](STRICT_BOUNDARY_OPERATIONS.md) for configuration.

## Reference-structure follow-up

The final layout restores frontend platform/apps and keeps Python application
assets at `intelligence/src/apps`. The generic import package remains `hdpi`.

| Additional verification | Result |
| --- | --- |
| Frontend locked install, strict typecheck and ESLint | PASS, Node 24.12.0 |
| Frontend extension, import guard and Service Desk client regression | PASS, 6 tests |
| Full and independent platform TypeScript builds | PASS, platform artifact has no app code |
| Python source and rebuilt installed-wheel regression after asset move | PASS, 272 tests each |
| Boundary mutation suite after separating src/hdpi from src/apps | PASS, 33 tests |
| Foundation, immutable version history against HEAD and legacy branch | PASS |
| Moved application assets versus previous committed bytes | PASS, 16 identical files |
| Built wheel/sdist and installed generic image | PASS, application profile assets excluded |
| Restored disposable Room Request gateway runner | PASS, 5 scenarios; 6 app and 4 HPDI contract responses |
| Default Service Desk profile root through gateway and real Python | PASS, create/triage/progress/statistics; 4 contract responses |
| Service Desk, Room Request and HPDI OpenAPI syntax | PASS |

Safe local evidence includes `structure-python-source.log`,
`structure-wheel-build.log`, `structure-wheel-tests.log`, `structure-room-e2e.log`,
`dx-tier-reuse-4f5a09f6a416.json`, `structure-servicedesk-summary.json` and the
captured HTTP responses. Service Desk captures use paths relative to the OpenAPI
server prefix; validation was rerun successfully after normalizing the capture
format. No product contract or implementation changed for that normalization.

Superseded reference files are intentionally not restored: production
`CoreApplication` is replaced by assembly bootstrap, `IntelligenceUnavailable`
by classified capability failures, Room Request V6 by V7 after the required
forward migration, and the old private HTTP test by the hardened transport suite.
