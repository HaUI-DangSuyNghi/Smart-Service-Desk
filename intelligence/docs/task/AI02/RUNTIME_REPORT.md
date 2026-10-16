# Báo cáo AI-02 — runtime triage deterministic

Ngày: **16/10/2026, Asia/Saigon**.
Baseline Core: `0238c590b905e6a47913fe844406894ef81e0cfd`.
Nhánh: `feature/ai02-intelligence-runtime`.
Trạng thái: **đã triển khai và kiểm chứng local; chờ review/merge**.

## Kết quả

Đã có Python library độc lập chạy pipeline: validate contract → resolve profile
bất biến → normalize → match rule → giải quyết conflict/impact/urgency → tra
priority/routing → tạo evidence nguyên bản → validate response.

**90 test PASS**, gồm **12/12 kịch bản conformance tự biên soạn**. Đây là kiểm
chứng tuân thủ hành vi trên fixture hiện có, không phải benchmark hoặc độ chính
xác của model. Đã build sdist/wheel, cài wheel và chạy kiểm tra ở chế độ `python -I`
để bảo đảm không import source checkout.

AI-01.5 chưa có bằng chứng review/merge. Runtime được chuẩn bị local trên nhánh
feature theo yêu cầu triển khai cả hai kế hoạch; chưa coi là phase được phê duyệt
để tích hợp hoặc release. Xem [báo cáo foundation](../AI015/CLOSEOUT_REPORT.md).

## Thành phần triển khai

| Thành phần | Hành vi đã kiểm chứng |
| --- | --- |
| Package | `pyproject.toml`, Python 3.12.7/Unicode 15.0.0, dependency pin và resolution metadata riêng |
| Registry | Chọn chính xác `ID@version`; từ chối profile/version không biết và profile lịch sử không thực thi; cache object bất biến |
| Contract | Draft 2020-12, giữ wire 0.1.0; chặn trường thừa, non-finite numeric, correlation sai và evidence giả |
| Normalizer | NFC, casefold, collapse whitespace, alias Wi-Fi theo biên từ; giữ dấu, không NFKC; mapping về input gốc |
| Engine protocol | Có `TriageEngine`, `Normalizer`, request/decision model; không buộc service phụ thuộc trực tiếp thuật toán matching |
| Rule matching | Literal/count template từ profile; match độc lập title/description và giữ span gốc |
| Conflict | Chỉ chọn một root cause có đủ evidence và bao phủ đối thủ; không dựa vào thứ tự YAML |
| Impact/urgency | Chỉ áp dụng override tường minh; thiếu hoặc mơ hồ dùng toàn bộ review fallback, không bịa level |
| Priority/routing | Đọc từ matrix/taxonomy; kiểm tra thực thi đủ 16 ô matrix |
| Evidence | Chỉ INPUT substring nguyên bản, thứ tự ổn định, không trùng, tối đa 16 mục |
| Trusted context | Bắt buộc object riêng do caller đã authorize tạo; kiểm tra workspace trước resolve profile/engine |
| Error mapping | Chỉ `EngineUnavailable` được map sang DEGRADED hợp schema; lỗi quyền/cấu hình/lập trình được raise |
| Trace | Correlation, profile/rules/normalization, engine build, Python/Unicode, rule ID, status, thời gian và error type; không chứa ticket/token |
| CLI | Interface engineering local; không xác thực hoặc cấp quyền truy cập |
| Phân phối | Wheel mang snapshot profile/schema từ nguồn chính thức trong repo; sdist đủ dữ liệu để build lại wheel |
| CI | Workflow Windows/Linux với pins, foundation, tests, build, installed-wheel check và diff check |

Không triển khai HTTP/APISIX, retrieval, LLM, embedding, vector DB hoặc workflow
tự động. Runtime không đọc Core DB, Keycloak DB hoặc Flowable internals. Các giá
trị category/route/priority/impact/urgency thuộc profile, không hard-code vào engine.

## Kết quả conformance

Fixture v0.1 giữ nguyên bytes và expected annotation lịch sử. Khi chạy runtime,
suite so sánh expected nghiệp vụ và required rule ID; runtime pin được kiểm tra
riêng là `service-desk@0.2.0` / rules `0.2.0`, không giả vờ đã chạy engine v0.1.
Evidence được kiểm tra theo invariant substring, không bắt buộc giống cách tác
giả chọn excerpt hoặc thứ tự matchedRules trong annotation cũ.

