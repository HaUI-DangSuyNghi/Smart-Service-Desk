# Changelog

Notable changes use Keep a Changelog categories. No product release has been
published; component versions are not separate product releases.

## [Unreleased]

### Changed
- Prepare minimal public-repository release and safe clean-install documentation.

## [0.1.0] - Unreleased

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
- License/notices and asset rights remain release gates.
  No unreviewed OCR/local work is included.
