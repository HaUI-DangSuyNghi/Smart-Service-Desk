# Intelligence capability contract v0.1

Status: **PROPOSED**, team review pending. Contract version `0.1.0`.
This is a design contract, not an implemented endpoint or runtime.
All Intelligence JSON/YAML contract and fixture field names use `camelCase`, aligned with the Core REST convention.

## Boundary and replaceable engines

Core owns identity, authorization, workspace, metadata/provenance, canonical files,
workflow state and audit. Intelligence owns derived normalization, chunks,
rules/model execution, indexes/cache and evaluation. Human/Application/Workflow
owns final business decisions; suggestions never grant access, assign/close tickets
or complete workflow tasks automatically.

Logical interfaces: `triage(input, authorizedContext, profile)` and
`search(query, authorizedContext, profile)`. Their schemas stay independent of
engine implementation. Rules and TF-IDF are future MVP candidates; BM25,
classifier, embedding, hybrid or LLM may replace them behind the same capability.
No engine is installed or implemented in N1.

Generic runtime handles contracts, authorized context and engine selection.
The versioned `service-desk` profile carries category/routing, priority and conflict
configuration. The Service Desk / Process Owner owns business priority/SLA policy;
Intelligence implements and explains the approved policy. A future room/equipment
profile can use different category names without
changing Core or generic result schema. Routing labels are suggestions, not Core
roles, workspace grants or confirmed group identities.

## Triage request and result

The [logical request schema](triage-request.schema.json) has `contractVersion`,
`requestId`, `profile`, server-injected `context.workspaceId`, and untrusted
`input` containing nonblank `title` and `description`. This is an internal adapter
envelope, not a public client request body. The adapter constructs context after
Core/Application authorization; it must never copy a client's context as trusted.
No user-supplied `owner`, credential or permission grant is accepted.

### Trusted authorization context

* **AUTH-INV-01:** No value in an Intelligence request grants authorization.
* **AUTH-INV-02:** `context.workspaceId` is meaningful only after Core/Application
  authorization succeeds. The adapter derives/checks the workspace and profile.
* **AUTH-INV-03:** Unauthorized workspace/profile requests are rejected before
  corpus access, cache lookup or engine execution. Intelligence does not grant
  access independently.
* **AUTH-INV-04:** `DEGRADED` is operational fallback, not authorization fallback.

The schemas validate envelope shape; they cannot attest that authorization ran.
These invariants require adapter/security tests at the implementation gate.

The [result schema](triage-result.schema.json) defines a generic response envelope:

* `SUCCESS`: result with category/priority/suggested group; literal input evidence;
  explanation with matched rule IDs, impact/urgency and reason; engine/profile/rule
  or model versions; explicit `needsReview`.
* `DEGRADED`: null result, empty evidence, null explanation/runtime,
  `needsReview=true`, and error code `INTELLIGENCE_UNAVAILABLE`. The application
  continues with manual triage; it must not fabricate an AI recommendation.

Every result echoes `contractVersion`, `requestId`, `profile`, and the trusted
`context.workspaceId` as top-level `workspaceId`.
Category values are profile-defined rather than hard-coded in the generic schema.
For this package, fixtures use [taxonomy](../../../intelligence/src/apps/servicedesk/intelligence/profiles/service-desk/taxonomy.yaml) and
[priority policy](../../../intelligence/docs/architecture/priority-policy.md). `SUCCESS` means suggestion produced,
not a confirmed business decision. Ambiguous input can be SUCCESS with review.

Contract v0.1 intentionally restricts triage evidence to `INPUT`. Evidence identifies
`title` or `description` and copies an exact substring from the original input,
never normalized text or a fabricated phrase. Knowledge/provenance evidence belongs
to `search` and cannot be inserted into the v0.1 triage response. The application
may compose both results at the presentation layer. A unified evidence envelope
requires a reviewed contract revision. Triage can therefore operate independently
of a corpus/index outage.
The rule engine reports matched rules, not invented statistical confidence.
Future model adapters keep evidence/review semantics and explanatory text, use
`modelVersion` and `matchedRules=[]` when no rules execute, instead of reporting
rules they did not execute. A rules/model combination may carry both versions;
nonempty matched rules require `rulesVersion`. These variants fit the same schema
without changing Core or requiring an invented rules version for a model.

