# BE-POSTMERGE-01 - Backend integration stabilization

Assessed 2026-10-18, Asia/Saigon. Production Security **G9 FAIL / RELEASE BLOCKED**.

## A. Baseline and CI

- Fetched `origin/develop`: `53eb992e12d3b08611a155aae6b911e0a253ba52`.
- PR #22 merge `da955d24eddf9004fea12aa45d51cdc58d5a537d` and PR #24
  head `934a86bbbbf11e2df375477c10314737999a4c54` are ancestors.
- #24 adds only `docs/BE_DEMO01_FRONTEND_HANDOFF.md` and
  `scripts/acceptance/browser-pkce.py`. No Java/AI contract delta; no revert needed.
- GitHub API: no develop Actions runs or checks on the merge SHA at assessment.
  Existing workflows only trigger on PR/manual events. This change adds develop
  push triggers to Core acceptance and Intelligence runtime, not security approval.
- Branch API reports `protected=false` for both develop/main. Protection/ruleset
  detail APIs return 403: effective settings cannot be fully inspected.
  Owner action: require PRs, one independent approval, dismiss stale reviews,
  resolved conversations, required functional/combined-head checks, no force-push
  or deletion, and restricted bypass/direct pushes. Enable only after owner approval
  and confirming actual check names. ReportOnly success must not satisfy G9.

## B. PR #23 readiness

- OPEN, DRAFT; base `integration/be-ai-triage-retrieval`; head
  `43df9d745cbfb190cdfca8ac9c08f080c0851b36`.
- Against develop: 16 changed files, runtime images/BOM, scanner/test pin consumers,
  inventory/exporter and evidence. No Java business source, API contracts or AI
  algorithm changes. `git merge-tree --write-tree origin/develop
  origin/fix/security-image-remediation` succeeds without conflicts; #24 paths disjoint.
- CI run `39121326727` succeeded on runtime commit
  `207b9ace6daf24e678e9fa35b49966997353571d`. Final head has no check-runs/runs;
  its sole later commit is documentation. Runtime/config/test diff from tested
  commit to head is empty. This is not proof of a newly tested merge result.
- Existing SEC-01 inventory reports 369 HIGH + 15 CRITICAL occurrences across
  five images, including 211 with candidate fixes. APISIX HIGH increased;
  Keycloak remains 89 HIGH + 7 CRITICAL. These are PR evidence, not a new scan.
- Proposed owner sequence: approve retarget to develop; reconcile normally with
  develop (no force-push); retain #24; rerun final-head and merged-result regression,
  PKCE and full image scans; review image identity/full findings and remaining risks.
  Keep draft until review conditions pass. Merge readiness is conditional; release
  stays BLOCKED even if functional CI succeeds. No retarget/merge/close performed.

## C-D. Local integrated demo and FE

Use a fresh checkout/worktree, Docker Linux containers and Compose supporting
`!override` (>=2.24.4). Ports 8081/9080 must be free; do not stop somebody else's
stack to free them. The following uses new project-scoped volumes, not `dx-core`.
Flowable is embedded in Core, not a missing standalone service.

```powershell
if (Test-Path .env) { throw 'First-run instructions require a fresh checkout; reuse existing credentials for subsequent starts' }
./scripts/setup.ps1
# Fresh .env only: provision the Java/Python shared service credential once.
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
$bytes = New-Object byte[] 48
$rng.GetBytes($bytes)
$rng.Dispose()
Add-Content .env ("INTELLIGENCE_TRIAGE_TOKEN=" + [Convert]::ToBase64String($bytes))
$env:INT01_PROJECT = 'int01-demo-' + [guid]::NewGuid().ToString('N').Substring(0,12)
$env:INT01_SOURCE_SHA = git rev-parse HEAD
$env:INT01_KEYCLOAK_PORT = '8081'
$env:INT01_GATEWAY_PORT = '9080'
docker compose -p $env:INT01_PROJECT -f compose.yaml -f infra/compose/compose.triage.yaml -f scripts/acceptance/integration-compose.yaml --profile intelligence up -d --build --wait --wait-timeout 300
if ($LASTEXITCODE -ne 0) { throw 'Demo startup failed' }
```

Retain these project/port variables in this shell for subsequent Compose commands.
On subsequent starts reuse the SAME token/.env and project; do not append another
token or rerun setup against an already imported realm expecting password changes.
Default `scripts/start.ps1` is Core-only; `setup.ps1` does not generate the shared
Intelligence token. Missing token makes Intelligence fail startup. No Dockerfile or
dependency change is needed; the integration overlay supplies Core's internal URL.

FE: public client `dx-web`, PKCE S256, realm `dx-lab`, IdP localhost:8081,
callback `http://localhost:5173/auth/callback`. API via gateway localhost:9080.
Use the existing handoff Vite `/api` proxy and relative API URLs; direct cross-origin
gateway preflight is not enabled. Core localhost:8080 in the handoff is a container
address/port, NOT a published host endpoint. Python stays private. Do not log tokens
or commit `.env`, generated realm or test payloads.

Runnable existing checks (Python 3.12; no new framework):

PKCE and Service Desk checks use stdlib. The triage checker also needs the installed
Intelligence wheel and locked jsonschema dependencies; prepare them as in
`.github/workflows/intelligence-triage-e2e.yml`, not just `setup.ps1`.

