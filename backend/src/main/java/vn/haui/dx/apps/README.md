# Application boundary

Applications own business entities, policy, public representations, persistence,
BPMN contributions and Intelligence profiles. Production code consumes HPDI public
APIs only; no Core imports, engine types, private adapters or cross-owner SQL/FKs.
The architecture gates enforce strict mode without migration exceptions.

Service Desk preserves its existing ticket/progress/triage REST behavior. Room
Request is a minimal H/P/D/I reuse proof, not a product roadmap. Profiles live under
`apps/<application>/intelligence`; generic runtime code lives in `intelligence/src/hdpi`.
See `docs/architecture/ADR-005-dxos-three-tier-boundary.md` and
`docs/architecture/STRICT_BOUNDARY_OPERATIONS.md` at the repository root.
