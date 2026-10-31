# Core acceptance and operating checks

Date: 2026-10-15 (Asia/Saigon). Branch: `test/core-acceptance`.
Overall: **PARTIAL**, not a completed Core sign-off or production approval.

## Baseline and authority

GitHub REST confirmed PR #11 merged at 2026-10-15T13:33:10Z (20:33:10 Asia/Saigon).
Merge `78e64de8e1808d22ca815f175865c4a0b59bc021` is an ancestor of fetched
`origin/develop`. The clean local `develop` was fast-forwarded before creating this
branch. No direct feature commits to `develop`/`main`, automatic merge or release.

The official brief is `docs/references/De_thi_PMNM_HaUI_2026_BAN_CHINH_THUC.pdf`,
SHA-256 `ebb7578a2b0cd3dc4a1dab0e146bd04cf1c0a461074b0e2b9f668af10928f016`.
Sections 3/4.1 define Core; sections 4.2/4.3 define upper tiers; sections 5/7/8/10
also apply to the final submission. Team acceptance gates are not invented contest
requirements. Source license approval cannot be supplied by an automated test.

## Recorded execution

Two fresh source/restore pairs were exercised: `acceptance-20261015-a` and
`acceptance-20261015-b`. The final functional summary was recorded at
2026-10-15T14:06:55Z (21:06:55 Asia/Saigon). Tests ran on the working-tree changes
based on `78e64de`; `workingTreeDirty=true` is explicit, not a claim of a clean
already-committed release. PR-head CI is a separate reproduction gate.

| Executed check | Result |
| --- | --- |
| PostgreSQL 16.6 / Maven clean verify | PASS: 66 tests, zero failures/errors/skipped in each run |
| Both OpenAPI specifications | PASS: validator 0.9.0 |
| Real Keycloak -> APISIX -> Core smoke | PASS: 80 JSON responses, both BPMNs, three completion races with one 204/one 409 |
| Independent room-loan client / negative authorization | PASS: 41 JSON responses; separate OIDC client; self-approval, wrong candidate, unclaimed completion, reserved variables, owner spoofing and cross-workspace/parent binding denied |
| Upload explicitly disabled | PASS: HTTP 403, unchanged file row count; 8 JSON responses |
| Graceful restart without pull/build | PASS: pending task, metadata and file persist; 11 JSON responses plus checked binary download |
| Quiesced DB/blob inventory | PASS: no missing/corrupt/orphan/invalid entries; seven detector self-check scenarios |
| Restore into separate new volumes | PASS: both databases and file archive restored, pending handover resumes to completion; 11 + 13 JSON responses |
| Restored audit/engine/scope consistency | PASS: scripts/check-db.sql |
| All captured JSON responses | PASS: 164 contract-validated responses per final run; binary SHA-256/attachment/nosniff checked separately |
| All seven identities after restart/restore | PASS: captured /me subjects, memberships and admin flags equal seed responses |
| Scan/runtime artifact match | PASS: all four runtime image IDs equal the final scan inventory |
| CI on the actual PR head | NOT VERIFIED at local handoff; read the live PR checks, do not substitute local results |
| Full Core approval / production approval | PARTIAL / FAIL, respectively; remaining gates are below |

Ignored evidence: `.local/acceptance-20261015-b/summary.json`, `images.json`,
`*-http.json`, `storage-inventory.json`, `backup-sha256.json`, container logs and
`backend/target/surefire-reports/TEST-*.xml`. Backups are confidential and not committed.
The first run exposed a PowerShell 5 JSON-array image-inventory serialization issue;
it was corrected, and the second run asserts nonempty image identities. Existing
projects are explicitly refused before mutation. Initial scanning refused an absent
base-image tag instead of declaring it clean; the final scan covers the actual
`dx-core-build` image, including its resolved build/test dependencies.

### Actual vulnerability findings

Final scan: `.local/scan-20261015-b/`. All five scans completed, including OS and
packaged Java detection for Core, Keycloak and the build image. The strict command
returned a nonzero exit code because the security policy failed, not because the
scanner or vulnerability DB was unavailable.

