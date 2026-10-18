# ADR-005: strict DX-OS boundaries and incremental recovery

Date: 2026-10-18. Status: recovery baseline implemented; integration pending.
Functional baseline: `develop@728cdc3`. Architecture reference:
`refactor/dxos-three-tier-boundary@cdca19f`.

## Decision

Recover architecture intent onto current develop in separate BND-00 through
BND-07 changes. Do not merge or rebase the old architecture branch wholesale.
Preserve current authorization, triage fallback, retrieval provenance, process
identities and public behavior while moving ownership to the proper layer.

| Tier | Owns | Allowed product dependencies |
| --- | --- | --- |
| T1 Core | Identity primitives, isolation, workflow engine, storage, audit, technical persistence | Infrastructure libraries |
| T2 H-P-D-I | Reusable Human, Process, Data and Intelligence capabilities | Core public contracts, through implementations |
| T3 Applications | Business entities, policies, statuses, statistics, BPMN, AI profiles and REST views | H-P-D-I public APIs |
| Assembly | Bootstrap and the complete module graph | All three tiers |

Core imports no upper tier. H-P-D-I APIs are Core-independent. H-P-D-I
implementations import neither app code nor private Core implementations.
Apps import no Core contracts and no private H-P-D-I adapters. Flowable types
belong exclusively to `core.workflow.internal`. T3 does not own Intelligence
HTTP transport, authentication headers or transport schema validation.

Production SQL accesses only its owner's tables. Cross-owner references use
opaque IDs, public capabilities, events or projections rather than foreign keys.
Historical V1-V5 migrations remain byte-identical; fixes use forward migrations.
Process keys and running-instance identity remain stable during resource moves.

Generic Intelligence loads configured application profiles. Applications own
taxonomy, priority and routing policy. Preserve auth-before-scoring and citation
provenance. The Python import package is `hdpi`, per the repository owner's
explicit recovery instruction; the existing wheel distribution identity remains
`smart-service-intelligence` for artifact compatibility.

## BND-00 enforcement

Current develop does not satisfy the strict target. The source scanner and
ArchUnit test enforce an explicit, expiring inventory in
[boundary-debt.json](boundary-debt.json). Exceptions match exact paths, rules
and values; they do not disable an entire file or package. New edges, extra
source occurrences, stale entries, duplicate entries, missing owners and expired
entries fail checks. Zero-debt directions are enforced immediately.

The inventory is migration debt, not approval for new dependencies. Changes
must remove relevant entries as debt is paid. Reviewers must reject additions
which merely hide new architecture drift. See [BOUNDARY_DEBT.md](BOUNDARY_DEBT.md)
for phases, limits and verification commands.

## Scope and consequences

BND-00 changes architecture checks and documentation without runtime refactoring.
The explicitly requested `hdpi` rename is a separate change on the same recovery
branch, including import sites, packaging resources, Docker entrypoint and CI.
Frontend restructuring, RoomRequest, profile extraction and broad layout changes
remain in later phases. Functional CI stays enabled alongside architecture CI.
This baseline does not assert that strict recovery or second-domain reuse is
complete.
