# Evaluation Readiness - 2026-10-28

Deadline supplied by team: 2026-10-29, Asia/Saigon. No earned scores assigned.
Read all 7 pages of the official HaUI contest PDF and all 3 pages of the supplied
`Phieu_du_kien_danh_gia_Hoi_dong_PMNM_HaUI_2026.pdf`. Official contest rules take
precedence over the provisional form; national rules prevail for national entry.
PDFs remain private and are not copied into the publication candidate.

Source fingerprints (SHA-256): official contest PDF
`ebb7578a2b0cd3dc4a1dab0e146bd04cf1c0a461074b0e2b9f668af10928f016`;
committee form read at the explicitly supplied original path:
`40c772dd32c2e2ab303c909b65aa09c15598fceff3dda5974223c1c573e368b4`.
The committee form is outside the approved baseline and was read only, not copied
or staged. Website authenticity/updates and announcement dates remain unverified.

## B - eight conditions, checked BEFORE product evaluation

| B | Condition | Evidence available | Missing / action | Readiness |
| --- | --- | --- | --- | --- |
| 1 | Public repository, Internet web viewer | Owner states existing GitHub repository private; anonymous API returns 404 | Owner-authorized publication route, logged-out clone/web verification | BLOCKED |
| 2 | Suitable OSI-approved license, full LICENSE and notices | LICENSING.md and docs/legal/redistribution-checklist.md record Apache-2.0 direction only | Copyright-holder approval, exact scope, official LICENSE text, notice closure | BLOCKED |
| 3 | Versioned release/tag before deadline, matching source/open archive | Proposed v0.1.0 plan and local source ZIP | Final commit/tag/archive consistency and owner approval to tag/publish before cutoff | PENDING |
| 4 | Reproducible source build/install/run with open tooling | README, Maven/Node/Python manifests, Dockerfiles and source | Clean candidate build; configure before build; run outside source directory; independent demo agent supplies runtime evidence | PARTIAL |
| 5 | Dependencies, versions, origins/licenses; no unauthorized library edits | docs/legal/dependency-inventory.md, Python resolution manifests, npm lock, Java resolution report, generated inventory | Complete transitive/plugin/image review; UNKNOWN licenses and bundled notices; assess copied snippets | PARTIAL |
| 6 | Truthful README/install, CHANGELOG, issue tracker | README.md, CHANGELOG.md, CONTRIBUTING.md, docs/core-architecture.md, contracts | Verify remote Issues enabled and externally readable; align final docs with sanitized contents | PARTIAL |
| 7 | No secrets/sensitive data in files OR history | Read-only 99-ref/406-commit/3512-blob local scan; reports identify candidates without values | Review all findings/PII and nonlocal refs; restricted historical assets unresolved; no clean-history certification | REVIEW REQUIRED |
| 8 | Genuine ongoing Git development in announced contest interval | 369 baseline ancestors; actual author/committer dates, branch refs and merge/PR subjects exported | Organizer confirms contest interval; PR/repository creation metadata; approved history-preserving publication | EVIDENCE AVAILABLE, ACCEPTANCE PENDING |

B8: baseline history runs from 2026-10-06 to 2026-10-28; 14-day contest period
must be verified against the actual announcement, not inferred from Git dates.
A late commit alone is not proof of fraud. A single new snapshot is NOT sufficient
development-history evidence. No commits/timestamps are manufactured or squashed.

## C - all eleven criteria (maximum points only)

