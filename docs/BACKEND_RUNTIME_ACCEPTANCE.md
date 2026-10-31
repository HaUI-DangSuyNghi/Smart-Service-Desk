# Backend Runtime Acceptance

Latest follow-up: isolated recovery diagnosis on 2026-10-25; see section 12.
Official pinned registry manifest is now VERIFIED, but no running independent
daemon is available. Recovery/full-stack acceptance remains BLOCKED. Sections
1-11 retain the earlier run and its evidence; they are not overwritten.

Date: 2026-10-25, Asia/Saigon. Source HEAD:
`f94f0ef102cbc7d5737ca05270b0affab1fde1a6` plus the existing uncommitted
hardening changes. This is NOT clean-commit or clean-install acceptance.

## 1. Executive summary

**Full-stack acceptance: BLOCKED.** An actual disposable Compose PostgreSQL
startup was attempted and failed before database readiness. The pinned local
image contains an empty `/etc/passwd` and a zero-byte LDAP shared library.
Neither application code nor migrations execute in this failure.

Real private Python HTTP runs successfully against the current source/profile,
without published ports or network egress. This is deliberately NOT described
as Keycloak/APISIX/Java/Python integration. No production source was changed.
No shared container, image, volume, cache or database was removed or repaired.

## 2. Runtime inventory

| Component | Reference / version | Observed result |
| --- | --- | --- |
| Docker Desktop | 4.75.0 (227598) | Engine reachable |
| Docker Engine | 29.5.2, linux/amd64, API 1.54 | PASS |
| Compose | v5.1.3 | Config validation PASS with temporary required variables |
| PostgreSQL | `postgres:16.15-bookworm@sha256:0ea6700a3b4f0ae6ce746519073558aed4d88a79d8d07622a9a644946c7319c4` | Startup FAIL, exit 1; runtime version NOT VERIFIED |
| Keycloak | Compose pin `26.0.8`, digest `09a381c715ab0b111835b70f2905955274843a219c6f27efb348e4d9f4086858` | NOT STARTED: DB prerequisite failed |
| Java / embedded Flowable | Targeted test JVM 17.0.15; Dockerfile runtime pin 17.0.20.1_1; POM Flowable 7.1.0 | No live application boot |
| APISIX | Compose build tag `dx-apisix:3.11.0-sec01` | NOT STARTED: upstream unavailable |
| Python HTTP | Existing dependency/runtime image `int01-sec01-final-20261018-intelligence:ci`; current source mounted read-only | Started; three real HTTP probes PASS |
| Python image identity | `sha256:42ad6a4ba7eee414a9f1b719fbd43d6e3450bbdd897b4986f7c6eb016629d3f3` | Local image ID, NOT registry manifest digest |
| Python profile / contract | `service-desk@0.3.0` / `0.1.0`, rules engine | Validated actual SUCCESS response |
| Frontend | Compose build tag `dx-frontend:0.1.0` | NOT STARTED; browser NOT RUN |

Configured versions are not claims of executed versions. Existing October 18
runtime images are not rebuilt-current-source artifacts: Python uses explicit
`PYTHONPATH=/workspace/intelligence/src` and current contract/profile resources.

## 3. Commands and evidence

Local evidence: `.local/runtime-acceptance-20261025/` (ignored by Git).
These files contain diagnostics/results, not credential values. Docker inspect
was restricted to nonsecret fields; no full Compose environment was exported.

Executed:

```powershell
git status --short
git rev-parse HEAD
docker version
docker compose version
docker ps -a --format '{{.Names}} {{.Image}} {{.Status}}'
docker image inspect 'postgres:16.15-bookworm@sha256:0ea6700a3b4f0ae6ce746519073558aed4d88a79d8d07622a9a644946c7319c4' --format '{{.Id}} {{json .RepoDigests}}'
docker manifest inspect 'postgres:16.15-bookworm@sha256:0ea6700a3b4f0ae6ce746519073558aed4d88a79d8d07622a9a644946c7319c4'
docker buildx imagetools inspect docker.io/library/postgres:16.15-bookworm
```

The manifest request failed verification; the independent tag lookup failed DNS
resolution for `registry-1.docker.io`. Registry authenticity/current availability
therefore remains unverified. Neither result establishes a bad upstream tag.
No pull/load/tag/remove was performed to repair the global image store.

