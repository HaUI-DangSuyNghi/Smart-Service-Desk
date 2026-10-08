# Changelog

## Intelligence foundation layout (unreleased)

- Promote proposed triage/search schemas to shared `contracts/intelligence/v1/`; keep wire contract version `0.1.0`.
- Split Service Desk taxonomy, matching rules, priority policy and normalization under a versioned profile; preserve rule IDs, labels and all 16 priority cells.
- Move synthetic fixtures, architecture/governance/N1 review docs and validation/Core acceptance utilities to repository-owned locations. Preserve exact source bytes and provenance; manifests change paths only.
- Add profile composition and generic-contract boundary validation. Core behavior is unchanged; N1 remains READY FOR BE REVIEW, with no Intelligence runtime or new dependencies.

## 0.1.0 baseline (unreleased)

- Organize backend under `backend/` and `vn.haui.dx.core`, OpenAPI under `contracts/openapi/`, original documents under `docs/references/`; extract secret-free Keycloak template and reserve frontend/Intelligence/app boundaries without adding features.

- Add headless Core assembly, scoped metadata, embedded workflow probes, JWT authorization and transactional audit.
- Add local Identity/Gateway/PostgreSQL infrastructure, contracts, startup/smoke scripts and team plan.
- File I/O, application tiers, production hardening, project license approval and release remain pending.
