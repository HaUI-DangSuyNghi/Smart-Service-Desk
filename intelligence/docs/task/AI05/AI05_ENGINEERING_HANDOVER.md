# AI-05 engineering handover and working v0.2 evaluation

This branch completes the Hybrid candidate and the B0-B3 evaluation workflow,
including the requested use of the new qrels in a working v0.2 dataset. Runtime
promotion and human-reviewed benchmark acceptance are separate decisions.

## Working dataset using the new qrels

`fixtures/intelligence/service-desk/dataset-v0.2-provisional.yaml` explicitly links
the existing v0.2 corpus/queries to all 720 populated judgments in
`retrieval/ai-assisted/qrels-candidate-v0.2.json`. It pins the base dataset, qrel
source manifest and qrel bytes. Working dataset identity:
`sha256:7f18c0e795ff1d50468b6bb2f8307b6034102900888f95497ae7a50f392cded1`.

The source labels retain `AI_ASSISTED_CANDIDATE_QRELS` provenance. At the user's
request, this explicit working profile enables provisional graded engineering
comparison before human review. It does not relabel those annotations as human
votes or modify the original authoritative matrix. Both modes have different
dataset identities and executable entry points.

The working loader validates all 72 query associations, 10 artifact columns,
720 integer grades, primary relevance, rationale codes, source digests and slice
metadata. Mutation tests reject forged review flags, stale pins, foreign artifacts,
duplicate/missing queries/cells, invalid grades and inconsistent source summaries.
The current label distribution is 607 grade-0, 41 grade-1 and 72 grade-2 cells.

The comparison scores only the 48 DEV queries; HOLDOUT has no quality evaluation.
Reports state `judgmentBasis=AI_ASSISTED_PROVISIONAL`, `humanReviewed=false`,
`benchmarkClaim=false`, and `runtimePromotion=NOT_AUTHORIZED`.

## Provisional DEV results

All four candidates use the same v0.2 A2 projection, paragraph chunks, normalization,
query construction and new qrels. Metrics use `graded-artifact-retrieval@0.2.0`.

| Metric | B0 TF-IDF | B1 BM25 | B2 Dense | B3 Hybrid |
| --- | ---: | ---: | ---: | ---: |
| PrimaryHit@1 | 32/48 | 32/48 | 32/48 | 34/48 |
| Hit@3 | 45/48 | 46/48 | 43/48 | 45/48 |
| MRR | 0.853125 | 0.847421 | 0.856597 | 0.878472 |
| RecallRelevant@3 | 0.819444 | 0.850694 | 0.810764 | 0.815972 |
| nDCG@3 | 0.800162 | 0.811219 | 0.794205 | 0.817557 |

Hybrid improves primary-first retrieval and MRR on these provisional labels.
BM25 has the strongest RecallRelevant@3. Hybrid does not dominate all metrics.
Against B0, Hybrid's PrimaryHit@1 improves on 4 queries, regresses on 2 and is
unchanged on 42. MRR improves on 7, regresses on 4 and is unchanged on 37. The
summary retains paired deltas for all 48 queries and all five primary metrics.

Critical slices also remain visible:

| Slice | Queries | B0 PrimaryHit@1 | B3 PrimaryHit@1 | B0 RecallRelevant@3 | B3 RecallRelevant@3 |
| --- | ---: | ---: | ---: | ---: | ---: |
| Hard negative | 15 | 0.733333 | 0.733333 | 0.800000 | 0.866667 |
| Root-cause conflict | 6 | 0.666667 | 0.833333 | 0.750000 | 0.916667 |
| NETWORK | 8 | 0.625000 | 0.500000 | 0.875000 | 0.750000 |
| ACCOUNT | 8 | 0.750000 | 0.875000 | 0.937500 | 0.937500 |
| HARDWARE | 8 | 0.625000 | 0.875000 | 0.895833 | 0.875000 |

NETWORK regressions prevent interpreting the aggregate gain as an unqualified
selection decision. Reports contain all six scenarios, coverage slices, exact
local-ID rankings, per-query metrics, model/config metadata and input digests.
No parameter sweep or tuning followed this first result. The matrix is measurement
evidence, without a fabricated acceptance threshold; report `PASS` denotes valid
measurement/provenance, not a production quality approval.

## Commands to continue working now

Use the existing optional CPU environment and explicitly provisioned pinned model
from AI05-02. These commands work without human-qrel acceptance:

```powershell
python scripts/intelligence/knowledge-data.py provisional-validate `
  --output .local/ai0503/working-data-validation.json
python scripts/intelligence/knowledge-data.py provisional-compare `
  --model-directory .local/ai0502-model --output .local/ai0503/v02-source
python scripts/intelligence/knowledge-data.py selection-readiness `
  --output .local/ai0503/selection-readiness.json
```

After building/installing the generic wheel, repeat with
`python -I scripts/intelligence/knowledge-data.py --installed provisional-compare`.
Require identical source/wheel evidence with:

```powershell
python -I scripts/intelligence/verify-candidate-matrix.py `
  .local/ai0503/v02-source .local/ai0503/v02-wheel
```

The CI CPU matrix runs the same workflow on Ubuntu/Windows and compares Hybrid
legacy controls and graded working-data reports. It retains safe JSON artifacts
for seven days. It does not upload model binaries, vectors, query text or documents.