With temporary process-only random values for POSTGRES_PASSWORD,
KC_ADMIN_PASSWORD and DIRECTORY_CLIENT_SECRET:

```powershell
docker compose -p int01-runtime-20261025 -f compose.yaml config --quiet
docker compose -p int01-runtime-20261025 -f compose.yaml up -d --pull never --no-build --wait --wait-timeout 15 postgres
docker compose -p int01-runtime-20261025 -f compose.yaml logs --no-color postgres
docker compose -p int01-runtime-20261025 -f compose.yaml ps -a
```

An initial config attempt failed because the existing `.env` lacks
DIRECTORY_CLIENT_SECRET. `scripts/setup.ps1` normally fills that value; it was
not run against the shared working directory. The corrected disposable attempt
passed config validation and reached actual PostgreSQL container startup.

Read-only, network-disabled image probes were also executed:

```powershell
docker run --rm --name dx-runtime-20261025-image-probe --network none --read-only --entrypoint postgres 'postgres:16.15-bookworm@sha256:0ea6700a3b4f0ae6ce746519073558aed4d88a79d8d07622a9a644946c7319c4' --version
docker run --rm --pull never --network none --read-only --entrypoint /bin/sh 'postgres:16.15-bookworm@sha256:0ea6700a3b4f0ae6ce746519073558aed4d88a79d8d07622a9a644946c7319c4' -c 'wc -c /etc/passwd /etc/group; ls -l /lib/x86_64-linux-gnu/libldap-2.5.so.0*; postgres --version'
python scripts/check-tier-boundaries.py
python scripts/test-tier-boundaries.py
```

See `postgres-start.txt`, `postgres-log.txt`, `postgres-filesystem.txt`,
`registry-manifest.txt`, `registry-tag.txt`, `boundaries.txt`, `boundary-tests.txt`.

Private Python was started as `int01-runtime-20261025-intelligence` with
`--network none --read-only --tmpfs /tmp --cap-drop ALL`, no-new-privileges,
UID 10001, 256 MiB, 1 CPU and 64 PIDs. A random process-only bearer credential
was passed by environment-variable name, never printed. Current checkout was
mounted read-only. The process listens on port 8000 only inside its own isolated
network namespace. A separate `docker exec` client exercised its real socket:

```powershell
docker exec int01-runtime-20261025-intelligence python /workspace/.local/runtime-acceptance-20261025/private-probe.py
```

The saved assert-based probe is runnable against a newly provisioned equivalent container.
`private-python-result.json` records response validation and HTTP status cases.

## 4. Integration evidence

| Required path | Status and limit |
| --- | --- |
| Keycloak login -> APISIX -> Java JWT/permissions | NOT RUN; DB prerequisite blocked |
| Service Desk create -> handle -> confirm/rework -> completion | NOT RUN live |
| SQL ticket persistence, audit/history, dashboard/statistics | NOT RUN live |
| Metadata create/search, file upload/download/provenance/rollback | NOT RUN live |
| Room Request Core reuse | Existing Java regression evidence only; NOT live reuse |
| Application-managed generic-start denial / self-service | Existing Java regression evidence only; NOT live gateway evidence |
| Java -> private Python -> Java result | NOT RUN; no Java boot |
| Private Python process, bearer auth and request/result schema | PASS: no token 401, wrong token 401, valid token 200 |
| Actual triage | SUCCESS, OTHER / MEDIUM / GENERAL-SUPPORT; INPUT evidence; needsReview=true |
| Private Python HTTP regression | 75 PASS; includes synthetic/injected cases, not Java integration |
| Browser UI / PKCE interaction | NOT RUN; Frontend Owner scope |

## 5. Issues and changes

Confirmed runtime blocker: local PostgreSQL filesystem damage. `/etc/passwd`
has 0 bytes; `libldap-2.5.so.0.1.8` has 0 bytes. Compose entrypoint fails with
`find: 'postgres' is not the name of a known user`; directly executing postgres
fails with `libldap-2.5.so.0: file too short` (exit 127). This reproduces before
any application database connection, ruling out Flyway or Backend as the cause
of these particular failures. The origin of the damaged layers is NOT established.

No backend/config fix is justified by this evidence. This task adds this report
only; it preserves earlier hardening edits, historical migrations, Frontend,
Python algorithms/models, licensing and repository settings.

## 6. Test classification

