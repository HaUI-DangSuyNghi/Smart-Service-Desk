# AI-04.5 Qrel Research Implementation

Status: **IMPLEMENTED ENGINEERING CONTROLS / HUMAN LABELING STILL PENDING**

This document records how the deep-research relevance-annotation findings were translated
into the AI-04.5 repository without changing public AI↔BE or FE contracts.

## Implemented

- Three-level relevance semantics remain `2 / 1 / 0`.
- Versioned application-owned rationale taxonomy:
  - `PRIMARY_*`
  - `SUPPORT_*`
  - `HARD_NEGATIVE_*`
  - `IRRELEVANT_DOMAIN`
- Root-cause-conflict queries require an explicit hard-negative judgment before release.
- Model-blind reviewer worksheets now:
  - sort artifacts by stable `artifactId`;
  - contain no retrieval rank or model score;
  - include grade, rationale code, reviewer alias and review status.
- Reviewer calibration precedents added in `reviewer-training-v0.2.md`.
- Independent-review agreement now reports:
  - Raw Agreement;
  - Linear Weighted Cohen's Kappa;
  - Quadratic Weighted Cohen's Kappa as sensitivity evidence;
  - Krippendorff's Alpha (ordinal);
  - Positive Specific Agreement.
- Disagreements are classified:
  - `CRITICAL_2_0`
  - `MODERATE_1_0`
  - `MINOR_2_1`
- New `review-report` CLI compares two blinded reviewer worksheets without modifying
  authoritative qrels.
- Reviewed release requires:
  - Raw Agreement >= 0.88;
  - Linear Weighted Kappa >= 0.70;
  - Ordinal Alpha >= 0.70;
  - Positive Specific Agreement >= 0.75;
  - 100% dual review for HOLDOUT;
  - at least 50% dual-reviewed DEV judgments.
- Query review contracts support:
  - `QUERY_ACCEPT`
  - `QUERY_REVISE`
  - `QUERY_REJECT`
  - structured issue codes.
- Knowledge review contracts support:
  - `DOCUMENT_ACCEPT`
  - `DOCUMENT_REVISE`
  - `DOCUMENT_REJECT`
- Release requires all reviewed queries/documents to be explicitly accepted.
- Cross DEV/HOLDOUT near-duplicate threshold tightened to < 0.60.
- Query/document lexical leakage auditing now includes:
  - longest common contiguous phrase;
  - trigram containment;
  - token-containment review signal.
- Existing exact-byte/schema pins were updated for changed Tier-3 contracts.

## Intentionally not implemented

The supplied external example label matrix was **not imported** into
`qrels-v0.2.json`.

Reason: its `SD-Q*` identifiers and query wording do not match the repository's current
`TICKET-V2-*` collection. More importantly, research policy explicitly prohibits LLM or
research-generated labels from becoming authoritative ground truth without independent
human judgment.

The authoritative qrels therefore remain blank until actual reviewers act.

## Human workflow

1. Human-review corpus + lexicon.
2. Human-review 72 query drafts and set explicit review decisions.
3. Generate blinded reviewer worksheets.
4. Run 8-query calibration pilot.
5. Reviewer A labels all DEV and HOLDOUT.
6. Reviewer B independently labels all HOLDOUT and at least 50% of DEV judgments,
   prioritizing difficult slices.
7. Run `review-report`.
8. Adjudicate every disagreement while preserving original votes.
9. Compile final reviewed qrels.
10. Lock HOLDOUT digests before final model evaluation.

Example:

```powershell
python scripts/intelligence/knowledge-data.py worksheet `
  --split HOLDOUT `
  --output .local/ai045/review/holdout-reviewer-a.csv

python scripts/intelligence/knowledge-data.py worksheet `
  --split HOLDOUT `
  --output .local/ai045/review/holdout-reviewer-b.csv

python scripts/intelligence/knowledge-data.py review-report `
  --reviewer-a .local/ai045/review/holdout-reviewer-a.csv `
  --reviewer-b .local/ai045/review/holdout-reviewer-b.csv `
  --output .local/ai045/review/holdout-agreement.json
```

## Architecture

All relevance semantics remain Tier-3 Service Desk concerns.

```text
apps / fixtures / contracts/apps/servicedesk
        ↓
generic HDPI evaluation/retrieval
```

No Service Desk rationale code, category or annotation policy was added to generic
`hdpi`, Core, Java BE runtime contracts or FE contracts.

## Completion state

```text
Research → engineering policy     DONE
Schema/tooling implementation     DONE
Blind worksheet workflow          DONE
IAA/conflict reporting            DONE
Release gates                     DONE
Human corpus review               PENDING
Human query review                PENDING
Human qrels                       PENDING
Adjudication                      PENDING
HOLDOUT lock                      PENDING
Remote CI                         PENDING
```
