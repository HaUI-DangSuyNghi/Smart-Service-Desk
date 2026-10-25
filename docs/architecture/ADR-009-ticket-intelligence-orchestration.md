# ADR-009: Rule-first ticket intelligence and bounded shadow classifiers

Date: 2026-10-25. Engineering decision implemented behind a default-off endpoint.
Owner acceptance, data rights, model promotion and production release remain pending.

## Context and baseline

AI07 implements the v1.3 classification decision in the supplied master plan.
Branch `feature/ai07-ticket-intelligence` starts at fetched `origin/develop`
`f94f0ef102cbc7d5737ca05270b0affab1fde1a6`, the merge of AI06 PR #37.
The AI06 feature head is `d4b838d7b936647db9d47b7c980272234edd61a5`.
Merge availability does not approve AI06 release, cloud data rights or HOLDOUT.

The external research reference is LabOps commit
`247cd167b6c7890a3aa89700334e077a9be2fad8`. This implementation does not fetch
private LabOps records or import its model. Its four-class artifact has no approved
mapping for the five-class Service Desk taxonomy, particularly ACCOUNT.
The plan's 40 actual versus 30 documented holdout cases and ignored training data
reproducibility remain unverified here. Do not present these as reproduced results.

## Decision

`RULE_PRIMARY + CPU_ML_SHADOW + GEMMA_SHADOW`; LabOps artifact: `NO_DIRECT_PORT`.
Service Desk Rules `service-desk@0.3.0` exclusively determine operational triage.
Candidate disagreement, probability or arrival order cannot replace category,
impact, urgency, priority, suggested group, risk or existing review flags.
The public runtime currently uses `rules_only`. CPU and local classifier seams
are explicit offline opt-ins. They run outside the synchronous assessment path.

Tier 3 owns the controller, snapshot, rule projection, explanation, question
template, five labels, review policy and experiment tooling. Tier 2 owns generic
bounded classification requests/proposals, TF-IDF/Logistic Regression and literal
span validation. A separate integration package owns the local sidecar transport.
Core remains identity, membership, source rights, lifecycle and audit authority.
No Core implementation dependency or frontend change is introduced.

The additive API is `POST .../tickets/{id}/assistant/assess`, contract `0.1.0`.
Its feature flag is `serviceDesk.assistant.enabled=false` by default. Requests
contain only version, guidance flag and an optional bounded question. Input is
read from the authorized persisted ticket. There is no assessment persistence,
cache, job, assignment, claim, close operation or client-selected model/source.

The trusted AI02 capability first validates the wire response. The app projection
then validates the active rules identity, category/group mapping, matched rule
catalog, impact/urgency matrix and literal input evidence. Unknown rules and
changed policy cannot silently become explanations. Missing information preserves
the explicit OTHER/MEDIUM fallback, null impact/urgency and manual-review state.
Risk is always `NOT_ASSESSED / NO_APPROVED_RISK_RUBRIC` with no numerical score.

Guidance reuses the existing authorized AI06 exact-quotation capability. Its
`SUCCESS/GROUNDED` becomes application `SUPPORTED`, retaining public citations and
mandatory human review. Source denial, stale authority and invalid output fail
the whole request. Only trusted operational 503/504 failures degrade to partial
triage. No provider retry, failover or wider egress is added. Semantic synthesis
and semantic answerability claims remain disabled pending independent validation.

Ticket identity, full immutable record equality and actor context are checked
again after all calls, including triage-only and unavailable-provider paths.
Core source rights/version/bytes revalidation remains in the existing AI06
capability; no cached authority or model-produced provenance replaces it.

## Classification contracts and evidence

`bounded-classification@0.1.0` is separate from citation selection v0.1.
Allowed classes are injected by the trusted caller. The decoder rejects unknown
classes, duplicate/extra fields, invalid offsets, tool fields, missing evidence,
unbounded payloads and non-exact copied spans. Offsets are Unicode code points.
ABSTAIN has no class or spans. CPU probabilities carry
`UNCALIBRATED_PROBABILITY` and never imply calibrated confidence.

The CPU candidate fits in memory only after explicit enablement, exact dataset
digest, rights/label receipt references, reviewed labels, full class coverage and
TRAIN/DEV/HOLDOUT family and exact normalized duplicate checks. Only TRAIN enters
fit. There is no external pickle/joblib deserialization. The manifest records
runtime versions, learned-state digest, features, parameters and policy/data
identities. Receipts require authentic external verification by the caller.
Near-duplicate and incident-family adjudication remain human responsibilities.

The optional local adapter requires independently provisioned artifact hashes,
operator receipts, exact tokenizer/sidecar identity, a literal loopback origin,
context at most 2048, concurrency one, bounded output and timeouts. It never pulls
Gemma weights or redirects to a cloud endpoint. Real Gemma/Q4 provisioning,
Ollama serving wrapper, VRAM/latency measurements and model-quality evaluation
remain blocked until genuine artifacts and deployment approvals exist.

## Acceptance and consequences

Local source/bytecode tests and DEV engineering controls verify implementation,
not classification accuracy, semantic superiority or production readiness.
Historical AI02–AI06 files and labels are never rewritten. The immutable inventory
binds exact files to the develop baseline. Worksheets contain blank DEV labels
and input hashes only; HOLDOUT is never exported. Acceptance recomputes all 48
DEV rule outcomes and identities instead of trusting a report's declared PASS.
Any promotion request fails while labels, rights and independent holdout are pending.

Rollback disables the endpoint flag; the old triage and assistance APIs remain
available with their existing configuration and contracts. Removing CPU/GPU
provisioning has no effect on authoritative rule processing.
