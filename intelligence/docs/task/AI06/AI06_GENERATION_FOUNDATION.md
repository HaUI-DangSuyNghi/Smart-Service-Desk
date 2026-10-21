# AI06-01 deterministic generation foundation

`GroundedAssistanceService` accepts a caller-verified bundle, question and
application-owned `GenerationPolicy`. Its provider is injected at construction.
No Core access, retrieval, authentication, public endpoint or workflow mutation
is introduced by this service.

## Behavior

1. Validate question, immutable bundle, policy and pinned provider identity.
2. Return `INSUFFICIENT_EVIDENCE` without generation when the bundle is empty.
3. Project evidence to local aliases and excerpts, capped by the policy claim
   budget. Request/profile/source provenance remains outside the provider view.
4. Invoke the provider and validate the structured draft.
5. Reject unknown citations and claims that are not exact quoted evidence.
6. Build answer text exclusively from validated claims, append citation aliases,
   and return only citations actually used by those claims.

The template emits complete excerpts in canonical alias order. It does not infer
relevance, summarize or execute source instructions. A question or source that
contains an instruction remains data. Exact extraction does not detect malicious
documents or conflicting guidance and is not proof of a real model's injection
resistance. The reference policy requires human review by default. Upstream
`needs_review` cannot be cleared by application policy.

## Results and failures

| Condition | Result |
| --- | --- |
| Fully supported exact quotations | `SUCCESS / GROUNDED` |
| Empty bundle or provider refusal | `INSUFFICIENT_EVIDENCE`, no answer, no error |
| Typed provider outage | `DEGRADED / GENERATION_UNAVAILABLE` |
| Typed provider timeout | `DEGRADED / GENERATION_TIMEOUT` |
| Malformed output or unsupported claim | Typed contract/grounding exception |
| Runtime or policy identity mismatch | Typed configuration exception |

Degraded results contain no answer or citations and always require review.
Unexpected provider exceptions are converted to fixed internal contract errors.
Provider error text is not included in results or traces. Providers must enforce
their execution deadlines and translate timeouts to `GenerationTimeout`; this
synchronous service does not attempt to terminate arbitrary provider code.

## Identity and observability

The template uses `citation-bound-template@0.1.0`, `exact-excerpts@0.1.0`,
`data-only@0.1.0` and `grounded-claims@0.1.0`. Model/revision are explicit `none`.
The application policy identity must match the provider's application prompt
version. Provider identity drift after service creation fails closed.

`AssistanceTrace` contains request/profile/runtime identity, bundle digest,
counts, review/status/reason and measured durations. It excludes question,
excerpts, prompt instructions, raw completions, source UUIDs and credentials.
The trusted caller must use opaque request IDs and nonsecret runtime identities.

## Validation commands

Run from the repository root with the locked CPython 3.12 environment:

```powershell
python -m pytest intelligence/tests/generation -q
python scripts/check-tier-boundaries.py
python scripts/test-tier-boundaries.py
```

The existing base source/wheel suites discover the new generation tests.
Cross-platform deterministic evidence is covered by the AI06 evidence CI steps.
Full delivery results are recorded in [handover](AI06_HANDOVER.md).

Final source freshness, real Core integration and public response validation are
required in the later application adapter. This foundation alone must not be
used as authorization, source freshness or production readiness evidence.