| Image | HIGH | CRITICAL | HIGH/CRITICAL with a reported fix | Gate |
| --- | ---: | ---: | ---: | --- |
| dx-core:0.1.0 | 39 | 10 | 49 | FAIL |
| postgres:16.6-bookworm | 203 | 24 | 161 | FAIL |
| quay.io/keycloak/keycloak:26.0.7 | 89 | 7 | 96 | FAIL |
| apache/apisix:3.11.0-debian | 90 | 7 | 34 | FAIL |
| dx-core-build:0.1.0 (build/test tooling, not deployed backend) | 146 | 24 | 167 | FAIL |

These are package findings, not unique exploitable attack paths. No exploitability
waiver or reachability triage has been performed. APISIX's Debian 11.11 base also
has Trivy's EOSL flag; OS detection can be incomplete after vendor support ends.
Trivy OS/Java scanning does not establish coverage of every vendored Lua/OpenResty
component or replace penetration testing, notices review or application authorization tests.

Trivy: 0.76.0, image `sha256:2416d1f74045b57274df1a340eee6b510cacd73c66a6ccb8da092b117eb7d222`.
Vulnerability DB v2 updated 2026-10-15T07:10:08Z; Java DB v1 updated
2026-10-15T00:58:48Z. Final backend image:
`sha256:e43e53ab4939bd744908bf42e630a004cbd78b34acac38250c93366bc93f1ce4`.
The raw JSON inventories retain installed/fixed versions and advisory references.
Examples include packaged Tomcat 10.1.30 and Spring Security 6.3.3 advisories;
package presence alone does not prove their exploit conditions are met in this runtime.
Broad version upgrades were not disguised as acceptance tooling: remediation needs
a supported Boot/Flowable combination and the full transaction/security/recovery retest.

## Acceptance matrix

| Gate | Classification | Evidence / boundary |
| --- | --- | --- |
| Identity, authentication and RBAC | Official Core, 4.1/7 | Real Keycloak JWTs; issuer/audience/signature/expiry tests; workspace and role negatives |
| Controlled API entry | Official Core, 4.1 | APISIX routing/rate limit; backend independently rejects unauthenticated/foreign access |
| Structured and/or unstructured data | Official Core, 4.1/7/8 | PostgreSQL metadata/provenance, private blob storage, checksum verified downloads |
| Workflow state and real history | Official Core, 4.1 | Two BPMNs; independent approver, claim/complete, concurrency and rollback regressions |
| Headless separation and reusable services | Official Core, 3/4.1 | Architecture diagram; separate Python room-loan client and OIDC client, no business DTO/Core changes |
| Source deployment and Core tests | Official technical, 7 | Docker build; real PostgreSQL tests with zero skipped; clean named Compose deployment |
| Provenance and synthetic data rights | Official data, 8 | API metadata stores source/version/usage rights/checksum; fixtures here are synthetic, not pending-rights reference material |
| Restart, consistent backup/restore and blob reconciliation | Team Core acceptance | Scripted restart; quiesced two-DB + blob snapshots; restore to different fresh volumes and resume a pending task |
| Independent team approval | Team plan, N4 | NOT VERIFIED: three members must review evidence and sign off; this agent cannot sign for them |
| Project LICENSE and redistribution clearance | Official final FOSS submission, 5/10 | PARTIAL: intended Apache-2.0 direction is not a current grant; see LICENSING.md |
| Full H-P-D-I and useful business application | Official final product, 4.2/4.3 | NOT VERIFIED here; the reuse probe is not a delivered upper-tier application |

The security scan being executed is a team safety gate. The brief does not mandate
a particular scanner, zero CVEs, Kubernetes, a scanner daemon, two public datasets,
task delegation, a dashboard inside headless Core, or an eight-page minimum report.
Two public data sources, Docker/Compose, OpenAPI and the report/video lengths are
recommendations where stated by the brief, not new Core blockers.

## Reproduce functional verification

