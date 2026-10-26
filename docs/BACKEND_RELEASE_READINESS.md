# Backend Release Readiness - 2026-10-26

HEAD: `f94f0ef102cbc7d5737ca05270b0affab1fde1a6`, plus existing uncommitted
hardening. Date/timezone: 2026-10-26, Asia/Saigon. Initial security evidence:
`.local/release-readiness-20261026/`. Resumed real-DB/runtime evidence:
`.local/rr-jackson-20261026-a/`. No commit/push/merge/tag/release or license grant.
Only the new, task-owned disposable Docker project was created/operated.

## 1. Decision and preserved baseline

**Release: NO-GO. Jackson-patched PostgreSQL 16.6 API demo: PASS.**
`FULLSTACK_INTEGRATION_ACCEPTANCE.md` retains the 95 Service Desk HTTP assertions,
additional authorization/Room Request/audit checks, real Java/Python, fallback and
restart persistence. Its accepted JAR SHA-256 remains
`6228e0d6041801b9ae5f293a5bab68a855efd8ee27dc92ec44ddcad9b15a3315`.
This historical PASS was not used to certify the dependency change. The new
candidate has its own passing DB, HTTP, Python and restart evidence in section 9.

The initial missing Docker Desktop pipe blocker is resolved after the owner
started Desktop. On resume at 09:19 +07, all five candidate services were healthy;
startup/build jobs had completed, and the Java-index download had failed with a
network timeout, not remained hung. No unknown/IDE/shared process was killed.
The 09:19 host sample had 1.89 GiB available RAM, 6% CPU, C: 68.8 GiB and
D: 62.1 GiB free. The daemon has 16 CPUs and a 7.61 GiB memory limit.

The old `int01-pg166-20261025-a` containers were already stopped at initial
inspection. Their images, containers and volumes remain untouched. The new
`rr-jackson-20261026-a` project uses fresh PostgreSQL/files volumes, independently
generated protected credentials, and loopback 29080/28081. No VM/WSL/DinD,
Desktop/global change, prune, old-container restart or PostgreSQL 16.15 recovery
operation was performed. No production pin or Frontend/Python source changed.

| Gate | Decision |
| --- | --- |
| Accepted old 16.6 API demo artifact | Historical PASS retained; stopped resources preserved |
| New Jackson-patched candidate / PostgreSQL 16.6 | PASS: real-DB 265/91/40, fresh live HTTP/Python/restart, zero required skips |
| Actual browser/PKCE/UI E2E | NOT ACCEPTED; stub-browser CI and API tests are different gates |
| Exact production-pinned PostgreSQL 16.15 | NOT ACCEPTED; previous filesystem blocker unresolved |
| Security G9 / current image scan | FAIL: source 7 HIGH + 2 CRITICAL; three runtime images 381 HIGH + 27 CRITICAL; Java/Keycloak image scans BLOCKED |
| Proof of FOSS / release approval | INCOMPLETE |

Reviewed all four required Backend/acceptance documents, SEC-01 report/inventory,
October 15 raw image reports, October 25 source scan, licensing documents and all
six CI workflows. Older runtime reports remain historical; the later 16.6 PASS
supersedes their earlier full-stack-blocked result only for its accepted artifact.

## 2. Confirmed fixes

### Reproducible acceptance configuration

The successful APISIX single-worker file and explicit Python command previously
existed only in ignored `.local/int01-pg166-20261025-a/` overrides. The tracked
`scripts/acceptance/integration-compose.yaml` now mounts
`scripts/acceptance/apisix-single-worker.yaml` read-only and explicitly runs
`python -m hdpi.http_service --host 0.0.0.0`. The current Python Dockerfile already
has that entrypoint; no Python source/algorithm defect was established.

The acceptance APISIX file differs from the normal standalone data-plane file
only by `nginx_config.worker_processes: 1`, preserving the demonstrated 128 MiB
workaround. Production APISIX configuration and main Compose DB pin are unchanged.

`scripts/acceptance/test-integration-config.py` checks the actual native Compose
merge: correct single config mount and entrypoint, loopback 18081/19080, no host
PostgreSQL/Java/Python ports, project-owned volumes and unchanged 16.15 pin. It
uses random process-only placeholders and an empty temporary env file, not shared
`.env` credentials. It creates no Docker resources and runs without a daemon.
The integrated workflow runs this guard in G10; no scanner/gate was weakened.

### Jackson dependency remediation

One property in `backend/pom.xml` moves the Boot-managed Jackson BOM from 2.17.3
to **2.18.11**. All eight packaged Jackson modules align, including JSON, YAML,
TOML and Java time/JDK8. Official FasterXML advisories and the Maven Central BOM
were retrieved with verified TLS; source URLs/hashes are retained locally.

`JsonSecurityRegressionTest` feeds an 8 KiB malformed token through DataInput:
old Jackson produces an unbounded diagnostic; the patched parser bounds it.
RED: one expected failure, zero errors/skips. GREEN: one pass, zero errors/skips.
This reproduces CVE-2026-89425 at library level, NOT a proven public endpoint
exploit. Existing validator/transport/security/architecture tests also execute.
Networknt 1.5.9's published POM uses Jackson 2.18.3, supporting selection of the
2.18 line, not certifying every Flowable/DB path.

Java 17, Boot 3.3.13, Framework 6.1.21, Security 6.3.10, Flowable 7.1.0, Tomcat
10.1.61 and pgJDBC 42.7.12 remain unchanged. No independent Spring override or
Keycloak migration. Jackson is a minor-line change; DB/live compatibility gates
were mandatory and now pass for this candidate's PostgreSQL 16.6 API demo.

## 3. Security before and after