N1 ticket outputs are **manually annotated expected suggestions**, not actual
engine outputs, accuracy measurements or calibrated probabilities. Some impact
and urgency annotations express intended scenario assumptions; their uncertainty
must be resolved by humans on real tickets. Examples cover fallback and conflicts.

## Retrieval and provenance

The [search request schema](search-request.schema.json) requires nonblank `query`,
request/contract/profile identifiers, trusted server-injected `context.workspaceId`
and integer `topK` between 1 and 10. There is no schema default or user-granted
filter/role/JWT field. The adapter authorizes before constructing this envelope.
Filter the accessible corpus **before scoring**;
never search a global corpus then expose/filter its hits after scoring. Any cache
must scope workspace, visibility/auth scope identity where needed, profile version,
corpus version, engine/config version and query/input fingerprint. Never cache
globally and filter afterwards.
Recheck current authorization when resolving/displaying evidence or citations.

The [search result schema](search-result.schema.json) has `status`, correlation identifiers, `results`,
`needsReview`, `runtime`, and `error`. SUCCESS has a result list (possibly empty),
engine/engine-version/profile/corpus/index versions and null error. DEGRADED has empty results,
null runtime, review true and `INTELLIGENCE_UNAVAILABLE`. Auth denial is an adapter
error with no results, not DEGRADED. Response correlation fields must match the
request, envelope/hit workspaces must match trusted context and hit count must not
exceed `topK`. Cross-envelope relationships need adapter checks, since standalone
JSON Schema cannot compare them. This package adds no HTTP status/endpoint.

Each hit must contain `workspaceId`, real Core-issued `metadataId` UUID,
`sourceVersion`, lowercase 64-character `sourceChecksum`, derived `chunkId`,
finite `score` and `excerpt`. Workspace must match authorized context. Scores are
engine-specific ranking values, not confidence or comparable across engines.
Record scoring/configuration semantics with engine versions; do not threshold one
engine using another engine's score scale. Resolve citations through Core using
workspace + metadata ID and retain version/checksum for exact artifact state.
UUID shape alone does not prove Core issuance or access rights: the adapter must
resolve the authorized record and match its version/checksum. The local validator
checks shape/fixture consistency only; it does not resolve citations through Core.

The local knowledge manifest has null metadata/workspace bindings because it has
not been ingested. Local artifact IDs may help organize source files but are not
acceptable runtime citations. No fake Core UUIDs, URL authorization or direct
Core/Keycloak/Flowable DB dependencies.

## Versioning and rebuild

Suggestions record engine/profile and rules and/or model versions. For this MVP,
`service-desk@0.1.0` pins `priority-policy@0.1.0`; changing the matrix/review fallback
requires Process/Service Owner review and a new profile version (or a reviewed
explicit `priorityPolicyVersion` in a later contract).
Search records engine/engine version, profile, corpus and index versions; add normalization/retrieval-config/model/prompt
versions when those capabilities exist. An immutable version label must identify
one configuration. Change content/configuration with a new version; do not quietly
reuse an old version. Contract-breaking changes require a new contract version and
consumer review. Never interpret an unknown enum/profile/version as a default.

Deleting any index/cache leaves Core records valid. Rebuild derived state from
authorized Core data plus versioned AI configuration, keeping source bytes and
provenance separate from normalized text. Synthetic examples are authoring fixtures,
not a holdout set; split future evaluation by scenario/template to prevent leakage.
The [normalization draft](../../../intelligence/src/apps/servicedesk/intelligence/profiles/service-desk/normalization.md) preserves original evidence
and Vietnamese diacritics; future normalization behavior must be versioned.

Dependency/model/data adoption and corpus release remain pending. See
[inventory](../../../intelligence/docs/governance/intelligence-dependency-candidates.md) and the
[package report](../../../intelligence/docs/task/N1/FOUNDATION_REPORT.md).
