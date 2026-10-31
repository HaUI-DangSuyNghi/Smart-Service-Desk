# Full-Stack Integration Acceptance - PostgreSQL 16.6

Run started 2026-10-25 and completed after midnight on 2026-10-26, Asia/Saigon.
Project: `int01-pg166-20261025-a`. Evidence: `.local/int01-pg166-20261025-a/`.

## 1. Decision

**PASS for the exercised live API integration on PostgreSQL 16.6.** Real
Keycloak login/JWT -> APISIX -> current Java Backend -> PostgreSQL/Flowable ->
current private Python service succeeded. Service Desk rework/confirmation,
Room Request reuse, file provenance, rollback and restart persistence passed.
All five services are left running and healthy for controlled local handoff.

**This is NOT pinned PostgreSQL 16.15 acceptance. Production/release remains
NO-GO.** Browser E2E, clean installation using every production-pinned image,
security/dependency release gates and licensing remain unverified/open. No
Frontend container or browser was run. The historical blocked runs remain valid
historical evidence; this report adds a separately authorized 16.6 result.

## 2. Source and build identity

- HEAD: `f94f0ef102cbc7d5737ca05270b0affab1fde1a6`, plus existing uncommitted hardening.
- Source fingerprint: `a1de2bf391c594551e82df819a91cc68e8228720feda6cc99e97a860837fc12a`.
- Fingerprint method: SHA-256 of sorted JSON mapping each of 987 tracked/nonignored
  untracked files to its SHA-256. Per-file values are in `source-fingerprint.json`.
- Built JAR SHA-256: `6228e0d6041801b9ae5f293a5bab68a855efd8ee27dc92ec44ddcad9b15a3315`.
- Runtime image: `int01-pg166-20261025-a-platform:local`, Docker-reported ID
  `sha256:40a193b2840b8dda45663992393bc27c41f32e433488b3e59678a3d0cbb35244`.

The current POM/src/contracts/docs were copied into a task-only builder's
temporary filesystem. Maven used a read-only host dependency cache, no network,
768 MiB container limit and `MAVEN_OPTS=-Xmx384m`:

```text
mvn -B -o -Dmaven.repo.local=/host-m2 -DskipTests package
BUILD SUCCESS
```

This build does not count skipped Maven test execution as PASS. The resulting
JAR replaced `/app/app.jar` in a NEW acceptance image based on existing
`int01-sec01-final-20261018-core:ci`; no old application image was retagged or
overwritten. Java runtime reports Temurin 17.0.20.1. The builder was task-labeled
and automatically removed. Final hashing confirmed all 987 original source files
unchanged. No product bug requiring a Backend/Frontend/Python patch was found.

## 3. Isolation and resource ownership

Existing Docker Desktop daemon was used with explicit owner authorization.
No VM/WSL/DinD, daemon/global setting change, prune, old-container restart or
existing-volume mount was performed. PostgreSQL 16.15 was not pulled or changed.

- Network: `int01-pg166-20261025-a_default`.
- NEW volumes: `int01-pg166-20261025-a_postgres-data`, `int01-pg166-20261025-a_files`.
- Containers: project prefix plus `postgres-1`, `platform-1`, `keycloak-1`,
  `gateway-1`, `intelligence-1`.
- `ownership.json` and `final-inventory.json` record names, images, mounts,
  readiness, OOM status and configured memory limits.
- Passwords, directory secret and private bearer token were independently
  generated in `secrets.env`; realm import was generated in `realm.json`.
  Neither shared `.env` nor shared realm file was modified or used for credentials.
- Evidence directory ACL permits the current user, SYSTEM and Administrators.
  Do not publish/copy `secrets.env` or `realm.json` into reports or source control.

The acceptance override pins PostgreSQL by existing image ID and all startup
commands use `--no-build --pull never`. The main Compose 16.15 pin is untouched.

## 4. Runtime and resources