| Kịch bản | Category | Impact/Urgency | Priority | Kết quả |
| --- | --- | --- | --- | --- |
| TICKET-001 | NETWORK | I3/U3 | HIGH | PASS |
| TICKET-002 | NETWORK | I1/U1 | LOW | PASS |
| TICKET-003 | ACCOUNT | I1/U2 | LOW | PASS |
| TICKET-004 | ACCOUNT | I2/U3 | HIGH | PASS |
| TICKET-005 | SOFTWARE | I1/U2 | LOW | PASS |
| TICKET-006 | SOFTWARE | I3/U4 | CRITICAL | PASS |
| TICKET-007 | HARDWARE | I1/U2 | LOW | PASS |
| TICKET-008 | HARDWARE | I2/U3 | HIGH | PASS |
| TICKET-009 | OTHER | I3/U2 | HIGH | PASS |
| TICKET-010 | ACCOUNT, root cause | I1/U2 | LOW | PASS |
| TICKET-011 | OTHER, cần review | null/null | MEDIUM | PASS |
| TICKET-012 | SOFTWARE, root cause | I3/U3 | HIGH | PASS |

## Lệnh kiểm chứng và kết quả

Chạy từ repository root trên Windows với venv `.local/ai02-venv`:

```powershell
.local/ai02-venv/Scripts/python -m pip check
.local/ai02-venv/Scripts/python scripts/intelligence/validate-foundation.py
.local/ai02-venv/Scripts/python -m pytest intelligence/tests -q
.local/ai02-venv/Scripts/python -m build --no-isolation intelligence
.local/ai02-venv/Scripts/python -m pip install --no-deps --force-reinstall intelligence/dist/smart_service_intelligence-0.2.0-py3-none-any.whl
.local/ai02-venv/Scripts/python -I scripts/intelligence/verify-installed-package.py
.local/ai02-venv/Scripts/python -I -m smart_service_intelligence.evaluation.conformance --fixtures fixtures/intelligence/service-desk/tickets/tickets-v0.1.jsonl --output .local/ai02-conformance.json
git diff --check
```

| Kiểm chứng | Kết quả local |
| --- | --- |
| Dependency consistency | No broken requirements found |
| Foundation | PASS, gồm 145 negative case lịch sử và profile/history checks mới |
| Pytest | 90 passed, lần xác nhận cuối 6.39 giây |
| Conformance | 12/12 authored scenarios PASS bằng package đã cài |
| Build | Tạo thành công `smart_service_intelligence-0.2.0.tar.gz` và wheel `py3-none-any` |
| Installed package | PASS, profile/schema được lấy từ `_resources` trong site-packages |
| CLI local | SUCCESS hợp contract và trace đúng version |
| Whitespace | `git diff --check` PASS |

Dependency resolution của môi trường sạch (CPython 3.12.7, Windows, pip 24.2)
được ghi tại [metadata đã lược bỏ đường dẫn máy](../../governance/ai02-dependency-resolution.json).
Nguồn release/license của các dependency chính ở [inventory](../../governance/intelligence-dependency-candidates.md).
`requirements.lock` gồm cả dependency bắc cầu đang dùng; metadata không chứa
extra chưa cài. Pin là baseline tương thích, không phải kết luận audit bảo mật.

Raw build/conformance/installation evidence nằm trong `.local/` và không commit.
Các lỗi quyền thư mục tạm của Windows sandbox khi setup/test đã được chạy lại
với quyền cho phép; kết quả PASS trên đây là các lần chạy hoàn tất.

## Commit AI-02 theo từng phần

| Commit | Phần triển khai |
| --- | --- |
| `aa97831` | Package/dependency pins và inventory |
| `7b92ff6` | Immutable registry và contract validator |
| `ec8e177` | Normalization v0.1 và original span |
| `a46b332` | Deterministic rules engine |
| `5cb269e` | TriageService, trusted context, trace và CLI |
| `d9765a5` | Conformance, negative/regression và immutable history gate |
| `0204d6c` | CI và installed-wheel verification |

## Giới hạn và phần chờ review

Rules dùng tín hiệu literal/count do nhóm biên soạn, chưa có khả năng hiểu phủ
định hoặc ngữ nghĩa tổng quát. Fallback yêu cầu con người xác nhận. Mười hai
fixture không đại diện dữ liệu thực tế hoặc holdout độc lập.

`TrustedContext` là ranh giới thư viện, không phải bộ xác thực. Tests chứng minh
library kiểm tra object/correlation và không gọi engine khi workspace sai; chưa
chứng minh JWT/membership của public adapter vì adapter chưa tồn tại.

Chưa chạy workflow trên GitHub hoặc kiểm chứng local Linux. Chưa chạy lại Core
acceptance vì không đổi implementation Core. Chưa ingest knowledge, chưa có
retrieval/citation integration. Source license, quyền dữ liệu, BE review,
Process Owner review và merge vẫn là các quyết định còn chờ.