Trivy 0.76.0 portable archive SHA-256:
`7b4762a5a5326cb4b83dfd2bb5d9aaf15a1ed867c75cad7dd8b38840a28e7be6`,
matching the publisher checksum. DB v2 UpdatedAt:
`2026-10-25T19:10:35.479132794Z`. Both comparison scans use this SAME cached DB,
offline Maven resolution and `--include-dev-deps`; no ignore/unfixed filter.
Tooling is task-local. Some native curl downloads failed certificate revocation
checking; Python's standard verified HTTPS succeeded, without TLS bypass/CA edits.

| Target | Before HIGH / CRITICAL | After HIGH / CRITICAL | Coverage |
| --- | --- | --- | --- |
| Backend source, same DB | 15 / 2 | 7 / 2 | Eight Jackson findings absent after patch |
| Frontend including development dependencies | No same-scope before scan | 2 / 0 | Owner handoff; unchanged |
| Acceptance pip requirements | No finding | 0 / 0 | NOT installed AI/dense/cloud coverage |
| Dockerfile static checks | Existing warnings retained | 2 HIGH, 5 LOW | Separate from vulnerability counts |
| Historical SEC-01 five-image inventory | 369 / 15 | NOT RESCANNED | 384 occurrences retained; do not subtract source fixes |

Evidence: `scan-before-all.json`, `scan-after-all.json`, `scan-after.json` and
`scanner-version.txt` under the new evidence directory. Source scan is not an
image/OS scan. Misconfig scanning mistakes `intelligence/Dockerfile.dockerignore`
for a Dockerfile and reports a parser error; that file is not claimed covered.
The five real Dockerfiles have separate results. Installed AI packages, optional
locks, final image users and OS packages still need runtime inventory/scans.

`security/release-readiness-20261026.csv` retains 405 non-deduplicated rows:
17 Backend source findings, two FE findings, all 384 historical image occurrences
and two static HIGH warnings. Each row records package/version/advisory, relation
or unresolved vendor provenance, impact/uncertainty, candidate fix, official-check
status, compatibility, disposition and owner/next gate. Historical rows are NOT
current-image certifications. A fetched page does not establish availability of
a compatible patched binary; unresolved candidates are explicitly labelled.

### Eight Jackson source fixes

All are transitive through Networknt, Boot JSON/web and/or Flowable; actual
resolved coordinates are in `security/release-readiness-20261026-dependencies.txt`.
Initial disposition was SOURCE_FIXED_RUNTIME_PENDING. The new candidate packages
all eight Jackson modules at 2.18.11, its running JAR hash matches the tested
artifact, and all required DB/live gates now pass. Historical rows in the initial
CSV remain point-in-time evidence, not rewritten image scan results.

| Advisory | Package before | Official 2.18 fix | Relevant condition/evidence |
| --- | --- | --- | --- |
| CVE-2026-68494 | jackson-core 2.17.3 | 2.18.8 | Async numeric parser; no direct application use found |
| CVE-2026-89407 | jackson-core 2.17.3 | 2.18.11 | String-to-number coercion ReDoS; endpoint exploit not reproduced |
| CVE-2026-89425 | jackson-core 2.17.3 | 2.18.11 | DataInput diagnostic growth; actual red/green library test |
| CVE-2026-54512 | jackson-databind 2.17.3 | 2.18.8 | Polymorphic generic validator bypass; no application activation found |
| CVE-2026-54513 | jackson-databind 2.17.3 | 2.18.8 | Array subtype allowlist bypass; no application enablement found |
| CVE-2026-68497 | jackson-databind 2.17.3 | 2.18.10 | XML Duration/Calendar coercion; no public XML datatype DTO found |
| CVE-2026-91776 | jackson-databind 2.17.3 | 2.18.11 | Polymorphic type cache; no application enablement found |
| CVE-2026-91777 | jackson-databind 2.17.3 | 2.18.11 | Identity-enabled forward references; no application JsonIdentityInfo found |

Primary sources: corresponding FasterXML jackson-core/jackson-databind GitHub
security advisories; exact URLs are in the CSV and local `primary-advisories.json`.
Application-source absence is not an exhaustive transitive-library immunity proof.

### Nine remaining Backend source findings

The official Spring advisories were checked individually. Only actuator starter
is direct; the others are transitive. All remain scanner-visible and fail G9,
even where the advisory condition is not observed in this headless application.

| Advisory / severity | Package/version | Official fix, impact and disposition |
| --- | --- | --- |
| CVE-2026-22732 CRITICAL | spring-security-web 6.3.10 | OSS 6.5.9/7.0.4; 6.3.15 enterprise-only. Existing eager-header workaround retained, timing test PASS. MITIGATED, dependency OPEN; cache-header override behavior differs with eager writing. |
| CVE-2026-47884 CRITICAL | spring-webmvc 6.1.21 | OSS 7.0.9; 6.1.29/6.2.20 enterprise-only. Requires XsltView plus wildcard implicit view rendering; none found. OPEN; do not force Framework 7 into Boot 3.3/Flowable. |
| CVE-2026-40984 HIGH | micrometer-core 1.13.15 | OSS 1.15.12/1.16.6; 1.13.19 enterprise-only. Actuator installed, instrumentation exposure not ruled out. OPEN; coordinated train validation required. |
| CVE-2026-40973 HIGH | spring-boot 3.3.13 | OSS 3.5.14/4.0.6; 3.3.19 enterprise-only. Needs local attacker and persistent servlet sessions; current JWT chain stateless, persistence not enabled. OPEN conditional exposure. |
| CVE-2026-22733 HIGH | spring-boot-starter-actuator 3.3.13 | OSS 3.5.12/4.0.4; 3.3.18 enterprise-only. Needs protected app endpoint below /cloudfoundryapplication; none found. OPEN. |
| CVE-2025-41249 HIGH | spring-core 6.1.21 | OSS 6.2.11. Affected generic method-security annotations/EnableMethodSecurity not found; custom MVC guard is different. OPEN. |
| CVE-2026-41850 HIGH | spring-expression 6.1.21 | OSS 6.2.19/7.0.8; 6.1.28 enterprise-only. No application untrusted SpEL evaluator found; server-owned BPMN unchanged. OPEN. |
| CVE-2026-41842 HIGH | spring-webmvc 6.1.21 | OSS 6.2.19/7.0.8; 6.1.28 enterprise-only. Needs filesystem static resources AND versioning; no such configuration found. OPEN. |
| CVE-2026-41845 HIGH | spring-webmvc 6.1.21 | OSS 6.2.19/7.0.8; 6.1.28 enterprise-only. JavaScriptUtils.javaScriptEscape usage not found in REST backend. OPEN. |

