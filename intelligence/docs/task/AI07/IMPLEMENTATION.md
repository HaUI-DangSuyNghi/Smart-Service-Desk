# AI07 backend handoff

Decision: **CHOSEN**. Backend rules-only MVP and offline challenger contracts:
**IMPLEMENTED**. Model empirical winner: **NOT_SELECTED**. Labels/rights:
**PENDING**. Model promotion: **BLOCKED_PENDING_REVIEW**. AI06 official release:
**not approved by this change**.

Read [ADR-009](../../../../docs/architecture/ADR-009-ticket-intelligence-orchestration.md)
and the versioned OpenAPI in `contracts/openapi/servicedesk.yaml`.

The v1.4 local-model extension is implemented and exercised on the local RTX 3050
4GB. Read [the measured results and release limits](LOCAL_MODELS_V1_4.md),
[ADR-010](../../../../docs/architecture/ADR-010-pinned-local-decision-classifiers.md)
and [the Docker runbook](../../../../infra/compose/README.ai07-local.md).
Tev1 decision observations and Gemma quote proposals remain separate offline
challengers. The measured DEV ledger contains 71 rejected Gemma responses;
classification quality is not evaluated and production remains **NO_GO**.

## Work-package status

| v1.3 package | Delivered | Remaining external gate |
| --- | --- | --- |
| T00R | ADR, pinned SSD baseline, decision and taxonomy policy | Authentic owner acceptance |
| T01R | 35-file immutable SSD inventory, blank DEV worksheets | LabOps rights/import/reproduction and 40-vs-30 reconciliation |
| T02R | Generic CPU estimator, feature/learned-state manifest, split checks | Reviewed five-class SSD training data and DEV quality |
| T03R | Authorized immutable orchestration, rule projection, isolated offline shadow seam | Production candidate execution intentionally disabled |
| T04R | Separate pinned bridge, native Tev1/Gemma adapters, verified model artifacts and measured Docker GPU profile | Reviewed labels, acceptable model conformance and independent release approval |
| T05R | Recomputed full-denominator DEV engineering receipts, tamper/promotion rejection | Independent labels/HOLDOUT, comparative macro-F1 and promotion decision |
| T06R | Additive typed API, deterministic reasons, risk-not-assessed, exact-quote guidance | Semantic synthesis remains disabled |
| T07R | Default-off flag, rollback and CI hooks | Hosted CI, independent release/security GO/NO-GO |

## Runtime

Enable only the additive endpoint with JVM property
`-DserviceDesk.assistant.enabled=true` (default false). This does not enable
AI06 assistance, a model, cloud egress or customer data processing. Existing AI02
transport and AI06 source-pins/route approvals must be configured independently.

```http
POST /api/apps/servicedesk/v1/workspaces/{workspace}/tickets/{id}/assistant/assess
Authorization: Bearer <existing workspace JWT>
Content-Type: application/json

{"contractVersion":"0.1.0","includeGuidance":false}
```

`question` is optional, at most 2000 UTF-16 characters in the Java implementation;
request body at most 16 KiB. Unknown/duplicate fields, trailing JSON, blank
questions and authority/model/source overrides return 400. Responses are
`Cache-Control: no-store`. An omitted question uses a bounded Tier-3 template
around the persisted ticket title. Full title/description drive rule triage.

| Condition | Aggregate status | Behavior |
| --- | --- | --- |
| Valid rules, guidance not requested | COMPLETE | Zero generation calls |
| Valid rules, verified quotation | COMPLETE | SUPPORTED; reviewRequired=true |
| Valid rules, disabled/timeout guidance | PARTIAL | Preserve triage; no answer/citations |
| Rules fallback | REVIEW_REQUIRED | Preserve needsReview, ask for missing facts |
| Rule service unavailable | DEGRADED | Null category/priority; no generation |
| Ticket/source denied, changed or malformed | 403/404/409/502 | No partial assessment released |

COMPLETE means requested components completed; it is not a diagnosis, resolved
ticket or workflow decision. Risk remains NOT_ASSESSED independently of priority.
Only exact quotes are supported. CONFLICT/semantic synthesis are not advertised
as implemented modes. No arbitrary model score changes public review flags.

## Reproducible offline controls

Use fresh evidence output names; existing attempts cannot be overwritten.
Only `.local/ai07` is writable by these CLIs.

```powershell
python scripts/apps/servicedesk/ai07/audit-datasets.py --sources ssd --output .local/ai07/audit.json
python scripts/apps/servicedesk/ai07/prepare-triage-worksheets.py --dataset ssd-v0.2 --split DEV --output .local/ai07/dev-worksheets.json
python scripts/apps/servicedesk/ai07/evaluate-triage-candidates.py --mode shadow --split DEV --output .local/ai07/metrics.json
python scripts/apps/servicedesk/ai07/verify-triage-acceptance.py --report .local/ai07/metrics.json
```

The v0.2 ticket source contains **48 DEV + 24 HOLDOUT**, all draft synthetic.
Worksheet rows do not copy scenario/category into an authoritative label. The
evaluation executes all 48 DEV rules and includes every attempt. CPU/Gemma are
explicitly unavailable, macro-F1 is null, and no supervised model is trained on
these drafts. The generic CPU estimator tests use unrelated tiny synthetic
classes solely to validate software. They are not SSD quality evidence.

`verify-triage-acceptance.py --require-promotion` deliberately exits nonzero.
`--sources ssd,labops`, HOLDOUT export and external evidence output paths are
unsupported. Import requires rights, exact source reproduction and a reviewed
taxonomy mapping first. Do not relabel existing AI05/AI06 ledgers as AI07 results.

## Threat model and limits

| Threat | Implemented control |
| --- | --- |
| Workspace/actor spoofing | Existing authorized TicketService reads; actor equality at release |
| Ticket bytes changed while inference runs | Full record snapshot comparison, 409 |
| Source ACL/bytes/usage rights drift | Existing AI06 Core revalidation; propagate denial/conflict |
| Injection/tool output/class spoof | Rules retain authority; bounded exact class/span decoder |
| False risk/confidence | No risk score; explicit uncalibrated CPU score semantics |
| Provider outage/429/deadline | Existing bounded transports; partial results only for 503/504 |
| Local SSRF/model drift | Literal loopback, no redirects, hashes/tokenizer/identity checks |
| Train/holdout leakage | Family/split and duplicate checks; no holdout worksheet export |
| Dataset/report tampering | Git-pinned source inventory and recomputed complete DEV receipts |
| Sensitive logs | No prompt/model error logging or raw text in evidence reports |

The shadow comparison seam is **offline only**, intentionally outside request
latency. It does not start background jobs and has no production worker/queue.
Candidate failures retain the authoritative result and emit a fixed diagnostic.
The sidecar protocol is a client integration contract; a real Ollama serving
wrapper, genuine tokenizer, isolated network/logging policy and authenticated
owner approvals must be supplied before any Gemma experiment. A sidecar's claim
that egress/logging is disabled is not proof of deployment enforcement.

No local execution can substitute for hosted CI, independent reviewer labels,
sealed HOLDOUT, security sign-off, GPU memory/latency evidence or model promotion.
The proposed 500 ms/6 s SLOs are not measured guarantees. Existing transport
deadlines apply; no new claim of a global 12-second aggregate deadline is made.

## Rollback

Set `serviceDesk.assistant.enabled=false` and restart the backend. Keep all
attempt receipts. Candidate execution is disabled by default and can be removed
without changing legacy rule or exact-quote APIs. No database migration is needed.
