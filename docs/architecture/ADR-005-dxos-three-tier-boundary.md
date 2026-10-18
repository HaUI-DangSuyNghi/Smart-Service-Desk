# ADR-005: DX-OS three-tier boundary

Date: 2026-10-18. Status: implementation decision requested by repository owner;
team review remains required before integration into develop.
Baseline: AI-03 closeout `c16598d`. Implementation follows the local
DXOS_3_TIER_REFACTOR_EXTRACTION_PLAN (ARC-00 through ARC-09).

Digital Core (T1) owns technical engines, identity, storage, audit and persistence.
H-P-D-I (T2) composes public Core contracts into reusable Human, Process, Data and
Intelligence capabilities. Applications (T3) own entities, rules, reporting,
BPMN definitions and AI profiles. Dependency direction is T3 -> T2 -> T1.

Core must not depend on either upper tier. H-P-D-I must not depend on apps or
Core internals. Application production code consumes only H-P-D-I public APIs;
it must not query Core or Flowable tables. Only Core workflow internals use
Flowable. Test fixtures may inspect internals to verify atomicity and isolation.

Use a modular monolith with explicit API/internal seams. Keep REST routes and
wire fields stable. Keep historical Flyway V1-V5 byte-identical. Keep process
keys stable when changing resource ownership. Do not rewrite running instances.
New migrations use globally unique versions and explicit ownership locations.

Intelligence runtime identity is `hdpi_intelligence` / `hdpi-intelligence`, as
spelled in the extraction plan. Runtime wheels carry generic contracts, never
application profiles. Profile roots and evaluation inputs are explicit config.
Preserve authorization before scoring, trusted context, provenance checks,
cross-workspace denial and token-free caches/traces.

Readiness: local baseline includes characterization/integration suites and AI-03
hardening closeout. This refactor adds executable boundary and reuse checks.
CI acceptance, team approval, and concurrent PR coordination are external gates;
local execution must not be represented as proof of those external approvals.

Each ARC is committed separately after its relevant checks. Compatibility debt
must have an owner, replacement and removal milestone in BOUNDARY_DEBT.md.