Official URLs follow `https://spring.io/security/<advisory-id>`; exact references
are recorded per CSV row. No enterprise entitlement, support status or waiver is
assumed. A compatible supported Spring train needs a separately reviewed change
and a NEW regression matrix for that changed artifact, not a blind major upgrade.
The Jackson-only artifact's now-passing tests do not certify a different Spring
train. Source findings remain **7 HIGH + 2 CRITICAL after runtime acceptance**.

The nine rows above are reassessed after the new-JAR acceptance. Only
spring-boot-starter-actuator is direct; the other eight findings are transitive,
as recorded by the resolved tree. No independently compatible drop-in OSS fix
on the currently selected versions was established. No dependency is upgraded in
this resumed phase, no finding is deleted, and no security gate is weakened.

| Advisory | Safe-update decision now | Mandatory evidence if changed |
| --- | --- | --- |
| CVE-2026-22732 | Retain existing vendor eager-header mitigation; dependency OPEN. Moving Security 6.3 to the patched 6.5 line needs aligned Boot review. | Header commit/cache regression, real JWT/F04/selfService, full profiles and live reacceptance |
| CVE-2026-47884 | OPEN; no XsltView/implicit wildcard MVC activation found. OSS fix is Framework 7; enterprise 6.1/6.2 availability is not an OSS entitlement. No Framework-7 override into Boot3/Flowable7.1. | Explicit XSLT/view exposure review, aligned Boot/Framework/Java/Flowable proof and full reacceptance |
| CVE-2026-40984 | OPEN; affected Micrometer installed through actuator. An untrusted metadata-to-metrics path is not ruled out by the API demo. No isolated 1.15/1.16 override. | Actuator/metrics authorization and resource-consumption regression plus full profiles/live gates |
| CVE-2026-40973 | OPEN conditional exposure; current chain is stateless, no persistent sessions found. Boot3.5 patch train is outside the validated 3.3 train. | Persistent-session condition/mitigation review, identity/session regressions and full reacceptance |
| CVE-2026-22733 | OPEN conditional exposure; no application /cloudfoundryapplication child handler found. No blind Boot patch-train substitution. | Actuator matcher/child-path authorization regressions and full reacceptance |
| CVE-2025-41249 | OPEN; generic method-security annotation activation not found. Custom MVC permission guard is not proof of transitive immunity. | Generic/inherited annotation authorization tests, real F04 and full reacceptance on an aligned train |
| CVE-2026-41850 | OPEN; no app-owned untrusted SpEL evaluator found; Flowable remains installed and its expression use needs separate threat review. | SpEL trust-boundary and BPMN/variable/transaction tests, full profiles/live reacceptance |
| CVE-2026-41842 | OPEN; no versioned filesystem static-resource configuration found. Avoid standalone Framework overrides. | Conditional static-resource/versioning disclosure regression, full profiles/live reacceptance |
| CVE-2026-41845 | OPEN; no application JavaScriptUtils.javaScriptEscape use found. REST-only observation is not a dependency clearance. | Escaping/XSS consumer review and conditional regression, full profiles/live reacceptance |

Application-source condition searches and existing header/stateless configuration
were checked again after acceptance. No application exploit is claimed solely
from a package match, and absence of an app-source symbol is not exhaustive
transitive-library analysis. Source scan JSON, per-finding triage and primary-page
hashes are retained locally. Trivy severity is kept even where the vendor differs:
Spring rates CVE-2026-22732 and CVE-2026-47884 MEDIUM; the scanner's two CRITICAL
findings still count and fail G9. No local rescoring/suppression was introduced.

### Historical infrastructure/image findings

| Image | Historical HIGH / CRITICAL | Owner / next action |
| --- | --- | --- |
| Java | 14 / 2 | Backend/DevSecOps: unique rebuild with new JAR and actual image scan; old image remains old |
| Python | 61 / 2 | AI/DevSecOps: vendor OS disposition and installed rules/retrieval/dense/cloud inventory; no algorithm change |
| PostgreSQL 16.15 | 95 / 4 | DBA/DevSecOps: exact-image validation, vendor gosu/OS fixes, disposable migration/restore; never old volumes |
| Keycloak 26.0.8 | 89 / 7 | Identity: vendor-supported update with separate realm/schema/backup/rollback acceptance; no JAR surgery |
| APISIX | 110 / 0 | Gateway/DevSecOps: exact RPM backports, OpenResty/Lua and safe UID/startup review; no bulk unpinned yum update |

These are old image identities, not scans of the accepted 16.6/current runtime.
All duplicate package paths remain in the ledger. Individual HIGH vendor fixes
not independently established remain OPEN, not completed exploit verification.
Critical-case primary-source checks additionally establish:

- Keycloak CVE-2026-18963 concerns reset-credentials/account takeover; candidate
  lines 26.4.15/26.6.6/26.7.2 are outside 26.0. Current realm binding is unverified;
  do not assume the flow is disabled or edit a shared realm.