| Check | Result |
| --- | --- |
| Source tier-boundary guard | PASS |
| Boundary guard mutation/unit checks | 39 PASS, no failures/skips |
| Current Python private HTTP tests | 75 PASS, no failures/skips |
| Current real private HTTP process probe | 3 PASS, schema/profile assertions PASS |
| Current Java selected permission/header/catalog/validator/transport/ArchUnit checks | 60 PASS, 0 failures/errors/skips |
| Pinned PostgreSQL startup | FAIL (environment/image) |
| PostgreSQL readiness, Flyway V1-V7, Flowable, transactions, restart persistence | NOT RUN |
| Current Java targeted offline attempt with image cache | BLOCKED: Spring Boot parent 3.3.13 absent from that cache |
| Full Java, Core-only, supported second-domain | Prior evidence only, not rerun as a full suite here |
| Live API acceptance, browser E2E, clean install | NOT RUN / blocked by prerequisites |

Python tests ran against read-only current source in
`int01-bedemo01-20261018195717-python-test:ci`, network none, temporary `/tmp`:

```text
python -m pytest tests/integrations/test_triage_http.py -p no:cacheprovider -q
75 passed in 57.68s
```

The initial Java image-cache attempt could not resolve the parent POM. A second
attempt to copy the entire host Maven cache was stopped because the copy was
unnecessarily slow; no tests ran in that attempt. The successful retry mounts
the existing host Maven repository read-only at `/host-m2`, copies only current
source/POM/contracts/docs into container-local `/tmp/runtime-check`, and runs
offline with no network or database:

```text
mvn -B -o -Dmaven.repo.local=/host-m2 test -Dtest=TierBoundaryTest,SecurityHeadersTest,KeycloakPermissionGuardTest,ContributedProcessCatalogTest,TriageValidatorTest,HttpIntelligenceTransportTest
Tests run: 60, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Fresh XML reports: `current-readonly-cache-reports/`; log:
`java-readonly-cache-targeted.txt`. Do not use `current-targeted-reports/` from
the failed first attempt: those were copied from an existing target directory
and are NOT fresh evidence. The final retry starts without a target directory.
These selected tests do not cover real Keycloak, PostgreSQL or live Java/Python.

Prior final evidence in `.local/core-workflow-start-20261025/` and
`docs/BACKEND_HARDENING_REPORT.md`: full 264 PASS, Core-only 90 PASS, supported
second-domain 39 PASS, PostgreSQL 16.6/Flowable and authorization/self-service
regressions. These are historical results for the previous hardening worktree,
NOT a fresh run on PostgreSQL 16.15 or proof of live authentication. They are
retained as context, not upgraded to this task's runtime PASS.

## 7. PostgreSQL 16.15 recovery gate

The exact Compose reference exists locally, but the server cannot report its
version. Matching local image ID/RepoDigest metadata does not prove usable
filesystem contents or successful registry verification. No fallback to 16.6
was used in this run; the deployment pin remains unchanged.

Owner action required before modifying the global Docker image store:

1. Restore registry DNS/connectivity and verify the exact manifest from official
   `docker.io/library/postgres`; distinguish config ID from manifest digest.
2. Prefer a separate Docker daemon/context with an independent data root to
   fetch and validate the image without touching shared images/layers.
3. If the shared Docker Desktop store must be repaired/refreshed, obtain explicit
   owner approval first, inventory dependants and agree a non-destructive plan.
   Do not prune, reset Docker Desktop or delete shared layers/volumes.
4. Repeat version probe and disposable startup before attempting migrations or
   claiming any pinned-version acceptance.

## 8. Frontend handoff

No working Backend/Gateway URL is offered from this run. Planned default local
endpoints are Frontend `http://localhost:5173`, Keycloak `http://localhost:8081`
realm `dx-lab`, and Gateway `http://localhost:9080`; they were NOT started here.
The integration overlay instead reserves Keycloak 18081 and Gateway 19080.
Backend and Python are internal services, not direct browser endpoints.

Use `docs/BACKEND_API_INTEGRATION_CONTRACT.md` as current API/permission contract.
The realm template defines synthetic role-named accounts (requester1-2, agent1-8, coordinator1-2, admin1), superadmin and workspace group mappings;
use the current template/contract to select requester, agent and coordinator.
They were NOT imported or logged in during this run. No passwords are recorded.
Create business tickets/room requests through their application APIs, never
generic Core start; retain manual fallback when Intelligence is unavailable.