| Service | Actual version / image identity prefix | Limit | Observed final usage |
| --- | --- | --- | --- |
| PostgreSQL | 16.6 / `557fea37a744` | 256 MiB, 0.75 CPU | 51 MiB |
| Backend / Flowable | Java 17.0.20.1 / `40a193b2840b8` / Flowable schema 7.1.0.2 | 640 MiB, 1.5 CPU | 244 MiB |
| Keycloak | 26.0.8 / `09a381c715ab` | 640 MiB, 1 CPU; heap max 320 MiB | 328 MiB |
| APISIX | 3.11.0 / `b833c7eda0d8` | 128 MiB, 0.5 CPU; one worker | 35 MiB |
| Intelligence | Python 3.12.15 / `42ad6a4ba7ee`; current source mounted read-only | 256 MiB, 1 CPU | 18 MiB |

Full PostgreSQL ID and RepoDigest:
`sha256:557fea37a744d5f4c8faab304b0a90858b53ab119735a88c131fd19dab802f36`.
Actual `postgres --version`, `SELECT version()` and `pg_isready` succeeded.
Fresh initialization created `core` and `keycloak`; PostgreSQL publishes no host port.

Host available RAM was initially 1.32 GiB, rose to 2.41 GiB after the DB stage,
was checked between expansions, and was about 3 GiB at final capture. Stable
pre-restart service usage was approximately 1 GiB; final usage about 677 MiB.
These are point-in-time measurements, not capacity/load-test guarantees.
Final idle CPU samples were below 1% per service. All final containers report
healthy, OOMKilled=false and zero current cgroup OOM events.

## 5. Live acceptance evidence

| Check | Result / evidence |
| --- | --- |
| PostgreSQL startup/readiness/SQL | PASS on new 16.6 volume |
| Flyway | V1-V7 all success; `database-initialization.txt` |
| Flowable | schema 7.1.0.2; four process definitions deployed |
| Service Desk existing acceptance script | 95 HTTP assertions PASS; `servicedesk-live-final.log` |
| JWT/F04/self-service/data/Room Request | 30 HTTP assertions PASS plus SQL/content assertions; `security-room-result.json` |
| Real Java/Python | 21 HTTP assertions plus six schema/correlation/evidence validations; `triage-live-result.json` |
| Private Python bearer | 401 missing, 401 invalid, 200 valid; `private-python-final.json` |
| Service Desk post-restart | 17 HTTP assertions PASS; `servicedesk-restart.log` |
| Room/file/Python/counts after restart | PASS; `restart-result.json` |

These counts describe live HTTP assertions, not Java unit-test counts. No mocks
were used for Keycloak, JWT verification, Gateway, DB, Flowable or Python in
the final live checks.

Service Desk: requester creates through the application API; independent agent
claims/resolves; requester requests rework then accepts; four history steps
match the correct actors. Concurrent duplicate create yields one 201 and one
409. Visibility, workspace separation, statistics and exactly one creation audit
event per successful ticket are verified by the existing acceptance assertions.

Identity/security: genuine tokens with correct issuer and audience work. A
tampered signature, real token lacking dx-core audience, and master-realm token
receive 401. Requester metadata/file/inbox/audit access receives 403; authorized
roles succeed; foreign workspace access is denied. These exercise the relevant
F04 guards; dynamic directory-role revocation was not separately replayed here.

Core generic starts for service-desk and room-request return 403 without changing
DB counts. Advertised self-service document-review starts successfully. Business
tickets/rooms are created only through their application APIs.

Data/Room Request: synthetic file upload/download bytes and SHA-256 provenance
match; metadata search and permission denials pass. Requester creates a room
request referencing the knowledge record; coordinator handles the shared HPDI
task; process ends and one room creation audit event exists.

Rollback: a temporary trigger in ONLY this task's database deliberately rejects
Room Request INSERT, after workflow creation has begun. HTTP returns expected
500; ticket/room/scope/history/task/audit counts remain unchanged. Trigger and
function are removed in finally; removal is verified. This is an injected DB
failure testing actual transaction rollback, not an ordinary success path.

Intelligence: real service-desk@0.3.0 responses succeed for requester/agent/
coordinator in both workspaces. Current ContractValidator validates all six
responses, including correlation and evidence text. Python has no public port.
Stopping ONLY this task's Python service causes 504 INTELLIGENCE_TIMEOUT, no
fabricated suggestion and no state mutation; agent resolves and requester
confirms the ticket while Python remains stopped. Python is then restarted.
This tests a real outage with timeout outcome, not a forced 503 or an injected
malformed response; those separate transport branches were not replayed live.

