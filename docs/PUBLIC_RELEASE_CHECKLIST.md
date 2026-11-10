# Public release and rights checklist

2026-10-28. **HOLD: private, no root LICENSE, no publication approval.**
Baseline: `ef047a31abfd8e35ce9b6155e3a06c224262269d`.
Approvals needed, not legal clearance or a present license grant.

## Publication disposition

| Assets reviewed | Handling / rights gate |
| --- | --- |
| Original backend, frontend/src, intelligence; team-authored infra/config/scripts/.github/contracts/build files | Candidate Apache-2.0 after contributor/copyright-holder and university/employer rights confirmation; copied third-party portions excepted. Not ownership certification. |
| Team-authored README/CHANGELOG/CONTRIBUTING/technical docs | Same conditional grant; review authorship, copied text and sensitive evidence. |
| All three PDF/XLSX under docs/references/ | Exclude unless actual rights holders authorize public redistribution. Receipt/official availability does not permit relicensing. Cite title/section or authorized public location. |
| Knowledge/tickets/generation/retrieval/AI-assisted fixture payloads and derivatives | Quarantine pending clearance; knowledge/tickets PENDING_OWNER_APPROVAL, generation rightsDecision PENDING. Review all other fixtures individually. Retain approved content-free schemas where separable. |
| frontend/public/landing/banner-phong-may.jpg | Used by landing Hero; no rights/attribution record located. Obtain source/creator approval, otherwise exclude and arrange approved replacement/reference removal before retesting. |
| Models/adapters/private research data/indexes/generated corpora/provider content | Exclude until individually approved. No tracked weight suffixes found in screening, not complete rights proof. Local/ignored assets never become implicit release attachments. |
| licenses/models/qwen35-08b/ | Preserve upstream text/provenance as applicable; base declares Apache-2.0, adapter license undeclared, dataset pending. Base license is not adapter/data approval. |
| licenses/runtime-supplements/ | Preserve applicable tokenizers text/provenance. CUDA EULA vendor-specific, not Apache; copied terms do not clear binary redistribution. No GPU image release without review. |
| Dependencies/images/fonts/icons | Own licenses; audit exact transitive/runtime notices and source obligations. Team grant does not relicense them. |
| .env/generated realms/.local/DB/files/logs/tokens/private captures | Exclude; redact approved evidence for credentials/personal data. |

Source-only delivery avoids bundled image/model rights issues, not restrictions on
tracked source/data. Exclusions cannot silently break builds: approved synthetic
replacements or explicit limitations and final public-source retesting required.

## History and safe delivery

- Local reachable scope: 405 commits; 9,096 objects, 3,501 blobs from
  git rev-list --objects --all. Three reference PDF/XLSX paths occur in history,
  commit `4c995ded475be3c6e952576385fd0f21f8d37aff` (2026-10-07).
  Uncleared assets, not an adjudicated infringement finding.
- Filename screening: no tracked actual .env/private-key/model/archive suffixes
  beyond example env/reference files; no blob >5 MB. Does not clear payloads in
  other formats, personal data or pending fixtures.
- Full-blob limited signatures: private-key headers, GitHub/AWS/Google tokens.
  Six matches in three blob versions inspected: synthetic header/token strings in
  two quality-test files, not complete keys or confirmed real credentials.
  .local/submission/history-signatures.json records paths/OIDs, not secret values.
  Comprehensive password/entropy/PII, server refs/unreachable objects NOT cleared.
- A deletion commit/.gitignore leaves earlier blobs reachable in history/tags/
  clones. Making this repo public exposes uncleared past files: deleting HEAD is
  insufficient. Rewriting cannot retract copies already distributed.
- Preferred after rights approval: **new private staging repository, fresh
  history**, allowlisted sanitized export from approved baseline. Never copy .git,
  local files, unchecked ZIP or full git bundle. Include approved source/fixtures/
  licenses/notices/build files; record baseline SHA and export/exclusion manifest
  without restricted content. No export/publication performed here.
- Preserve original private history for authorized provenance review; disclose
  sanitized-history limitation to organizers (rubric values history). Real review/
  preparation commits only, no fabricated historical development.
- Alternative needs explicit coordinated history-rewrite approval, all refs/tags,
  GitHub cache/fork/PR handling and rotation for genuine secrets. Not authorized.

