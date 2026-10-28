# Contest submission readiness

Runtime update: `DEMO_RECOVERY_20261028.md` records recovered API-assisted demo,
restart/fallback PASS and remaining Browser E2E blockers. Earlier .local/submission
logs referenced below are unavailable; use fresh recovery evidence, not reconstructed PASS.

2026-10-28 (Asia/Saigon); deadline **2026-10-29**. **NOT READY TO SUBMIT.**
Preparation only: no license grant, merge, publication, tag or release authorized.

## Version and preservation

- Tested source: `ef047a31abfd8e35ce9b6155e3a06c224262269d` (approved develop).
- Branch/worktree: `prep/contest-submission-20261028`, `D:/Smart-Service-Desk-submission`.
- Original workspace at `8e93f5f316c6bf55c1ca367cfdc62cc8366b19cb` and seven
  untracked files preserved. Only three preparation documents added here.
- Finished audit read, not repeated, at
  `D:/Smart-Service-Desk-final-audit/docs/audits/FINAL_BACKEND_GAP_AUDIT.md`;
  it is absent from the approved tracked tree. No runtime settings/R7/code changed.
- Private evidence: `.local/submission/` in this worktree. Redact credentials and
  identities before sharing; do not publish that directory.

## Official format: section 10, PDF page 5

Read directly: `docs/references/De_thi_PMNM_HaUI_2026_BAN_CHINH_THUC.pdf`.
PDF redistribution not approved. Section 9: 10 minutes demo + up to 5 minutes Q&A.
Section 7 requires Intelligence, not an LLM. Two public datasets/second app not mandatory.

| Mandatory deliverable | Status / next action |
| --- | --- |
| Public repository or equivalent | BLOCKED: private, anonymous API 404; rights/publication approval required. |
| Product release/tag | BLOCKED: no local/remote tags observed; proposed v0.1.0 pending approval. |
| README: three tiers, H-P-D-I, features, install/config/start, API, demo accounts | Present; reconcile historical/version prose and final tested instructions. |
| LICENSE + dependency/license table | Table partly historical; root LICENSE and approved grant absent. |
| CHANGELOG + issue/bug tracker | Unreleased entries present; final entry and anonymous tracker unverified. |
| Technical report | Required; final report not supplied/verified. 8-10 pages recommended, not minimum. |
| Demo slides | Required, maximum 12; final deck not supplied/verified. |

Recommended, not mandatory: CONTRIBUTING (present), 3-5 minute video, report length.
Report: problem, reusable Core/three tiers, H-P-D-I, lifecycle/data/provenance,
Intelligence/fallback, actual tests/evaluation, FOSS/dependencies, limits, version/links.
Deck: problem, architecture, Core reuse, H-P-D-I, lifecycle, Intelligence, data/rights,
evidence, demo, limits, release, conclusion (at most 12 slides).
These checklists/backend audit do not replace final report/presentation.

## Actual exact-source execution

| Check | Result / private evidence |
| --- | --- |
| Docker/Compose | Server 29.5.2; selected config resolves after isolated setup. |
| Images | Backend/APISIX/frontend/Python built, cached steps used; application-build.log and intelligence-build.log. Not cache-free proof. |
| Backend build tests | 894 run, zero errors/failures, **328 skipped**; not integrated acceptance or the audit's separate counts. |
| Actual six-service startup | FAIL: Compose exit 1; PostgreSQL exits before initialization, blocking dependent services. compose-start.log. |
| PostgreSQL isolation | Same pinned image/no network: id postgres reports no such user, binary exists; postgres-isolated.log, postgres-image-config.json, postgres.log. |
| Real Chromium | Frontend and Keycloak navigation ERR_CONNECTION_REFUSED; browser-probes.json. No successful login/E2E. |
| HTTP probes | 5173/9080/8081 connection refused; probes.json. |
| Actual Python | Healthy; anonymous 401, authenticated synthetic triage 200 SUCCESS, rules service-desk@0.3.0, OTHER/MEDIUM, needsReview=true; python-http.jsonl. Direct private-service proof only. |
| Service Desk workflow | BLOCKED: lifecycle, DB restart/persistence, live permissions/contracts, Java/Python outage fallback not verified. |
| Room Request reuse | Source/contracts available, actual UI/API demonstration BLOCKED by startup. |

### Reproduced demo-breaking blocker

`postgres:16.15-bookworm@sha256:0ea6700a3b4f0ae6ce746519073558aed4d88a79d8d07622a9a644946c7319c4`
exits 1: `find: 'postgres' is not the name of a known user`. Independent no-volume
check also lacks user. Local image/runtime-filesystem blocker, not proof of upstream
defect. Historical PG16.6 PASS does not establish this baseline works.

Minimal safe recovery: acquire/verify same image on a clean, separately selected
Docker engine/context, check id postgres, rerun unchanged stack with fresh project
volumes. No pin change, adding users, shared-image deletion or backend features.
If independently reproduced, infrastructure owner approves isolated correction/new
SHA and retest. Runnable checks in DEMO_RUNBOOK.md. No backend code fix indicated.

PowerShell 5.1 start.ps1 also terminates on Docker stderr progress as NativeCommandError.
Explicit Compose independently exits 1 for PostgreSQL; not merely logging failure.
Use PowerShell 7/explicit commands; future wrapper fix needs benign-stderr and
nonzero-exit tests. No wrapper changes made here.

## Human handoff before deadline

- READY: isolation, preparation documents, image builds, direct Python triage,
  official-format mapping and rights inventory.
- BLOCKED: rights/root LICENSE, references/fixtures/photo, dependency notices,
  sanitized public source, integrated demo, report/deck, release/public access.
- Rights owner approves authorship/license/data scope or exclusions. Security
  owner performs comprehensive history/privacy scan and rotates genuine exposures.
- Infrastructure/demo owner recovers stack and runs browser/lifecycle/permissions/
  restart/fallback checks. Repository owner approves delivery route, integration/
  default change if any, grant and publication separately.
- Submission owner finishes report/deck, rehearses and verifies public links before
  2026-10-29. Follow PUBLIC_RELEASE_CHECKLIST.md and DEMO_RUNBOOK.md.
- Disclose: settings PERSISTED_NOT_ENFORCED; R7 blocked; production G9 FAIL; no full
  R1-R12 completion/model-quality/100k-user claim. No waiver invented.

## Fill after authorized publication

| Field | Value |
| --- | --- |
| Tested integration SHA | ef047a31abfd8e35ce9b6155e3a06c224262269d |
| Final public SHA | TODO; changed/sanitized source requires new acceptance |
| Version | Proposed v0.1.0, not created |
| Source / tag / Release URLs | TODO / TODO / TODO |
| LICENSE / dependencies / issues URLs | TODO / TODO / TODO |
| Report / slides / optional video | TODO / TODO / optional |
| Rights signoff / demo evidence / reviewer check | TODO names, dates, results |
