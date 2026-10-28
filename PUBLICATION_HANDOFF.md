# Publication handoff - 2026-10-28

**HOLD: not authorized for public distribution or final release.**
Deadline: 2026-10-29 (Asia/Saigon; organizer cutoff still to confirm).

## 1. Ready now

- Isolated worktree `D:/Smart-Service-Desk-publication`, branch
  `prep/publication-20261028`, from approved baseline
  `ef047a31abfd8e35ce9b6155e3a06c224262269d`. No original history rewritten.
- Both official contest (7 pages) and provisional committee form (3 pages) read;
  `docs/publication/EVALUATION_READINESS.md` checks B1-B8 first, maps all C1-C11
  (50 PoF + 50 product), and supplies a <=12-slide evidence outline. No earned
  points assigned. Final report/deck not found among tracked baseline files.
- README/CHANGELOG proposal, dependency table/inventory, real commit/ref export,
  issue-access check, release notes/steps and anonymous-review checklist prepared.
- Local source-only candidate `.local/public-source` has fresh EMPTY Git history
  and no remote; `.local/smart-service-desk-v0.1.0-candidate-source.zip` is private
  review material, not a compliant final FOSS release. No commits/tags/pushes made.
  ZIP SHA-256: `1395efc4c93809fad5f4a0ab7f5d131a4d118f4ae4390f99cc937986afea79a9`.

## 2. Exclusions

84 baseline files excluded: 70 fixture/data files, 3 supplied PDF/XLSX references,
1 uncleared landing JPG, 10 internal operational evidence/report files.
Exact paths, baseline hashes and reasons: `docs/publication/source-manifest.csv`.
The additionally supplied committee PDF is never copied. Untracked/runtime files,
secrets, .git, caches, dependencies, build outputs and model payloads never exported.
Candidate-only Hero.tsx uses CSS gradient instead of the removed image. Fixture
evaluations/CI and historical document links remain affected and explicitly noted;
no tests are silently disabled. Original evidence files remain untouched.

## 3. Missing license/notice closure

Root LICENSE grant/text and copyright scope await rights-holder authorization.
Apache-2.0 direction is not permission. Data/docs/copy-pasted source need clearance;
Qwen adapter license undeclared, dataset rights pending; fonts/icons/copied UI,
Python bundled notices and image packages need exact-artifact review. Tokenizers
0.21.0 notice evidence is missing in its dense Windows report; 0.23.3 supplement
does not cover it. Java POM declarations resolved for 156 artifacts, with 162
notice entries inventoried; metadata is not a compatibility/redistribution signoff.
CUDA is optional non-OSI runtime, not part of an all-FOSS Core claim. Preserve
existing upstream notices; add NOTICE only as actually required, not invented.

## 4. Decisions requiring humans

- Copyright holders approve Apache-2.0 scope, documentation/data, attribution and
  university/contributor conditions; no license grant has been added.
- Owner/committee choose history route in `docs/publication/HISTORY_OPTIONS.md`:
  A clear existing full history; B separately filtered genuine history with mapping;
  C snapshot plus provenance only if committee explicitly accepts. Do not default
  to one import commit: B8/C1 require genuine ongoing development.
- Confirm actual contest interval: baseline has 369 ancestors, 2026-10-06 to
  2026-10-28. Repository creation/PR metadata and the announced interval need
  verification, not inference. No backdating, squashing or fabricated progression.
- Complete secret/privacy review. Local scan: 99 refs, 406 commits, 3512 blobs;
  four binaries inventoried, six strong-pattern findings in negative-test inputs,
  136 assignment candidates (also code/placeholders). No confirmed live key from
  reviewed strong patterns; this is NOT clean-history certification. Server refs,
  LFS, attachments, PII and unknown secret formats remain outside full assurance.
- Owner approves destination, Issues configuration, version mapping and final
  tag/release/visibility only after gates close. Anonymous GitHub API returned 404;
  `has_issues` and repository creation metadata could not be verified anonymously.

## 5. Does sanitized source build?

**Yes for compilation/packaging; NOT certified for full test/install/live demo.**
React `tsc -b && vite build`, Java Maven package (tests skipped), Python 3.12.7
Intelligence and integration wheels all pass. Installed Python imports also pass
outside source with `-I`. Initial Python 3.11 mismatch was resolved using a private
3.12.7 toolchain; no project requirement bypass. npm install reports 6 vulnerabilities
(including 2 high): not a security waiver. Exact commands/scope/private log paths:
`docs/publication/BUILD_VALIDATION.md`. No Docker/DB/demo recovery performed.

## 6. Exact next actions before submission

1. Obtain written license, data/asset and publication approvals; add official
   LICENSE only after approval and finalize exceptions/notices.
2. Agree genuine-history route with owner/committee; review/filter only in a
   separately authorized mirror if necessary, preserving original repository.
3. Finish final secret/privacy/notice triage; restore only approved samples;
   regenerate candidate/manifest/archive and repeat source tests/clean install.
4. Obtain current runtime/demo evidence from its responsible agent; finish report
   and <=12-slide deck using evaluation matrix. State remaining limitations.
5. Owner publishes approved refs/source only, enables Issues and checks logged-out
   clone/viewer/history/archive access. Never push private history wholesale.
6. Owner approves final public SHA/version mapping, creates annotated v0.1.0 and
   matching release/source archive + SHA-256 before confirmed October 29 cutoff;
   submit exact repository/release URLs and required report/slides.

Runnable candidate check: `py -3.11 scripts/publication/prepare.py --check`.
Refresh prepared docs/archive only: `py -3.11 scripts/publication/prepare.py --refresh-docs`.
Both commands are local; neither approves rights nor publishes anything.
