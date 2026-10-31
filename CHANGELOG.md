# Changelog

Notable changes use Keep a Changelog categories. v0.1.0 was published on
2026-10-31 at 12:31:12 UTC+07, commit ba82b2fc5eb36ea309563d2d7442db95b96a7608.
The historical v0.1.0 tag/source archives and its evidence are preserved.

## [Unreleased]

### Changed
- Add the post-release PoF evidence index, dependency inventories/production SBOM
  and contribution/issue guidance; synchronize published release status.
- Keep external document/photo permission evidence and the MODERATE fast-uri
  finding explicit; no dependency/code change or new release.

## [1.0.0] - 2026-11-01

Published at 02:22:52 UTC+07 (2026-10-31 19:22:52 UTC).
Tag: `v1.0.0`; source commit: `f845ff3dd0e2e49cb2a94608daaf365777f58877`.
The existing tag and source archives are unchanged.

### Added
- Frontend Markdown/GFM rendering and STOMP client dependencies recorded in the
  released package manifests, alongside merged frontend fixes (PRs #71, #76,
  #77, #80 and #81; release integration PR #82).

### Release evidence (post-release supplement)
- [v1.0.0 evidence and reproduction instructions](docs/publication/v1.0.0/README.md),
  frontend production CycloneDX SBOM, exact input hashes and SHA256SUMS.
- The SBOM covers frontend production dependencies only, not backend, Python,
  container operating-system packages or a vulnerability/security certification.
- Historical v0.1.0 test logs and inventories remain scoped to their recorded
  source commits; they do not certify v1.0.0 runtime behavior.

## [0.1.0] - 2026-10-31

### Added
- Headless Core: Keycloak identity/workspace authorization, PostgreSQL data/files,
  Flowable workflow and transactional audit.
- Reusable H-P-D-I capabilities, Service Desk ticket intake/review/assignment,
  handling, requester confirmation/rework and search/statistics.
- Room Request application and React workspace/application screens.
- Knowledge library, extracted preview, controlled downloads, cited streaming chat
  and browser history from merged develop work.
- Rules/retrieval and optional Qwen/API integration source; no-AI/manual workflow
  is the supported startup baseline for this release.

### Fixed
- Fresh-database migration/engine ordering and current acceptance contract checks.

### Security
- Add the official Apache-2.0 license for authorized team-owned work following
  team approval on 2026-10-31; retain explicit third-party and pending-asset exceptions.
- Update Axios' transitive follow-redirects to 1.16.2 and direct Zod to 4.6.6;
  adapt the process-variable record schema to the patched Zod API.
- Workspace authorization, audit/provenance and document download controls.
- Production dependency audit after these fixes reports no HIGH/CRITICAL findings;
  fast-uri 3.1.8 remains MODERATE and is not waived. This is not full-stack certification.
- Record the team leader's distribution approval for the reviewed fixtures in
  an exact-path, exact-hash release manifest; do not alter payload bytes or
  evaluation/ingestion approval states. Post-release PR #74 records asset-specific
  author permissions and reported HaUI permissions; school written evidence still
  needs archiving. No unreviewed OCR/local work is included.