- Netty CVE-2026-75595 concerns fragmented TLS/SNI fallback with per-SNI mTLS;
  official fixes 4.1.137.Final/4.2.17.Final. Lab HTTP is not production TLS proof.
  Bouncy Castle CVE-2025-14813 concerns GOSTCTR beyond 255 blocks;
  CVE-2026-8763 concerns certificate name constraints. Algorithm/certificate use
  remains unverified; preserve both duplicate paths and use vendor updates.
- FreeMarker CVE-2026-84939 locale traversal has candidate 2.3.35. The fetched
  Apache mailing-list shell is JS-rendered, not verified advisory text; candidate
  availability and runtime use remain Identity-owner work.
- Debian lists bookworm SQLite CVE-2025-7458 vulnerable/no-dsa and libxml2
  CVE-2026-6653 vulnerable/postponed. Do not install another distro's package.
  No HTTP-to-native exploit is inferred just from presence.
- Debian CVE-2023-45853 distinguishes src:zlib (bookworm ignored, contrib/minizip
  not built) from separate minizip binaries/fix. Without current binary inventory
  this is not a scanner suppression or VEX clearance.
- Gosu Go-stdlib CVE-2025-68121 / GO-2026-4337 concerns TLS session resumption,
  with fixes including Go 1.24.13/1.25.7. A startup privilege-drop helper is not
  the PostgreSQL TLS listener. Obtain a verified vendor rebuild.

Static HIGH root-user warnings are separate: frontend's final base is
nginx-unprivileged, so the warning does not prove the final inherited user is
root; APISIX UID/filesystem behavior needs actual image inspection and routing
tests. Neither warning was suppressed or blindly patched. Gateway system Python
RPM findings are not AI-service dependency findings; no distro Python replacement.

## 4. Initial no-daemon regression evidence (historical)

The BLOCKED/skip statements in this section describe the first phase only.
Section 9 supersedes these missing runtime gates with fresh, zero-skip Linux DB
and new-JAR live evidence; these Windows runs are not recounted as fresh tests.

Windows Java 17 / Maven 3.9.9; Maven heap 512 MiB; sequential, task-only source
copies initially without target outputs. No existing checkout build/report is
overwritten. No database variables are supplied because no permitted DB is
reachable. Existing conditional tests consequently skip: these are BLOCKERS,
not green full gates. CI's rejection of failed/skipped reports is unchanged.

| Invocation | Passed | Failures/errors | Skipped | Interpretation |
| --- | --- | --- | --- | --- |
| Jackson RED / old BOM | 0 | 1 / 0 | 0 | Expected reproduction |
| Jackson GREEN / new BOM | 1 | 0 / 0 | 0 | Focused PASS |
| Full default clean verify | 174 of 265 | 0 / 0 | 91 | Build/unit subset works; FULL GATE BLOCKED |
| Core-only clean verify | 28 of 91 | 0 / 0 | 63 | Compiles without upper tiers; DB GATE BLOCKED |
| Supported second-domain + new regression | 36 of 40 | 0 / 0 | 4 | ArchUnit/HPDI/policy PASS; Room Request DB GATE BLOCKED |
| Source boundaries / mutation checks | guard + 39 | 0 | 0 | PASS |
| Native Compose merge / inventory exporter self-test | PASS | 0 | 0 | No daemon mutation |

Counts overlap; do not sum them into unique tests. Full includes 7 Core
authorization, 6 contributed-process catalog, 24 validator and 24 Java controlled
HTTP transport/fallback tests, plus header timing/Jackson/compiled ArchUnit,
executed without skips. Controlled Java HTTP-server tests are NOT real Python.

The full 91 skips: Core runtime 8, signed generic-start/selfService 4, Service
Desk 16, Room Request 4, ticket triage DB 4, metadata JDBC 10, metadata Spring 4,
migration 1, local files 23, workflow approval 17. Actual PostgreSQL/Flyway/
Flowable, rollback/concurrency, signed authorization/selfService and Linux POSIX
file tests remain unaccepted on the new candidate. No fresh Keycloak/APISIX/
Java/Python lifecycle, real fallback or restart test was possible.

Evidence: `jackson-red.log`, `jackson-green.log`, `full-windows-no-db.log`,
`core-only-windows-no-db.log`, `second-domain-windows-no-db.log`, independently
computed `regression-summary.json`, and each snapshot's Surefire XML. Guards:
`source-boundaries.log`, `boundary-mutations.log`, `acceptance-config.log`,
`exporter-self-test.log`. Earlier 264/90/39 Linux and live 16.6 results remain
historical, not substituted for fresh missing tests.

New JAR SHA-256:
`d8be398a4d1ffaed296122b1712e43c0b459e5e6f33415f6854403be4432642c`.
Location: `.local/release-readiness-20261026/green/backend/target/`. Its 117 nested
JARs include all eight Jackson modules at 2.18.11. It has NOT replaced the
accepted Docker artifact; populated-cache packaging is not clean-machine or
byte-reproducible-build acceptance.

## 5. Reproduction and initial runtime follow-up

The follow-up requested here has now executed in section 9. Retain these
instructions as context; do not rerun destructive test DB setup or an already
passing live harness against populated data merely to regenerate logs.

Runnable no-daemon checks, from repository root (Maven must be on PATH):

```powershell
python scripts/acceptance/test-integration-config.py
python scripts/check-tier-boundaries.py
python scripts/test-tier-boundaries.py
python scripts/security/export-inventory.py --self-test
mvn -B -f backend/pom.xml -Dtest=JsonSecurityRegressionTest test
```

