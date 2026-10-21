# AI05-01: BM25 lexical foundation

Base: `develop@ee1771352a74043e9685c74d5ddb5cf0a8291272`.
Branch: `feature/ai05-01-bm25-foundation`.

This change adds a generic, authoring-only BM25 challenger and injects it into the
AI-04 evaluator. Production retrieval continues to use the existing TF-IDF engine,
authorized partitions and citation verifier. Public contracts, executable profiles,
corpus bytes, lexicon and relevance labels are unchanged.

## Frozen behavior

`bm25@0.1.0` uses `bm25-config@0.1.0`, with `k1=1.2` and `b=0.75`.
The implementation uses CPython 3.12, Unicode 15.0.0 and standard-library math;
it adds no dependencies, models, index persistence or external service calls.

The candidate holds the TF-IDF representation constant:

- normalization: `text-normalization@0.1.0`;
- chunking: `markdown-paragraph@0.1.0`;
- query construction: `title+newline+description@0.1.0`;
- case-sensitive Unicode word token pattern `(?u)\b\w\w+\b` over normalized text;
- unigram and adjacent bigram features, without stop words or accent stripping.

For each chunk, `dl` is its total unigram and bigram feature count. `N` includes
all supplied chunks, including chunks with no lexical features. `df(t)` counts
chunks containing the feature, and `avgdl` is the mean feature count across chunks.
The score sums the following contribution once for each distinct matching query
feature (query term frequency is deliberately binary):

```text
idf(t) = log(1 + (N - df(t) + 0.5) / (df(t) + 0.5))
score(t,d) = idf(t) * tf(t,d) * (k1 + 1)
             / (tf(t,d) + k1 * (1 - b + b * dl(d) / avgdl))
```

Chunk and feature statistics have canonical ordering, and summation uses
`math.fsum`. Hits sort by score descending, source ID ascending, ordinal ascending,
then chunk ID ascending. The document-level evaluator retains its existing first-hit
source deduplication. Empty vocabulary, empty queries and unmatched queries return
no hits. Zero scores are omitted; negative or non-finite scores fail closed.

Only the published configuration is supported. Config mutations, candidate identity
drift, foreign index types, duplicate chunk/source IDs, oversized sources/corpora,
invalid topK and oversized queries are rejected. These derived authoring indexes
do not grant runtime authorization or produce Core citations.

## Legacy engineering comparison

Both candidates use the same 12 legacy queries, corpus bytes, manual relevance
controls, evaluator and metric definition. Dataset digest:
`sha256:f4a08ef239f08e9f93f40258f6b8dfc193437a9180e8ad873694159fbe01029f`.

This is authored regression evidence, **not a v0.2 benchmark or a runtime promotion
decision**. The frozen Hit@1 and Hit@3 floors remain enforced; the CLI cannot accept
or rewrite a baseline.

| Metric | TF-IDF B0 | BM25 B1 |
| --- | ---: | ---: |
| Hit@1 | 7/12 | 7/12 |
| Hit@3 | 11/12 | 11/12 |
| MRR | 0.738889 | 0.725000 |
| Recall@3 | 0.875000 | 0.833333 |

BM25 passes the frozen Hit@1/Hit@3 gates but regresses MRR by 0.013889 and Recall@3
by 0.041667. Those secondary regressions remain visible in the comparison report;
no parameter tuning or baseline change was used to hide them. This evidence does
not justify replacing TF-IDF in the runtime.

Human-reviewed v0.2 qrels and HOLDOUT locking remain prerequisites for final model
selection. The 72-query smoke test checks only deterministic rankings and known
artifact IDs. It computes no relevance metrics and reads no AI-assisted labels.

## Reproduce

Use the existing CPython 3.12.7 environment and `intelligence/requirements.lock`.
`HDPI_PROFILE_ROOT` must point to the external Service Desk profile directory.

Run all unit, contract, retrieval, evaluation, data-quality and regression tests:

```powershell
python -m pytest intelligence/tests -q --junitxml=.local/ai05/source-tests.xml
python scripts/check-tier-boundaries.py
python scripts/test-tier-boundaries.py
```

The stable runner defaults to TF-IDF. Explicit candidate selection affects only
authoring evaluation; it does not select an engine in SearchService:

```powershell
$evalArgs = @(
  '--dataset', 'fixtures/intelligence/service-desk',
  '--baseline', 'fixtures/intelligence/service-desk/evaluation/baseline-v0.1.json',
  '--checks', 'fixtures/intelligence/service-desk/evaluation/security-checks.json',
  '--conformance-config', 'fixtures/intelligence/service-desk/evaluation/conformance-config.json'
)
python scripts/intelligence/run-evaluation.py @evalArgs `
  --candidate bm25 --junit .local/ai05/source-tests.xml --output .local/ai05/source
```

For retrieval-only experiments the existing module also accepts `--candidate bm25`:

```powershell
python -m hdpi.evaluation.retrieval --candidate bm25 `
  --dataset fixtures/intelligence/service-desk `
  --baseline fixtures/intelligence/service-desk/evaluation/baseline-v0.1.json `
  --output .local/ai05/retrieval-report.json
```

Build the wheel, install it in the pinned environment, run the installed suite with
`python -I -m pytest -c scripts/intelligence/installed-pytest.ini`, then run the same
evaluation with `python -I scripts/intelligence/run-evaluation.py --installed`.
Require identical source/wheel reports with:

```powershell
python -I scripts/intelligence/verify-evaluation-equivalence.py `
  .local/ai05/source .local/ai05/wheel
```

The report carries engine/config/runtime identities, chunk/normalization/query
versions, dataset/component digests, metric version, exact rankings, gate decisions
and fingerprints. It contains no query or document text. Safe CI artifacts are
retained for seven days. The existing Ubuntu/Windows matrix now evaluates BM25 in
source and wheel modes and compares both operating systems; the inherited TF-IDF,
knowledge controls and real-Core acceptance jobs remain required.

## Validation and operational observations

Local verification on 2026-10-21 used the pinned dependencies and CPython 3.12.7:

| Check | Result |
| --- | --- |
| Dependency consistency and foundation validation | PASS |
| Strict tier boundaries and 34 boundary mutation proofs | PASS |
| Windows source / installed wheel | 500 / 500 tests passed |
| Linux container source / installed wheel | 500 / 500 tests passed |
| BM25 source/wheel evidence on each OS | Identical |
| BM25 Windows/Linux evidence in both modes | Identical |
| TF-IDF source/wheel evaluation and frozen A0/A1/A2 evidence | Identical |
| Retrieval quality floors, contract conformance and security/provenance gates | PASS |
| Real Core HTTP acceptance, inherited TF-IDF runtime | 8 scenarios passed |

BM25 retrieval fingerprint:
`sha256:9bea89b7e5a23cbecf987c8a5abb20cd545ecdb1f2bae94484e453415c4db575`.
Serialized retrieval report digest:
`sha256:8b8dd52a1ce538e05f191443a582e1e5628e6e23f35e3881bd381c6b1c3508aa`.

Local evidence is under `.local/ai05/{source,wheel,tfidf-source,tfidf-wheel}` and
`.local/ai05-linux/{source,wheel}`. Source and wheel JUnit files are retained beside
those directories. Real-Core proof is `.local/ai05/core-acceptance.json`, with
`PASS_WITH_DOCUMENTED_CORE_LIMITS`. It covers authorized ingest/search/citation,
cross-workspace denial before scoring, immutable version/catalog rotation,
checksum corruption, real Core 404, refreshed-JWT membership revocation, authority
outage and independent triage. The inherited runner's documented limitations still
apply: actual metadata deletion is unavailable and revocation uses refreshed JWTs.
The package was built from this working tree before commit, so the acceptance
runner recorded the base commit as HEAD. Its temporary identity was removed and
the isolated acceptance stack was stopped.

The GitHub Ubuntu/Windows and Core jobs are configured but have not been executed
remotely as part of this local commit task.

A single instrumented Windows smoke run over the fixed draft v0.2 A2 projection
(10 sources, 22 chunks, 72 queries) observed 355 ms index construction, 3.20 ms median
query ranking and 23.96 ms maximum query ranking. Build peak tracked Python memory
was 946,027 bytes. The hypothetical JSON representation occupied 189,833 bytes;
no serialized index was written. Construction was measured with `tracemalloc`
enabled and these figures are observations, not an SLA or comparative speed claim.
No embedding model is loaded. Safe observations are in
`.local/ai05/smoke-performance.json`; timings are excluded from deterministic
evaluation artifacts.

Dense/Hybrid development starts on a later branch after this foundation is reviewed
and merged, as specified by the AI-05 entry readiness document.