Prerequisites: Docker Linux engine/Compose v2, PowerShell 5.1+ or pwsh, Python 3.11+
with the pinned contract validators, free loopback ports 8081/9080. Internet is
needed for initial dependency/image/database downloads. Do not run against a shared
realm. On Linux use a virtual environment; on Windows `py -3.11` can install:

```powershell
py -3.11 -m pip install -r scripts/acceptance/requirements.txt
./scripts/test-core-acceptance.ps1 -Python py
./scripts/scan-core.ps1
```

Use `-Python /absolute/path/to/venv/bin/python` with pwsh on Linux. Each acceptance
run requires unused `acceptance-*` and `acceptance-*-restore` project names and new
volumes; existing resources are refused rather than erased. The source tree is not
rewritten. Docker/Maven caches may be reused: this is clean runtime/data reproduction,
not a claim of an air-gapped or bit-for-bit identical source build.

`test-core-acceptance.ps1` builds the Maven test image, runs the PostgreSQL suite,
validates both OpenAPI documents, runs the existing smoke and the independent client,
checks disabled uploads persist nothing, restarts services without pulls/builds,
backs up both databases and the file volume, restores to another project, verifies
IDs/provenance/checksums/history and resumes a pending handover. Native command
failures and skipped tests fail the run. The script stops its projects but never
removes volumes. Its summary deliberately keeps overall Core sign-off PARTIAL.

The second application is a synthetic classroom/projector loan: the test provisions
an acceptance-only `room-loan-probe` client through the Keycloak Admin API and uses
that client's real tokens against the same generic Core APIs. Credentials, candidate
roles and workspace checks are unchanged. A restored Keycloak DB must preserve the
new client and all user subjects. This proves headless API reuse, not browser SSO,
Authorization Code + PKCE, a new UI or a complete production business application.

CI runs the functional script on PRs to `develop`; a manual workflow run also runs
the strict security scan. Actions are pinned to commit SHAs. Only test XML and
non-secret status/version summaries are uploaded, never backups, `.env`, realm imports,
tokens, image archives or raw DB/blob contents. CI execution status is separate from
local evidence and must be read on the actual PR.

The repository's current default branch is `main`. Manual `workflow_dispatch`
availability is NOT VERIFIED while the workflow exists only on the acceptance PR /
`develop`; GitHub may require it on the default branch first. Run the scanner locally
now. Do not change repository settings or put an unapproved release on `main` merely
to enable a manual trigger. The PR functional check is verified separately on GitHub.

## Backup/restore and storage safety

The rehearsal is a maintenance-window backup, not live PITR: stop Gateway/Core and
Keycloak first, keep PostgreSQL running, dump `core` and `keycloak` with `pg_dump -Fc`,
archive the private files volume, record SHA-256, then stop the source DB. This avoids
inconsistent DB/blob or identity snapshots while writers are active. `pg_restore`
runs with `--exit-on-error` only in the newly created restore project. Shared DBs and
existing volumes are never restore targets. Matching image versions and the same
protected configuration/secrets are required; user IDs and client/password login
are verified after restore. File UID/GID and mode are preserved by tar; the backend
must still start with its strict owner-only storage check.

Local evidence/backups are under ignored `.local/acceptance-*`. DB dumps include
credential hashes, signing keys and identity information: **confidential backup material**.
The helper uses root only in a network-disabled one-shot archive container, not in
the backend. Linux evidence directories are mode 0700. Windows evidence directories
grant access only to the current operator, SYSTEM and Administrators. `.gitignore` is
not encryption or access control. Production additionally requires encrypted off-host
backups, access/retention policy, secret recovery and measured RPO/RTO. These are not
verified by a same-host synthetic rehearsal.

Read-only reconciliation uses a quiesced DB inventory plus tar without extracting it:

```powershell
py -3.11 scripts/acceptance/core/check-storage.py --self-test
py -3.11 scripts/acceptance/core/check-storage.py .local/<run>/storage-inventory.json .local/<run>/files.tar
```

