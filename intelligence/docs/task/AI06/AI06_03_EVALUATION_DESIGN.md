# AI06-03 evaluation design and review handoff

State: **synthetic DEV tooling implemented; official quality BLOCKED**.

API-first real synthetic smoke is a separate opt-in tool:
`scripts/intelligence/cloud-api-smoke.py`. It requires approved external cases,
records each Gemini/Groq result independently and never relabels these authored
controls as reviewed gold. See [cloud operations](AI06_API_FIRST_OPERATIONS.md).
Keys/approvals were not supplied; real cloud success/refusal and full cloud
Core/Keycloak/APISIX/browser acceptance remain NOT_VERIFIED.

The application owns `fixtures/intelligence/service-desk/generation/dev-v0.1.json`:
52 AI-authored synthetic controls across 13 categories. Expected behaviors are
provisional design hypotheses, not gold labels. Every review and rights decision
is PENDING. No existing AI05 document, qrel, winner or HOLDOUT is promoted.

The generic evaluator receives an injected runner. The application CLI composes
the active TF-IDF retrieval engine and explicit deterministic quotation baseline.
It checks request-local citation integrity and reports behavior mismatches and
unsafe exact selections separately. Quoting injected or hostile source content
is a safety failure even when exact-quotation checks pass. These findings must
remain visible in source/wheel artifacts. They are not real-model measurements.

Artifacts bind HEAD, actual implementation bytes, profile/contract bytes, runner,
dataset and configuration. Verification recomputes current execution. Boolean
substitution for integer counters and reports from older revisions are rejected.
Artifacts contain case identifiers and counters, without questions, excerpts,
answers, source UUIDs, JWTs or prompts. Artifact retention is seven days in CI.

## Required independent review

For each proposed official case, two domain reviewers independently record:

1. Whether the question is answerable from the currently authorized sources.
2. Which exact excerpts are relevant, safe and useful, and whether sources conflict.
3. Expected refusal, manual review or hard failure and a reason from the rubric.
4. Source provenance, rights to use for inference/evaluation and expiry/revocation.
5. Reviewer identity, timestamp and authentic receipt; disagreements require a
   third reviewer and an adjudication receipt. The author cannot mint approvals.

Data Owner approves source rights. Security reviews malicious exact quotations.
PO and AIE pre-register numeric answerability/source-quality targets and the
environment-specific latency/memory budget before any official HOLDOUT run.
P0 requirements are zero unauthorized disclosures/actions, 100% citation
integrity and appropriate fail-closed behavior on the fixed denied/revoked set.
Measured model quality and resource use need actual approved hardware and model.

## HOLDOUT protocol, not yet authorized

An independent owner must freeze the case/corpus/rubric/threshold digests and
retrieval/generation identities in a signed lock. Keep HOLDOUT data unavailable
to prompt authors and DEV runners. The controlled runner must reserve one
attempt in an append-only owner-managed ledger *before* opening the cases;
crashes consume the attempt. A changed model/prompt/candidate invalidates the
association and requires a new owner decision. Do not delete or rewrite ledger
entries to choose a better attempt. This repository has no approved HOLDOUT
lock, signed labels or official execution adapter. `--mode official` writes a
blocked receipt and exits 2 before dataset/model execution.

## Reproduction

```text
python scripts/intelligence/run-generation-evaluation.py --output .local/ai06-completion/source.json
python scripts/intelligence/verify-generation-evidence.py .local/ai06-completion/source.json
python -I scripts/intelligence/run-generation-evaluation.py --installed --output .local/ai06-completion/wheel.json
python -I scripts/intelligence/verify-generation-evidence.py --installed .local/ai06-completion/source.json .local/ai06-completion/wheel.json
```

G3 stays BLOCKED until genuine reviews, rights, locked HOLDOUT, real-model
security/quality/resource evidence and an independent GO/NO-GO exist.

## Synthetic v0.2 extension

The immutable 52-case fixture remains the historical regression. The separate
400-case application-owned extension is described in
[dataset design](AI06_03_SYNTHETIC_V02_DATASET_DESIGN.md) and
[reproduction handover](AI06_03_SYNTHETIC_V02_HANDOVER.md).
Its provisional labels and additional unsafe findings do not change G3.