## 6. Restart persistence

All five services belonging to this project were stopped, then started in stages
with the SAME task-only volumes: PostgreSQL, Keycloak, Backend/Python, Gateway.
No volume was removed. `restart-services.log` records actual operations.

Before and after restart, counts were identical:

```text
servicedesk_ticket | roomrequest_request | process_scope | act_hi_procinst | act_ru_task | audit_event | file_object | metadata
7                 | 1                   | 9             | 9               | 6           | 31          | 1           | 1
```

The seven tickets include synthetic data from the retained failed attempts;
they are not seven completed workflows. Three Service Desk harness tickets
retain exact content, ownership and process references; expected statistics and
workflow history pass. Room Request remains completed, uploaded bytes remain
exact, and real triage succeeds again after restart. Keycloak credentials/realm
persist and fresh login works. Reads/suggestions do not change the captured counts.

## 7. Failed attempts and bounded corrections

Two acceptance-environment problems were confirmed; neither required a product
source change:

1. **Gateway OOM under the acceptance limit.** APISIX defaults to auto workers;
   the first 128 MiB container hit its cap, with observed oom=31 / oom_kill=8,
   OOMKilled=true and HTTP disconnects despite intermittent healthy probes.
   Expansion stopped, gateway was stopped, and evidence was preserved in
   `gateway-oom-state.json`, `gateway-oom.log`, `servicedesk-live.log`.
   A task-only copy of APISIX config sets `nginx_config.worker_processes: 1`.
   The limit was NOT increased, shared config was NOT changed, and one bounded
   retry passed all 95 Service Desk HTTP assertions with zero new OOM events.
   Post-correction usage was around 50 MiB before restart.
2. **Legacy Python image entrypoint.** The reused dependency/runtime image still
   invoked `smart_service_intelligence.http_service`; mounting current hdpi source
   alone did not change that command. Valid private requests returned 400 and
   Backend returned 502 INTELLIGENCE_CONTRACT_FAILED. Direct current-hdpi schema,
   profile and engine checks succeeded. The acceptance override now explicitly
   invokes `python -m hdpi.http_service --host 0.0.0.0`, matching the current
   `intelligence/Dockerfile`. No Python algorithm/model was edited. The failed
   `triage-live.log` and `private-python-probe.txt` remain; corrected results are
   in `triage-live-final.log` and `private-python-final.json`.

An initial persistence inventory query used the wrong table name metadata_record;
the query was corrected to the actual metadata table before taking the baseline.
It was a read-only harness mistake, not a product or migration failure.

## 8. Test boundaries and files changed

- New live acceptance: PASS as itemized in section 5, with explicit failed initial
  attempts preserved in section 7. No required final assertion was silently skipped.
- Maven build: PASS, tests deliberately not executed in that packaging command.
- Full 264 / Core-only 90 / supported second-domain 39, PostgreSQL/Flowable and
  authorization regression suites: prior evidence in BACKEND_HARDENING_REPORT;
  NOT RERUN here because product source is unchanged. Do not count them as new tests.
- Earlier 60 selected Java / 39 source boundary / 75 Python HTTP tests: historical
  evidence only. No fresh broad ArchUnit/security scan is claimed in this run.
- Browser E2E, clean build/install from all current Dockerfiles, pinned PostgreSQL
  16.15 acceptance, backup/restore and load testing: NOT RUN.

Repository addition: `docs/FULLSTACK_INTEGRATION_ACCEPTANCE.md` only. Original
source hashes and uncommitted changes remain unchanged. All configuration,
assert-based probes, logs, runtime image build context and secrets for this task
are under its ignored evidence directory. No commit/push/merge/tag/release.

The Service Desk harness is reused from `scripts/acceptance/servicedesk.py` with
only task-env path and endpoint substitutions; assertions are unchanged.
`harness-adaptation.json` records its original hash. The resume copy uses
127.0.0.1 to avoid localhost address-fallback overhead. `security-room.py` and
`triage-live.py` are runnable additional stdlib checks using `live_common.py`;
they intentionally create synthetic records only in this task's project.

