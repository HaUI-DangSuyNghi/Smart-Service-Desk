# Changelog

Notable changes use Keep a Changelog categories. v0.1.0 was published on
2026-10-31 at 12:31:12 UTC+07, commit ba82b2fc5eb36ea309563d2d7442db95b96a7608.
The released tag/source archives are preserved; subsequent documentation is Unreleased.

## [Unreleased]

### Changed
- Add the post-release PoF evidence index, dependency inventories/production SBOM
  and contribution/issue guidance; synchronize published release status.
- Keep external document/photo permission evidence and the MODERATE fast-uri
  finding explicit; no dependency/code change or new release.

## [2.0.0] - Unreleased Candidate

### Fixed
- Harden browser authentication/session handling and live authorization checks.
- Support existing Keycloak 26.0.8 deployments with the explicit backend setting
  `DX_DIRECTORY_REQUEST_ADMIN_SCOPE=false`; retain scoped requests by default.

### Changed
- Run pull-request acceptance and boundary checks for main as well as develop.
- Prepare release/2.0.0 from develop commit 8dbf7d6. The VM candidate uses backend
  image de73126e1cf7 and frontend image e7c72d786ebe; image names are not release tags.

### Release Gates
- This candidate is not published. Create v2.0.0 only after release review/merge.
- HIGH/CRITICAL runtime dependency findings documented in PRODUCTION_HARDENING.md
  remain unresolved; functional CI and reported VM smoke success do not clear them.
- Preserve VM backups and rollback images; do not upgrade the existing identity
  database or recreate its realm as part of this application-only release.

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