Compose order remains `compose.yaml`, `infra/compose/compose.triage.yaml`,
`scripts/acceptance/integration-compose.yaml`, then the task-only image/credential/
resource override, with profile intelligence. No real secret is tracked. Existing
task env/realm/ownership files remain protected local state, not release artifacts.

**Owner availability of the existing daemon is needed, not a new Engine or
privileged DinD.** Do not autonomously start shared Docker Desktop. Once available,
match labels/images/mounts to the accepted task's ownership record and verify
resources. Use the known verified 16.6 image ID in a new disposable regression
project or explicitly owned disposable DB, never old/shared databases or volumes.
Keep the 16.15 production pin. Pull, container recreate and volume deletion are
distinct operations; no volume deletion is required here.

Use a fresh source snapshot including dirty hardening and this patch, a builder
cache containing the new dependencies, unique tags and evidence paths. Existing
`scripts/test-file.ps1 -DatabaseImage <verified-16.6-id>` supports default full,
`-MavenProfile core-only`, and the explicit supported second-domain selection plus
`JsonSecurityRegressionTest`; it rejects skipped/failed XML. Do not run mounted
clean builds over another member's target output. The existing old builder's
cache alone previously missed current dependencies; inspect/prepopulate the new
task builder rather than assuming a tag has a complete cache.

Before promoting the new JAR, require full 265/Core-only 91/supported second-domain
40 with zero skips, boundaries/ArchUnit, real PostgreSQL/Flyway/Flowable and signed
authorization/selfService. Repeat unchanged 95-assertion HTTP lifecycle, Room
Request/audit/files/rollback, real Java-Python success/private bearer/unavailable/
timeout/malformed/manual fallback and task-only restart persistence. Reuse local
assertion harnesses with NEW output files, not overwritten baseline evidence.
Scan all actual deployed images and installed optional AI dependencies afterward.
These expected current test counts are not substitutes for scenario/gate checks.

## 6. Proof of FOSS

| Item | Status and remaining action |
| --- | --- |
| Root LICENSE | ABSENT; Apache-2.0 direction in LICENSING.md is not a grant. Rights holders approve exact scope/text; none added. |
| Source/contributor/university rights | Approval pending; technical review cannot grant rights. |
| Data/reference rights | Knowledge manifests PENDING_OWNER_APPROVAL, null usageRights; supplied PDF/XLSX redistribution unverified. Approve or exclude. |
| Public repository | origin is HaUI-DangSuyNghi/Smart-Service-Desk; anonymous GitHub API returns 404 on 2026-10-26. Public access NOT VERIFIED; 404 does not distinguish private/absent. No visibility change. |
| CHANGELOG | Present; new unreleased technical entry, not release approval. |
| Dependency licensing/provenance | New resolved tree tracked; BOM/packaged-library hashes retained. Jackson parent POM declares Apache-2.0. Full transitive/image/model licensing, SBOM and notices NOT CLEARED; older tables are historical. |
| Source build instructions | README/CONTRIBUTING, Dockerfiles/profiles and this report exist. Populated-cache dirty-source build succeeds; clean-checkout/clean-cache build still required. |
| Reproducible demo | Two acceptance corrections now tracked/tested. Historical 16.6 run needs preloaded images and protected generated local state; clean-machine demo NOT PROVEN. |
| Version tag/release | Local tag list empty; remote release state not established. No tag/release created. |
| Issue tracker | PR/workflow references exist; anonymous metadata unavailable, public issue access/enabled state NOT VERIFIED. Owner must verify. |
| Technical report/slides | Engineering/requirements/integration docs exist; no submission-ready report or slide deck identified in tracked/nonignored inventory. Team must assemble/approve. |

Use `license-release-checklist.md` and `LICENSING.md`. This prepares technical
evidence, not legal clearance, source/data publication permission or a license
badge. Open-source dependencies do not themselves grant rights to team assets.

## 7. Owner handoff

**Frontend:** no FE source/dependency change here. Fresh all-dependency scan finds
direct zod 3.25.76 / CVE-2023-54404 (candidate 4.6.6, major API/form/resolver review
required) and development-transitive body-parser 2.3.0 / CVE-2026-111181 (official
2.4.0/1.20.9 fixes; inflate=false workaround only for affected running middleware).
Lock path: shadcn -> MCP SDK -> express -> body-parser; static nginx is not that
development server. Verify candidate availability/ranges and regress FE-owned
forms/tooling/browser tests. Inspect final image user before addressing DS-0002.

The accepted patched candidate uses Gateway 29080 / Keycloak 28081 (section 9).
The stopped historical task endpoints remain Gateway 19080 / Keycloak 18081,
realm dx-lab, dx-web PKCE, audience dx-core. FE defaults differ (9080/8081); owner
must configure the test instance. Accept real login/renewal, permission 403/503,
cross-workspace denial, download policy and requester confirm/rework. Use app
create endpoints, not generic Core starts. Never publish local env/realm passwords
or embed private Intelligence credentials in browser code.

**AI + DevSecOps:** no algorithm/profile/model/source/lock change. Preserve
hdpi.http_service, service-desk@0.3.0 and contract 0.1.0. Source pip scan is not
installed rules/retrieval/dense/cloud inventory: provide actual wheel/package
SBOM, dependency consistency and licensing evidence for enabled profiles. Review
Python-image native findings against distro vendor disposition. Real
Java/Python/fallback now passes with the rebuilt candidate (section 9).
Separate Room Request AI profile
acceptance from its already tested non-AI lifecycle. No paid/cloud/provider calls.

**Identity/Gateway/DB:** review remaining historical findings, current actual image
identities and support/backport availability. Keycloak schema/realm/TLS, Docker
global repair, base refresh or shared deployments need their own approval and
isolated acceptance; no vendor JAR replacement or unverified binary substitution.

## 8. Files and preservation

