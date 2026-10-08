# Core / Intelligence boundary

Status: **PROPOSED**. This records the existing
[capability contract](../../../contracts/intelligence/v1/contract.md) and
[data/provenance draft](data-provenance-contract.md); it does not ratify or change Core behavior.

| Concern | Authority |
| --- | --- |
| Identity, workspace and access decisions | Core / authorized Application adapter |
| Canonical metadata, provenance and files | Core; File I/O and server checksum verification remain backlog |
| Workflow state and audit | Core / Application through supported APIs |
| Final classification, priority and override | Human / Application / Process Owner |
| Taxonomy, signals and derived normalization | Intelligence domain profile |
| Chunks, index/cache, ranking and evaluation | Future Intelligence derived state |
| Capability schemas | Shared repository contracts |
| Synthetic tickets, local knowledge and manual expected retrieval | Repository fixtures; no production truth or Core bindings |

No request field grants access. The Application adapter derives trusted workspace
and profile context after Core authorization. Unauthorized requests stop before
corpus/cache/engine access. `DEGRADED` represents operational unavailability and
manual fallback; it does not bypass authorization.

Triage v0.1 carries exact original INPUT substrings only. Search citations use an
authorized workspace, real Core metadata UUID, source version and exact checksum.
Local fixture IDs are not citations, and UUID shape does not prove issuance/access.
Recheck authorization when resolving or displaying a citation.

Intelligence never writes Core tables or treats a local fixture directory as the
production corpus. The SQL utility under `scripts/acceptance/core/` is a read-only
acceptance probe, outside the runtime boundary. Future derived state is rebuilt
from authorized Core data and versioned profile/engine configuration.

Removing Intelligence leaves Core usable. Clearing derived indexes leaves source
records and files intact. Changing a retrieval/classification engine preserves
Core ownership and compatible capability envelopes. Adding a domain adds a profile
and fixtures; domain categories/groups do not enter generic Core metadata.

The existing [Core acceptance evidence](../../reviews/intelligence/N1/ACCEPTANCE_REPORT.md)
is historical. Role policy, response mapping, adapter/citation checks and Core
regression evidence remain subject to the [BE review gate](../../reviews/intelligence/N1/BE_REVIEW_BRIEF.md).
