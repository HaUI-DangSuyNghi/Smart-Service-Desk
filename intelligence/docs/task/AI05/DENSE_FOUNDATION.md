# AI05-02: offline dense retrieval foundation

Base: `develop@3b637f4f072a7c36aa4bf0ba4debad550bdcf9d2`.
Branch: `feature/ai05-02-dense-retrieval`.

## Entry check and scope

[AI05-01 PR #32](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/pull/32)
was merged, including corrective commit `ecffac1`. Its Intelligence runtime, Core,
tier boundaries and integrated acceptance workflows all completed successfully.
The correction makes the frozen A2 projection the primary engine comparison; A0
remains the separate compatibility regression. This phase preserves that distinction.

The generic wheel now contains an `EmbeddingProvider` protocol, local ONNX provider,
immutable in-process vector index, cosine engine and `DenseCandidate` adapter.
Model loading remains separate from scoring. Production `SearchService`, TF-IDF
partitions, Core authorization, citations, public contracts and executable profiles
are unchanged. This phase introduces no Hybrid, vector database or runtime promotion.

## Pinned reference

`dense@0.1.0` uses `dense-e5-cpu-config@0.1.0` and the upstream
`intfloat/multilingual-e5-small` ONNX export at revision
`614241f622f53c4eeff9890bdc4f31cfecc418b3`.
[Model/dependency governance](../../governance/ai05-dense-model.md) records declared
licenses, file sizes, hashes, sources, dependency closure and release constraints.
The two public model artifacts total 487,351,240 bytes and stay outside Git/wheels.

The frozen inference behavior is:

- 384 dimensions; float32 ONNX hidden states, float64 mask-mean pooling and vectors;
- the model's `query: ` / `passage: ` prefixes and right truncation at 512 tokens;
- one input per inference call, CPU provider only, sequential execution, one thread,
  deterministic compute enabled and graph optimizations disabled;
- ONNX Runtime 1.20.1, Tokenizers 0.21.0 and inherited NumPy 2.2.6;
- unchanged `markdown-paragraph@0.1.0`, `text-normalization@0.1.0` and
  `title+newline+description@0.1.0` inputs from A2.

Vectors must have the pinned dimension, finite real values and nonzero norm.
Normalized dot products use `math.fsum`; only positive cosine hits are returned.
Negative and zero similarities are omitted. A semantic result does not require an
exact lexical match. Empty/punctuation-only queries and empty corpora return no hits.
Ties use score descending, source ID, ordinal, then chunk ID ascending.
The inherited evaluator deduplicates source IDs and then legacy families.

Missing/corrupt artifacts, model/config identity drift, wrong tensor shape/dtype,
NaN/Inf, zero vectors, duplicate identities, resource bounds and inference failures
fail closed. There is no lexical fallback. Scores are similarities, not probabilities.
Index representations suppress chunks/vectors, and reports contain no text or vectors.

## Engineering evidence on frozen A2

All candidates consume the same 10-source A2 projection, paragraph chunks, 12 legacy
queries and legacy-family judgments. The dataset identity is
`sha256:6ec3574b03e603b6bacfa5c538b31c3051493cdda75c8c6609b40be08c01f183`;
projection digest is `276abb40b41eb0653431599916e863966290047b02f61f3aa1382ac81624f54b`.

| Metric | B0 TF-IDF | B1 BM25 | B2 Dense |
| --- | ---: | ---: | ---: |
| Hit@1 | 11/12 | 11/12 | 10/12 |
| Hit@3 | 12/12 | 11/12 | 12/12 |
| MRR | 0.944444 | 0.937500 | 0.902778 |
| Recall@3 | 0.958333 | 0.875000 | 0.875000 |

Dense regresses from B0 by 0.083333 Hit@1, 0.041667 MRR and 0.083333 Recall@3.
It preserves Hit@3 and does not justify replacing TF-IDF on this evidence. No model,
prefix, chunk policy or numerical setting was tuned after observing the result.
The candidate remains available for the later Hybrid experiment.

This is coarse authored engineering evidence, not a human-reviewed v0.2 document
benchmark. The 72 draft queries are used only for deterministic smoke checks; no
pending or AI-assisted relevance labels produce quality metrics. HOLDOUT selection
evaluation is not run, and runtime promotion is explicitly `NOT_AUTHORIZED`.

`qualityResult` retains the inherited legacy-floor policy from AI05-01. A `PASS`
means that floor is met, not that Dense beats A2 TF-IDF. The B2-minus-B0 deltas remain
explicit. `engineeringResult=PASS` means the experiment completed with all identity
and representation checks intact. A recorded quality rejection is still a completed
experiment: `--include-dense` returns zero with `qualityResult=FAIL` when applicable.
Configuration, provenance and validation errors exit nonzero. The equivalence gate
requires identical reports and preserves both quality rejection and promotion blocks.

Dense report fingerprint:
`sha256:c82041725f828262b0173a549d59946e384956f4637cbfc6e7997601e52d12e1`.
Model artifact-set digest:
`sha256:8f5c24508c0307b029da65796be624c0a3345be7f5b96622a04e0b6285bc53fb`.

## Reproduce

Use CPython 3.12.7 and a separate environment. Optional model dependencies do not
change the inherited runtime lock:

```powershell
python -m pip install -r intelligence/requirements-dense.lock
python -m pip check
python scripts/intelligence/prepare-dense-model.py --directory .local/ai0502-model
python scripts/intelligence/prepare-dense-model.py --offline --directory .local/ai0502-model
$env:HDPI_PROFILE_ROOT = (Resolve-Path 'intelligence/src/apps/servicedesk/intelligence/profiles').Path
$env:HDPI_DENSE_MODEL_ROOT = (Resolve-Path '.local/ai0502-model').Path
$env:HDPI_REQUIRE_DENSE_MODEL = '1'
python -m pytest intelligence/tests -q --junitxml=.local/ai0502/source-tests.xml
python scripts/intelligence/knowledge-data.py engine-compare --include-dense `
  --model-directory .local/ai0502-model --output .local/ai0502/source
```

Only explicit provisioning downloads public artifacts, always from the pinned
revision and with exact size/SHA-256 verification. The provider never calls that
script or a hub API. Offline tests block network connections during provider
construction and inference. No protected content is sent to an embedding service.

Build/install the wheel with existing helpers, then run the installed suite and
the same A2 comparison:

```powershell
python -m build --no-isolation --outdir .local/ai0502-dist intelligence
python scripts/intelligence/install-built-wheel.py --dist .local/ai0502-dist
python -I -m pytest -c scripts/intelligence/installed-pytest.ini intelligence/tests `
  -q --junitxml=.local/ai0502/wheel-tests.xml
python -I scripts/intelligence/knowledge-data.py --installed engine-compare `
  --include-dense --model-directory .local/ai0502-model --output .local/ai0502/wheel
python -I scripts/intelligence/verify-knowledge-evidence.py --dense `
  .local/ai0502/source .local/ai0502/wheel
```

The generic retrieval CLI and AI-04 runner also accept `--candidate dense
--model-directory ...`; their dataset remains the historical A0 track. The main
AI-05 comparison is the A2 command above. Without `--include-dense`, the existing
`engine-compare` command continues to emit the same B0/B1 artifacts and summary.

## Verification

Local verification on 2026-10-21 used the pinned dependencies and model artifacts:

| Check | Result |
| --- | --- |
| Windows source / installed wheel, including the real model | 580 / 580 passed |
| Linux container source / installed wheel, including the real model | 580 / 580 passed |
| B0/B1/B2 source/wheel evidence on each OS | Identical |
| B0/B1/B2 Windows/Linux evidence in both modes | Identical |
| Inherited TF-IDF/BM25 historical source/wheel evidence | Identical |
| Frozen A0/A1/A2 corpus experiment evidence | Identical |
| Dependency consistency, foundation, strict boundaries | PASS |
| Boundary mutation proofs | 34 passed |
| Dense tests with only the inherited dependency set and no model configured | 73 passed, 4 optional checks skipped |
| Real-Core acceptance using the newly built wheel's inherited runtime | 8 scenarios passed |

The optional CPU CI matrix requires the real model; missing configuration cannot
silently skip its tests. Its reports upload only safe JSON evidence for seven days.
The ordinary base-dependency suite may skip the four optional real-model checks.
New GitHub jobs have been configured but have not been executed remotely on this
branch as part of the local commit task.

Evidence lives in `.local/ai0502/{source,wheel}` and
`.local/ai0502-linux/{source,wheel}`, with JUnit files beside those directories.
`.local/ai0502/core-acceptance.json` records
`PASS_WITH_DOCUMENTED_CORE_LIMITS`: authorized ingest/citation, cross-workspace
denial before scoring, immutable version/catalog rotation, checksum corruption,
real Core 404, refreshed-JWT revocation, authority outage and independent triage.
The inherited limitations about refreshed JWTs and unavailable metadata deletion
remain applicable. Validation built this working tree before commit, so the runner
records the develop base as HEAD. Its temporary identity was removed and the local
acceptance stack was stopped.

## Resource observations

A single local Windows run on the same A2 sources/chunks and 12 legacy queries
observed the following. Timings are outside deterministic evidence, and they are
not production SLAs or an isolated performance benchmark.

| Observation | TF-IDF | BM25 | Dense |
| --- | ---: | ---: | ---: |
| Index construction | 29.83 ms | 28.13 ms | 2775.09 ms |
| Median query ranking | 0.54 ms | 0.69 ms | 30.37 ms |
| Maximum query ranking | 0.76 ms | 1.39 ms | 37.86 ms |
| Model load | none | none | 1925.35 ms |

The Dense numeric vector payload is 67,584 bytes for 22 x 384 float64 values, in
addition to chunks, Python objects and the model. Process peak working set after
Dense loading/indexing/querying was about 1.56 GB. For context, TF-IDF CSR numeric
buffers occupied 61,844 bytes and BM25's hypothetical lexical-statistics JSON
182,579 bytes. These are partial representation sizes, not comparable total index
memory measurements. No serialized index or vectors were persisted. Safe local
observations are retained in `.local/ai0502/performance.json`.

Hybrid work starts from reviewed develop after this phase is merged, as required
by the implementation guide. Human qrel acceptance remains a separate prerequisite
for final candidate selection and runtime promotion.