Frontend Owner still needs real login, task lifecycle/rework, file interactions,
cross-workspace denial and browser E2E after infrastructure recovery. Older
handoff documents are context, not current runtime acceptance.

## 9. AI handoff

Actual endpoint: `/internal/intelligence/v1/triage`, private bearer auth;
request envelope includes `request`, adapter-owned `trustedContext` and
`profileVersion`. Contract 0.1.0, profile service-desk@0.3.0, rules 0.3.0.
The Backend overlay sets `http://intelligence:8000` and a matching private token;
`scripts/start.ps1 -Intelligence` enables that overlay/profile. No paid/cloud
API was called; standalone tests/process had network disabled.

Observed response passed current ContractValidator correlation/evidence/schema
checks. No Python source defect was confirmed; no algorithm/model was changed.
Backend timeout, unavailable, malformed-response mapping and manual workflow
fallback remain NOT RUN in live Java/Python integration here. Python regression
fault cases do not substitute for those checks.

## 10. Reproducible demo and remaining blockers

Prerequisites: usable pinned vendor images, Docker/Compose, PowerShell, Python
with acceptance dependencies (`scripts/acceptance/requirements.txt`), sufficient
disk/memory, official-registry access and explicitly disposable resource names.

The following is a **post-recovery recipe, NOT executed acceptance**. Run setup
in a separate source snapshot containing the current uncommitted hardening work,
not a clean HEAD that omits it. Do not reuse shared project names or volumes.

```powershell
./scripts/setup.ps1
$env:INT01_PROJECT = 'int01-runtime-retry-20261025'
$env:INT01_SOURCE_SHA = git rev-parse HEAD
$env:INTELLIGENCE_TRIAGE_TOKEN = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
$env:INTELLIGENCE_TRIAGE_BASE_URL = 'http://intelligence:8000'
$stack = @('compose', '-p', $env:INT01_PROJECT, '-f', 'compose.yaml', '-f', 'infra/compose/compose.triage.yaml', '-f', 'scripts/acceptance/integration-compose.yaml', '--profile', 'intelligence')
docker @stack config --quiet
docker @stack up -d --build --wait --wait-timeout 300 postgres keycloak platform gateway intelligence
docker @stack ps
python scripts/acceptance/triage-gateway.py --project $env:INT01_PROJECT --output .local/runtime-retry-triage.json
```

Before executing, refuse an already-existing project/container/volume, retain the
same secret variables for all subsequent commands, and preserve the dirty-source
inventory along with image IDs. The revision label alone does not identify dirty
source. Frontend is deliberately omitted until browser/redirect/port ownership is
coordinated; the overlay does not isolate the default Frontend port itself.

For broad synthetic file/workflow/ticket/restart acceptance, reuse
`scripts/test-core-acceptance.ps1` with its fresh `acceptance-*` project after
the image gate is fixed; it creates its own isolated environments. It is a
separate core acceptance harness, not the Intelligence stack recipe above.
Service Desk HTTP lifecycle script currently uses default 8081/9080 and `.env`;
do not point it at unrelated/shared services or claim it ran on 18081/19080.
Use `scripts/test-tier-reuse.ps1` for supported second-domain evidence and its
separate disposable profile. Inspect their generated results for required skips.

Then exercise requester create, independent agent claim/resolve, exact requester
confirm/rework, statistics, audit, files and Room Request; verify persisted SQL
and Flyway/Flowable versions. Restart only that project's services and repeat
read/persistence checks. Login/seed/lifecycle/restart were NOT executed here.

For resources created by this attempt only, these cleanup commands were executed
successfully after evidence capture (`cleanup.txt`):

```powershell
docker rm -f int01-runtime-20261025-intelligence
docker rm int01-runtime-20261025-postgres-1
docker volume rm int01-runtime-20261025_postgres-data
docker network rm int01-runtime-20261025_default
```

The standalone Python service, failed database container, task-only volume and
task-only network no longer exist. Do not generalize these commands to other projects. The disposable database
never reached initialization; its generated password was discarded. Evidence
is retained separately. Security/dependency findings and the licensing/release
checklist remain open; no new security scan or licensing approval is claimed.

## 11. Final decision

| Gate | Decision |
| --- | --- |
| Backend API development | Controlled development only, based on prior regression evidence; no live endpoint from this run |
| Full-stack integration | BLOCKED / NOT ACCEPTED |
| Browser demo | NOT READY / NOT RUN |
| Production / release | NO-GO: runtime, security and licensing gates remain open |

