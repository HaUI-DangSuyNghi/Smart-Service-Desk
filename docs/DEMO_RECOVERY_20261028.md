# Integrated demo recovery: final handoff

2026-10-28, Asia/Saigon. **API-assisted local demo READY; full Service Desk Browser
E2E NOT READY.** Overall full-browser submission-demo gate remains NOT READY until
the three FE actions in `FE_DEMO_BLOCKERS.md` are wired and retested.
This is runtime verification, not a repeat backend audit or license/release approval.

## Frozen source and preserved environment

- Source: `ef047a31abfd8e35ce9b6155e3a06c224262269d`.
- Worktree: `D:/Smart-Service-Desk-submission`, branch prep/contest-submission-20261028.
- Running project: `ssd-p0-20261028`; **leave it running**. All six services healthy
  after the full-stack stop/start test, including subsequent browser checks.
- Configuration: unchanged `compose.yaml`, `infra/compose/compose.triage.yaml`,
  task-owned `.local/recovery/demo-only.yaml`; identical durable copy at
  `D:/ssd-demo-recovery-20261028/demo-only.yaml`. No production configuration change.
- PostgreSQL **16.6 DEMO ONLY**, not PostgreSQL 16.15 acceptance. Image reference
  `postgres:16.6-bookworm@sha256:557fea37a744d5f4c8faab304b0a90858b53ab119735a88c131fd19dab802f36`.
  Running image ID is the same sha256 value on this Docker engine.
- New project-owned named PostgreSQL/files volumes were used; full restart retained
  them. No shared image, prior volume/database/project or original-workspace file
  was deleted/reset/cleaned/stashed. No backend/FE business code changes, merge/push.

## Start or resume this exact preserved demo

Docker Desktop must be running; localhost 5173/8081/9080 must be free. Use existing
ignored .env and generated .local/dx-lab-realm.json. Do not regenerate passwords,
reset Keycloak or replace populated DB volumes. Prefer PowerShell 7; explicit
commands below check native exit codes instead of treating stderr progress as failure.

```powershell
Set-Location D:/Smart-Service-Desk-submission
git rev-parse HEAD
$demo = @('compose', '--progress', 'plain', '-p', 'ssd-p0-20261028',
  '-f', 'compose.yaml', '-f', 'infra/compose/compose.triage.yaml',
  '-f', 'D:/ssd-demo-recovery-20261028/demo-only.yaml', '--profile', 'intelligence')
docker @demo config --quiet
if ($LASTEXITCODE -ne 0) { throw 'Invalid demo configuration' }
docker @demo up -d --no-build --wait --wait-timeout 300
if ($LASTEXITCODE -ne 0) { throw 'Demo startup failed; inspect project logs' }
docker @demo ps -a
```

Frontend http://localhost:5173; Keycloak http://localhost:8081/realms/dx-lab;
gateway http://localhost:9080. Use localhost consistently for OIDC redirect/issuer.
PostgreSQL and Python expose no host port. Read-only health/log commands:
`docker @demo ps -a`, `docker @demo logs --tail 80 platform postgres intelligence`.
Do not publish resolved non-quiet config, .env, generated realm, token files or raw logs.

Accounts: user1 lab-a requester, user2 lab-a agent, user3 lab-a coordinator,
user4 lab-b requester. Password is existing local DEMO_PASSWORD, shared privately.
Disposable seed prerequisites already persisted: user2 added to
`/support-groups/lab-a/hardware` through this project's Keycloak admin API; user2
set own duty true via HPDI API. The stock template lacks that support membership;
without it review/assignment returns 400. No validation bypass was introduced.
See demo-seed-membership.json and captured DutyStatus. Fresh-machine reproduction
needs equivalent authorized seed; startup alone does not create it. Do not place
master-realm credentials in FE or give coordinators permission to toggle others' duty.

## Verified PASS/FAIL matrix

Private evidence root: `D:/ssd-demo-recovery-20261028/`. Records are fresh recovery
executions, not reconstructed results from the missing earlier .local/submission.

