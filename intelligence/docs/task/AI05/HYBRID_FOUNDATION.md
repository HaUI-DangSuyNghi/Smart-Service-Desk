# AI05-03: deterministic Hybrid retrieval

Base: `develop@0fa1931124cda9470e974d993eaa44ba2fa753df`.
Branch: `feature/ai05-03-hybrid-retrieval`.

AI05-02 PR #33 was merged, including `ef5c6cc` which separates optional real-model
checks from the base G2 suite. Its runtime, Core, integrated and boundary workflows
completed successfully. New real-model tests retain the `dense_model` marker.

## Frozen fusion behavior

`hybrid@0.1.0` uses `hybrid-rrf-config@0.1.0`, combining the existing
`bm25@0.1.0 / bm25-config@0.1.0` and
`dense@0.1.0 / dense-e5-cpu-config@0.1.0` candidates. The Dense model, artifact hashes
and optional dependencies are unchanged; see [model governance](../../governance/ai05-dense-model.md).

Fusion follows [reciprocal rank fusion](https://cormack.uwaterloo.ca/cormacksigir09-rrf.pdf):

```text
score(document) = sum(1 / (60 + documentRankInChild))
```

Both children operate on one identical, canonical tuple of paragraph chunks.
The complete child rankings are validated for known chunk bytes/identities,
duplicates, positive finite scores and deterministic ordering. Each child is then
deduplicated by source ID, keeping its first/best chunk, before taking 20 documents.
This keeps candidate depth separate from final topK and prevents repeated chunks
from exhausting a child's candidate pool.

Only ranks enter fusion; raw BM25/cosine scales do not affect the fused score.
Fractions are summed and ordered exactly before converting the final score to a
float. Ties use source ID, ordinal and chunk ID ascending. Each source contributes
one output hit, represented by its lowest-ordinal contributing chunk, then lowest
chunk ID. The source remains an authoring identity, not a Core citation.

`rank()` returns the fused union of the two depth-20 document lists. `search()`
defaults to final topK 3 and accepts the inherited integer 1..10 request range.
All fusion parameters, deduplication, representative selection, child identities
and runtime metadata are pinned. Experimental defaults were fixed before viewing
the comparison; they are not claimed to be tuned on human-reviewed DEV.

If either child fails, Hybrid fails. There is no fallback or partial-result
substitution. A legitimate empty child ranking is allowed; two empty rankings
return no hits. Wrong config/index/model/corpus associations, unstable child order,
foreign chunk bytes, duplicate chunk IDs and invalid query/topK fail closed.

Generic fusion stays inside `hdpi`. Application qrels, slices, projection and
selection policy remain outside the generic wheel. Core, public contracts,
production TF-IDF partitions and the citation verifier remain unchanged.

## Frozen A2 legacy-family control

The legacy 12-query engineering control still runs all candidates on the same A2
projection and coarse legacy-family labels. It remains separate from the newly
enabled v0.2 working-qrel comparison.

| Metric | B0 TF-IDF | B1 BM25 | B2 Dense | B3 Hybrid |
| --- | ---: | ---: | ---: | ---: |
| Hit@1 | 11/12 | 11/12 | 10/12 | 11/12 |
| Hit@3 | 12/12 | 11/12 | 12/12 | 12/12 |
| MRR | 0.944444 | 0.937500 | 0.902778 | 0.958333 |
| Recall@3 | 0.958333 | 0.875000 | 0.875000 | 0.916667 |

Hybrid improves MRR but regresses Recall@3 from B0 on this coarse control. This
does not establish a production winner. B0/B1/B2 reports retain their original
fingerprints; B3 fingerprint is
`sha256:77baf9bafe58c6a9735742c287ab23c36000a06e55410868028fa02066c65534`.

```powershell
python scripts/intelligence/knowledge-data.py engine-compare --include-hybrid `
  --model-directory .local/ai0502-model --output .local/ai0503/source
python -I scripts/intelligence/knowledge-data.py --installed engine-compare --include-hybrid `
  --model-directory .local/ai0502-model --output .local/ai0503/wheel
python -I scripts/intelligence/verify-knowledge-evidence.py --hybrid `
  .local/ai0503/source .local/ai0503/wheel
```

The generic retrieval CLI and AI-04 runner also accept `--candidate hybrid
--model-directory ...`; those remain the historical A0 compatibility track.

## Validation

Hybrid math tests cover hand-computed rank sums, exact ties, score-scale invariance,
deduplication before depth, empty rankings, child failures, foreign IDs/bytes,
duplicate chunks, query/topK bounds and config/index mutations. The real model
test verifies repeatability after source reordering. The new working-data tests
also exercise all four engines, graded metrics, per-query deltas and slice analysis.

Final source/wheel/OS results and Core acceptance are recorded in the
[AI-05 engineering handover](AI05_ENGINEERING_HANDOVER.md).
