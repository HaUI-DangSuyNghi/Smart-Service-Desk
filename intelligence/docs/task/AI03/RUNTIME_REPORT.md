# AI-03 — Báo cáo nền tảng truy xuất tri thức

Ngày thực thi: 17/10/2026. Nhánh: `feature/ai03-knowledge-retrieval`.
Base hardening: `develop@e39356cc44ef50421a9b81199756ec9784fe161a`.
Base triển khai ban đầu: `f348beb200b84bb31f2550879d38e744fa695230`.
Trạng thái hiện tại: **INTEGRATION VERIFIED — REVIEW PENDING**. Package 0.3.0;
chưa đạt mục tiêu retrieval gợi ý, chưa có approval production.

## Hardening và provenance hiện tại

Nhánh khi tiếp nhận hardening đã chứa `origin/develop@e39356c`; behind = 0.
Các thay đổi upstream là thống kê Service Desk, không sửa Intelligence contract
hay Core Metadata/File. Lịch sử local đã rebase trước phiên hardening, còn remote
giữ SHA cũ; đã merge remote bằng `eea45299dac4cc9eec35f11d634cbab384dab42e`, giải
quyết duy nhất xung đột package version bằng cách giữ 0.3.0. Không force-push.

Runtime/package được kiểm chứng tại `eea45299dac4cc9eec35f11d634cbab384dab42e`;
các commit docs/CI đóng evidence nằm sau mốc này. SHA HEAD hiện hành xem lịch sử
[PR #19](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/pull/19).
Chủ nhánh tự mời reviewer; agent không gửi yêu cầu review hoặc tự merge.

- Package 0.3.0, profile service-desk@0.2.0, contract 0.1.0 tách biệt.
- Wheel discovery đòi đúng một artifact, kiểm distribution/version và cài lại;
  không còn hardcode tên wheel 0.2.0 trong CI.
- PR-range check dùng base SHA thực của PR; workflow_dispatch dùng merge-base.
- Adapter Core HTTP có timeout/giới hạn bytes, HTTPS mặc định, local HTTP opt-in,
  không redirect token, không cache quyền, không đọc DB.
- Scope suy ra từ tập source Core đã cho đọc, có version và rotation; chờ BE duyệt.
- [Core integration report](CORE_INTEGRATION_REPORT.md): 8/8 scenario PASS với
  giới hạn JWT mới, metadata bất biến, 404 và failure injection được ghi riêng.

CI đầu tiên của hardening được kích hoạt bằng PR tại runtime commit nói trên:
[Intelligence runtime #2](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/actions/runs/39047786245).
Kết quả đã quan sát: **PASS cả ubuntu-24.04 và windows-2022**, bao gồm source suite,
installed-wheel suite, evaluation và PR-range check. Workflow sau đó bổ sung job
Core HTTP acceptance thật trên Ubuntu tại `4c56ca964e1f0839073978b54643e73b0500ac1e`,
dùng wheel cài riêng và stack disposable; kết quả job mới được ghi sau lần chạy thực tế.

## Phạm vi hoàn thành

| Phần kế hoạch | Kết quả |
| --- | --- |
| AI-03.0 | Đọc contract/Core; ghi baseline, ADR-004, threat model và ranh giới adapter |
| AI-03.1 | Pin scikit-learn 1.6.1 cùng NumPy 2.2.6, SciPy 1.15.3, joblib 1.4.2, threadpoolctl 3.6.0; ghi resolution/license |
| AI-03.2 | Protocol engine, corpus, index và citation; domain bất biến, source integrity và scope/version chính xác |
| AI-03.3 | Chunk Markdown theo đoạn/heading, tối đa 900 ký tự, không overlap, ID xác định và offset/excerpt gốc |
| AI-03.4 | TF-IDF sparse; manifest digest; registry kiểm tra lại source/chunk/matrix/config; publish JSON nguyên tử và loader dựng lại |
| AI-03.5 | SearchService, request/response validator, topK, no-result, ranking ổn định và trace không chứa query/source |
| AI-03.6 | CoreCitationVerifier qua resolver có phân quyền; loại stale, từ chối nguồn khác workspace, không bịa citation |
| AI-03.7 | Hit@1, Hit@3, MRR cấp artifact; CLI/evaluation tách khỏi runtime; ghi kết quả thực |
| AI-03.8 | Build sdist/wheel; cài wheel và kiểm tra bằng isolated Python |
| AI-03.9 | CI Windows/Linux có installed-wheel suite/evaluation; hardening thêm PR-range và Core HTTP acceptance |

Không thay schema search v0.1, profile bất biến AI-02, luật triage, fixture gốc
hoặc Core bindings. Không thêm HTTP production, LLM, embedding, DB/vector store,
framework orchestration, truy cập DB Core hoặc hành động sửa workflow.
Hardening nâng package từ 0.2.0 lên 0.3.0; chưa gắn tag release.

## Quyết định triển khai và ranh giới an toàn

`SearchEngine` chỉ nhận text chuẩn hóa/index đã chọn và trả internal hit.
Adapter xác nhận quyền hiện tại trước khi registry/index được truy cập; IDF cũng
chỉ fit trong partition đó. Key gồm workspace, profile/version, scope hash,
corpus version, chunker, normalizer, engine/version/config. Không fallback `latest`.
Adapter phải đổi scope khi tập tài liệu có quyền đọc thay đổi. Giao diện này
không tự chứng minh quyền: review BE-03-A vẫn bắt buộc trước production.

`AuthorizedDocument` kiểm tra UUID, media type, UTF-8 và SHA-256 trên bytes gốc.
UUID đúng định dạng không chứng minh Core phát hành. Resolver phải đọc metadata
và file thông qua API Core có phân quyền; library không tự tạo credentials.
`build_authorized_index` xác nhận scope trước khi yêu cầu adapter tải tài liệu.

Verifier kiểm tra nguồn hiện tại rồi dựng lại chunk để đối chiếu ID, ordinal,
offset, excerpt và text chuẩn hóa. Nguồn mất/đổi version/checksum làm hit stale;
hit bị bỏ, tăng `stale_hit_count` và yêu cầu review. Nếu tất cả stale thì SUCCESS
rỗng. Không tự bù bằng kết quả ngoài topK. Mất quyền luôn gây
`AuthorizationRejected`; invariant sai gây `CitationVerificationError`.

Mặc định `CorpusUnavailable`/`EngineUnavailable` được truyền ra ngoài. Tùy chọn
`allow_operational_degraded=True` chỉ ánh xạ hai lỗi vận hành này thành DEGRADED
rỗng, đúng contract. Chưa coi đây là chính sách production đã được BE duyệt.
Lỗi lập trình, cấu hình, provenance và phân quyền không bị biến thành DEGRADED.

Không cache query/result. JSON snapshot lưu bytes base64 và manifest rồi dựng
lại vector; không dùng pickle/joblib deserialization. Lựa chọn này đơn giản hóa
kiểm chứng cho corpus nhỏ và tốn thời gian load hơn serialize ma trận. Snapshot
chứa dữ liệu nguồn nhạy cảm, phải được adapter bảo vệ ACL; hash không chứng minh
quyền hoặc chống kẻ có quyền ghi sửa toàn bộ snapshot. Xem [vòng đời index](../../architecture/corpus-index-lifecycle.md).

Giới hạn nội bộ: topK 1–10, query tối đa 8.192 ký tự, source 2 MiB, 4.096
chunk/source, 1.000 document và tổng 16 MiB source/partition. Schema chung không
có maxLength query; giới hạn bổ sung được kiểm tra riêng trong library.

## Bằng chứng đã chạy

Windows local dùng CPython 3.12.7, Unicode 15.0.0, venv riêng tạo mới tại
`.local/ai03-venv`. Lần chạy baseline đầu bị sandbox chặn thư mục tạm pytest;
chạy lại với quyền phù hợp đạt 90 test. Bộ pin sạch không dựa vào package host.

| Kiểm tra | Kết quả đã quan sát |
| --- | --- |
| AI-02 trước thay đổi | 90 test PASS |
| `pip install -r intelligence/requirements.lock` vào venv mới | PASS; URL/hash/closure trong resolution AI-03 |
| `pip check` | PASS, không có dependency lỗi |
| Foundation validator | PASS, 145 negative case, 5 source digest, profile bất biến |
| `python -m pytest intelligence/tests -q` | 181 test PASS, gồm 26 test hardening mới |
| `python -m build --no-isolation intelligence` | PASS, sdist và wheel; wheel được build từ sdist |
| Cài wheel `--no-deps --force-reinstall` | PASS |
| `python -I scripts/intelligence/verify-installed-package.py` | PASS cả triage, resource, TF-IDF, snapshot và citation qua FakeCore |
| `python -I -m pytest -c scripts/intelligence/installed-pytest.ini intelligence/tests -q` | 181 test PASS từ package 0.3.0 trong site-packages |
| Evaluation bằng package đã cài | PASS tính toán; kết quả bằng lần chạy từ source |
| Authoring CLI query tài khoản | Trả `kb-account-001` đầu tiên; nhãn AUTHORING_ONLY_NOT_RUNTIME |
| `git diff --check` | PASS |

`-I` bỏ PYTHONPATH/source path; config pytest riêng không đặt `pythonpath=src`
và dùng importlib. Script xác nhận package nằm trong site-packages và schema/
profile được đóng trong `_resources`. Test double được ghi rõ FakeCore; đây
không phải bằng chứng tích hợp API Core thật.

Ở vòng triển khai ban đầu (package 0.2.0), Linux container đã kiểm chứng thực tế,
dùng CPython 3.12.7 trong image
`python:3.12.7-slim@sha256:60d9996b6a8a3689d36db740b49f4327be3be09a21122bd02fb8895abb38b50d`.
Repository được mount chỉ đọc, dependency cài trong container mới, wheel dựng
từ sdist vào thư mục tạm. Resolution Linux có 19 package (Windows có thêm colorama),
ghi tại [resolution Linux](../../governance/ai03-linux-dependency-resolution.json).

| Kiểm tra Linux local | Kết quả đã quan sát |
| --- | --- |
| Cài lock và `pip check` | PASS |
| `pip wheel --no-deps --no-build-isolation` từ sdist | PASS |
| Foundation validator | PASS, gồm 145 negative case |
| Script `python -I` kiểm tra wheel | PASS triage và retrieval/citation FakeCore |
| Full suite từ installed wheel | 155 test PASS, 23,11 giây |
| Authored evaluation | Giống Windows: Hit@1 7/12, Hit@3 11/12, MRR 0,7388888889 |

Bằng chứng container Linux local phía trên là lịch sử của baseline 155 test,
không thay kết quả CI hardening 181 test. GitHub Actions đã được kích hoạt bởi
PR #19; trạng thái hiện tại được ghi ở phần hardening đầu báo cáo.

Đo local trên 5 tài liệu/12 chunk, sau import: build 112,209 ms; đỉnh bộ nhớ
Python do tracemalloc theo dõi 767.235 byte; median search 0,914 ms, max 2,531 ms
trong 20 lần. Phép đo chỉ bao gồm index lexical nhỏ, không gồm network/Core
verification và không đo RSS/native allocation đầy đủ; không phải SLA.

## Đánh giá retrieval thực tế

Dữ liệu: 12 ticket tự biên soạn, 5 artifact; query = title + newline + description.
TF-IDF unigram/bigram, sublinear TF, smooth IDF, L2, không strip accent/stopword.
Normalizer v0.1 có NFC, casefold, khoảng trắng và chuẩn hóa biến thể Wi-Fi.
Xếp hạng chunk theo cosine giảm dần, tie-break theo source ID/ordinal/chunk ID.
Evaluation khử trùng lặp artifact theo lần xuất hiện đầu, tương đương điểm chunk
cao nhất, và tính MRR trên toàn bộ ranking. Runtime vẫn trả chunk trong topK.

| Chỉ số | Kết quả | Mục tiêu gợi ý trong plan |
| --- | --- | --- |
| Hit@1 | 7/12 = 58,33% | Ít nhất 10/12 — chưa đạt |
| Hit@3 | 11/12 = 91,67% | 12/12 — chưa đạt |
| MRR | 0,7388888889 | Ghi baseline thực tế |

| Ticket chưa trúng top 1 | Hạng artifact liên quan đầu tiên |
| --- | --- |
| TICKET-002 | 3 |
| TICKET-005 | 2 |
| TICKET-007 | 2 |
| TICKET-008 | 3 |
| TICKET-011 | 5 |

Phân tích: các truy vấn dài chứa nhiều từ phổ biến về phạm vi/ảnh hưởng, nên tài
liệu priority hoặc network có thể đứng trước hướng dẫn đúng nhóm. TICKET-011
mô tả chưa xác định phần mềm hay thiết bị; nhãn mong đợi escalation cần hiểu ngữ
cảnh thiếu thông tin mà lexical overlap thể hiện yếu. Đây là suy luận từ fixture
và ranking, không phải nghiên cứu lỗi trên dữ liệu production. Không chỉnh query
theo ticket, boost theo gold, sửa fixture hay luật AI-02 để làm đẹp chỉ số.

Đóng băng config v0.1 và baseline đo được. AI-04 có thể so BM25/hybrid trên cùng
giao diện và bổ sung dữ liệu có quyền sử dụng, annotation độc lập và tập holdout.
Không tuyên bố đạt hai mục tiêu gợi ý hoặc độ chính xác production. Dataset
nguồn giữ `MANUAL_EXPECTED`, `NOT BENCHMARK`, `NOT ENGINE OUTPUT`; báo cáo engine
được sinh riêng trong `.local/ai03-evaluation*.json`, không sửa nhãn dữ liệu nguồn.

## Review và bàn giao còn chờ

| Gate | Trạng thái và việc cần làm |
| --- | --- |
| AI review | Chờ người review xác nhận thiết kế, config và đánh giá; local test không thay approval |
| BE-03-A | Adapter đã có real API acceptance; chờ duyệt scope/rotation, giới hạn JWT và semantics 404 |
| Chính sách lỗi vận hành | Chờ BE/Application quyết định khi nào bật DEGRADED |
| OWNER-03-A | Fixture vẫn PENDING_OWNER_APPROVAL, usageRights=null, NOT_INGESTED; chưa được cấp quyền public/production |
| Process Owner | Không sửa nghiệp vụ; quyền sử dụng knowledge chứa policy cần xác nhận khi tích hợp |
| QA | Windows hardening 181 test PASS, Core thật 8/8 PASS có giới hạn; chờ review độc lập |
| CI | PR #19 đã kích hoạt matrix; trạng thái cụ thể ghi ở phần hardening |
| Retrieval target | Hai mục tiêu gợi ý chưa đạt; đã ghi miss analysis để review và chuyển AI-04 |

Đã push và mở PR #19 vào develop; chưa merge/phát hành. Commit message tiếng Anh;
tài liệu tổng hợp tiếng Việt. Fixture vẫn PENDING_OWNER_APPROVAL, usageRights=null,
NOT_INGESTED. Không thêm model/vector DB/RAG. Các thử nghiệm lexical, dataset v0.2,
coverage/SBOM/pip-audit là hạng mục tùy chọn chưa thực hiện, không tuyên bố đã đạt.