## 12. Isolated recovery follow-up, 2026-10-25

### Source and safety

HEAD remains `f94f0ef102cbc7d5737ca05270b0affab1fde1a6` with the existing
uncommitted hardening/report work. This follow-up changes this document only.
Evidence is separate: `.local/isolated-recovery-20261025/`. Historical evidence
in `.local/runtime-acceptance-20261025/` is preserved.

All Docker actions in this follow-up were read-only inspection. No context was
created/switched, no distro/daemon/container was started, no image was pulled,
loaded, rebuilt, replaced or removed, and no volume/cache/database was modified.
No Frontend/Python source, migration, API contract or dependency pin was changed.

### Daemon and resource isolation

`desktop-linux` endpoint is `npipe:////./pipe/dockerDesktopLinuxEngine`;
`default` endpoint is `npipe:////./pipe/docker_engine`. Both report daemon ID
`762b9349-4c08-4af1-8f5b-834bad840de1`, root `/var/lib/docker`, storage
`overlayfs` with `io.containerd.snapshotter.v1`. They are the SAME shared
Docker Desktop image store. A new context pointing to either would not isolate it.

The read-only inventory found 75 containers, none running, across 18 Compose
projects, including `creatshop-client`, `labops-ai`, `dx-core`, previous acceptance
projects and SEC-01 work. Stopped does not mean disposable. There are 43 images,
47 local volumes and existing build cache; the pinned PostgreSQL image is still
referenced by older SEC-01 containers. Resource ownership is recorded in
`container-inventory.jsonl`, `resource-users.txt`, `image-inventory.txt`,
`volume-inventory.txt` and `resource-summary.json` without environment secrets.

WSL lists running `docker-desktop` and stopped `HexaUdon`. The latter was not
started or assumed to be a permitted disposable daemon. Two previous nested
Docker containers exist but are stopped: `sec01-pg-recovery-independent` and
`sec01-pg-recovery-independent2`. The second is privileged, has its own historical
`sec01-pg-recovery-store2` volume at `/var/lib/docker`, and a read-write mount of
another source directory. Its old command disables bridge/iptables, so even a
successful image probe there would not establish Compose networking readiness.
Neither old container nor its data was restarted, reused or altered.

Host free space observed: C approximately 50.6 GiB; D approximately 64.8 GiB.
`docker system df` reports images 37.53 GB, container writable layers 2.239 GB,
volumes 19.85 GB and build cache 29.9 GB. Host free space does NOT establish free
space/quota inside the Docker Desktop VHD or a future independent daemon.

### Official registry verification

Windows DNS now resolves `registry-1.docker.io` and `auth.docker.io`. Native
`curl.exe` receives the expected 401 authentication challenge from `/v2/`.
Authenticated, read-only Registry v2 GET requests then succeed with TLS
verification enabled. Anonymous pull-scope bearer tokens were kept in memory,
passed to curl through stdin configuration, and not written to logs/arguments.

`registry-native-verification.json` records these independently SHA-256-checked
manifest response bodies, matched against `Docker-Content-Digest` headers:

| Identity | Official registry result |
| --- | --- |
| Exact source pin / OCI index | `sha256:0ea6700a3b4f0ae6ce746519073558aed4d88a79d8d07622a9a644946c7319c4` VERIFIED |
| Pinned index linux/amd64 child manifest | `sha256:ec9f656f17c8b4dfebf49e58cf5c46d493d4846d4ea6d8b85d0637f68ee33bfc` VERIFIED |
| Pinned linux/amd64 config digest | `sha256:e8538db784b854a9baac5c1be6d059dcf0db257af2d94c8c5807b5fc15f11222` |
| Current mutable 16.15-bookworm tag / OCI index | `sha256:d81f7b51697014367236d07ec58b54153fc28f828b72bf247fe5c7487520bcb0` VERIFIED |
| Current tag linux/amd64 manifest | `sha256:e92e17ca202da4bb205f6f4a6c8cb30539edfbaa04987b506630f5d5851ff3c8` VERIFIED |

The mutable tag has drifted; it is NOT a substitute for the pinned index. No
source pin was changed. Both resolved platform manifests list 14 layers.
This is manifest/architecture verification, NOT layer download or server boot.