| C | Criterion | Maximum | Existing repository evidence (not fresh demo certification) | Missing / required action |
| --- | --- | ---: | --- | --- |
| 1 | Public source and real development history | 5 | development-history.csv, refs-snapshot.txt, actual merge PR #62 at ef047a3; originals remain private | Approve safe history route; verify creation date/PR links and contest interval; logged-out viewer and clone |
| 2 | Open license and compatibility | 10 | LICENSING.md, license checklist, licenses/models, runtime-supplements | Authorized root LICENSE; rights exceptions; dependency compatibility and required notices |
| 3 | Release/version management | 5 | CHANGELOG proposed entry, RELEASE_PLAN.md, source manifest and local ZIP | Authorized public commit and annotated tag/release before deadline; final checksums |
| 4 | Build/install from source | 10 | backend/pom.xml, frontend/package-lock.json, intelligence/pyproject.toml and lockfiles, Dockerfiles | See BUILD_VALIDATION.md for exact attempted results; full clean source install/run outside checkout still required; no Docker recovery performed |
| 5 | Dependency/provenance management | 10 | dependency-inventory.csv; ai02/ai03 resolution JSON; ai05 inventory; Java resolved report in private baseline | Resolve UNKNOWNs; actual notices per artifact; Linux/Windows/GPU closures separate; no unnecessary vendor payload |
| 6 | Docs, tracker and communication | 10 | README, CHANGELOG, CONTRIBUTING, contracts/OpenAPI, architecture docs | Remote Issues verification; final source-only instructions; report/slides must accurately state current limits |
| 7 | Three-tier Core headless and H-P-D-I | 10 | docs/core-architecture.md; docs/architecture/tier-boundary-map.md; backend core/hpdi/apps; compose Keycloak/APISIX/PostgreSQL; Flowable BPMN; Service Desk and Room Request | Present live calls from both apps through reusable capabilities/Core, identity/authz, gateway, workflow, structured data and files; boundary map includes historical targets, not blanket completion proof |
| 8 | Completeness and stable real workflow | 10 | docs/FULLSTACK_INTEGRATION_ACCEPTANCE.md reports historical PostgreSQL 16.6 API success; docs/BACKEND_RUNTIME_ACCEPTANCE.md records prior blockers; source acceptance scripts | Other agent supplies current exact-SHA demo result, reproducible workflow, error path and approved sample data; historical reports do not establish current health |
| 9 | Usability and practical value | 10 | frontend/src/apps/servicedesk and platform; role-aware UI and real support-request workflow source | Live requester/agent/coordinator flow, benefit versus manual process, permission-denied path, user-focused screenshots approved for publication |
| 10 | Technical quality, extensibility and sustainability | 10 | contracts, modular source/tests, deployment configs, security reports and CONTRIBUTING | Current test/build results at final SHA; disclose open security gates and data exclusions; explain reuse/replacement boundaries; do not claim production readiness |
| 11 | Presentation, explanation and community potential | 10 | Architecture/API/governance docs; contribution guide; release preparation | Final <=12-slide deck, timed 10-minute demo + <=5-minute Q&A; explain model/version/license/input/fallback/limits, real history, contribution path and truthful remaining gates |

Maximum totals: C1-C6 = 50 PoF; C7-C11 = 50 product; total = 100.
Statuses describe ability to supply evidence, not expected or achieved points.

## Submission packet and slide/evidence mapping

No final slide deck or submission technical report was located among tracked
baseline files; PDF/XLSX references are not submission deliverables. Do not claim
the report/deck has been checked. Use this immediate outline (<=12 slides):

| Slide | Content and evidence to attach | Criteria |
| --- | --- | --- |
| 1 | Team, actual problem, public repository/release links after approval | C1, C3, C9, C11 |
| 2 | Three tiers and real source path map; Core is headless | C7, C10 |
| 3 | Identity/SSO, gateway request, workflow task, data/file audit trail | C7, C8, C10 |
| 4 | Service Desk requester -> agent -> confirmation with actual IDs | C8, C9 |
| 5 | Room Request reuse of unchanged Core/H-P-D-I | C7, C10 |
| 6 | Intelligence model/tool/version/license/input, explanation and deterministic fallback; disclose optional GPU/cloud restrictions | C2, C5, C7, C8 |
| 7 | Genuine dated commit/merge/PR progression, repository creation and agreed contest interval | C1, B8 |
| 8 | Authorized LICENSE scope, dependency table, data/model exclusions/notices | C2, C5 |
| 9 | Exact public tag -> clean source build/configure -> run outside checkout | C3, C4 |
| 10 | Current test evidence, permission/error path; distinguish historical reports | C8, C10 |
| 11 | Limits, remaining security/license/data gates; no unsupported claims | C6, C10, C11 |
| 12 | Issue tracker, contribution route, release download and next steps | C1, C3, C6, C11 |

Technical report: recommended 8-10 pages per official contest, focused on Core,
H-P-D-I, app workflow, data, AI/limits, licensing and evaluation; not page-padding.
Demo video 3-5 minutes is recommended, not a substitute for source/history.
Every attached runtime result must identify source SHA, date and actual scope.
Publication audit does not recreate or modify the other agent's demo evidence.
