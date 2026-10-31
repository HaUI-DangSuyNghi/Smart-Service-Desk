# AI05 / AI06 — Kiểm tra sau merge PR #35 và gói regenerate tối thiểu

Ngày kiểm tra: 23/10/2026 (Asia/Saigon). Repository: `HaUI-DangSuyNghi/Smart-Service-Desk`.

## 1. Kết luận từ trạng thái thực tế

`develop` tại thời điểm kiểm tra là `7ba72945d4bb113cf9c50fb99d6832ec8d908d0b`, chính merge commit PR #35. PR merge lúc 12:46:30 ngày 23/10/2026, có 11 commit, 119 file. API discussion trả về danh sách rỗng; điều đó không thay thế technical/data approval.

**B0–B3 provisional evidence thực tế đã được regenerate sau merge.** Không nên ghi trạng thái "chưa có evidence mới" chỉ dựa vào lời nhắc trong PR hoặc handover cũ. Workflow `Intelligence runtime` run `39907629593` trên merge SHA đã hoàn tất thành công, bao gồm source/wheel, Ubuntu/Windows và cross-platform equivalence. Đã tải và kiểm tra JSON matrix, generation và readiness của run này.

AI05-01/02/03/04 có implementation và engineering evidence mới; **official AI05 closure vẫn BLOCKED**. AI05-05 reranker tiếp tục deferred. AI06-00/01 có deterministic foundation và evidence; **toàn phase AI06 và production generation chưa hoàn tất**. Không có quyết định chọn winner hoặc promote runtime trong evidence được kiểm tra.

Nguồn trực tiếp:

