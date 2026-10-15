# Changelog

## Core acceptance (unreleased)

- Add isolated PostgreSQL/E2E/security, restart, logical backup/restore and read-only blob inventory checks, plus a second application's headless reuse probe.
- Add PR functional CI and digest-pinned image/dependency scanning; functional success is not full Core or production approval. See `docs/core-acceptance.md` for evidence and unresolved gates.
- Document metadata creation's file-link error responses and make the PostgreSQL test mount portable to Linux CI.
- Supersede historical realm-reset advice: migrate through the Admin API/console or use new isolated test volumes; never reset shared volumes to refresh imports.

## Intelligence documentation location (unreleased)

- Keep Intelligence architecture, decisions, governance and N1 reports under `intelligence/docs/`, with N1 evidence in `task/N1/`; update repository links and LF attributes.

## Intelligence foundation layout (unreleased)

- Promote proposed triage/search schemas to shared `contracts/intelligence/v1/`; keep wire contract version `0.1.0`.
- Split Service Desk taxonomy, matching rules, priority policy and normalization under a versioned profile; preserve rule IDs, labels and all 16 priority cells.
- Move synthetic fixtures, architecture/governance/N1 review docs and validation/Core acceptance utilities to repository-owned locations. Preserve exact source bytes and provenance; manifests change paths only.
- Add profile composition and generic-contract boundary validation. Core behavior is unchanged; N1 remains READY FOR BE REVIEW, with no Intelligence runtime or new dependencies.

## 0.1.0 baseline (unreleased)

- Organize backend under `backend/` and `vn.haui.dx.core`, OpenAPI under `contracts/openapi/`, original documents under `docs/references/`; extract secret-free Keycloak template and reserve frontend/Intelligence/app boundaries without adding features.

- Audit: `AuditService` implementation (actor and request ID taken from the current request, mandatory caller transaction), migration `V2__audit_details.sql` adds `resource_type` and JSON `details`, `GET /workspaces/{workspace}/audit` for coordinators and `platform-admin`. `RequestIdFilter` keeps a valid UUID `X-Request-ID` from client/gateway and adds it to log MDC. Metadata and workflow write audit through `AuditService`; legacy `Audit`/`RequestIds` removed.
- Replace flat workspace groups and `core-reader/writer/reviewer` realm roles with per-workspace roles from Keycloak groups `/workspaces/{workspace}/{role}` (defaults `requester`, `agent`, `coordinator`; extensible without code changes) and realm role `platform-admin`. Demo users are now `user1`–`user7`; add `dx-web` PKCE client. Implement `WorkspaceAccess`; `/me` returns memberships; 401/403 return Problem Details. Metadata create requires `agent`/`coordinator`. Requires `docker compose down -v` to re-import the realm.
- Add module contracts (`core.common` and `identity`/`workflow`/`data`/`audit` `api` packages) so modules can be built independently; `CoreException` renders Problem Details with `code` and `requestId`. Interfaces only; existing endpoints unchanged. Add shared `Problem`/`PageMeta` OpenAPI components, unimplemented target spec `contracts/openapi/core-target.yaml` and REST conventions in `contracts/README.md`.
- Add headless Core assembly, scoped metadata, embedded workflow probes, JWT authorization and transactional audit.
- Add local Identity/Gateway/PostgreSQL infrastructure, contracts, startup/smoke scripts and team plan.
- File I/O, application tiers, production hardening, project license approval and release remain pending.
