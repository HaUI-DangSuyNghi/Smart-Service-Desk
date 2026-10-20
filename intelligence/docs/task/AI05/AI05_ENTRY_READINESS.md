# AI-05 Entry Readiness — Retrieval Intelligence Upgrade

> Source phase: AI-04.5 Knowledge Data Quality & Corpus Enrichment  
> Target phase: AI-05 BM25 / Dense / Hybrid Retrieval  
> Status: **GO FOR ENGINEERING / BENCHMARK ACCEPTANCE GATED**

## 1. Entry decision

AI-05 may start implementation now against the current v0.2 fixture structure.

The following inputs are frozen enough for engine development:

- 10 focused Service Desk knowledge artifacts;
- 19 controlled lexicon concepts;
- 72 `TICKET-V2-*` evaluation queries;
- deterministic v0.2 projection;
- app-owned annotation policy `0.2.0`;
- 720-cell authoritative qrel matrix shape;
- AI-assisted 720-cell candidate review evidence;
- existing A0/A1/A2/A3 experiment evidence;
- AI-04 evaluator, security and provenance gates.

AI-05 must not treat the AI-assisted candidate as benchmark truth.

## 2. Fixture contract

Authoritative evaluation inputs:

```text
fixtures/intelligence/service-desk/knowledge/manifest-v0.2.json
fixtures/intelligence/service-desk/knowledge/lexicon-v0.2.yaml
fixtures/intelligence/service-desk/tickets/tickets-v0.2.jsonl
fixtures/intelligence/service-desk/retrieval/qrels-v0.2.json
fixtures/intelligence/service-desk/dataset-v0.2.yaml
```

Review assistance only:

```text
fixtures/intelligence/service-desk/retrieval/ai-assisted/
├── manifest-v0.2.json
└── qrels-candidate-v0.2.json
```

The review-assistance manifest explicitly excludes candidate labels from dataset identity
and benchmark metrics.

## 3. Architecture boundary for AI-05

```text
Tier 3 Service Desk
  corpus / query / qrel / taxonomy / lexicon
              ↓
Tier 2 HPDI
  generic BM25 / Dense / Hybrid retrievers
  generic ranking interfaces
  generic evaluation adapters
              ↓
Tier 3 adapter / profile
  selects engine/configuration
```

Forbidden:

- Service Desk categories or synonyms inside generic `hdpi`;
- direct engine-specific logic in Core;
- changing FE/BE public contracts just to support a retrieval experiment;
- engine-dependent qrels;
- using candidate qrels as model-selection truth.

## 4. AI-05 experiment order

Implement candidates one variable at a time:

```text
B0  TF-IDF current baseline
B1  BM25 lexical candidate
B2  Dense retrieval candidate
B3  Hybrid BM25 + Dense
B4  optional reranking experiment
```

For each candidate pin:

- engine name/version;
- configuration version;
- normalization version;
- chunk policy;
- corpus/projection digest;
- query strategy;
- dependency versions.

No candidate may silently change corpus, qrels, normalization and engine in the same
comparison.

## 5. Evaluation policy

While authoritative qrels remain pending:

- use frozen legacy A0/A1/A2 controls for engineering regression;
- use the 72-query set for smoke/error analysis only;
- candidate AI-assisted labels may support manual review but may not produce official
  model-selection claims.

After human review and HOLDOUT lock:

- DEV is visible for tuning;
- HOLDOUT is evaluated only after candidate/configuration freeze;
- compare all engines on the same corpus/query/qrel/evaluator identity;
- primary graded metrics:
  - PrimaryHit@1;
  - Hit@3;
  - MRR;
  - RecallRelevant@3;
  - nDCG@3;
- security/provenance/contract failures override quality metrics.

## 6. Definition of Ready

```text
Strict boundary intact                  PASS
Corpus/query fixture alignment          PASS
720-cell matrix alignment               PASS
Candidate review evidence               PASS
AI-04 evaluator available               PASS
Legacy baseline evidence                PASS
Runtime AI↔BE contract unchanged        PASS
Current FE contract unchanged           PASS

Human qrel acceptance                   PENDING
HOLDOUT lock                            PENDING
Remote cross-platform CI                PENDING
```

Therefore:

> **AI-05 implementation may begin. Final benchmark acceptance remains gated.**

## 7. First AI-05 deliverable

The first AI-05 PR should introduce only the generic lexical candidate foundation:

1. generic `RetrievalCandidate`-compatible BM25 implementation in HPDI;
2. pinned BM25 configuration;
3. deterministic ranking/tie-breaking;
4. source/wheel equivalence tests;
5. no Service Desk literals in generic code;
6. comparison runner reusing AI-04 evaluation;
7. evidence report against frozen legacy controls;
8. no Dense/Hybrid code in the same first PR.

This keeps the first AI-05 change reviewable and establishes a clean lexical baseline
before adding embedding infrastructure.

## 8. Stop conditions

Stop AI-05 promotion if any of the following occurs:

- fixture digest drift without an explicit dataset-version decision;
- Service Desk semantics leak into HPDI;
- candidate changes public AI↔BE contracts unnecessarily;
- HOLDOUT is inspected repeatedly during tuning;
- candidate qrels are copied into authoritative qrels without human provenance;
- source/wheel results diverge;
- security/provenance/contract gates fail.
