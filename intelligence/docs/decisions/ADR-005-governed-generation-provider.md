# ADR-005: Governed local evidence selection

Status: IMPLEMENTED DORMANT MECHANISM; architecture and operational sign-offs pending.
Date: 2026-10-23. Baseline: `develop@d12c3498ccf46e1ea14e9bab5d09924f7b0228b4`.

## Decision

Implement `GenerationProvider` using a pinned, local, structured evidence selector.
The model can select supplied citation aliases or refuse. Trusted code constructs
exact quotations and the existing `validate_grounded_draft` remains the final
grounding gate. No free prose, tools, external model API or public route is added.

`PinnedLocalSelectionProvider` is explicitly injected by trusted server code.
Its constructor defaults to disabled. Startup verifies the offline artifact
inventory, tokenizer fingerprint and sidecar identity. No automatic registration,
environment-driven enablement or application integration is provided.

The generic envelope owns technical role separation. Business instructions and
their version remain caller-owned `GenerationPolicy` values. The generic wheel
contains no application prompts, weights, evaluation corpus or model manifest.

## Ownership and trust

| Asset or decision | Owner | Mechanism |
| --- | --- | --- |
| Identity, ACL, source state | Core and trusted application | Existing authorized retrieval; final freshness deferred to AI06-04 |
| Business question and instructions | Tier 3 application | Explicit policy identity; no generic defaults |
| Serialization, parsing, quotation | Tier 2 `hdpi` | Fixed roles, strict aliases, unchanged grounding validator |
| Model bytes, serving image, approvals | Platform and Security | Offline inventory and manifest digest; external receipt authenticity review |
| Source rights and privacy | Data Owner and Privacy | Separate content-addressed review receipts |
| Benchmark judgments | AIE and QA | AI06-03 reviewed dataset, independent of synthetic protocol tests |

## Transport decision

The initial implementation accepts literal loopback origins with an explicit
port. It does not resolve hostnames. HTTP has no ambient credentials or proxy
support, redirects, retries, compression, chunked transfer or streaming. Body,
context, output, concurrency, rate, connect/read and overall deadlines are bounded.
A deadline watchdog shuts down the active socket even after HTTP detaches it.

The sidecar protocol is documented in the runtime architecture. It requires an
engine wrapper; an ordinary OpenAI-compatible endpoint is not this protocol.
Docker private-network addressing and mTLS need a separate reviewed transport.
Do not broaden the loopback rule to accommodate an unreviewed deployment.

An identity response is an assertion, not proof of process isolation, disabled
logging, image execution or no egress. Platform must enforce those independently.
The trusted tokenizer adapter must count the actual pinned chat template and
schema overhead offline. No character estimator is a production tokenizer.

## Consequences and release conditions

Exact quotation proves evidence membership, not relevance, safety or truth.
The synthetic pack intentionally records eight malicious excerpts being quoted.
Source review, relevance/refusal evaluation and final Core revalidation remain
required before public use. No heuristic keyword blocklist is introduced.

Trace delivery is best effort: sink exceptions cannot replace results or safety
errors and are not logged. Mandatory security audit belongs to a separate
application/platform path with an explicit failure policy.

No approved operational manifest, pinned real tokenizer, engine wrapper, compute
receipt or signed approvals exists. Real execution is
`REAL_MODEL_VALIDATION_BLOCKED`; production promotion is `NOT_AUTHORIZED`.
The real-mode CLI emits a blocked readiness report and exits 2 without a model
call. Implementing its deployment adapter is an explicit remaining prerequisite,
not a completed real-model validation. Human review is not inferred from this ADR.

AI05 retrieval, historical profiles, judgments, HOLDOUT and current identity gates
are unchanged. AI06-03 owns reviewed quality; AI06-04 owns public integration.
