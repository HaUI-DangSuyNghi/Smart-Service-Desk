# AI06 entry readiness

Baseline: `origin/develop@828fbcf2273e3ed54e5d67e7a2594f1190f319cc`.
Branch: `feature/ai06-01-grounded-generation-foundation`.

This delivery implements the recommended first task, AI06-00 plus AI06-01.
Architecture/contracts and deterministic runtime are separate reviewable commits.
See [runtime decision](../../architecture/grounded-generation-runtime.md).

Inherited invariants remain: adapter-owned trusted context, authorization before
scoring, authorized partitions, Core-verified citation provenance, generic Tier-2
code and application-owned semantics. Existing retrieval runtime, endpoints,
profiles and contracts are unchanged.

## Scope

- Immutable authorized knowledge, safe provider request, claim and runtime types.
- Deterministic citation-bound extraction provider and assistance orchestration.
- Empty-evidence refusal, typed operational degradation and contract failures.
- Content-free traces and source/installed-wheel regression checks.

## Deferred gates

No real model is selected or downloaded. No external provider receives content.
Model/privacy/license approval, prompt-injection resistance of a real model,
reviewed generation benchmarks, semantic paraphrase grounding, final Core
freshness revalidation and public integration remain AI06-02 through AI06-04 work.
AI05 human qrels, official HOLDOUT and retrieval promotion remain independent.

Foundation completion does not mean the entire AI06 phase or production release
is complete. This provider is an engineering reference requiring human review.
