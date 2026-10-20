# Service Desk Relevance Annotation Policy v0.2

Status: **DRAFT — HUMAN REVIEW REQUIRED**

This policy turns the AI-04.5 research findings into an executable annotation SOP.
It applies to the 72 synthetic queries, 10 curated knowledge artifacts and all
720 query-document judgments. The current qrels remain blank until actual humans
review them. LLM output may assist QA but must never become authoritative gold.

## 1. Relevance scale

Use exactly three ordered grades:

| Grade | Meaning | Rule |
| --- | --- | --- |
| 2 | Primary / answer-bearing | Directly resolves the primary information need, identifies the supported root cause, or is itself the requested policy/routing answer. |
| 1 | Supporting / useful | Materially helps resolution but is not the principal answer. Typical examples are priority, escalation, credential hygiene or a secondary diagnostic check. |
| 0 | Not relevant | Does not materially help the information need. Surface keyword overlap is not enough. |

Multiple grade-2 documents are allowed only for genuinely multi-intent requests or
cases where two distinct answer-bearing responsibilities are explicitly present.

## 2. Six-gate decision tree

Apply the gates in order for every query-document pair.

1. **Insufficient-information gate.** If the request cannot support a concrete diagnosis,
   `intake-evidence-quality` may be grade 2; escalation may be grade 1. Do not guess a
   technical cause from generic words.
2. **Explicit root-cause gate.** Prefer the document matching explicit causal evidence,
   even when another document shares more surface vocabulary.
3. **Direct-answer gate.** If the document directly solves the primary information need,
   assign grade 2.
4. **Supporting-policy gate.** If it only contributes priority, escalation, security hygiene
   or a necessary secondary check, assign grade 1.
5. **Hard-negative gate.** If vocabulary matches but explicit evidence rules the document
   out, assign grade 0 with a `HARD_NEGATIVE_*` code.
6. **Irrelevant-domain gate.** Otherwise assign grade 0 / `IRRELEVANT_DOMAIN`.

Never infer relevance from a retrieval rank or model score.

## 3. Rationale taxonomy

Grade 2 must use a `PRIMARY_*` code:

- `PRIMARY_DIRECT_ANSWER`
- `PRIMARY_ROOT_CAUSE`
- `PRIMARY_INSUFFICIENT_INFO`
- `PRIMARY_SECURITY_BREACH`
- `PRIMARY_ROUTING_NON_IT`
- `PRIMARY_ESCALATION_TRIGGER`

Grade 1 must use a `SUPPORT_*` code:

- `SUPPORT_POLICY_PRIORITY`
- `SUPPORT_SECURITY_HYGIENE`
- `SUPPORT_ESCALATION_PATH`
- `SUPPORT_TRIAGE_HANDOFF`
- `SUPPORT_SECONDARY_CHECK`
- `SUPPORT_RELATED_POLICY`

Grade 0 normally uses `IRRELEVANT_DOMAIN`. Explicit hard negatives use:

- `HARD_NEGATIVE_LEXICAL_TRAP`
- `HARD_NEGATIVE_NEGATIVE_EVIDENCE`
- `HARD_NEGATIVE_SPECULATIVE`

A root-cause-conflict query cannot be released unless at least one relevant misleading
alternative is explicitly represented as a hard negative.

## 4. Annotation blindness

Reviewers must not see:

- TF-IDF/BM25/Dense/Hybrid ranking;
- engine score;
- expected category supplied by a model;
- previous reviewer vote;
- candidate qrels produced by an LLM.

The worksheet contains only query text and canonical knowledge identity/title. Documents
are presented in stable alphabetical `artifactId` order. Review the information need
before judging documents.

## 5. Reviewer allocation

DEV:

- Reviewer A covers 100% of the 480 judgments.
- At least 50% of DEV judgments (240 cells) require a second independent human review.
- Prefer difficult slices for double review: root-cause conflict, ambiguous, multi-intent,
  hard-negative and cross-cutting policy cases.

HOLDOUT:

- Reviewer A and Reviewer B independently judge all 240 cells.
- The same two reviewer aliases must be used consistently.
- Reviewers must not see each other's votes before submission.

Use repository-safe aliases such as `rev_a`, `rev_b`, `adj_lead`; do not store
personal emails or student identifiers.

## 6. Calibration pilot

Before full annotation, both reviewers independently judge 8 representative DEV queries
(80 cells). Compute agreement. If linear weighted Cohen's kappa is below 0.70, stop,
review the precedents and decision tree, then repeat calibration. Do not alter labels only
to increase agreement.

## 7. Disagreement handling

Severity:

- `CRITICAL_2_0`: grade 2 vs 0.
- `MODERATE_1_0`: grade 1 vs 0.
- `MINOR_2_1`: grade 2 vs 1.

All disagreements require an independent domain-owner adjudication before final gold is
accepted. A 2-vs-0 conflict requires explicit review of whether the query is itself too
ambiguous; if so, revise/reject the query instead of forcing a judgment.

Keep both original votes. The final grade and rationale must equal the adjudicated decision
when reviewers disagree.

## 8. Agreement gates

For reviewed release evidence calculate over all double-reviewed cells:

- Raw Agreement >= 0.88
- Linear Weighted Cohen's Kappa >= 0.70
- Krippendorff's Alpha (ordinal) >= 0.70
- Positive Specific Agreement >= 0.75

Quadratic weighted kappa may be reported as sensitivity analysis but is not the primary
acceptance statistic because one-level disagreements must remain visible.

## 9. Query quality and leakage

A query must be revised or rejected when human review confirms:

- `LEXICAL_LEAKAGE`: more than 8 consecutive words copied from a source, query 3-gram
  containment > 0.35, or a long query copies >75% of meaningful tokens from one passage;
- `UNNATURAL_SYNTHETIC_TONE`: wording does not resemble a plausible Service Desk ticket;
- `UNINTENDED_CONTRADICTION`: non-ambiguous query contains mutually incompatible facts;
- `NEAR_DUPLICATE_QUERY`: DEV/HOLDOUT query similarity is too high.

DEV and HOLDOUT scenario families must remain disjoint. Cross-split 3-gram Jaccard must
stay below 0.60. Automated checks flag candidates; humans decide whether wording must
change.

## 10. Corpus/lexicon review before qrels

Every knowledge artifact must be manually classified:

- `DOCUMENT_ACCEPT`
- `DOCUMENT_REVISE`
- `DOCUMENT_REJECT`

Review accuracy, scope, single responsibility, source authority, rights, positive/negative
signals, duplication, credential/PII safety and relationship to other artifacts. Freeze
approved bytes before authoritative qrels are finalized.

## 11. LLM assistance policy

Allowed after or outside the independent judgment step:

- flag suspicious inconsistency;
- suggest possible rationale codes for human consideration;
- find near duplicates or lexical leakage;
- prioritize conflicts for review;
- draft synthetic queries before human validation.

Forbidden:

- automatically filling official `grade` values;
- replacing either independent HOLDOUT reviewer;
- final adjudication;
- changing qrels because a retrieval engine ranked a document differently.

The supplied research/example label matrix is reference material only and does not match
the repository's current `TICKET-V2-*` query wording. It must not be imported.

## 12. Release gates

A reviewed dataset must satisfy all of:

1. Corpus and lexicon reviewed; approved internal-evaluation rights; zero unresolved
   secret/PII findings and exact-byte pins valid.
2. 72/72 queries reviewed; query-quality/leakage findings resolved.
3. Exactly 720/720 qrels populated with grades in {0,1,2}.
4. Required rationale taxonomy and independent reviewer records complete.
5. Required IAA thresholds pass.
6. Zero unresolved reviewer disagreements.
7. HOLDOUT is cryptographically locked to corpus, query, qrel and projection digests
   before final evaluation.

Until these gates pass, the dataset is authored research evidence, not production
benchmark evidence.