Local `docker image inspect` still reports image ID
`sha256:0ea6700a3b4f0ae6ce746519073558aed4d88a79d8d07622a9a644946c7319c4`
and the same RepoDigest. The official value is an OCI INDEX digest, while the
official linux/amd64 CONFIG digest is different. Do not equate those identities
or infer usable local files from the index metadata.

The first Python urllib attempt failed TLS verification because its local CA
chain could not find the issuer (`SSLCertVerificationError`). Native curl using
the Windows trust configuration succeeded; no TLS bypass or CA-store edit was
used. This failure is separate from the previous DNS failure. The shared daemon's
own registry pull connectivity was NOT tested, since pulling there is prohibited.

Confirmed local damage remains the earlier zero-byte passwd/LDAP evidence.
The original cause (download/import, layer storage, snapshotter or other host
fault) remains UNKNOWN. The registry has not been shown to contain corrupt
filesystem data. Its manifest now verifies, disproving any assumption that the
source pin is simply absent upstream, but not proving layer integrity.

### Executed checks and acceptance status

Commands executed include `git status --short`, `git rev-parse HEAD`,
`docker context ls/inspect`, both contexts' `docker info`, `docker ps -a`,
`docker image ls --digests --no-trunc`, `docker volume ls`, selected-field
`docker inspect` for container mounts/image references and recovery configuration,
`docker system df`, `Get-PSDrive`, `wsl --list --verbose`, `Resolve-DnsName`,
and native curl Registry v2 requests. There was NO `docker run/up/start/pull`.

| Gate | This follow-up result |
| --- | --- |
| Shared daemon/context identity and resource inventory | PASS, read-only |
| Official pinned manifest / linux/amd64 resolution | PASS, read-only |
| Independent running daemon with permitted fresh data root | BLOCKED: none available |
| Image layers/filesystem and actual PostgreSQL 16.15 version | NOT RUN on an independent daemon |
| Fresh DB / Flyway / Flowable / rollback / audit / concurrency / restart | NOT RUN |
| Full, Core-only, supported second-domain, authorization / ArchUnit suites | NOT RERUN; prior results retained in sections 6 and 11 |
| Live Keycloak/APISIX/Java / Service Desk / Room Request / Java-Python | NOT RUN |
| Clean install and browser demo | NOT RUN |

No new failed application test, no hidden skip and no application fix is claimed.
The previous 60 Java, 39 boundary, 75 Python HTTP tests and three standalone
Python socket probes remain historical evidence, not fresh integrated acceptance.

### Owner approval needed before recovery

Preferred path: owner provisions or authorizes a dedicated VM/WSL Docker Engine
with a fresh data root and grants its endpoint/context for disposable testing.
Before any pull, verify a different daemon ID and an independent storage root,
disk capacity, TLS/authenticated endpoint access and no shared-volume mounts.
Use explicit `--context` for EVERY build/Compose/test command; never just switch
a context label while retaining the shared endpoint.

Alternative requiring explicit approval: create a NEW privileged nested-Docker
container and a NEW task-only data volume, using an already-present Docker
daemon image by its inspected image ID. Do not restart the old recovery containers
or reuse `sec01-pg-recovery-store2`. Do not mount the shared Docker socket or
existing data directories. Mount only a disposable source snapshot; keep inner
Docker API unexposed and operate through an explicit wrapper. This creates new
resources on the shared outer daemon and grants a privileged process access to
the Docker Desktop VM kernel; separate inner image storage is NOT VM/security
isolation. The owner must approve that privilege/resource impact first.

Once an independent daemon is approved/available: pull the EXACT pinned reference
for linux/amd64 into that daemon, verify index/child/config/layer identities,
check passwd/LDAP contents and `postgres --version`, then run a fresh disposable
16.15 DB with readiness and restart checks. Only then run the existing database
test suites and integration overlays, followed by real ticket/rework/audit/file/
Room Request and Java-Python outage/manual-fallback acceptance. Do not import
the known-damaged local PostgreSQL image as the recovery source.

FE/AI handoff: no new live endpoint or integration defect has been established;
their source is unchanged. Keep the current contracts and manual fallback; real
login, business lifecycle and Java-Python acceptance await infrastructure recovery.

**Backend demo remains NOT READY; full-stack remains BLOCKED. Production/release
remains NO-GO.** Security, licensing, public-repository compliance and release-tag
requirements have not been closed by registry verification. No visibility change,
commit, push, merge, tag or release was performed.
