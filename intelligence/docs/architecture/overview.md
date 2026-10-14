# Intelligence architecture overview

Status: **PROPOSED — READY FOR BE REVIEW**. N1 contains design artifacts and local
fixtures; no Intelligence runtime has been implemented or adopted.

Core remains authoritative for identity, authorization, workspace, metadata,
canonical source files, workflow and audit. Intelligence owns domain profiles and,
when implemented, rebuildable derived matching/index state. Applications own final
business decisions and can use Core without Intelligence.

Shared [triage/search contracts](../../../contracts/intelligence/v1/contract.md)
live at repository level for all consumers. The `v1` directory is a capability
family; its current proposed wire contract version remains `0.1.0`. Engine changes
do not require Core changes when capability/evidence semantics remain compatible.

The [Service Desk profile](../../profiles/service-desk/profile.yaml)
pins taxonomy, rules, business priority policy and normalization files/versions.
Taxonomy defines categories/exclusions/routing; rules define signals and rule IDs;
priority policy defines impact/urgency, the 16-cell matrix and review fallback.
The Process Owner retains priority authority. See the [policy rationale](priority-policy.md).

Repository [fixtures](../../../fixtures/intelligence/service-desk/knowledge/manifest.json)
are synthetic/test seeds with pending rights and no Core bindings. In development
they feed the local [validator](../../../scripts/intelligence/validate-foundation.py).
Approved integration/demo sources enter through Core File/Metadata APIs when those
capabilities exist; an authorized Core interface then feeds derived Intelligence
state. Reading a fixture directory as a production corpus is outside this design.
The knowledge fixture's policy table preserves historical source bytes; it is not
another policy authority.

Future corpus/index partitions include `workspaceId`, `profileId` and
`corpusVersion`, with authorization/visibility scope reflected in cache keys.
Authorize before accessing/scoring corpus data and recheck citations on resolution.
Deleting an index cannot delete canonical Core data. Runtime implementation and
partition enforcement remain future work.

Additional domains such as Room Request add their own profile and fixtures.
Generic contracts and Core remain reusable. Rules and TF-IDF are proposed MVP
candidates, replaceable behind capability interfaces; no engine is adopted here.
See [Core boundary](core-intelligence-boundary.md), [data/provenance](data-provenance-contract.md),
[decisions](../decisions/ADR-001-profile-driven-intelligence.md) and
[N1 review brief](../task/N1/BE_REVIEW_BRIEF.md).
