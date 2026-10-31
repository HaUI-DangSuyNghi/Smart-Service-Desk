# Synthetic cloud processing policy

Status: PENDING. This document and all committed templates grant no egress rights.
No production/customer ticket or arbitrary user question is authorized.

The data owner must approve both vendor recipients (Google Gemini and Groq), exact
catalog byte digest, synthetic source hashes, exact question hashes, test actors,
resource/workspace identities and immutable resource content digests. For Service
Desk, the resource digest is SHA-256 of UTF-8 `title + "\n" + description` without
normalization. Source digests use the actual Core bytes. Question hashes use the
exact UTF-8 input without trimming. Rights must be `synthetic-public-evaluation`.
Rights alone do not authorize outbound processing.

Approval is an owner-controlled JSON file outside the checkout, pinned by SHA-256,
with explicit APPROVED status and an unexpired timezone-aware timestamp. The
isolation file binds the same environment ID/catalog and attests a synthetic-only
database. Both services rehash all three files on every request. Protect files,
parent directories and environment configuration with operator-only write access;
on Windows use ACLs, on Linux use a read-only mount and restrictive permissions.
The deployment operator is trusted to mount these files; a JSON owner label is
not a signature or proof against a compromised administrator. No repository
fixture can authorize real cloud HTTP; TEST_ONLY owners are rejected by the real
transport composition.

The owner must retain a separate review record for terms/retention, privacy,
rights, synthetic dataset provenance, independent fixture review, account quota,
free entitlement, provider-side spending caps, chosen model/observed IDs, expiry
and the two external recipients. Copying a template or installing an API key does
not satisfy those reviews. Catalog changes require new approval. No code-generated
APPROVED receipt is provided.

| Threat | Enforced control / remaining trust |
| --- | --- |
| Key leakage | Server-only env or secret file; no VITE keys, URL query secrets, raw errors or model payload logs; .env ignored |
| Real ticket mistaken for synthetic | Exact actor/resource/content/question/source allowlists plus separate DB attestation |
| Tenant or ACL crossing | Core resolve before snapshot, exact pins, selected-source revalidation and ticket read after inference |
| Prompt injection | Untrusted data envelope, SELECT/REFUSE only, strict alias checks and exact quotes; unsafe source guidance still needs human review |
| Vendor storage/retention | External human review before activation; technical grounding cannot prove privacy or semantic safety |
| SSRF/DNS/redirect/proxy | Fixed two hosts, all resolved IPs public, pinned peer and TLS SNI, no redirects or ambient proxies, internal Docker sidecar network |
| Upstream substitution | No aggregator; exact configured model plus observed response allowlist and bound runtime digests |
| Quota abuse | One route/vendor, one attempt/request, bounded rate/token reservation and cooldown, operator account caps |
| Overflow/hanging request | Bounded JSON nesting/size, output tokens, concurrency, request deadline and relay byte/time limit |
| Approval/config drift | Byte hashes, expiry, read-only external mounts and per-request revalidation |
| Telemetry exposure | Fixed codes/counts/buckets/digests only; no prompts, sources, raw answers, actor IDs or credentials |

Automatic release, ticket mutations, production mode and paid escalation are not
implemented. Known malicious exact-quote findings in the provisional 52-case DEV
set remain open; no historical human sign-off is changed by this policy.
