# Changelog

## 0.1.0 baseline (unreleased)

- Organize backend under `backend/` and `vn.haui.dx.core`, OpenAPI under `contracts/openapi/`, original documents under `docs/references/`; extract secret-free Keycloak template and reserve frontend/Intelligence/app boundaries without adding features.

- Add module contracts (`core.common` and `identity`/`workflow`/`data`/`audit` `api` packages) so modules can be built independently; `CoreException` renders Problem Details with `code` and `requestId`. Interfaces only; existing endpoints unchanged. Add shared `Problem`/`PageMeta` OpenAPI components, unimplemented target spec `contracts/openapi/core-target.yaml` and REST conventions in `contracts/README.md`.
- Add headless Core assembly, scoped metadata, embedded workflow probes, JWT authorization and transactional audit.
- Add local Identity/Gateway/PostgreSQL infrastructure, contracts, startup/smoke scripts and team plan.
- File I/O, application tiers, production hardening, project license approval and release remain pending.
