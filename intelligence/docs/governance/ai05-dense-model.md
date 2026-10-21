# AI05-02 model and dependency provenance

Reviewed for local engineering use on 2026-10-21. This records provenance and
declared licenses; it does not change the repository's release approval policy.

## Model artifacts

Source: [intfloat/multilingual-e5-small](https://huggingface.co/intfloat/multilingual-e5-small).
Frozen revision: `614241f622f53c4eeff9890bdc4f31cfecc418b3`.
Use the [model card at that revision](https://huggingface.co/intfloat/multilingual-e5-small/blob/614241f622f53c4eeff9890bdc4f31cfecc418b3/README.md)
for the declared `MIT` license, supported languages and intended retrieval usage.
Vietnamese is listed among the supported languages. This is not a claim about
quality on the Service Desk dataset. The accompanying
[technical report](https://arxiv.org/abs/2402.05672) describes multilingual E5.

The reference uses the upstream float32 ONNX export, without quantization or
hardware-specific AVX512 weights. No model binary is committed or bundled in the
generic wheel. `hdpi.retrieval.dense.artifacts` is the authoritative pin list.

| File | Bytes | SHA-256 |
| --- | ---: | --- |
| `onnx/model.onnx` | 470268510 | `ca456c06b3a9505ddfd9131408916dd79290368331e7d76bb621f1cba6bc8665` |
| `onnx/tokenizer.json` | 17082730 | `0b44a9d7b51c3c62626640cda0e2c2f70fdacdc25bbbd68038369d14ebdf4c39` |

Total artifact footprint is 487,351,240 bytes, before runtime memory. Model input
uses `query: ` and `passage: ` prefixes, right truncation at 512 tokens, attention
mask mean pooling, float64 pooling/normalized vectors and cosine similarity.
The CPU reference processes one input at a time with one thread, sequential
execution, deterministic compute and graph optimization disabled.

The model card declares MIT, with no additional model-specific restriction stated
there. Preserve applicable copyright/license notices if redistributing model
artifacts, and review the actual notices for the intended distribution. Engineering
provisioning downloads only the named public files; it grants no rights to private
corpora or pending-rights fixtures. The separate project release/license gate
remains applicable.

## Optional dependencies

`requirements-dense.lock` includes the unchanged inherited lock and pins the extra
dependency closure. The wheel exposes an optional `dense` extra; ordinary TF-IDF /
BM25 installation does not acquire the model stack.

| Direct dependency | Pin | Primary license source |
| --- | --- | --- |
| ONNX Runtime CPU | 1.20.1 | [MIT and third-party notices](https://github.com/microsoft/onnxruntime/blob/v1.20.1/LICENSE) |
| Hugging Face Tokenizers | 0.21.0 | [Apache-2.0](https://github.com/huggingface/tokenizers/blob/v0.21.0/LICENSE) |
| NumPy, inherited | 2.2.6 | [BSD and bundled notices](https://github.com/numpy/numpy/blob/v2.2.6/LICENSE.txt) |

[Windows resolution evidence](ai05-dense-windows-dependencies.json) records the
optional wheel URLs/hashes and hashes of bundled license/notice files. Transitive
hub/network packages are installed because Tokenizers declares them; inference
uses `Tokenizer.from_str` on checksum-verified local bytes and never calls the hub
client. `hf-xet` is pinned for the same architecture markers declared by the hub
package; it is not used by inference or the explicit downloader.

These pins establish reproducible behavior and do not claim the latest or an
audited vulnerability-free release. Existing binary notice and repository release
requirements continue to apply.

## Data handling

Only the explicit provisioning command accesses the model host. Inference accepts
an explicit local directory, verifies byte sizes and SHA-256, and fails when files
or optional dependencies are unavailable. It never substitutes a different model,
downloads a revision, uses remote code, loads pickle/joblib state, calls an external
embedding API or falls back to another engine.

Chunk text and vectors stay in process. Dense index representations suppress those
fields, and evaluation artifacts contain only local IDs, pinned system metadata,
rankings, metrics and digests. CI uploads no model files, vectors or document text.
The provider's network prohibition is tested during construction and inference.
