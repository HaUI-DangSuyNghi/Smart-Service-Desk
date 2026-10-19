# Human annotation protocol v0.2

Status: DRAFT. There are 72 LLM-drafted synthetic queries (48 DEV, 24 proposed
HOLDOUT), ten draft knowledge artifacts and 720 empty cells. No grades, human
reviews, rights approvals or holdout acceptance have been invented. All queries
require human validation; HOLDOUT also requires independent authorship review.

1. Validate the scenario as a realistic request and check generation provenance.
   Read title and description before seeing any ranking. Write the primary and
   secondary information need plus explicit negative evidence in the review reason.
2. Read every canonical artifact, not the retrieval output. Grade 2 for direct
   answer-bearing knowledge, 1 for useful support, 0 for irrelevant material.
   Every query/artifact pair needs a grade and reason. At least one primary
   answer must exist per accepted query; otherwise revise coverage explicitly.
3. DEV needs at least one actual human reviewer. HOLDOUT needs two distinct,
   independent human reviewers for every pair. Enter reviewer identity, grade,
   rationale and the `queryReviewed` / `independent` attestations truthfully.
4. Disagreements require a domain-owner adjudication record; grade-2 disputes
   must never be resolved automatically. Keep the original independent votes.
   The final cell grade must match the consensus or explicit adjudication.
5. Track raw agreement and quadratic weighted Cohen's kappa over HOLDOUT votes.
   Targets are >= 0.85 and >= 0.70. Undefined kappa is not evidence of agreement;
   fix the guidance if observed agreement is poor. Do not edit labels to game it.
6. Review near duplicates and phrase-overlap findings (more than eight consecutive
   identical words) before locking. No identical or near-duplicate scenario may
   cross DEV/HOLDOUT. Corpus writers must not tune against HOLDOUT rankings.
7. Once content, metadata, aliases, renderer and judgments are reviewed, pin the
   collection, query, qrels and projection digests in an explicit holdout lock.
   Mark it frozen before evaluation. Holdout evaluation requires that lock,
   actual review records and approved internal-evaluation rights.

The initial `qrels-v0.2.json` is an annotation worksheet, not authoritative gold.
`build-qrel-matrix.py` only creates blank cells; it never fills grades. It refuses
to overwrite an existing file. Editing a reviewed collection changes its digest
and requires a versioned review/update. Never re-use old qrels on changed content.

No credentials, real emails, student identifiers or telephone numbers are needed.
Use `example.invalid`, `synthetic-user-*` or `token-placeholder` only when a
negative synthetic scenario needs a marker. The safety scanner is an engineering
check, not a claim that arbitrary PII can always be detected automatically.