Only these paths change/add in this task:

- `backend/pom.xml`: one BOM property.
- `backend/src/test/java/vn/haui/dx/core/common/JsonSecurityRegressionTest.java`.
- `scripts/acceptance/integration-compose.yaml` and `scripts/acceptance/apisix-single-worker.yaml`.
- `scripts/acceptance/test-integration-config.py`.
- `.github/workflows/intelligence-triage-e2e.yml`: no-daemon guard in G10.
- `docs/security/release-readiness-20261026.csv` and `docs/security/release-readiness-20261026-dependencies.txt`.
- `docs/BACKEND_RELEASE_READINESS.md`, `docs/dependency-licenses.md`, `CHANGELOG.md`.

Starting worktree: 23 modified and 15 untracked files. Before edits, 988 original
source hashes plus porcelain/HEAD inventories were saved. Original hardening,
controllers/tests/scripts and all four required reports are preserved, not
reformatted/overwritten. No migrations, Frontend or AI source changed. Final
source/evidence hash maps identify intentional changes; no member's work reverted.

## 9. Resumed Jackson candidate acceptance - PostgreSQL 16.6

### Artifact, source and isolation

Project/evidence: `rr-jackson-20261026-a`, `.local/rr-jackson-20261026-a/`.
HEAD remains `f94f0ef102cbc7d5737ca05270b0affab1fde1a6` plus the preserved dirty
hardening/security tree. SHA-256 of the sorted, compact JSON per-file hash map:
`5faeed9776a5b3fd76fa20f0e43191febdd91b5e8fdad5dff7761fe579e9a922`.
`source-before.json`, `worktree-before.txt`, `resume-source-check.json` retain
the exact inputs; the resumed source check found no changed original files.
The report itself is updated after acceptance, not part of a rebuilt artifact.

Accepted Linux full-test JAR SHA-256:
`544fcad7afc972dba31fe289964d919ca406ee2415c30d8e752beb09cdcd6f62`.
Image tag: `rr-jackson-20261026-a-platform:local`; actual image ID:
`sha256:d5189394231a4ac705b22a1fbb2b4b3455a41ae0dde42cf396b28632402bfcdf`.
All eight Jackson modules in its 117 nested libraries are 2.18.11. The deployed
`/app/app.jar` SHA-256 matches before AND after restart. This is neither the old
accepted `6228e0...` JAR nor the earlier no-DB Windows `d8be398...` artifact.
Evidence: `candidate-build.json`, `deployed-artifact.json`, `runtime-image-build.log`.

Daemon ID: `762b9349-4c08-4af1-8f5b-834bad840de1`, context desktop-linux,
root `/var/lib/docker`, overlayfs. Existing image identities were reused unchanged;
a unique new Backend image was added without retagging old images. PostgreSQL
16.6 uses existing image ID
`sha256:557fea37a744d5f4c8faab304b0a90858b53ab119735a88c131fd19dab802f36`.
Production Compose still pins 16.15 with its original digest; it was not pulled.

Only `rr-jackson-20261026-a_default`, `rr-jackson-20261026-a_postgres-data`,
`rr-jackson-20261026-a_files` and task-prefixed containers are operated. Services
retain the demonstrated limits: PG 256 MiB, Java/Keycloak 640 MiB each, APISIX
128 MiB/one worker, Python 256 MiB/hdpi.http_service. Java/PG/Python have no host
ports. Gateway 29080 and Keycloak 28081 bind loopback only. Old project 19080/18081
and its data remain untouched. No root/shared .env credentials were used.

### Executed gates

| New-candidate gate | Result | Evidence |
| --- | --- | --- |
| Full Backend, Linux + real PostgreSQL | PASS 265/265; 0 failures/errors/skips | `full-retry1/summary.json`, Surefire XML and Maven log |
| Core-only, fresh task test DBs | PASS 91/91; 0 failures/errors/skips | `core-only/summary.json` and XML |
| Supported second-domain selection | PASS 40/40; 0 failures/errors/skips | `second-domain/summary.json` and XML |
| Source boundary / compiled ArchUnit / mutations | PASS; 39 mutation checks | boundary logs; TierBoundaryTest in full and second-domain |
| PostgreSQL/Flyway/Flowable | PASS; 16.6, V1-V7 successful, schema 7.1.0.2, four definitions | `database-initialization.txt` |
| Authorization/selfService/Jackson/header timing | PASS; includes CoreStart 4, CoreAuthorization 7, Jackson 1, header 1 | full fresh XML; live checks below |
| Flowable approval / Service Desk / Room DB tests | PASS 17 / 16 / 4 | full fresh XML; included in 265, not extra unique tests |
| Fresh Service Desk HTTP lifecycle | PASS 95 HTTP status assertions | `servicedesk-live.log`, `servicedesk-summary.json` |
| Genuine JWT/F04/selfService/Room/data/audit | PASS 30 HTTP checks plus SQL/bytes/rollback checks | `security-room-result.json` |
| Real Java-Python in two workspaces | PASS 21 HTTP checks; six schema/correlation/evidence validations | `triage-live-result.json`, `triage-live.log` |
| Private Python bearer | PASS 401 missing / 401 invalid / 200 valid | `private-python.json`; no public Python port |
| Real outage / manual workflow | PASS 504 INTELLIGENCE_TIMEOUT; no fabricated result or DB mutation; manual resolve/confirm succeeds while Python stopped | `triage-live-result.json` |
| Five-service restart / fresh identity login | PASS; 17 Service Desk HTTP checks plus Room/file/triage/counts | `restart-live.log`, `restart-result.json` |
| Installed Python dependency consistency | PASS pip check; actual package inventory captured | `installed-python-consistency.txt`, `installed-python-packages.json` |
| Browser E2E / Room Request AI / exact PG16.15 / clean-machine build / load / backup-restore | NOT RUN / NOT ACCEPTED | separate owner/release gates; no inferred PASS |

