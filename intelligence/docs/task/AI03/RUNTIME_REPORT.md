# AI-03 — Báo cáo nền tảng truy xuất tri thức

Ngày thực thi: 17/10/2026. Nhánh: `feature/ai03-knowledge-retrieval`.
Base: `develop@f348beb200b84bb31f2550879d38e744fa695230`, đã fetch và đối chiếu
`origin/develop` trước khi tạo nhánh. Trạng thái: **ĐÃ TRIỂN KHAI LIBRARY VÀ
KIỂM CHỨNG LOCAL; CHỜ REVIEW/TÍCH HỢP, CHƯA ĐẠT MỤC TIÊU RETRIEVAL GỢI Ý**.

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
| AI-03.9 | Mở rộng CI Windows/Linux với installed-wheel suite và authored evaluation; chưa chạy GitHub Actions |

Không thay schema search v0.1, profile bất biến AI-02, luật triage, fixture gốc
hoặc Core bindings. Không thêm HTTP production, LLM, embedding, DB/vector store,
framework orchestration, truy cập DB Core hoặc hành động sửa workflow.
Giữ package version 0.2.0 của baseline; đây là nhánh phát triển, chưa gắn tag release.

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
| `python -m pytest intelligence/tests -q` | 155 test PASS, gồm 65 test AI-03 mới |
| `python -m build --no-isolation intelligence` | PASS, sdist và wheel; wheel được build từ sdist |
| Cài wheel `--no-deps --force-reinstall` | PASS |
| `python -I scripts/intelligence/verify-installed-package.py` | PASS cả triage, resource, TF-IDF, snapshot và citation qua FakeCore |
| `python -I -m pytest -c scripts/intelligence/installed-pytest.ini intelligence/tests -q` | 155 test PASS từ package cài trong site-packages |
| Evaluation bằng package đã cài | PASS tính toán; kết quả bằng lần chạy từ source |
| Authoring CLI query tài khoản | Trả `kb-account-001` đầu tiên; nhãn AUTHORING_ONLY_NOT_RUNTIME |
| `git diff --check` | PASS |

`-I` bỏ PYTHONPATH/source path; config pytest riêng không đặt `pythonpath=src`
và dùng importlib. Script xác nhận package nằm trong site-packages và schema/
profile được đóng trong `_resources`. Test double được ghi rõ FakeCore; đây
không phải bằng chứng tích hợp API Core thật.

Linux container cũng đã kiểm chứng thực tế, dùng CPython 3.12.7 trong image
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

Bằng chứng container Linux local không thay thế GitHub Actions trên
`ubuntu-24.04`/`windows-2022`; workflow chưa được kích hoạt vì nhánh chưa push.

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
| BE-03-A | Chờ xác nhận tập quyền file/metadata, scope identity/rotation và triển khai adapter Core API có phân quyền |
| Chính sách lỗi vận hành | Chờ BE/Application quyết định khi nào bật DEGRADED |
| OWNER-03-A | Fixture vẫn PENDING_OWNER_APPROVAL, usageRights=null, NOT_INGESTED; chưa được cấp quyền public/production |
| Process Owner | Không sửa nghiệp vụ; quyền sử dụng knowledge chứa policy cần xác nhận khi tích hợp |
| QA | Windows và Linux container local unit/security/package PASS; review độc lập và acceptance Core thật còn chờ |
| CI | Matrix đã cập nhật; chưa có bằng chứng GitHub Actions trên nhánh này |
| Retrieval target | Hai mục tiêu gợi ý chưa đạt; đã ghi miss analysis để review và chuyển AI-04 |

Chưa push, mở PR, merge hoặc phát hành. Các commit được chia theo kiến trúc,
dependency, domain, chunker, index, service, citation, evaluation/security, CI và
tài liệu. Commit message dùng tiếng Anh; tài liệu tổng hợp dùng tiếng Việt.
