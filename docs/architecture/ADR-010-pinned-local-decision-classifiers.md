# ADR-010: Pinned native local decision classifiers in an isolated lab

Date: 2026-10-25. Scope: AI07 v1.4 Part IV, extending ADR-009.
Status: engineering implementation; production, model-license acceptance and
supervised classification promotion remain pending independent review.

## Decision and contracts

Rules `service-desk@0.3.0` remain the sole operational authority. Neither native
adapter is injected into the synchronous Java assessment path. The bridge has
no Core credentials, ticket lifecycle capability, retrieval authority or database
connection. Only a trusted, explicitly enabled synthetic DEV operator experiment
can call it. This is a separate model runtime, not an AI06 generation replacement.

Tev1 uses the native `/v1/systemone` choice endpoint. Its vote becomes a new generic
`decision-observation@0.1.0`: opaque class, optional unvalidated distribution,
optional concentration, no input spans, no operational authority. Missing scores
are NOT_AVAILABLE. Probability/concentration never means empirical correctness.
The span-required classifier v0.1 contract is unchanged. The endpoint has no
generation `options` control; context is pinned in a separate derived model alias,
not assumed from ignored JSON fields. See the [System One API](https://docs.ollama.com/api/systemone)
and [Ollama 0.40.8 request types](https://github.com/ollama/ollama/blob/v0.40.8/api/types.go).

Gemma uses `/api/chat`, nonstreaming JSON Schema, temperature zero, seed zero,
context 2048, output at most 256 tokens, truncation/shift disabled. Its exact quotes
must have exactly one occurrence across the original input fields. The generic
mapper computes Unicode code-point offsets and invokes the existing proposal
validator. Absent, repeated, overlapping-ambiguous or normalized quotes fail;
there is no best-effort first-occurrence choice. Exact copying proves syntactic
provenance only. See [chat API](https://docs.ollama.com/api/chat) and
[structured outputs](https://docs.ollama.com/capabilities/structured-outputs).

Tier 3 owns input field names, five class IDs, criteria, instructions, schema and
experiment identities in `triage_ai07/experiments`. The integration receives
versioned, hashed policy files. HDPI owns only neutral observations, literal span
mechanisms and validators. No application prompts, fixtures or model bytes enter
the generic wheel or bridge image. A changed option order changes trusted policy
identity; response probabilities are mapped by exact keys into the trusted order.

## Model identity and token budgets

The observed host downloads are `tev1:0.8b-q8_0` and `gemma3:1b`, Ollama 0.40.8.
The Tev1 source artifact sets `num_ctx=8192`. Provisioning creates the explicit
`ai07-tev1-0.8b:ctx2048` alias, reusing verified weights with new pinned parameters.
Both original and derived manifest identities are recorded; the original tag and
host model store are preserved. This is not an alias silently promoted to a new tag.

The pinned GGUF contains the tokenizer. The native runtime performs actual token
counting, rejects context overflow and returns token usage. The bridge verifies
Tev1's context parameter before inference, actual loaded context after inference
and usage bounds. It never substitutes a character estimate or fabricated token
counter. A false `num_ctx` claim, unexpected backend version or CPU placement in
the explicit GPU profile fails the request. Partial CPU offload is recorded via
actual `size` and `size_vram`; GPU use alone is not proof of full GPU residency.

## Deployment and artifact integrity

Ollama and a separate code-only bridge share one Linux network namespace. Both
APIs bind literal loopback. The runtime has one Docker `internal` network, zero
published ports, dropped capabilities, no-new-privileges, read-only roots and
disabled Docker log drivers. A collocated operator worker authenticates to the
bridge using a dedicated mounted secret. The bridge has one request worker,
bounded backlog/body/rate/deadline limits, fixed native endpoints and no proxy,
redirect, retry, model install, tools or cloud fallback. GPU reservations follow
the [Compose device reservation contract](https://docs.docker.com/compose/how-tos/gpu-support/).

Provisioning and runtime use the same persistent external model volume. Import
reuses only the two host downloads, verifies recursive manifest/blob hashes,
checks copied bytes and derives the context alias in a temporary isolated server.
Alternatively, the separate provision-only overlay permits deliberate operator
downloads. Provisioning must stop before the final read-only runtime mounts it.
No normal application service or global `.env` is needed for these standalone files.

Bootstrap hashes pinned artifacts once on a read-only mount and records file
identity, size and timestamps in a lease. Requests compare that lease, policy
identity, backend version and native tag digest before and after inference. Drift
permanently revokes the runtime until restart/reprovisioning. Host/daemon root is
outside this protection boundary. A host administrator can replace the deployment;
an HTTP self-reported identity alone is not attestation. External Docker probes
also check images, mounts, logging, namespace, authentication and denied egress.

Ollama 0.40's `manifests-v2` index uses symlinks to content-addressed blobs. This
native integration accepts only a relative leaf link resolving exactly to
`/models/blobs/sha256-<expected digest>`; parent links and arbitrary model links
remain forbidden. Bootstrap hashes the target and captures both link and target
file identity/timestamps. A changed link or target revokes the lease. The generic
legacy `ModelManifest` symlink prohibition is unchanged.

## Evaluation and release

Source fixtures stay immutable. Only the 48 draft synthetic DEV records are sent
to the model; the 24 proposed HOLDOUT rows are excluded. Explicit
`--allow-draft-synthetic` selects architecture/resource controls, never gold.
Every attempted invalid, unavailable or timed-out inference stays in the ledger.
Cold/warm state, native API and end-to-end wall timings, token counts, placement,
global GPU samples and all identities are recorded without raw prompts or output.
Global NVIDIA memory samples are not per-process memory attribution.

Human-reviewed TRAIN/DEV, rights, independent HOLDOUT, calibrated quality, model
license review, SBOM/security acceptance, hosted CI and independent reproduction
remain separate gates. Macro-F1 and calibrated accuracy stay null for draft data.
The release validator rejects incomplete/tampered ledgers and always returns
production NO_GO for this lab lane. Disabling the lab profile preserves the model
volume and receipts and leaves existing Java endpoints and rule policies intact.
