# Báo cáo AI-01.5 — đối chiếu foundation và profile thực thi

Ngày: **16/10/2026, Asia/Saigon**.
Baseline Core: `0238c590b905e6a47913fe844406894ef81e0cfd`.
Nhánh triển khai: `feature/ai02-intelligence-runtime`.
Trạng thái: **phần kỹ thuật đã triển khai và kiểm tra local; chờ review**.

## Kết quả đối chiếu hai kế hoạch

Đã đọc kế hoạch AI-01.5 và AI-02 trong `.local/`. Các gap mô tả trong kế hoạch
đúng với baseline: tài liệu N1 còn giả định Core cũ; impact/urgency chỉ có rule
ID; conflict chỉ có nguyên tắc; chưa có runtime Python.

Triển khai theo thứ tự foundation → profile → validation → runtime. Kế hoạch
AI-02 yêu cầu AI-01.5 được review/merge trước khi bắt đầu phase chính thức.
Theo yêu cầu thực hiện cả hai kế hoạch, phần AI-02 được chuẩn bị trên cùng nhánh
feature để review. Việc này **không xác nhận gate review/merge đã đạt**; không tự
merge vào `develop` hoặc đánh dấu approval nghiệp vụ.

## Phạm vi đã hoàn thành

| Hạng mục | Kết quả |
| --- | --- |
| Đối chiếu Core | Cập nhật README, architecture, provenance, requirements mapping, N1 report và BE brief với Core `0238c59` |
| Phân quyền hiện tại | Metadata read/search cần membership; create cần `agent`/`coordinator`; `requester` là role mặc định còn lại |
| Provenance | Ghi đúng Metadata search, File HTTP, `fileId`, checksum Core, camelCase và transaction/audit |
| Bảo toàn lịch sử | Giữ timestamp/commit và acceptance N1 cũ; không sửa fixture hoặc profile v0.1 |
| Profile thực thi | Thêm `service-desk@0.2.0`, rules `0.2.0`; giữ taxonomy, normalization, priority policy v0.1 |
| Luật cấu hình | Impact/urgency có literal và count template giới hạn; precedence và root-cause override nằm trong YAML |
| Version bất biến | SHA-256 cho artifact và đối chiếu Git baseline, chặn sửa cả nội dung lẫn hash của version đã tồn tại |
| Error taxonomy | Phân biệt lỗi profile, contract, configuration, vận hành, quyền và các lỗi dành cho AI-03 |
| Contract | Giữ wire `contractVersion=0.1.0`; không thay schema chung để thêm domain literal |

Thiết kế và đường dẫn profile: [profile thực thi](../../architecture/executable-profile.md).
Thông tin đối chiếu API: [provenance](../../architecture/data-provenance-contract.md).

## Kiểm chứng

Đã chạy `python scripts/intelligence/validate-foundation.py`, sau đó chạy lại bằng
môi trường AI-02 pin PyYAML 6.0.2/jsonschema 4.23.0:

- PASS 4 schema, 5 category, 16 ô priority matrix và 12 annotation lịch sử.
- PASS checksum chính xác của 5 knowledge source, ticket dataset và retrieval gold.
- PASS 145 ca dữ liệu/cấu trúc không hợp lệ của validator N1.
- PASS profile v0.2: pin, signal, route, conflict reference và precedence.
- PASS đối chiếu version bất biến với Git; test phát hiện cả việc tính lại digest.
- `git diff --check`: PASS.

Các test cấu hình bổ sung kiểm tra thiếu signal impact/urgency, duplicate rule ID,
thiếu route, root-cause reference sai, priority matrix thiếu/sai, normalization
không hỗ trợ, regex tùy ý, count không giới hạn, precedence vòng và path escape.
Kết quả suite chung nằm trong [báo cáo AI-02](../AI02/RUNTIME_REPORT.md).

Không chạy lại Core acceptance trong đợt này. Đối chiếu code Core không phải bằng
chứng integration/security mới; hồ sơ acceptance cũ được giữ nguyên baseline.

## Các commit AI-01.5

| Commit | Nội dung |
| --- | --- |
| `7aaba18` | Đối chiếu tài liệu N1 với Core đã tích hợp |
| `d5302bd` | Profile thực thi v0.2 và thiết kế semantics |
| `0f25234` | Validator và test invariant cấu hình |

Bổ sung kiểm tra lịch sử Git được commit cùng regression suite AI-02 tại
`d9765a5`; nội dung profile v0.2 không thay đổi.

## Gate còn chờ con người

- [ ] BE review ranh giới Core, trusted context và cách dùng provenance hiện tại.
- [ ] Process/Service Owner review impact, urgency, root-cause conflict và fallback.
- [ ] Reviewer xác nhận AI-01.5 và merge theo Git Flow của nhóm.
- [ ] Owner quyết định quyền dữ liệu/source license trước ingest hoặc release.

Không có bằng chứng approval nên báo cáo không ghi AI-01.5 là APPROVED, RATIFIED
hoặc gate PASS. Phần triển khai AI-03 chưa được thực hiện.
