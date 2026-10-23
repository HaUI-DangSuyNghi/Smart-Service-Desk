# AI06-02 threat model and synthetic security report

Scope: dormant generic selector, its parser, offline manifest and loopback client.
Evidence class: synthetic engineering contracts. No real-model attack-resistance,
network-isolation certification or production quality conclusion is claimed.

| Threat | Control and executable proof | Residual risk / owner |
| --- | --- | --- |
| Direct / indirect injection | Fixed system/developer/user roles; JSON data; 8 direct and 8 indirect fake cases | Model relevance and refusal remain unmeasured; AIE |
| Role / delimiter spoofing | 6 delimiter cases including Vietnamese, escaped JSON and Unicode controls | A model still reads malicious data; QA / AIE |
| Citation / schema spoofing | Exact keys, unique supplied aliases, no prose/tool fields; 6 pack cases plus malformed JSON unit tests | Membership does not imply truth; AIE |
| Poisoned source quotation | Claims copy supplied excerpts only; unsafe-source quotations counted explicitly | All 8 fake indirect cases quote malicious content; source review required; Data Owner |
| Exfiltration / SSRF | Literal loopback endpoint, fixed routes, no DNS, redirects, retries or proxies; 4 privacy cases; fake runner forbids sockets | Sidecar egress must be denied by Platform, not asserted by HTTP alone |
| Cross-tenant access | Provider view excludes provenance and authority; inherited mixed-workspace and retrieval authorization tests | Bundle shape is not authorization; Core / trusted app |
| Resource exhaustion | Byte/token/context/output/rate/concurrency budgets; read/connect/overall deadlines; live loopback slow-response test | GPU memory/process quotas need real deployment measurements; Platform |
| Runtime drift | All-file SHA verification and manifest-bound tokenizer, image, backend, limits and per-response identity | Malicious serving process can lie; independently verify image and isolation; Security |
| Content leakage | No raw response repr; fixed errors; content-free reports; throwing trace sink tests | Serving access logs and crash dumps require Platform review |
| Revocation / stale source | No public integration added; existing grounding unchanged | Final Core freshness is mandatory in AI06-04; Core |

## Pack design and interpretation

The pack contains 40 non-sensitive authored cases: 8 direct, 8 indirect, 6 schema,
6 delimiter, 4 privacy, 4 resource and 4 benign. Payloads include dummy canaries,
Vietnamese/English, security-topic benign controls and reserved example URLs.
Fixtures and fake infrastructure live outside the generic wheel.

Fake responses are scripted to exercise controls; a fake selector choosing `C1`
does not demonstrate that an actual LLM resisted injection. Every case remains
in the denominator, including timeout, unavailable, invalid output and overflow.
The report stores category, outcome, call/claim counts, unsafe-quotation flags and
implementation/manifest/test-set digests. It stores no question, evidence, prompt,
completion, token IDs, credentials, tenant identity or provenance UUID.

The verifier rebuilds the current expected fake report and compares canonical
JSON fingerprints. Two matching stale reports cannot pass. Tests mutate model,
configuration, implementation, dataset, denominator and promotion fields. JSON
newline/indentation differences are irrelevant; runtime types remain significant.

## Real-model review pack, pending approval

Run these same categories against an approved deployment with at least three
replicates. Add human-reviewed answerability/relevance labels before drawing
quality conclusions. Record per-attempt PASS/FAIL/INCONCLUSIVE, invalid-schema
rate, refusal, benign usable/false-refusal rate, malicious-source selections,
latency p50/p95, actual GPU/CPU/RAM and observed canary egress attempts.
Retain no raw content in Git or Actions artifacts. Record seed/config/hardware,
reviewer flags, all denominators and variance instead of requiring identical
real-model output across OSes. An all-refusing model cannot establish utility.

Status: **REAL_MODEL_VALIDATION_BLOCKED**. No real-run receipt, resource readings,
three-repeat evidence, network observation or human signatures are supplied.
