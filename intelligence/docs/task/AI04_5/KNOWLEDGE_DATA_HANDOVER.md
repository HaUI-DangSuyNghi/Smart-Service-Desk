# AI-04.5 knowledge data handover

Status: engineering implementation for review; human annotation and release
acceptance are pending. Branch `feature/ai04-5-knowledge-enrichment` starts at
AI-04 `23a70d9`; it does not imply that AI-04 has been merged into `develop`.
No production ingestion, approved source authority or independent holdout result
is claimed. The user requested blank worksheets because human qrels are absent.

## What is available

- Ten focused draft artifacts, with immutable raw snapshots, exact-byte pins,
  ownership, positive/negative evidence and lineage to the untouched v0.1 sources.
- Nineteen controlled concepts. Ambiguous `mạng` / `network` cannot be expanded
  automatically; none of these aliases changes runtime normalization.
- A deterministic A1 structural projection and A2 lexical projection. Draft
  rendering needs an explicit `--draft`; generated text is not edited by hand.
- Seventy-two LLM-drafted synthetic queries: eight DEV and four proposed HOLDOUT
  cases for each of NETWORK, ACCOUNT, SOFTWARE, HARDWARE, OTHER and AMBIGUOUS.
- A complete 72 × 10 annotation worksheet with null grades, empty reviewer lists
  and no adjudications. Every query, corpus artifact and concept still needs review.
- Graded metrics ready for reviewed data: PrimaryHit@1, RecallRelevant@3 and
  nDCG@3, alongside the inherited Hit@1, Hit@3, MRR and Recall@3.
- Validation for schemas, exact bytes, NFC/LF, paths, lineage, aliases, duplicate
  identities, scenario/split isolation, phrase overlap, credential/PII patterns,
  complete qrels, independent votes, adjudication and release locks.

The legacy inventory and interpretation are in
[CORPUS_INVENTORY_V0.1.md](CORPUS_INVENTORY_V0.1.md). The inherited evaluator is
documented in [AI-04 operations](../AI04/EVALUATION.md).

## Measurements and their limits

| Experiment | Change | Hit@1 | Hit@3 | MRR | Recall@3 |
| --- | --- | ---: | ---: | ---: | ---: |
| A0 | Frozen v0.1 | 7/12 | 11/12 | 0.738889 | 0.875000 |
| A1 | Focused documents | 10/12 | 11/12 | 0.895833 | 0.791667 |
| A2 | Controlled lexical projection | 11/12 | 12/12 | 0.944444 | 0.958333 |
| A3 | Section chunks over the same A2 text | 9/12 | 12/12 | 0.861111 | 0.958333 |

TF-IDF, numerical runtime, normalization and query construction are fixed across
A0/A1/A2. A3 changes only chunking. Paragraph policy remains active because A3
regresses Hit@1 and MRR. Its report deliberately says `FAIL` and the experiment
summary recommends `REJECT_SECTION_POLICY`; reproducing a rejected experiment
successfully does not make that candidate acceptable.

A1/A2/A3 use the frozen twelve legacy queries and unchanged legacy judgments,
with new document rankings projected to their declared legacy source families.
This coarse lineage control lets us inspect corpus contribution without inventing
new qrels. It does not prove relevance of every individual v0.2 document and does
not pass the AI-04 same-dataset comparator under a changed corpus digest. Reports
explicitly mark `sameDatasetComparison=false` and `v02DocumentQrels=false`.
Facilities and intake coverage are draft proposals, not human relevance decisions.
A1's Recall@3 regression is retained, not hidden by its improved Hit@1.

There are no DEV or HOLDOUT quality scores for the 72 new cases. Corpus drafts
were prepared before these query drafts; independent holdout authorship and
human review are still required. `DRAFT_NOT_LOCKED` is not a blind, accepted
holdout. Safety/overlap validation may inspect both splits, while tuning reports
expose DEV findings and the experiments rank only the legacy queries.

## Repository ownership

| Material | Location |
| --- | --- |
| Domain schemas | `contracts/apps/servicedesk/intelligence/` |
| Draft corpus and snapshots | `fixtures/intelligence/service-desk/knowledge/` |
| Queries | `fixtures/intelligence/service-desk/tickets/tickets-v0.2.jsonl` |
| Blank matrix and guidelines | `fixtures/intelligence/service-desk/retrieval/` |
| Versioned pins | `fixtures/intelligence/service-desk/dataset-v0.2.yaml` |
| Frozen legacy evidence | `fixtures/intelligence/service-desk/evaluation/error-analysis/legacy-v0.1.json` |
| Reproducible experiment evidence | `fixtures/intelligence/service-desk/evaluation/corpus-experiments/` |
| Application tooling | `intelligence/src/apps/servicedesk/intelligence/knowledge/` |
| Generic additions | `hdpi.evaluation.graded`, experimental `hdpi.retrieval.section_chunking` |

The generic wheel includes no application knowledge, qrels, aliases, profiles or
schemas. The application CLI stays external even when it evaluates the installed
wheel. Core, runtime profiles, current engine/configuration and baseline v0.1 are
unchanged. Source, wheel and Linux/Windows evidence are compared in CI.

## Reproduce and prepare review

Run in PowerShell from the repository root with CPython 3.12:

```powershell
python -m venv .local/ai045-venv
$py = '.local/ai045-venv/Scripts/python.exe'
& $py -m pip install -r intelligence/requirements.lock
& $py scripts/intelligence/knowledge-data.py validate --output .local/ai045/data-quality.json
& $py scripts/intelligence/knowledge-data.py project --draft --output .local/ai045/projection
& $py scripts/intelligence/knowledge-data.py analyze --output .local/ai045/corpus-quality.json
& $py scripts/intelligence/knowledge-data.py compare --output .local/ai045/source
& $py scripts/intelligence/verify-knowledge-evidence.py .local/ai045/source fixtures/intelligence/service-desk/evaluation/corpus-experiments/paragraph-v0.2
& $py scripts/intelligence/knowledge-data.py chunk-experiment --output .local/ai045/section
& $py scripts/intelligence/verify-knowledge-evidence.py --section .local/ai045/section fixtures/intelligence/service-desk/evaluation/corpus-experiments/section-v0.2
```

Stop on a nonzero exit. `compare` fails if enriched legacy controls regress or do
not improve Hit@1/MRR. Draft `validate` can pass structural checks while reporting
`phaseReadiness=PENDING_HUMAN_REVIEW`; it does not waive release annotation gates.

Generate separate, blank UTF-8 CSV worksheets for real reviewers:

```powershell
& $py scripts/intelligence/knowledge-data.py worksheet --split DEV --output .local/ai045/review/dev.csv
& $py scripts/intelligence/knowledge-data.py worksheet --split HOLDOUT --output .local/ai045/review/holdout-reviewer-a.csv
& $py scripts/intelligence/knowledge-data.py worksheet --split HOLDOUT --output .local/ai045/review/holdout-reviewer-b.csv
```

The tool refuses to overwrite an existing worksheet and never pre-fills grades,
reasons or reviewer identities. DEV has 480 cells; each independent HOLDOUT sheet
has 240. Transfer actual votes and rationale into the versioned qrels through an
explicit reviewed data change; no command merges, labels or approves them
automatically. Use reviewer handles rather than real email addresses in fixtures.
See [annotation guidelines](../../../../fixtures/intelligence/service-desk/retrieval/annotation-guidelines-v0.2.md).

Review each query's information need before looking at a ranking. At least one
primary document is required for each accepted query. HOLDOUT uses two consistent
independent reviewer identities; any disagreement needs a separate domain-owner
adjudication. Keep the original votes. Raw agreement targets 0.85 and quadratic
weighted Cohen's kappa targets 0.70; undefined kappa cannot establish acceptance.

Release commands remain fail-closed until review and locking are complete:

```powershell
& $py scripts/intelligence/knowledge-data.py validate --require-reviewed --output .local/ai045/reviewed-validation.json
& $py scripts/intelligence/knowledge-data.py evaluate --split DEV --output .local/ai045/dev-report.json
& $py scripts/intelligence/knowledge-data.py acceptance --output .local/ai045/acceptance.json
```

The current drafts return nonzero for these commands. Final acceptance requires
approved internal-evaluation rights, reviewed corpus/lexicon/queries, complete
human judgments, sufficient agreement and a lock matching collection, query,
qrels and projection digests. The lock attests independent authorship review and
freezing before evaluation. Freeze selection before inspecting HOLDOUT rankings;
do not use repeated holdout results to tune content. After review, acceptance
compares the same human qrels under A1 and A2, requires DEV improvement and rejects
any gated HOLDOUT regression. This is still authored evidence, not production
accuracy or permission to ingest customer-facing knowledge.

## Tests and wheel verification

Local Windows / CPython 3.12.7 verification on 2026-10-19:

| Check | Result |
| --- | --- |
| Full source suite | 432 passed |
| Full installed-wheel suite (`python -I`) | 432 passed |
| Inherited AI-04 retrieval, conformance and security | PASS; source/wheel identical |
| A0/A1/A2 controls | PASS; source/wheel/frozen evidence identical |
| Section experiment | Reproduced rejection; runtime policy unchanged |
| Data validation and draft projection | PASS; 720 judgments explicitly pending |
| Secret/PII patterns, split leakage, phrase overlap, near duplicates | 0 findings |
| Foundation, strict boundaries and boundary mutations | PASS; 34 boundary tests, zero debt |
| Built wheel and dependency consistency | PASS |

Remote Ubuntu/Windows evidence is pending; it is not implied by local PASS.

```powershell
& $py -m pytest intelligence/tests -q --junitxml=.local/ai045/source-tests.xml
& $py -m build --no-isolation --outdir .local/ai045-dist intelligence
& $py scripts/intelligence/install-built-wheel.py --dist .local/ai045-dist
$env:HDPI_PROFILE_ROOT = (Resolve-Path 'intelligence/src/apps/servicedesk/intelligence/profiles').Path
& $py -I scripts/intelligence/verify-installed-package.py
& $py -I -m pytest -c scripts/intelligence/installed-pytest.ini intelligence/tests -q --junitxml=.local/ai045/wheel-tests.xml
& $py -I scripts/intelligence/knowledge-data.py --installed compare --output .local/ai045/wheel
& $py -I scripts/intelligence/verify-knowledge-evidence.py .local/ai045/source .local/ai045/wheel
```

`--installed` exposes only external application tools on the import path and
requires `hdpi` from `site-packages`. CI preserves safe JSON summaries for seven
days, excludes raw assertion logs and reproduces both the successful corpus
control and the rejected chunk candidate. Formatting was cleaned up locally;
there is no new formatter dependency or format check in CI.

## Remaining human work before AI-05

The engineering deliverables cover DE-00 through DE-06, the worksheet and review
machinery for DE-07, data-quality checks, A0/A1/A2 and the separate A3 experiment.
DE-07 final human qrels, DE-11 independent locked holdout acceptance and DE-12
approved handover remain pending. Before AI-05, reviewers must approve the draft
corpus/lexicon and rights, validate or replace queries, fill/adjudicate judgments,
review agreement, lock the holdout and execute acceptance. Remote cross-platform
and inherited real integration checks must also pass. No BM25, dense retrieval,
RAG or production ingestion is introduced here.