- [PR #35](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/pull/35)
- [Merge commit](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/commit/7ba72945d4bb113cf9c50fb99d6832ec8d908d0b)
- [Intelligence runtime: 39907629593](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/actions/runs/39907629593)
- [Tier boundaries: 39907629563, success](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/actions/runs/39907629563)
- [Core acceptance: 39907629542, success](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/actions/runs/39907629542)

## 2. Artifact inventory

Tất cả artifact bên dưới thuộc run `39907629593` và merge SHA nêu trên. API báo chưa expired khi kiểm tra; thời hạn gốc là ngày 30/10/2026. Gói bàn giao giữ lại JSON đã tải, inventory API, SHA-256 từng file và receipt xác minh. Không có model/vector/document/query text trong các evidence JSON này.

| Artifact | ID | Nội dung / mức kiểm chứng trong phiên này |
| --- | --- | --- |
| `ai0503-hybrid-ubuntu-24.04` | `12482180456` | Đã đọc JSON: `source/`, `wheel/` legacy controls; `v02-source/`, `v02-wheel/` graded B0–B3 |
| `ai0503-hybrid-windows-2022` | `12482056369` | Đã đọc JSON và đối chiếu với Ubuntu |
| `ai06-foundation-ubuntu-24.04` | `12482085471` | Đã đọc và so sánh `source.json`, `wheel.json` |
| `ai06-foundation-windows-2022` | `12481422366` | Đã đọc và so sánh với Ubuntu |
| `ai045-data-quality` | `12481134387` | Đã đọc readiness, working validation, data/corpus quality, section summary |
| `ai0502-dense-ubuntu-24.04` | `12481991309` | API inventory và các CI steps PASS; không tải riêng ZIP này |
| `ai0502-dense-windows-2022` | `12482091242` | API inventory và các CI steps PASS; không tải riêng ZIP này |
| `ai05-bm25-ubuntu-24.04` | `12481582567` | API inventory và CI PASS; không tải riêng ZIP này |
| `ai05-bm25-windows-2022` | `12481372668` | API inventory và CI PASS; không tải riêng ZIP này |
| `ai045-corpus-ubuntu-24.04` / `windows-2022` | `12481353759` / `12481722498` | API inventory và CI PASS |
| `ai04-evaluation-ubuntu-24.04` / `windows-2022` | `12481378575` / `12481433484` | API inventory và CI PASS |
| `ai03-core-acceptance-summary` | `12481537433` | API inventory và CI PASS; không thay thế review giới hạn Core |

Mỗi `v02-source/` và `v02-wheel/` chứa:

- `b0-tfidf-report.json`
- `b1-bm25-report.json`
- `b2-dense-report.json`
- `b3-hybrid-report.json`
- `v02-comparison-summary.json`

Source/wheel hai OS có JSON matrix giống nhau; SHA-256 bytes của cả bốn summary là `0bc51c0ec86b327e2850f5974c90ce9479bc0214fb332aca3f734451b9242008`. AI06 JSON giống nhau về nội dung; bytes giữa OS khác line endings, nên không dùng raw byte equality làm gate cho generation.

## 3. Identity hiện hành và ownership

| Thành phần | Identity phải dùng | Chủ sở hữu / ghi chú |
| --- | --- | --- |
| Generic normalization | `text-normalization@0.2.0` | Tier 2, chỉ cơ chế text; không phục hồi alias Wi-Fi vào đây |
| Service Desk executable profile | `service-desk@0.3.0` | Tier 3; v0.1/v0.2 giữ nguyên historical bytes |
| Room Request executable profile | `room-request@0.3.0` | Tier 3; profile lịch sử không executable |
| Generic query input | `caller-provided-query@0.1.0` | Generic candidate/config không biết ticket fields |
| App evaluation query | `service-desk-title-newline-description@0.1.0` | Chỉ app evaluation evidence ghi strategy cụ thể |
| B0 | `tfidf-config@0.1.0` | Config vẫn v0.1; identity tổng thể thay đổi theo normalization |
| B1 | `bm25-config@0.2.0` | Tier 2 engine/config |
| B2 | `dense-e5-cpu-config@0.2.0` | Tier 2 engine/config |
| B3 | `hybrid-rrf-config@0.2.0` | BM25/Dense children đều config v0.2 |
| Chunking | `markdown-paragraph@0.1.0` | Không đổi policy để làm đẹp kết quả |
| App projection | `service-desk-knowledge-projection@0.2.0` | Tier 3 |
| Matrix / metric | `ai05-candidate-matrix@0.2.0` / `graded-artifact-retrieval@0.2.0` | Giữ nguyên metric definition |
| Dense model | `intfloat/multilingual-e5-small@614241f622f53c4eeff9890bdc4f31cfecc418b3` | Pinned revision, CPU, không tải model ở runtime |
| Model artifact digest | `sha256:8f5c24508c0307b029da65796be624c0a3345be7f5b96622a04e0b6285bc53fb` | Không thay bằng model khác cùng tên |

Profile version không phải trường của graded retrieval matrix. Không thêm profile giả vào report; profile được kiểm tra bằng executable-profile/runtime/conformance gates riêng. App query strategy ghi đè trường query strategy trong app-owned report là có chủ ý, không phải semantic leak vào generic config.

Dataset identities vẫn là:

- Authoritative draft: `service-desk-knowledge-enrichment@0.2.0`, digest `sha256:eb3b06d874d6e1538e7f3938196031590aa5f5388b0bbd17b697b57700ff9444`.
- Provisional working: `service-desk-ai05-provisional-v02@0.2.0`, digest `sha256:7f18c0e795ff1d50468b6bb2f8307b6034102900888f95497ae7a50f392cded1`.
- Post-merge DEV freeze: `sha256:46c0129229b1d326c23df6441bfb84c4be3ee3301f7d9586a274cda6f6ea33c5`.

Dataset digest không cần đổi khi input corpus/qrels không đổi. System identity, fingerprint và freeze mới chứng minh thay đổi preprocessing/config; đừng sửa pin data chỉ để tạo digest khác.

## 4. Số đo mới: provisional DEV, 48 query

| Metric | B0 TF-IDF | B1 BM25 | B2 Dense | B3 Hybrid |
| --- | ---: | ---: | ---: | ---: |
| PrimaryHit@1 | 31/48 | 31/48 | 32/48 | 34/48 |
| Hit@3 | 45/48 | 46/48 | 44/48 | 45/48 |
| MRR | 0.842708 | 0.837004 | 0.858333 | 0.878472 |
| RecallRelevant@3 | 0.819444 | 0.850694 | 0.831597 | 0.815972 |
| nDCG@3 | 0.792473 | 0.803530 | 0.805942 | 0.816897 |

Đây là số đo trong artifact mới, không phải bảng historical trong `AI05_ENGINEERING_HANDOVER.md`. Ví dụ B0 PrimaryHit@1 hiện là 31/48, không còn 32/48. Hybrid không thống trị RecallRelevant@3; không tự chọn winner từ bảng tổng hợp. Slice và paired deltas nằm trong từng report/summary.

Tất cả report giữ `AI_ASSISTED_PROVISIONAL`, `humanReviewed=false`, `benchmarkClaim=false`, `runtimePromotion=NOT_AUTHORIZED`, `split=DEV`, `holdout=NOT_EVALUATED`. Dataset có 72 query nhưng matrix chỉ đánh giá quality trên 48 DEV. Test smoke có thể rank cả 72 draft query để kiểm tra kỹ thuật, không tính relevance metric trên HOLDOUT.

## 5. Governance: đã có gì, thiếu gì

| Gate | Trạng thái kiểm chứng | Artifact / hành động còn cần |
| --- | --- | --- |
| AI-assisted working labels | Có 720 grade; dùng được cho provisional DEV | `retrieval/ai-assisted/qrels-candidate-v0.2.json`, manifest và `dataset-v0.2-provisional.yaml`; không coi là phiếu người |
| Human authoritative qrels | 720 pending; 0 dual-reviewed DEV judgment; agreement `null` | `retrieval/qrels-v0.2.json`; cần actual human votes/rationale/adjudication theo annotation policy |
| Query decisions | 72 unreviewed | `tickets/tickets-v0.2.jsonl`; actual reviewer quyết định `QUERY_ACCEPT` |
| Corpus decisions | 10/10 `DRAFT`; chưa có `reviewDecision` | `knowledge/manifest-v0.2.json`; reviewer xác nhận `DOCUMENT_ACCEPT` và rights từng artifact |
| Lexicon review | 19 concept đều `DRAFT` | `knowledge/lexicon-v0.2.yaml`; cần reviewStatus hợp lệ, không chỉ đổi dataset flag |
| Rights | `PENDING_OWNER_APPROVAL` | Owner phê duyệt dataset và corpus thành `APPROVED_INTERNAL_EVAL` theo loader |
| Split / safety / phrase overlap | PASS kỹ thuật: 0 split leakage, 0 secret finding, overlap review list rỗng | Không suy ra data đã human-reviewed |
| Agreement | Chưa có | Raw ≥ 0.88; linear weighted kappa ≥ 0.70; ordinal alpha ≥ 0.70; positive agreement ≥ 0.75 |
| HOLDOUT lock | `DRAFT_NOT_LOCKED`, `lock=null` | Khóa collection/query/qrels/projection digests sau actual review, trước reviewed evaluation |
| Reviewed DEV freeze | Chưa có reviewed evidence trong repo/run được kiểm tra | `reviewed-compare --split DEV`; provisional freeze không thay thế được |
| HOLDOUT quality evaluation | Chưa có official run | Exact reviewed DEV freeze + single-attempt ledger; không chạy thử/tune trên HOLDOUT |
| Final candidate selection | Chưa có quyết định được evidence kiểm chứng | Reviewer duyệt metric/slice/regression/resource tradeoffs và candidate/config/model/freeze chính xác |
| Runtime promotion | `NOT_AUTHORIZED`; runtime retrieval vẫn TF-IDF | Quyết định riêng sau selection; không sửa runtime registry trong gói này |
| AI05-05 reranking | Deferred | Chỉ mở khi reviewed residual errors chứng minh cần thiết |

`selection-readiness` trả `PROVISIONAL_ENGINEERING_READY`, `reviewedBenchmarkReady=false`, diagnostic `Human qrels are incomplete`. Đây là first failing gate, không phải toàn bộ blocker; bảng trên được kiểm tra thêm từ loader và fixture.

Đã gọi `reviewed-compare` với model directory chưa provision; command bị chặn bởi human-qrel gate **trước khi mở model**. Không phát sinh reviewed evidence hay HOLDOUT attempt.

HOLDOUT ledger nằm `.local/ai05-selection/holdout-ledger/<digest(dataset_identity)>.json`: local operator journal, không phải external tamper-proof authority. Không xóa ledger để chạy lại; failure vẫn cần operator review. Không thể từ repo/API khẳng định một operator khác chưa từng có local attempt ngoài phiên này.

## 6. AI06 status

Đã có contracts, authorized bundle, citation-bound exact claims, deterministic provider, assistance orchestration, refusal/degraded behavior. Artifact có 5 case: grounded success, empty evidence, timeout, unknown citation alias, unsupported claim.

Runtime identity: provider `citation-bound-template@0.1.0`, config `exact-excerpts@0.1.0`, envelope `data-only@0.1.0`, output `grounded-claims@0.1.0`, model/revision `none`, synthetic app prompt `synthetic-evidence-policy@1`. Đây là authored foundation evidence, không phải reviewed real-model generation benchmark hay bằng chứng xử lý prompt injection của LLM.

Còn thiếu các gate AI06-02→04: real provider/model/privacy/license approval; reviewed generation benchmark và injection resistance cho model thật; semantic grounding cho paraphrase; final Core freshness/revalidation; app/public integration và production review. Foundation không tự cấp quyền gọi tool/action, sửa workflow hoặc promote retrieval.

## 7. Bộ thay đổi tối thiểu đã chuẩn bị

1. `scripts/intelligence/verify-candidate-matrix.py`: thêm opt-in `--require-current-identities`; pin current system/model/runtime/fusion identities, đối chiếu freeze với dataset/components và report system. Reject cả hai bộ stale giống nhau dù đã tính lại fingerprint/freeze digest. Chế độ historical comparison hiện có vẫn đọc được evidence cũ.
2. `.github/workflows/intelligence-runtime.yml`: bật flag ở source/wheel graded matrix và cross-OS graded matrix comparisons.
3. `intelligence/tests/dense/test_matrix.py`: regression/mutation tests cho stale normalization/config/child/query/model/projection và freeze association ở cả hai phía.
4. Tài liệu này: receipt, inventory, governance và runbook.

Không đổi `hdpi`, executable/historical profiles, schemas, corpus, qrels, locks, baseline, threshold, runtime registry hoặc generation policy. Script CI thuộc lớp orchestration/app evaluation; không đóng gói app semantics vào generic wheel.

Scope patch: harden kiểm chứng evidence để review; không bao gồm phê duyệt governance, merge hoặc runtime promotion. Base của patch là nội dung chính xác tại merge SHA ở đầu tài liệu. Các file nguồn tải qua API đã được kiểm tra Git blob hash; git history đầy đủ không có trong workspace review này.

## 8. Áp dụng và regenerate an toàn

Chạy từ checkout đầy đủ của repository. Gói ZIP có `changes.patch`, file thay thế, evidence đã kiểm chứng, `receipt.json`, `artifact-inventory.json`, `SHA256SUMS.json`. Kiểm tra patch trước khi apply:

```bash
git switch -c chore/ai05-postmerge-evidence-identity origin/develop
git apply --check /path/to/changes.patch
git apply /path/to/changes.patch
python -m pip install -r intelligence/requirements-dense.lock
python scripts/check-tier-boundaries.py
python scripts/test-tier-boundaries.py
python scripts/intelligence/validate-executable-profiles.py
python -m pytest intelligence/tests/dense/test_matrix.py intelligence/tests/generation -q
```

Workflow hiện có đã đủ khả năng regenerate; không cần viết lại engine/pipeline hoặc reset baseline. Có thể chạy workflow `Intelligence runtime` trên branch chứa patch để lấy source/wheel + Windows/Linux artifacts mới. Ghép receipt với đúng `head_sha`, run ID, artifact ID; chỉ artifact name không đủ chứng minh provenance.

Local regeneration dùng **output mới** và pinned model đã xác minh. Thay `RUN` bằng ID thư mục chưa tồn tại; không tái sử dụng `.local/ai0503` cũ:

```bash
python scripts/intelligence/prepare-dense-model.py --directory .local/ai0502-model
python scripts/intelligence/prepare-dense-model.py --offline --directory .local/ai0502-model
python scripts/intelligence/knowledge-data.py provisional-validate --output .local/RUN/working-data-validation.json
python scripts/intelligence/knowledge-data.py selection-readiness --output .local/RUN/selection-readiness.json
python scripts/intelligence/knowledge-data.py engine-compare --include-hybrid --model-directory .local/ai0502-model --output .local/RUN/legacy-source
python scripts/intelligence/knowledge-data.py provisional-compare --model-directory .local/ai0502-model --output .local/RUN/v02-source
python scripts/intelligence/generation-evidence.py --output .local/RUN/generation-source.json
python -m build --no-isolation --outdir .local/RUN/dist intelligence
python scripts/intelligence/install-built-wheel.py --dist .local/RUN/dist
python -I scripts/intelligence/knowledge-data.py --installed provisional-compare --model-directory .local/ai0502-model --output .local/RUN/v02-wheel
python -I scripts/intelligence/generation-evidence.py --installed --output .local/RUN/generation-wheel.json
python -I scripts/intelligence/verify-candidate-matrix.py --require-current-identities .local/RUN/v02-source .local/RUN/v02-wheel
python -I scripts/intelligence/generation-evidence.py --compare .local/RUN/generation-source.json .local/RUN/generation-wheel.json
```

App adapters tiếp tục được compose từ Tier 3 bên ngoài wheel, theo entrypoint hiện có. Chạy equivalent commands trên Windows và Linux rồi dùng verifier cho hai `v02-wheel` directories; generation dùng `--compare` cho hai `generation-wheel.json`. Giữ JSON safe cùng commit/run provenance, không upload model/vector/private content.

`engine-compare` là phép đo mới với **legacy family judgment controls**, không phải official v0.2 benchmark. Nó có thể đọc baseline lịch sử theo mục đích compatibility đã khai báo. Không dùng baseline đó làm ground truth mới hoặc sửa threshold để xóa regression. Candidate selection chỉ dùng reviewed protocol riêng khi đủ gate.

Sau actual human review: cập nhật decisions/votes/rights và component pins theo nội dung thực; chạy reviewed validation, lập HOLDOUT digest lock; tạo reviewed DEV freeze; chỉ sau khi duyệt freeze mới chạy HOLDOUT một lần theo protocol. Nếu data/labels/config đổi, giữ evidence trước đó nguyên trạng và tạo identity/evidence mới. Không dùng bảng provisional hoặc `PASS` engineering để điền approval.

## 9. Validation và giới hạn phiên kiểm tra

Kết quả cụ thể được ghi trong `receipt.json` của gói bàn giao. Đã kiểm tra artifact thật bằng current-identity verifier; source/wheel Ubuntu/Windows agree. Đã regenerate AI06 source locally và so sánh chính xác JSON với post-merge CI; đã tái kiểm readiness locally. Boundary gate, 39 boundary mutation tests và hai executable-profile gates PASS.

Không chạy lại real Dense inference/model provisioning, Docker Core acceptance hay Windows suite trong phiên này; bằng chứng cho chúng là post-merge CI run, không phải local rerun. Các test dùng fake vectors chỉ là regression/mutation tests, không được đưa vào bảng quality measurement. Handover cũ vẫn là historical record; tài liệu này cập nhật trạng thái ngày kiểm tra mà không viết lại lịch sử.
