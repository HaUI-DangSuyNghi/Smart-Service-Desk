# AI06-02 model selection and approval record

Decision: candidate for evaluation only. Operational approval: **BLOCKED**.
Runtime promotion: **NOT_AUTHORIZED**. No weights were downloaded for this task.

The official [Qwen3-4B-Instruct-2507 model card](https://huggingface.co/Qwen/Qwen3-4B-Instruct-2507)
was checked on 2026-10-23. It lists Apache-2.0 and a 4.0B parameter architecture.
This is initial license evidence, not Legal approval or permission to process
customer data. Its tool-oriented examples are outside this provider's scope.

| Option | Assessment |
| --- | --- |
| Qwen/Qwen3-4B-Instruct-2507 | Candidate for offline multilingual selection; hardware, relevance and refusal unmeasured |
| Hosted external model | Excluded by AI06-02 processing and egress requirements |
| Unpinned or quantized derivative | Excluded until independent provenance, byte hashes and quality review exist |
| Deterministic reference provider | Retained unchanged as regression oracle; not a real model |

There is no measured winner and no real-model quality claim. Temperature zero is
not a guarantee of deterministic generation. CPU latency is not an approved SLA.

## Required receipts before execution

| Required evidence | State | Owner |
| --- | --- | --- |
| Exact immutable upstream revision | NOT_PROVISIONED | AIE / Platform |
| Enumerated weight, tokenizer, config, template and license byte digests | NOT_PROVISIONED | Platform |
| Quantization method and derivative provenance, if any | NOT_SELECTED | AIE / Security |
| Backend version and serving image SHA-256 | NOT_PROVISIONED | Platform |
| Authentic license / notice / model-use review | PENDING | Security / Legal |
| Synthetic dataset rights approval | PENDING | Data Owner |
| Processing / logging / retention review | PENDING | Privacy |
| Egress-denied deployment, quotas and security observations | PENDING | Security / Platform |
| Real offline tokenizer and sidecar protocol wrapper | NOT_IMPLEMENTED | Platform / AIE |
| Three-repeat measurements and reviewer signatures | NOT_RUN | AIE / QA |

`ai06-model-manifest.schema.json` defines the receipt format. Runtime validation
also checks inventory completeness, safe paths, required artifact kinds, distinct
paths, immutable revision, all approval roles and coherent bounded limits.
Receipt values are SHA-256 references to authentic external reviews, not an
approval issued by the validator. Platform verifies signatures and owner authority.

No `ai06-model-manifest.json` is committed: inventing pins would misrepresent
approval. Synthetic manifests are generated only in temporary test directories
from actual synthetic bytes, explicitly named `synthetic/local-selector`; they
are never the Qwen candidate or operational provisioning evidence.

## Offline provisioning procedure

1. Obtain explicit compute, network, model and data approval.
2. Review upstream revision, license notices, backend dependencies and SBOM/CVEs.
3. Provision in a separate authorized environment. Enumerate every model file;
   compute each SHA-256 and record immutable serving-image and backend identities.
4. Store reviewed receipt digests for model, data, privacy, security and platform.
5. Verify the entire inventory offline with `verify-generation-model.py`.
6. Supply a reviewed offline `TokenCounter` bound to the manifest and an isolated
   sidecar implementing the documented protocol. No implicit model downloads.
7. Implement and review the real-run adapter; run the pack three or more times,
   retain every timeout/invalid result, measure actual resources and record human
   decisions. All skipped or missing measurements remain inconclusive.

Model approval does not approve business data. AI05 owner reviews and HOLDOUT are
independent and remain unresolved where previously recorded.
