# ADR-005: strict DX-OS boundaries and incremental recovery

Date: 2026-10-19. Status: strict recovery implemented locally; integration pending.
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

The original develop baseline did not satisfy the strict target. The source scanner
and ArchUnit test recorded and reduced an exact, expiring inventory in
[boundary-debt.json](boundary-debt.json). Exceptions match exact paths, rules
and values; they do not disable an entire file or package. New edges, extra
source occurrences, stale entries, duplicate entries, missing owners and expired
entries fail checks. Zero-debt directions are enforced immediately.

All inventory entries are now closed and strict mode permits no migration exceptions.
The original inventory was migration debt, not approval for new dependencies. Changes
must remove relevant entries as debt is paid. Reviewers must reject additions
which merely hide new architecture drift. See [BOUNDARY_DEBT.md](BOUNDARY_DEBT.md)
for phases, limits and verification commands.

## Scope and consequences

BND-00 established architecture checks without runtime refactoring; BND-01 through
BND-07 now implement workflow data isolation, public HPDI contracts, external profiles,
Human/Data alignment, assembly separation and second-domain reuse.
The explicitly requested `hdpi` rename is a separate change on the same recovery
branch, including import sites, packaging resources, Docker entrypoint and CI.
Frontend restructuring remains outside this recovery. Room Request is a minimal
reuse proof; its profiles/resources and semantics belong to that application. Functional CI stays enabled alongside architecture CI.
Local evidence is recorded in REFACTOR_VALIDATION.md; remote integration approval
and branch-protection configuration remain external.