Profile counts overlap and must not be summed as unique tests. No required final
DB/HTTP gate is SKIPPED. The retained OOM attempt is FAIL; the two incomplete
Java image scans are BLOCKED, not PASS or zero findings.

Service Desk covers creation, agent claim/resolve, requester rework/confirmation,
concurrent duplicate 201/409, workspace visibility, statistics and exactly-once
creation audit. The unchanged harness ran once against the NEW artifact. Its
JSON retains 73 non-Core nonempty replay records; the fresh log records all 95
HTTP status assertions. Two outer counting checks were corrected without rerunning
the passing harness or altering its assertions; this was not an API failure.

The 30-check suite uses real Keycloak JWTs, tampered signatures, wrong audience
and wrong issuer, actual 403 permissions/workspace separation, forbidden generic
business starts with unchanged DB counts, allowed document-review selfService,
file bytes/SHA-256 provenance, Room completion and audit. A temporary trigger
rejects Room INSERT after workflow creation to verify transaction rollback; it
is removed in finally. No shared database or original volume is mounted.

Restart stops/starts ONLY the five candidate services with the SAME new volumes.
Counts before/after, in order ticket/room/scope/history/task/audit/file/metadata:
`5|1|7|7|4|27|1|1` = `5|1|7|7|4|27|1|1`. Three seed tickets retain exact content,
ownership and process references; completed Room progress and uploaded bytes are
unchanged, fresh Keycloak login works, manual-fallback ticket remains ended, and
real triage succeeds after restart. All five services are healthy and not OOMKilled.
Live 503/malformed-response injection was NOT replayed: controlled Java transport
tests cover those branches (including 24 HttpIntelligenceTransportTest cases).

### Commands and retained failed attempts

Actual commands, executed sequentially with new evidence paths; not instructions
to repeat setup over an already populated project:

```powershell
$run = '.local/rr-jackson-20261026-a'
python $run/setup.py
python $run/compose.py up -d --no-build --pull never --wait --wait-timeout 60 postgres
python $run/regress.py full
python $run/regress.py full-retry1
python $run/regress.py core-only
python $run/regress.py second-domain
python scripts/check-tier-boundaries.py
python scripts/test-tier-boundaries.py
python scripts/acceptance/test-integration-config.py
docker build --pull=false --network=none --label org.opencontainers.image.revision=f94f0ef102cbc7d5737ca05270b0affab1fde1a6 -t rr-jackson-20261026-a-platform:local $run/image
python $run/compose.py up -d --no-build --pull never --wait --wait-timeout 180 platform
python $run/deploy.py
docker exec rr-jackson-20261026-a-platform-1 sha256sum /app/app.jar
python -u $run/servicedesk-live.py $run/servicedesk-http.json
python -u $run/security-room.py
python -u $run/triage-live.py
# private-check.py was streamed to docker exec -i ...-intelligence-1 python -
python -u $run/restart.py
```

Maven executes clean verify offline using the read-only host cache and container-
local source/target, never another member's target directory. First full attempt:
264 reports passed, but the fork was killed with exit 137 during ArchUnit under
768 MiB; this attempt is FAIL, not a full PASS. It remains in `full/` with
OOMKilled=true. A single bounded retry used a 1536 MiB builder, Maven heap 192 MiB
and test fork heap 512 MiB/metaspace 256 MiB, then all 265 passed. The later two
profiles used the same cap, sequentially. Runtime service limits were not raised.
Only disposable file_storage/workflow_approval test DBs were reset between profiles.

An initial Windows PowerShell native-stderr redirection error stopped orchestration
after the platform became healthy. deploy.py resumed only the missing services;
it did not rebuild/restart the healthy platform. On user resume there were no
active host task build/startup/scan jobs and no processes were killed. The unrelated
VS Code Java language server was left alone. Four stopped task builders remain
(one retained OOM attempt, three successful); five service containers remain running.
Scanner download/gate results and final resource inventories are recorded below.

### Security scans after runtime acceptance

Verified Trivy 0.76.0 is reused; a 0.76.1 availability notice is retained in its
logs, not misrepresented as the version used. Vulnerability DB UpdatedAt is
2026-10-25 19:10:35Z, NextUpdate 2026-10-26 19:10:35Z; the update check retained
this same still-current DB for before/after comparability. Source POM scan is
7 HIGH / 2 CRITICAL, eight fewer Jackson findings than the same-DB prepatch
15 HIGH / 2 CRITICAL. This is not a five-image improvement claim.

| Actual deployed image | Scanner status | HIGH / CRITICAL | Raw evidence |
| --- | --- | --- | --- |
| PostgreSQL16.6 / 557fea37a744 | COMPLETE, security FAIL | 212 / 24 | `images/postgres.json`: Debian12.9 + gosu Go binary |
| APISIX / b833c7eda0d8 | COMPLETE, security FAIL | 108 / 1 | `images/gateway.json`: Red Hat8.10 RPMs |
| Python / 42ad6a4ba7ee | COMPLETE, security FAIL | 61 / 2 | `images/intelligence.json`: Debian12.15 + installed Python packages |
| New Backend / d5189394231a | BLOCKED, NOT VERIFIED | unknown, NOT zero | `images-java/platform.log`; Java index unavailable |
| Keycloak / 09a381c715ab | BLOCKED, NOT VERIFIED | unknown, NOT zero | `images-java/keycloak.log`; Java index unavailable |