## Reviewed benchmark and HOLDOUT workflow

`reviewed-compare` is implemented separately. It first uses the existing strict
release loader, before loading a model, requiring reviewed corpus/lexicon/queries,
actual independent human votes and adjudication, the existing agreement thresholds,
rights approval and a valid HOLDOUT digest lock. Provisional labels cannot unlock it.

```powershell
python scripts/intelligence/knowledge-data.py reviewed-compare --split DEV `
  --model-directory .local/ai0502-model --output .local/ai05-reviewed/dev
python scripts/intelligence/knowledge-data.py reviewed-compare --split HOLDOUT `
  --freeze .local/ai05-reviewed/dev/v02-comparison-summary.json `
  --model-directory .local/ai0502-model --output .local/ai05-reviewed/holdout
```

The DEV summary records a freeze digest of all candidate/model/config identities,
data components, projection and metric version. HOLDOUT requires the exact same
freeze. It atomically records an attempt under
`.local/ai05-selection/holdout-ledger/<dataset-digest>.json` before scoring. A second
attempt for that dataset is rejected even if a different configuration is supplied.
Failed attempts stay recorded for operator review; output must be fresh to preserve
DEV evidence. This is a local operator journal, not an external tamper-proof audit
authority. Neither command activates a runtime engine.

## Phase status and remaining decisions

| Work | State |
| --- | --- |
| AI05-01 BM25 foundation | Merged, PR #32 |
| AI05-02 Dense foundation | Merged, PR #33 |
| AI05-03 Hybrid foundation | Implemented on this branch |
| New qrels usable in the working v0.2 dataset | Implemented and exercised on DEV |
| AI05-04 matrix, slices, paired analysis, config freeze and controlled HOLDOUT workflow | Implemented |
| Human-reviewed benchmark acceptance | Pending actual review/rights and HOLDOUT lock |
| Winning runtime engine promotion | Not authorized by provisional evidence; current TF-IDF runtime retained |
| AI05-05 optional reranker | Deferred; no reviewed residual-error evidence justifies it |

Current readiness records 720 pending human judgments, 72 unreviewed queries,
10 unreviewed corpus artifacts, missing independent agreement and an unlocked
HOLDOUT. These do not block the working dataset's provisional DEV experiments.
They remain the explicit prerequisites for closing the official AI-05 benchmark
and promotion decision. No human judgments or approvals were invented.

## Local validation

Verified on 2026-10-21 with the pinned optional CPU model required:

| Check | Result |
| --- | --- |
| Windows source / installed wheel | 629 passed / 629 passed |
| Linux source / installed wheel, Python 3.12.7 container | 629 passed / 629 passed |
| Hybrid legacy-control source/wheel and Windows/Linux evidence | Identical |
| Working v0.2 graded matrix source/wheel and Windows/Linux evidence | Identical |
| Inherited TF-IDF/BM25 source/wheel and frozen A0/A1/A2 evidence | Identical |
| Foundation / strict boundaries / dependency consistency | PASS |
| Boundary mutation checks | 34 passed |
| Hybrid/matrix tests with base dependencies, real-model marker excluded | 31 passed, 1 deselected |
| Real Core HTTP acceptance using the newly built wheel | 8 scenarios passed |
| Wheel model/vector binary exclusion | PASS, wheel size 79,835 bytes |

Evidence is retained locally under `.local/ai0503/` and `.local/ai0503-linux/`,
including JUnit files and the `source`, `wheel`, `v02-source`, `v02-wheel` reports.
Remote CI has been extended for the same comparisons but has not run for this
local commit. The real-model Hybrid test is explicitly marked `dense_model`, so
the inherited base-runtime G2 acceptance retains its zero-skip contract.

`.local/ai0503/core-acceptance.json` records
`PASS_WITH_DOCUMENTED_CORE_LIMITS`: authorized ingest/citation, cross-workspace
denial before scoring, immutable version/catalog rotation, checksum corruption,
real Core 404, refreshed-JWT revocation, authority outage and independent triage.
The inherited limitations about refreshed JWTs and unavailable metadata deletion
remain applicable. The runner tested this working tree before commit and therefore
records develop base `0fa1931124cda9470e974d993eaa44ba2fa753df` as HEAD. Its temporary
identity was removed and the local acceptance stack was stopped.

## Resource observations

A single local Windows CPU run built Hybrid on the 22 shared A2 paragraph chunks
and ranked all 48 working DEV queries. Model loading took 1,780.76 ms; index
construction took 2,944.50 ms. Median query ranking took 30.32 ms and the maximum
was 92.70 ms. Process peak working set was 1,555,017,728 bytes (about 1.45 GiB),
including Python, model and all index state. The numeric Dense vector payload was
67,584 bytes; Hybrid also retains BM25 statistics and shared chunks. These are
local observations, not isolated performance benchmarks or production SLAs.

Safe observations are retained in `.local/ai0503/performance.json`, outside the
deterministic quality evidence. Hybrid reuses the existing pinned model and
dependencies; it adds no new model, dependency or license requirement. No model,
vector or serialized index is included in the wheel or evidence artifacts.