## Rights, notices and source gates

- [ ] Rights owner signs exact authorship/license scope, contributor consents,
  university/employer conditions, fixture rights, copyright names and exclusions.
- [ ] Only after approval: official unmodified Apache-2.0 root LICENSE, accurate
  LICENSING scope/exceptions and README metadata; no premature license badge.
- [ ] Preserve required upstream notices; prepare artifact-specific attributions.
  NOTICE not universally mandatory: include where applicable, no invented names.
  No root NOTICE/full release-wide bundle established here.
- [ ] Refresh docs/legal/dependency-inventory.md (older tables marked historical). Current
  Boot 3.3.13/Keycloak 26.0.8/PostgreSQL 16.15 and frontend/Python locks prevail.
  docs/security/release-readiness-20261026-dependencies.txt and Intelligence
  governance inventories are inputs, not complete legal clearance.
- [ ] Inventory Java/npm/Python closure, fonts/icons, image OS/runtime packages;
  attach exact-artifact SBOM/license/notices and required source obligations.
  Existing model/supplement notices are not an all-stack inventory.
- [ ] Comprehensive history/archive/attachment secrets/privacy/rights scan;
  investigate matches and rotate genuine exposed credentials.
- [ ] Clean sanitized-source build verifies contexts/contracts/profiles/locks.
  Backend image tests skip cases; frontend image Vite build bypasses typecheck.
  Run supported typecheck/tests plus actual browser/API/restart/fallback acceptance.
- [ ] Review docs/BACKEND_RELEASE_READINESS.md security findings; retain localhost
  nonproduction scope, no G9 PASS claim or invented waiver.

## Branch/version/publication gates

Observed remote HEAD main: `11773eced0b07ddee6230038f905e5cae56c5250`;
local origin/develop = approved SHA; main is ancestor. No local/remote tags.
Anonymous repository API 404; releases not anonymously enumerable while private.
No-release fact supplied by team, not independently certified here.

- [ ] Prefer sanitized staging while history uncleared. Original private/default
  unchanged; old main is not approved integrated demo.
- [ ] Existing-repo route requires all exposed history cleared first; owner chooses
  protected PR into main or authorized default change. Neither cleans history.
  No later develop changes without new acceptance.
- [ ] Proposed v0.1.0 aligns with app versions; Python 0.3.0 separate component.
  Owner approves version; recheck remote collision. No tag/release created here.
- [ ] Record final sanitized SHA, diff from ef047a3, export manifest/checksums,
  rerun exact-source acceptance. Tag final reviewed commit, not arbitrary branch tip.
- [ ] Separate explicit approvals: license, integration/default, visibility,
  tag/push, final GitHub Release. Preparation authorizes none.

### Prepared Release body (not published)

```text
Title: Smart Service Desk v0.1.0 - HaUI 2026 submission
Public source SHA: TODO
Integration provenance: ef047a31abfd8e35ce9b6155e3a06c224262269d
Source/tag/Release links: TODO
Scope: reusable Core, H-P-D-I, Service Desk; Room Request evidence TODO.
Run: README + docs/DEMO_RUNBOOK.md; generated local passwords only.
Evidence: exact-source build/lifecycle/browser/permissions/restart/fallback TODO.
License: TODO approved scope, root LICENSE and dependency/notice inventory.
Exclusions: export manifest; no unapproved reference/fixture/model/data/photo,
            secrets, local volumes or runtime dumps.
Limits: settings persisted-not-enforced; R7 blocked; production G9 FAIL;
        disclose unresolved demo failures, never call unverified demo ready.
Report/slides/optional video/issues: TODO.
Assets: reviewed source archive/checksums; no uncleared images/models/data.
```

## Anonymous reviewer checks after authorized publication

- [ ] Signed-out browser opens source/README/LICENSE/dependency table/tag/Release/
  tracker/report/slides; no private attachment links.
- [ ] Credential-free clone checks out exact tag; SHA/checksums match archive;
  clean build/run using only documented approved prerequisites.
- [ ] Issues enabled/readable, correct license detection, anonymous downloads.
- [ ] No team-machine path/private package/model permissions/cloud keys required;
  generated demo credentials/approved data sufficient.
- [ ] Record reviewer/date/results and final URLs in SUBMISSION_READINESS;
  tagged source equals delivered artifact before submission.