The three completed image scans contain **408 HIGH/CRITICAL occurrences**:
381 HIGH + 27 CRITICAL, including repeated package paths. They are not 408 unique
CVEs and do not include the unscanned Java/Keycloak images. The historical
SEC-01 PostgreSQL16.15 inventory is different; no counts were subtracted from it.
Raw rows retain package/version/advisory/fix/status/path/URL in
`image-scan-final-high-critical.csv`. Final summary and gate:
`image-scan-final-summary.json`, `image-security-gate-final.json` (**FAIL**).
The earlier `images/summary.json` is a retained intermediate, not the final gate.

All actual image IDs were inspected from this project's running containers and
exported read-only with docker image save. No Docker socket was mounted and no
scanner cache belonging to another project was used. Scans run sequentially,
parallelism1, task-only cache, GOMEMLIMIT384MiB/GOMAXPROCS2, with a 1 GiB host-free
memory expansion guard for the initial batch. No severe finding was suppressed;
an empty task ignore file is explicit. Both scan runners exit nonzero at the
security gate; successful analysis is not a production PASS.

Java-index acquisition failed at official GCR (network timeout), GHCR (peer
HTTP/2 PROTOCOL_ERROR) and the official public ECR mirror. The last attempt's exact child
PID30600 was created/recorded by this task and stopped after its 300-second bound;
no unrelated process was stopped. Its command, ownership and exit are retained in
`trivy-java-db-ecr-process.json`. No repeated job remains running. Afterward the
two Java image archives were actually attempted once with automatic Java-index
updates disabled, not Java analysis disabled: both fail initialization because
the index is absent on first run. This is explicitly BLOCKED, not zero findings
or image acceptance. No scanner/gate in CI was changed. Further download retries
are deferred until a verified index/network path is available.

Representative commands (full arguments/limits are in the local runners/logs):

```powershell
$scanner = '.local/release-readiness-20261026/tools/trivy.exe'
& $scanner --cache-dir "$run/trivy-cache" image --download-db-only --no-progress
& $scanner --cache-dir "$run/trivy-cache" fs --scanners vuln --include-dev-deps --list-all-pkgs --no-progress --format json --output "$run/backend-source-scan.json" backend
# Index download attempts used official GCR, GHCR, then bounded public ECR.
python $run/scan.py
python $run/scan-java.py
```

The nine Backend source findings are individually reviewed in section 3 and
`source-security-triage.json`: versions/advisories, direct/transitive relation,
official OSS versus enterprise fix, exposure uncertainty, compatibility and
required reacceptance. No additional safe drop-in dependency remediation was
established; the existing eager-header vendor mitigation is retained and tested.
No unvalidated Spring/Flowable/Keycloak upgrade is made merely to change counts.

DBA/DevSecOps receives the actual 16.6 OS/gosu inventory; Gateway Owner receives
108 HIGH/1 CRITICAL with vendor RPM/OpenResty/Lua provenance/backport review still
needed. AI/DevSecOps receives the installed Python inventory and 61 HIGH/2 CRITICAL
image findings; no source, algorithm or dependency of theirs is changed. These
image rows remain OPEN pending vendor-impact/fix validation. Native scan success
does not establish optional model/cloud/dense licensing or Lua dependency coverage.
Identity/Backend image SCA stays BLOCKED on the index, not waived by source SCA.

### Final handoff state

Five healthy task services, four stopped task builders, two task volumes and one
task network are retained; the new image tag and protected generated credentials
remain local. No build, Compose startup, acceptance or scanner background job is
intentionally left running. `final-inventory.json`, `final-host.json` and
`final-processes.json` record the final sample; `old-project-preserved.json`
confirms original IDs/images/mounts/full container states unchanged. Source hash
comparison permits only this report to differ from the 994-file starting map;
Frontend/AI/production Compose and all previous member changes are preserved.
At 10:03 +07, host free RAM is 2.09 GiB, CPU 11%, C: 69.0 GiB and D: 61.1 GiB
free; no owned host background jobs are active. `final-docker-stats.json` retains
per-service memory/CPU/PID usage. These are point samples, not load acceptance.
In this resumed phase the only repository-file change is this report; generated
runner/evidence/cache/image archives stay in the protected ignored task folder.
Evidence hashes are in `evidence-sha256.json`; credential/realm files are excluded
from that publishable index and must never be published with the raw local folder.

## 10. GO / NO-GO before submission

- [x] Preserve historical 16.6 full-stack PASS and its artifact/evidence/resources.
- [x] Track the demonstrated worker/entrypoint corrections with native merge guard.
- [x] Patch Jackson with red/green regression and eight fewer HIGH source findings.
- [x] Retain all other findings, uncertainty and unchanged security policy.
- [x] Owner makes existing daemon available; verify current task isolation/resources.
- [x] Updated candidate passes Linux full/profile DB regressions with zero skips.
- [x] Fresh full-stack HTTP/Java-Python/504/manual fallback/restart accepts new artifact.
- [ ] FE/QA accepts actual browser E2E and owner-reviewed FE dependency fixes.
- [ ] Exact pinned PostgreSQL 16.15/clean install accepted, or explicit reviewed release-target decision; never relabel 16.6 results.
- [ ] G9 closes on actual deployed images/OS/installed dependencies; two Backend CRITICAL findings still scanner-visible.
- [ ] Rights holders approve license/contributor/data/reference scope; complete notices/SBOM/public repo/issue verification.
- [ ] Team approves business profile, submission report/slides and clean-source reproducible demo.
- [ ] Final SHA reviewed; publication/merge/tag/release separately authorized with immutable evidence.

Recommendation: use the newly accepted Jackson-patched 16.6 artifact for the
controlled synthetic API demo; preserve the old artifact/evidence separately.
No new Engine is needed. This is not production/competition release approval:
security, browser, exact 16.15, clean-install and Proof-of-FOSS gates remain open.
Release/Proof of FOSS: **NO-GO**.
