# ADR-006: API-first governed assistance

Date: 2026-10-24. Engineering decision implemented; human cloud/data approval PENDING.
Branch: `feature/ai06-02-governed-real-model-provider`, existing PR #37.

Cloud APIs are the primary AI06 DEV path while GPU infrastructure is unavailable.
Gemini and Groq are the P0 adapters. Local selection remains dormant and unchanged;
weights, tokenizer files and local serving are not cloud readiness prerequisites.
ADR-005 continues to describe the optional local provider.

The generic `smart-service-intelligence` wheel owns retrieval, selection contracts,
exact quotation grounding and validation. A separate optional
`smart-service-cloud-integration` wheel owns vendor HTTP and credentials. Service
Desk owns the prompt, approved questions, ticket classification and source pins.
Core has no vendor dependency. The browser submits only a question.

Java authorizes the synthetic actor, immutable resource digest, exact question
digest and every source digest. It reserves a quota slot and selects one approved
route before constructing the private request. Additive private v0.2 schemas pin
catalog, route and approval digests, attempt UUID and all eight runtime fields.
Python checks the same operator files and exact binding before retrieval or egress.
Reference v0.1 and its deterministic behavior remain unchanged.

Each request has one provider and no automatic retry or fallback. A 429 or timeout
returns a typed failure and cooldown. A later request may select the next eligible
vendor with a fresh attempt and runtime identity. Credential, redirect, malformed
output or observed-model drift disables the route. Java still verifies the exact
response identity and quotes, re-resolves selected Core sources and rechecks the
ticket before releasing an answer. No transaction spans inference.

The adapter sends only the question, bounded excerpts with local aliases and app
instructions. SELECT/REFUSE JSON becomes exact source quotes through the existing
strict decoder. Native vendor schema constraints are a first filter; local alias,
uniqueness, decision, claim and grounding validation remains authoritative. No
tools, streamed output, free prose, model-selected endpoints or user credentials.

Transport uses fixed HTTPS origins, public-IP DNS checks and certificate/SNI
verification, bounded concurrency/body/deadline, no ambient proxy, redirects or
retries. Compose places the sidecar on an internal network with an explicit
CONNECT relay. Only the relay has outbound connectivity; TLS remains end to end.

Hosted revision provenance is `vendor-unpublished`, never a fabricated weights
hash. Exact configured model and approved observed response IDs are distinct.
Catalog byte changes invalidate both approvals and route identities. Availability,
strict-output support and free entitlements must be verified in the operator's
actual account before activation. Local quotas are conservative estimates and
cannot prevent billing by themselves: configure provider-side free-only spending
limits. No account cycling, automatic paid escalation or assumed perpetual quota.

The quota ledger is in memory for one isolated DEV instance. Restart loses local
counters; external account caps remain necessary. Multiple replicas, distributed
budgets, Cloudflare, OpenRouter and Cerebras are deferred. No paid-tier escalation.

Readiness requires real Gemini and Groq success/refusal plus authorized full-stack
acceptance. Offline mocks prove contracts only. No credentials or owner approval
were supplied during implementation, so CLOUD DEV READY and official release
remain BLOCKED. Production data requires a separate reviewed policy and is rejected
by the implemented mode.

References: [Gemini native API](https://ai.google.dev/api/generate-content),
[Gemini structured output](https://ai.google.dev/gemini-api/docs/structured-output),
[Groq structured output](https://console.groq.com/docs/structured-outputs),
[Groq rate limits](https://console.groq.com/docs/rate-limits).
See [processing policy](../governance/ai06-cloud-processing-policy.md) and
[operator runbook](../task/AI06/AI06_API_FIRST_OPERATIONS.md).