## 9. Commands and local reproduction

Existing images and the warmed Maven dependency cache are prerequisites for this
offline acceptance path. It is not a clean-machine installation claim.

The generated helper fixes the project name, task-only env file and these Compose
files in order: compose.yaml, infra/compose/compose.triage.yaml,
scripts/acceptance/integration-compose.yaml, task override.yaml; profile intelligence.
It does not print or source the shared .env. Representative ACTUAL commands:

```powershell
$run = '.local/int01-pg166-20261025-a'
python $run/compose.py config --quiet
python $run/compose.py up -d --no-build --pull never --wait --wait-timeout 60 postgres
python $run/compose.py exec -T postgres postgres --version
python $run/compose.py exec -T postgres pg_isready -U core -d core
docker build --pull=false --network=none -t int01-pg166-20261025-a-platform:local $run/image
python $run/compose.py up -d --no-build --pull never --wait --wait-timeout 180 platform
python $run/compose.py up -d --no-build --pull never --wait --wait-timeout 180 keycloak
python $run/compose.py up -d --no-build --pull never --wait --wait-timeout 90 gateway
python -u $run/servicedesk-live.py $run/servicedesk-http-final.json
python $run/compose.py up -d --no-build --pull never --wait --wait-timeout 60 intelligence
python -u $run/security-room.py
python -u $run/triage-live.py
python -u $run/servicedesk-resume.py $run/servicedesk-after-restart.json $run/servicedesk-http-final.json
```

Do not overwrite original evidence when rerunning: choose new output filenames
or a new task directory/project and regenerate secrets/realm/ownership records.
An existing volume is not a fresh-install test. All remaining synthetic data is
deliberately preserved in the task-only volumes for inspection.

## 10. Frontend and AI handoff

Live Gateway: `http://localhost:19080`; health: `/health`; API prefix: `/api`.
Live Keycloak authority: `http://localhost:18081/realms/dx-lab`.
Browser client: dx-web, Authorization Code + PKCE; API audience: dx-core.

Synthetic accounts: requester1/requester, agent1/agent, coordinator1/coordinator in lab-a;
requester2/requester, agent6/agent, coordinator2/coordinator in lab-b. User7 is platform-admin
without workspace membership. Password values are not included in this report;
the authorized local owner can retrieve the task-only DEMO_PASSWORD from the
protected secrets.env. Never publish the credential files with the evidence.

Frontend Owner must configure the test frontend's OIDC authority for 18081 and
its same-origin API proxy for 19080. Current frontend/vite.config.ts still targets
9080, and frontend/.env.example uses identity 8081; they were not changed.
Browser integration is therefore NOT automatically ready just because these API
checks passed. Use BACKEND_API_INTEGRATION_CONTRACT.md for permissions and use
application create endpoints for tickets/rooms, not generic Core start.

AI Owner: service-desk@0.3.0 / contract 0.1.0 / rules runtime works through real
Java HTTP, with valid schema/evidence and manual fallback. No algorithm defect
was confirmed. Keep the current hdpi entrypoint when using existing older images.
Room Request Core reuse was verified without enabling its separate Python profile;
Room Request AI suggestions were NOT part of this service-desk-profile run.

Services are left running, resource-limited. To release RAM without losing this
task's data, stop ONLY this project:

```powershell
python .local/int01-pg166-20261025-a/compose.py stop
```

No deletion is required. Before any future cleanup, match container labels,
network and volume names against ownership.json/final-inventory.json and retain
the nonsecret evidence. Never use global prune or remove another project's data.

## 11. Final readiness

| Gate | Decision |
| --- | --- |
| Backend API local integration / API demo on PostgreSQL 16.6 | READY for exercised scenarios |
| Five-service live integration and restart | PASS on this disposable worktree-based environment |
| Frontend browser demo | NOT RUN; FE owner configuration and browser checks required |
| Pinned PostgreSQL 16.15 / clean-install reproducibility | NOT ACCEPTED |
| Production / release | NO-GO: pinned runtime, security and licensing gates remain open |
