# Grounded generation runtime (AI06-00 / AI06-01)

Decision: generation is an internal Tier-2 capability consuming an immutable
`AuthorizedKnowledgeBundle`. It does not depend on a particular retrieval engine.
TF-IDF remains the production retrieval runtime; no generation model is promoted.

## Trust and ownership

Core owns identity, membership, source state and authorization. The application
authenticates and authorizes the request, constructs `TrustedContext`, and invokes
authorized retrieval. Only citations verified against current Core state may be
converted to a knowledge bundle. Dataclass validation establishes shape and
consistency; constructing a dataclass does not establish authorization.

The trusted caller supplies request/profile/retrieval identity and verified
citations. The bundle rejects duplicate identities, mixed workspaces, inconsistent
source versions and resource overflow. Canonical provenance ordering assigns
`C1`, `C2`, etc. A stable digest binds the complete internal bundle.

The application owns question construction, business policy, prompt instructions,
review rules and presentation. Generic `hdpi` contains no application taxonomy.
Clients cannot select a model, engine, system prompt or authorization context.

## Provider boundary

`GenerationProvider.generate(GenerationRequest)` receives only:

- question;
- server-owned policy identity and instructions;
- output schema identity;
- local citation aliases and evidence excerpts.

The provider receives no bundle, workspace/source IDs, access-scope hash, bearer
token, membership state, Core adapter, database handle or retrieval callback.
Question and evidence remain untrusted content. Policy instructions are supplied
by the application. Neither questions nor evidence become executable commands.

Providers have no tools, function calling, source fetching or workflow/action
interface. They cannot mutate application state. There is no fallback to an
ungrounded answer. Errors contain fixed messages; raw provider failures, prompts
and completions must not enter public responses or safe traces.

## Contract and validation

All evidence, requests and drafts use frozen dataclasses and immutable tuples.
Budgets are 10 evidence items, 4,096 characters per excerpt, 16,000 evidence
characters, 8,192 question characters, 10 claims and 16,000 answer characters.
Every claim requires unique, known citation references. Provider drafts contain
only claims; the service builds the rendered answer from validated claims.

The foundation supports exact evidence quotations only. A known citation alias
alone does not establish semantic support. Each claim must equal every cited
excerpt. This deliberately conservative gate proves extraction and citation
binding; paraphrase entailment, conflict detection and model prompt security
require later evaluation and governance. The baseline does not establish the
relevance or safety of arbitrary source content. Human review is enabled by default.

An empty bundle never reaches a provider. Empty provider claims mean refusal.
Operational provider failures may return `DEGRADED`. Malformed output, identity
mismatch and grounding violations fail closed as typed internal exceptions.
These exceptions are not operational fallbacks and never produce `SUCCESS`.

Final freshness checking remains the trusted orchestration caller's responsibility
before releasing a result. The generic service cannot establish current Core
authorization or detect source changes during generation. A production adapter
must revalidate cited sources and suppress stale answers. This foundation exposes
no HTTP endpoint and is not a production integration or an autonomous assistant.

## Versioning and delivery

Runtime identity separates provider/version, model/revision, generation config,
prompt envelope, application prompt and output schema. Configuration and prompt
changes require explicit identity changes. The deterministic template is a test
and reference provider; it has no model dependency or external network access.

AI06-02 owns governed model selection and prompt security. AI06-03 owns reviewed
grounding evaluation. AI06-04 owns ticket-scoped integration, final freshness and
public contract validation. None of these promotion decisions is implied here.
