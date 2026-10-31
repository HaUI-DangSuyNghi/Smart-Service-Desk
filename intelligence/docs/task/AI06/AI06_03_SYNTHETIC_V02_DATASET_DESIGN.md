# AI06 synthetic DEV dataset v0.2

This release appends 348 invented cases to the immutable 52-case v0.1 baseline.
It is a reproducible engineering fixture, with all labels DEV/PENDING, rights
PENDING and HOLDOUT NOT_LOCKED. It does not establish official model quality.

## Content and allocation

The allocation is fixed in `generation-plan-v0.2.json`: 400 cases, 30 categories,
and six axes: GROUNDING 100, REFUSAL 60, SECURITY 80, CONFLICT 60, VIETNAMESE 60,
ROBUSTNESS 40. IDs are contiguous `GEN-SD-0001` through `GEN-SD-0400`.
The first 52 case objects remain deeply equal to v0.1; its file bytes are unchanged.

Twelve invented fact families contain 36 independent facts and exclusive
alternative instructions. Sixty templates define bilingual surface forms,
evidence topology, missing evidence, contradictions and synthetic attacks.
No AI05, real ticket or HOLDOUT content is imported. The oracle graph records
supporting sources, required/unsupported relations and contradiction pairs;
automatic labels remain provisional.

Four declared source-order pairs reuse the same documents in reverse order.
Eight evidence-removal children preserve an answerable parent's question but
remove its evidence and expect refusal. Paraphrase and Vietnamese transformations
also retain explicit parent links. Exact duplicate detection ignores category
and UUID; only declared reverse-order pairs receive an exception. Character
trigram near-duplicate candidates remain visible for review.

## Reproduction and integrity

Generation is offline, bounded, seeded with `20261024`, and uses UUIDv5 with
fixed namespace and content-derived names. Files use UTF-8, LF, sorted JSON keys,
two-space indentation and a final newline. Comparison normalizes NFC, case and
whitespace; deliberate NFD cases retain their raw bytes. No wall clock or random
UUID enters a fixture. Raw input, tooling, schema, source and case digests are
pinned in the manifest. The baseline raw SHA-256 is
`f3ba65dd318b2e06cce0e450faf7f2978916e8a6001c94270de38cb23fcb0c2e`.

Each request has at most 10 documents, 2,000 question characters, 16,000 characters
per document and 32,000 total excerpt UTF-8 bytes. Oracle citations are limited
to three. Near-limit examples exercise 31,000 excerpt bytes without bypassing
the runtime limit. Duplicate JSON keys, floats, boolean counters masquerading
as integers, invalid references and stale pins fail closed.

The earliest Git addition of `dev-v0.2.json` freezes its dataset digest. Later
same-version changes fail validation, even if local manifest hashes are updated.
Changed content requires a new dataset revision and new receipts. Roll back DEV
consumers to v0.1; do not rewrite released v0.2 or disable historical gates.

## Ownership and risk checks

All new application tooling lives under `scripts/apps/servicedesk/ai06/` and its
tests under `intelligence/tests/apps/servicedesk/generation/`. The generic evaluator
is consumed through public APIs. No application prompt, category, dataset path or
default is added to hdpi. A small neutral evaluator contract test stays generic.

The extended ownership gate checks full generic Python and optional integration
source, new/shared script and test dependencies, app imports and active approval
bypass flags. Existing shared semantic debt is measured against the frozen
`1940b6eca68eca2509bef43280065412e33de42a` tree; added dependencies fail. It does
not rewrite that baseline or widen an allowlist. Wheel inspection verifies the
actual distribution metadata and contract resource bytes and rejects app-owned
content. Hostile source, string, resource, wheel and bypass mutations test the
gate. AST/string checks cannot prove absence of arbitrary encoded or dynamically
computed dependencies; independent architecture review remains necessary.

Content checks reject credential shapes, private keys, JWTs, PII patterns,
unapproved external references and undeclared attack surfaces. Only designated
canary cases accept the exact synthetic canary and narrowly reserved example
addresses. This is automated risk linting, not a human rights/security approval.

## Evaluation and cloud boundaries

The reference runner reports technical failures, provisional mismatches and
unsafe exact selections separately. It re-executes against current HEAD, actual
implementation bytes and current inputs before accepting evidence. Reports
contain IDs, counts and digests, never raw questions or excerpts. Source, installed
wheel and cross-OS semantics must agree. Preserve the historical 52/13/8
case/category/unsafe regression; new unsafe findings must remain visible.

The stable smoke manifest selects one case per category (30), independent of
scores. Cloud execution is OFF by default, requires an external expiring approval
for the exact dataset/manifest/provider/model/catalog/scope and separate existing
route approval and keys. Request/input/output budgets default to zero; maximum
cost is zero. A full 400-case run needs its own `full` scope approval. Approval is
checked before runtime creation and again per case. No retry or same-request
provider fallback occurs; quota/timeout stops retain unexecuted cases in the
planned denominator. Fake transport never produces real-model verification.

The optional integration and Java catalog accept up to 1,024 approved question
or source digests so a 400-case approval can enumerate all 328 unique questions.
Actor/resource inventory limits remain 256; request sizes and other route limits
are unchanged. This is a technical inventory capacity change, not egress consent.

Provider reports pin the integration implementation and configuration, distinguish
request reservations from observed responses, and retain unknown usage/cost as
unknown. The cloud comparison command checks operator receipt consistency only;
it cannot remotely replay or attest hosted model outputs. Provider console
reconciliation and owner review are still required. Official mode stays BLOCKED.
