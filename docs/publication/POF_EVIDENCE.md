# PoF evidence - Smart Service Desk v0.1.0

Snapshot: 2026-10-31. Six criteria/weights below follow the team leader's supplied
rubric (50 points total); these are evidence-readiness labels, NOT Council marks.
The original Council evaluation sheet has not been independently verified here.
This post-release documentation does not alter the v0.1.0 tag or source archives.

| Criterion | Weight | Status | Evidence / remaining limit |
| --- | ---: | --- | --- |
| Public repository and development history | 5 | ĐẠT | [Repository](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk), [GitHub metadata](https://api.github.com/repos/HaUI-DangSuyNghi/Smart-Service-Desk), [release commit history](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/commits/v0.1.0/), [PRs](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/pulls?q=is%3Apr), [branches](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/branches); three-member examples below. |
| License and compatibility | 10 | CẦN BỔ SUNG | Official [Apache-2.0](../../LICENSE), scoped [LICENSING](../../LICENSING.md), [82-asset rights manifest](release-distribution-status.csv), preserved [third-party licenses](../../licenses/). Four external document/photo permission terms remain unrecorded; publishing a release does not clear them. |
| Release and versioning | 5 | ĐẠT | [Published v0.1.0](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/releases/tag/v0.1.0), [release API](https://api.github.com/repos/HaUI-DangSuyNghi/Smart-Service-Desk/releases/tags/v0.1.0), [ZIP](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/archive/refs/tags/v0.1.0.zip), [tar.gz](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/archive/refs/tags/v0.1.0.tar.gz), [CHANGELOG](../../CHANGELOG.md). No tag movement/new release. |
| Build/install from source | 10 | ĐẠT | [Startup/build/demo](../../README.md), [21-check integrated source](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/commit/7b56e71ea1fe608d7d39de5b7a225abdb4c31de0/checks), [7-check final-tree CI](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/actions/runs/41172037297), [CI procedure](../../.github/workflows/tier-boundaries.yml). Exact source/evidence distinctions below. |
| Dependencies and provenance | 10 | ĐẠT | [Pinned inventories, production SBOM and reproduction](pof/README.md): 602 npm lock entries, 119 production SBOM components, 160 resolved Java artifacts, 20 Python pins and 19 image references. Metadata provenance is not complete image/native-license certification. |
| README, CHANGELOG, Issues, CONTRIBUTING | 10 | ĐẠT | [README](../../README.md), [CHANGELOG](../../CHANGELOG.md), [real issue #16](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/issues/16), [issue tracker](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/issues), [report template](../../.github/ISSUE_TEMPLATE/bug_report.md), [contribution/review guide](../../CONTRIBUTING.md), [architecture](../architecture/tier-boundary-map.md), [Core API](../../contracts/openapi/core.yaml), [Service Desk API](../../contracts/openapi/servicedesk.yaml). |

## Repository and three-member development

GitHub reports public visibility and creation at **2026-10-06T15:06:14Z**.
The frozen release has **401 reachable commits** (including merge commits) and
**68 merged PRs reachable from its tag**: 32 by MinhHieuDev2005, 24 by ManhT005,
12 by HuyHoang3425. Counts are traceability, not human hours or individual scores.
The current branch API snapshot lists 70 branches; that live count may change.

| Member / principal stream | Concrete merged work in the release |
| --- | --- |
| MinhHieuDev2005 / BE and integration | [#8 workflow](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/pull/8), [#11 integration](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/pull/11), [#17 ticket domain](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/pull/17), release reviews/integration #69/#70/#72. |
| HuyHoang3425 / FE and early shared Core | [#29 React base](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/pull/29), [#38 auth](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/pull/38), [#63 ticket workflow UI](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/pull/63); earlier identity/audit work #4/#5. |
| ManhT005 / AI, data and boundaries | [#15 runtime](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/pull/15), [#19 authorized retrieval](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/pull/19), [#35 grounded assistance](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/pull/35). |

Work overlaps roles; PR records show staged implementation, review and integration,
not a claim of manual-only authorship. [Team plan](../core-team-plan.md) is historical.
PR #71 is not reachable from this release; PR #68 artwork is not required.

## Frozen release and tested-source mapping

- Published **2026-10-31T05:31:12Z / 12:31:12 UTC+07**; not draft/prerelease.
- Tag resolves to **ba82b2fc5eb36ea309563d2d7442db95b96a7608**. It is currently a
  lightweight tag (`git cat-file -t refs/tags/v0.1.0` returns `commit`), not annotated;
  preserve it exactly. Both GitHub source archive formats were downloaded/opened.
- Release tree equals final tested **0835c86375c1b2056bb59018294eeebc4ac2a16c**:
  7/7 checks PASS, including clean locked frontend install/types/lint/unit/build,
  Chromium contracts and real isolated three-tier integration.
- Integrated **7b56e71ea1fe608d7d39de5b7a225abdb4c31de0** passed 21/21. Changes
  from that tree to the release are documentation/approval metadata only; runtime,
  build manifests, lockfiles and fixture/evaluation bytes are unchanged.
- This is equivalent-source evidence, NOT a claim that release main itself ran
  all 21 checks. No full suite or Docker stack was rerun for this docs-only PR.
  Existing local no-AI smoke used an isolated PostgreSQL override/cached same-source
  artifact, so it is not claimed as an unmodified default-image clean build.
- Manual/no-AI is the supported demo. OCR, GPU/Qwen and unaccepted cloud modes,
  full image-security scanning and universal production certification are excluded.
  One MODERATE **fast-uri 3.1.8** remains; no security waiver or clean-audit claim.

## Small remaining owner action

Record owner/provenance, redistribution permission and attribution conditions for
the three files under `docs/references/` and `frontend/public/landing/banner-phong-may.jpg`
listed individually in [LICENSING](../../LICENSING.md). A team ownership assertion
must identify which file it covers; external ownership must not be relabeled Apache.
Review issue #16's acceptance links and close it only if its existing work is verified.
Have another member approve/merge this documentation PR; do not move the released tag.