```powershell
python scripts/acceptance/browser-pkce.py --output .local/postmerge01/pkce.json
python scripts/acceptance/servicedesk.py .local/postmerge01/servicedesk.json
python scripts/acceptance/triage-gateway.py --project $env:INT01_PROJECT --core-url http://127.0.0.1:9080 --identity-url http://127.0.0.1:8081 --output .local/postmerge01/triage.json
```

Triage acceptance deliberately stops/restarts ONLY this project's Python container.
Stop after the demo in the SAME shell, preserving the project/port variables:

```powershell
if (!$env:INT01_PROJECT) { throw 'Set the existing demo project name; do not create a new project' }
docker compose -p $env:INT01_PROJECT -f compose.yaml -f infra/compose/compose.triage.yaml -f scripts/acceptance/integration-compose.yaml --profile intelligence stop
if ($LASTEXITCODE -ne 0) { throw 'Demo stop failed' }
```

This retains the disposable volumes. Never use `down -v` or volume prune.
See local `.local/postmerge01/` for private run evidence, not committed
HTTP bodies/tokens. Browser application E2E remains NOT RUN.

## E. Proof of FOSS

Read `docs/references/De_thi_PMNM_HaUI_2026_BAN_CHINH_THUC.pdf`, especially
sections 10-12: public repository, source build/reproducibility, dependency origins,
README, CHANGELOG, LICENSE, release/tag and secret hygiene are explicit evidence.

| Item | State / next owner action |
| --- | --- |
| README / CHANGELOG | Updated integration entry points; baseline versions unchanged until #23 merges |
| Java / Python inventory | Existing `docs/dependency-tree.txt`, `docs/dependency-licenses.md`, `intelligence/requirements.lock`, AI-03 dependency resolution; full current transitive/image license closure still pending |
| Third-party notices | BLOCKED: complete distribution-specific notices from resolved dependencies/images; existing AI-03 license documentation is not a repository-wide notice bundle |
| Source LICENSE / public repository / release-tag | BLOCKED owner approval; intended Apache-2.0 is not a formal grant; no publication or tag performed |
| References / AI fixtures | BLOCKED rights review; PDFs/XLSX not automatically team-owned; fixture manifests still `PENDING_OWNER_APPROVAL`, `usageRights=null` |
| Secret scan | See validation below; scanners are evidence, not proof that every secret was detected |
| Source build / Compose | See validation below; no existing developer volume reused/deleted; offline reproducibility and cross-platform builds not claimed |
| Release checklist | `docs/license-release-checklist.md` remains authoritative; require G9, rights, notices, browser demo, final clean-source CI and owner approval before release |

## F-H. Scope and coordination

- Changes: CI event gap plus documentation only. No FE screens, backend behavior,
  Dockerfiles/dependencies owned by #23, AI algorithm or BE-02 implementation.
- FE: use proxy/PKCE handoff, report concrete request/status/requestId blockers;
  run real-browser login, refresh/logout and workflow demo before Browser E2E PASS.
- AI: confirm shared-token provisioning and private endpoint; keep contract/profile
  versions unchanged; coordinate remaining data rights and image findings.
- Backend priority: obtain #23 retarget/review decision, final merged-source CI/scan;
  owner-enabled branch rules; FE browser rehearsal; complete license/notice evidence.
  Do not broaden into retrieval or architecture work.

## Validation

- Tested backend/runtime source: `53eb992e12d3b08611a155aae6b911e0a253ba52`;
  disposable project `int01-postmerge01-20261018`. All five services healthy after
  supplying the shared token. Compose build succeeded from the initially clean
  worktree, with build cache allowed. Full clean/no-cache Java+DB and AI regression
  were NOT rerun locally; integration acceptance is not that broader claim.
- API integration PASS: existing `servicedesk.py` completed 96 PASS assertions,
  including create/list/detail, task inbox/claim/complete, requester rework/confirm,
  statistics, duplicate concurrency, RBAC and ProblemDetail/requestId checks.
- PKCE protocol PASS: requester/agent S256, code reuse rejection, missing PKCE and
  foreign redirect rejection; gateway API preflight 401/no allow-origin (not ready).
  Browser E2E NOT RUN: no running FE application was exercised.
- AI Triage integration PASS: 11 cases through real Keycloak/APISIX/Java/Python,
  private endpoint, workspace/role isolation, no state mutation and Python outage
  with manual workflow completion. Observed outage code `INTELLIGENCE_TIMEOUT`.
  Host MSYS Python 3.12 lacked pip/jsonschema/package. The checker alone ran on
  existing CPython 3.11/jsonschema using validator/resources copied from the built
  image; the real Python 3.12 Intelligence service stayed in Docker. This does not
  certify Python 3.11 as a supported Intelligence runtime or rerun the AI unit suite.
- Gitleaks v8.24.2 image digest
  `sha256:b5918eb91b8d2473cec722f066abb4352e4ffdc4ec9f4283ec143aba9ec9ebc4`:
  baseline tracked-source archive and all fetched Git refs (116 commits) scanned;
  no leaks found, redacted output. Generated local secrets excluded from source
  archive; history scan does not prove privacy/redistribution rights.
- Workflow YAML parsed; develop push plus PR/manual triggers and read-only
  permissions asserted. `git diff --check` passed. Changed files are outside #23's
  write set. New push triggers await review/merge; no merge-SHA CI PASS claimed.
- Production Security G9 FAIL / RELEASE BLOCKED. No fresh image-vulnerability
  scan is claimed here; #23 evidence remains unresolved, not a waiver.