| Check | Actual result | Evidence relative to root |
| --- | --- | --- |
| Six-service startup | PASS: PostgreSQL, Keycloak, platform, APISIX, frontend, Intelligence healthy | startup.log, final-runtime.json |
| Flyway/Flowable | PASS: 33 successful migration rows, 0 failed; 6 process definitions deployed | db-compatibility.txt, platform.log |
| Real browser PKCE | PASS: S256 challenge, actual Keycloak login and authenticated app for user1/2/3/4; no stubs | demo-check.log, user*-login.png |
| Ticket create/review/assign/start | API PASS against real services; corresponding UI actions missing | api-captures.json, demo-check.log |
| Agent receive/complete | Browser inbox and completion PASS, POST complete 204; START prepared through real API | agent-inbox-started.png, agent-completed.png |
| Requester confirm | API PASS; additional real Browser PASS from ticket timeline, 204, CLOSED/REQUESTER | confirm-ui/result.json, confirm-ui/requester-confirmed.png |
| Statistics | PASS scoped live counts and dashboard rendering; counts retained across restart | api-captures.json, statistics-after-restart.png, confirm-ui/api-captures.json |
| RBAC/isolation | PASS anonymous 401, requester/agent cannot review 403, cross-workspace hidden 404, wrong confirmer 403, duplicate/replay 409 | api-captures.json |
| Actual Python | PASS Java->private Python rules service-desk@0.3.0, response shown in browser | browser-triage-success.png, continue-check.log |
| AI outage/manual fallback | PASS stopped only own Intelligence: actual 504 INTELLIGENCE_TIMEOUT, browser manual-workflow message, process unchanged; manual lifecycle completes while Python stopped | browser-ai-outage.png/.txt, continue-check.log, api-captures.json |
| AI recovery | PASS restart same Python container, Java triage returns SUCCESS again | continue-check.log |
| Full-stack persistence | PASS stop all six, up same containers/volumes, both tickets identical, ended processes and scoped statistics retained; browser SSO/readback restored | operations.log, continue-check.log, result.json |
| Room Request reuse | Browser PASS create -> independent coordinator claim -> approve -> requester readback; shared Core workflow ends | room/result.json, room/*.png, room/api-captures.json |
| Room Intelligence | FAIL/unavailable: 502 INTELLIGENCE_CONTRACT_FAILED with selected Service Desk-only profile; no profile switch attempted | room/room-advisory.png, room/api-captures.json |
| Live response schemas | PASS 60 JSON responses: SD 38, Core 8, HPDI 9, Room 5; bodyless 204 checked separately, not counted as JSON | all-contract-validation.log, *-all-contract-captures.json |
| Entire Service Desk browser lifecycle | NOT READY: create, standard review/assignment form and explicit START missing | FE_DEMO_BLOCKERS.md |

Persistent primary tickets: `c01554f3-c9c6-439d-bbe3-1d750b25bffe` and
`0325836a-7227-4a90-beb2-9808309f254b`. Browser-confirm test ticket:
`4dee3ca8-78b8-418a-a467-6100ffbbbb11`. Room:
`b20020f2-45fd-4953-82b5-200f02c50704`. All synthetic; earlier failed-attempt data
was retained, so global statistics include other tickets. Use scoped query/filter,
not an assumption of an otherwise empty database.

### Corrections and evidence limits

- Initial harness expected only 503 on outage; actual documented 504 was recorded
  and test resumed accepting 503/504, not relabeled without execution. Original
  attempts remain under attempt1/2/3 and demo-check.log; continuation is separately logged.
- Earlier missing-requester-confirm conclusion was wrong: /inbox denies requester,
  but ticket timeline already offers the assigned confirmation. confirm-ui test
  actually exercised it successfully. Do not implement another confirmation system
  or weaken /inbox gate. Existing completion label could be clearer, not a blocker.
- Room reuse is real H/P and app data/API reuse; a Room Intelligence success is NOT
  claimed. No LLM quality or production security/performance acceptance inferred.
- Runtime scripts: demo_check.py, continue_check.py, room_check.py,
  confirm_ui_check.py and verify_registry.py at evidence root. They are private
  diagnostic runners, not source changes. They create synthetic records; do not
  rerun repeatedly just to start the already-working demo.

## PostgreSQL diagnosis retained; no further storage investigation needed

Local 16.15 materialized /etc/passwd was empty, unlike independently downloaded
SHA-verified official layers containing postgres. Re-pull same pin did not repair
it. Local filesystem divergence CONFIRMED; underlying storage mechanism unverified,
no upstream defect claimed. Both configured Docker contexts use the same engine;
no independent-engine PASS claimed. Evidence: filesystem-comparison.json and
official-filesystem-verification.json. Demo 16.6 official layers were independently
downloaded/hash-checked; local passwd, postgres binary and entrypoint hashes match
registry contents: pg166-registry-verification.json. Do not change the stable fallback.

## Workspace reconciliation and next action

Original workspace read-only: 0bbaf13833f74ebb934e62c56cc43330e3f679fa. Seven formerly
untracked docs are now tracked by that documentation consolidation; author/process
not inferred. Earlier .local/submission evidence is unavailable, not fabricated.
The newly supplied Council PDF remains untracked/untouched; no attribution or
redistribution approval inferred. Only documentation is added in this worktree.

**Next:** FE owner implements FE-P0-01/02/03 on an isolated branch, uses preserved
demo data/duty, then rehearses actual browser lifecycle without API substitutions.
Until then present only an explicitly API-assisted local demo; do not claim full
Browser E2E or PostgreSQL 16.15/production readiness. No merge/push authorized.