Missing, corrupt, unreferenced, duplicate and unsafe-path entries fail closed. No
deletion or automated GC is implemented. On a finding: pause writers, preserve a
matching DB/blob snapshot, investigate unknown commit outcomes, quarantine only
confirmed unreferenced UUID blobs in a private durable location with an operator
manifest, then re-inventory. Retention and human review precede deletion. Missing
referenced bytes require recovery from a known-good backup, never deleting provenance.
Do not delete by age or reconcile live uploads. Automated GC is optional backlog,
not needed for the brief; crash recovery still needs a separate failure-injection drill.

## Production gates and remaining work

Unscanned upload remains disabled by default in application configuration. Demo
Compose opts in only for local synthetic data; the acceptance run verifies explicit
disablement through HTTP and checks no new `file_object` row appears. Extension,
signature, UTF-8 and checksum checks are not malware scanning. No scanner service
has been added. Before non-demo upload, integrate an approved fail-closed scanner
and test malicious samples, scanner timeouts/unavailability and quarantine policy.

`scan-core.ps1` uses Trivy 0.76.0 pinned by image digest, exports images without
mounting the Docker socket into the scanner, scans OS packages and packaged Java
dependencies, and retains full JSON/package inventories and DB/version metadata.
Scan execution failure is NOT VERIFIED, not zero vulnerabilities. HIGH/CRITICAL
findings or an end-of-support OS fail the default production gate; `-ReportOnly` reports findings without
claiming approval. No CVEs, unfixed findings or packages are silently ignored.

Before external exposure: remediate/review vulnerability findings and retest supported
versions; replace Keycloak `start-dev`/HTTP/password-grant probe usage; enforce TLS,
appropriate DB roles, secret management, controlled Flowable schema upgrades and
digest-locked deployment. Browser SSO/PKCE and immediate token revocation are not
demonstrated by password-grant probes (existing tokens retain claims until expiry).

Before full Core sign-off: independent human evidence review, an approved treatment
of existing workflow versions (old instances are readable but mutation returns 409),
and explicit agreement on workspace-shared metadata/unlinked file semantics. Do not
use unlinked uploads for confidential owner-private drafts. Broad dependency upgrades,
new ACL models and new infrastructure must be separate reviewed changes, not hidden
inside acceptance tooling.

## Remaining master-plan gates

| Gate | Owner role | Current state / next evidence |
| --- | --- | --- |
| Formal Core sign-off and workspace/content-policy agreement | All three members | NOT VERIFIED: independently review this PR and record approval; no proxy signatures |
| Applied workflow-version treatment | BE + reviewer | PARTIAL: fail-closed regression passes; inventory real pending instances and approve migration/cancellation policy before use |
| Vulnerability remediation and supported-version retest | BE (Java/Flowable), FE (IdP/Gateway/images) | FAIL: review fixed versions, upgrade in a scoped PR, rerun PostgreSQL/E2E/restore and scan the exact resulting images |
| Non-demo file scanning and orphan operating drill | AI/Data + FE/Ops | PARTIAL: disabled gate/detection pass; scanner integration and operator quarantine/retention rehearsal NOT VERIFIED |
| Off-host recovery, hard crash, secrets/TLS/DB-role hardening | FE/Ops + AI/Data | NOT VERIFIED: local graceful restart/logical restore is not disaster recovery or production hardening |
| Source license and third-party redistribution clearance | Rights holders/team | PARTIAL: finalize the approved scope and LICENSE/notices; this PR does not grant rights |
| Actual PR CI and branch protection | Reviewer/repository admin | NOT VERIFIED locally: inspect actual workflow result and configure required checks if approved; no settings changed automatically |

Optional backlog: automated blob GC, task delegation UI, extra workflow automation,
new storage/distributed infrastructure and expanded telemetry. None is introduced
as an invented requirement of the official Core brief. H-P-D-I, Intelligence, a real
upper-tier application and submission/release artifacts remain mandatory later product
work, not optional backlog and not delivered by this acceptance probe.
