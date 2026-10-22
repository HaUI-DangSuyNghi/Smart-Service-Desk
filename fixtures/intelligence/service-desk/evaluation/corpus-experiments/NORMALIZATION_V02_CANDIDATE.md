# Normalization v0.2 engineering reference candidates

Generated on 2026-10-22 from the PR35 runtime with CPython 3.12.7 and
`intelligence/requirements.lock`. These are proposed engineering references for
review, not approved benchmarks or a production promotion. Human qrels, rights,
HOLDOUT and selection review remain pending.

`paragraph-v0.2` and `section-v0.2` retain the exact historical evidence produced
with `text-normalization@0.1.0`. The sibling `*-normalization-v0.2` directories
record the new generic normalization and app-owned query identity explicitly.
The source data, projections, gold labels, quality thresholds and section-policy
rejection have not changed. CI compares the new runtime with the separate
candidates using the same exact report/ranking/fingerprint verifier.

The v0.2 normalizer preserves hyphens and spaces in Wi-Fi variants instead of
rewriting application vocabulary. This changes token frequencies and rankings:

| Control | Historical | Normalization v0.2 candidate |
| --- | --- | --- |
| A0 Hit@1 / Hit@3 / MRR | 7/12 / 11/12 / 0.738889 | unchanged |
| A1 Hit@1 / Hit@3 / MRR | 10/12 / 11/12 / 0.895833 | 11/12 / 12/12 / 0.958333 |
| A2 Hit@1 / Hit@3 / MRR | 11/12 / 12/12 / 0.944444 | 11/12 / 12/12 / 0.958333 |
| A3 Hit@1 / Hit@3 / MRR | 9/12 / 12/12 / 0.861111 | unchanged; still rejected |

A1 first-relevant rank improves for TICKET-001 (2 to 1) and TICKET-002 (4 to 2).
A2 improves TICKET-002 (3 to 2). Other ranking-order changes retain the first
relevant rank: A0 TICKET-001; A1 TICKET-010; A2 TICKET-005 and TICKET-010;
A3 TICKET-010 and TICKET-012. No reported aggregate metric regresses.
These results use the existing coarse authored family judgments; they do not
establish reviewed document-level quality.

Reproduce outside authored fixture directories, then compare without rewriting:

```powershell
python scripts/intelligence/knowledge-data.py compare --output .local/pr35/paragraph
python scripts/intelligence/verify-knowledge-evidence.py .local/pr35/paragraph fixtures/intelligence/service-desk/evaluation/corpus-experiments/paragraph-normalization-v0.2
python scripts/intelligence/knowledge-data.py chunk-experiment --output .local/pr35/section
python scripts/intelligence/verify-knowledge-evidence.py --section .local/pr35/section fixtures/intelligence/service-desk/evaluation/corpus-experiments/section-normalization-v0.2
```
