# Chạy và nghiệm thu local — bổ sung ngữ cảnh, gợi ý ticket trùng

## Hành vi đã triển khai

Client dùng `ticketId` đã có để đọc revision và gửi câu trả lời bổ sung. Server lưu
trên cùng ticket, giữ nội dung gốc, tăng revision và ghi audit. Replay không ghi
thêm. AI đọc snapshot đã lưu, trả slot có bằng chứng, thông tin thiếu và gợi ý
ticket trùng có giải thích; không tạo quy trình hay tự gộp ticket.

[ADR-013](../../../../docs/architecture/ADR-013-ai08-enrichment-dedup-advisory.md)
giải thích ranh giới và giới hạn;
[OpenAPI](../../../../contracts/apps/servicedesk/context-openapi.yaml)
mô tả đầy đủ request/response. Baseline nhánh là `origin/develop` tại
`122519e046a1153aebe997a67bb522b914aa78c5`.

## Cấu hình

Tên biến mô tả chức năng, không dùng mã giai đoạn làm tên cờ. Cấu hình mặc định:

```dotenv
SERVICE_DESK_CONTEXT_ENABLED=false
SERVICE_DESK_CONTEXT_MODE=DISABLED
SERVICE_DESK_CONTEXT_POLICY_BUNDLE_DIGEST=
SERVICE_DESK_CONTEXT_PROCESSING_RECEIPT=
SERVICE_DESK_CONTEXT_PROCESSING_VERIFICATION_KEY=
```

| Biến | Kiểu / giá trị | Ý nghĩa |
| --- | --- | --- |
| `ENABLED` | `true`, `false` | Cờ tổng cho API gợi ý; mặc định `false` |
| `MODE` | `DISABLED` | Tắt, trả 404 sau kiểm tra quyền ticket |
| `MODE` | `SHADOW` | Chạy nội bộ, không trả slot/câu hỏi/candidate/provenance suy luận |
| `MODE` | `ASSISTED` | Trả gợi ý, luôn cần người duyệt |
| `MODE` | `PROMOTED` | Backend từ chối; không phải cách bỏ qua phê duyệt phát hành |
| `POLICY_BUNDLE_DIGEST` | 64 ký tự hex SHA-256 | Pin đúng bộ policy runtime và grant |
| `PROCESSING_RECEIPT` | Đường dẫn file trên backend | Grant do bên có thẩm quyền cấp độc lập |
| `PROCESSING_VERIFICATION_KEY` | Chuỗi bí mật ≥32 ký tự | Khóa HMAC xác minh grant; không đưa vào Git/frontend/log |

Tên rút gọn trong bảng đều có tiền tố `SERVICE_DESK_CONTEXT_`. Bật cờ tổng vẫn
cần MODE hợp lệ và grant. API ghi bổ sung/đọc revision không cần AI hoặc grant.
Các cờ cho phép thực thi/gộp trong response luôn `false`.

Compose đã truyền các biến cấu hình. Nếu dùng grant trong container, mount file
read-only bằng Compose override và đặt đường dẫn **bên trong container**. Không
đặt khóa ký hay raw ticket trong `.env.example` hoặc override được commit.

Grant dùng envelope `{payload,signature}`: payload là Base64 của JSON UTF-8,
signature là hex HMAC-SHA256 của chính bytes payload. Payload chỉ có:

```json
{
  "purpose": "AI08_ADVISORY_PROCESSING",
  "workspace": "<workspace được phép>",
  "scope": "OWN_TICKET",
  "policyBundleDigest": "<SHA-256 bộ policy>",
  "expiresEpochSecond": 0,
  "issuer": "<đơn vị cấp độc lập>"
}
```

Đây là mẫu cấu trúc, **không phải grant hợp lệ**: thời hạn 0 đã hết hạn. Requester
dùng `OWN_TICKET`; agent/coordinator dùng `WORKSPACE_REVIEW` cho API gợi ý. Scope
không thay thế membership/ACL. Không có endpoint hoặc script runtime tự cấp grant.
Digest được in trong report kiểm thử, gồm ba policy ngữ cảnh/câu hỏi/dedup cùng
taxonomy và factual policy AI07. Khi đổi một nguồn, grant cũ không còn dùng được.

## Gọi API

Các route dưới đây có tiền tố
`/api/apps/servicedesk/v1/workspaces/{workspace}/tickets/{ticketId}` và cần bearer
token của người có quyền đọc ticket. Agent/coordinator dùng quyền workspace;
requester chỉ đọc/ghi ticket của mình. Ticket ID không phải token truy cập.

1. `GET /context-revision` lấy `contextRevision` hiện tại. Ticket mới bắt đầu ở 0.
2. `POST /clarifications` gửi câu trả lời; không gọi lại API tạo ticket.

```json
{
  "contractVersion": "ticket-clarification@0.1.0",
  "clientAnswerId": "00000000-0000-4000-8000-000000000001",
  "expectedContextRevision": 0,
  "answers": [
    {"slot": "device.identifier", "value": "15", "correction": false},
    {"slot": "location.room", "value": "1304", "correction": false}
  ]
}
```

`201` là sự kiện mới, `200` là replay cùng payload. Giữ nguyên clientAnswerId khi
retry vì lỗi mạng. Cùng ID khác payload hoặc revision cũ trả `409`; đọc revision
mới, giữ câu trả lời người dùng và giải quyết xung đột trước khi gửi request mới.
Replay trả revision của sự kiện cũ; GET mới trả revision hiện tại.

`correction=true` xác nhận sửa lời khai trước. Câu trả lời “có thể phòng 1305” vẫn
bị coi là dè dặt dù bật cờ; không tự ghi đè phòng đã báo. Không chấp nhận các slot
`category`, `priority` hoặc trường `actor`. Giới hạn: 8 answers/request, 256 ký tự
mỗi value, 16 KiB body và 32 sự kiện/ticket. Chạm giới hạn thì chuyển xử lý thủ công.

3. Khi được bật và có grant, `POST /assistant/enrichment`:

```json
{"contractVersion":"ticket-enrichment@0.1.0","readinessProfile":"R10_DUPLICATE"}
```

`ASSESSMENT` là profile còn lại, hỏi scope/blocking/workaround. Không gửi profile
thì mặc định `R10_DUPLICATE`. Response có tối đa 3 câu hỏi và `questionId` ổn định;
client chống hiện lặp cùng câu hỏi còn pending. Unknown/ambiguous/conflict trả value
null. Bằng chứng giữ source ID, revision, quote và offset Unicode code point.

4. `POST /assistant/duplicates`:

```json
{"contractVersion":"ticket-duplicate-probe@0.1.0"}
```

Ứng viên chỉ trong cùng workspace, tiền nhiệm trong 90 ngày, tối đa 50 hàng trước
lọc process. Response báo `candidateWindowFull` nếu đã chạm giới hạn; không diễn
giải “không có ứng viên” thành “chắc chắn không trùng”. Requester không nhận ID
ứng viên. Reviewer nhận tối đa 3 ID được phép xem và reason codes, không raw text.

Runtime chưa có category đã duyệt và shared-service mapping. Vì vậy chưa phát
`MATCH_READY_FOR_REVIEW` trong API thực tế. `POSSIBLE_DUPLICATE` vẫn hữu ích để
người điều phối xem xét. Matcher thuần có test MATCH bằng category fixture đã đánh
dấu verified; không coi đó là tích hợp category production. `duplicateOf` luôn null.

Sau bổ sung, AI08 tính lại từ facts hiện tại; `AI07_REFRESH_REQUIRED` báo assessment
AI07 cần refresh theo contract augmented-input riêng trong tương lai. Không gọi
thêm model và không giả nhận AI07 đã dùng câu trả lời mới.

## Kiểm tra tái lập

Chuẩn bị Python 3.12 với `intelligence/requirements.lock`, Docker và Maven build
image có dependency được cache. Script sau tạo database/network tạm, không dùng
database ứng dụng đang chạy. `postgres:16-alpine` là image local đã có khi nghiệm
thu; script mặc định/CI dùng digest PostgreSQL bookworm được pin trong repository.

```powershell
python scripts/check-tier-boundaries.py
python scripts/test-tier-boundaries.py
python scripts/intelligence/validate-foundation.py
python -m pytest intelligence/tests -q

./scripts/test-file.ps1 -BuildImage ai07-v19-maven:local `
  -DatabaseImage postgres:16-alpine -Tests 'Context*Test,Duplicate*Test'
python scripts/apps/servicedesk/ai08/evaluate-context.py `
  --output .local/ai08/<run-id>/receipt.json

./scripts/test-file.ps1 -BuildImage ai07-v19-maven:local -DatabaseImage postgres:16-alpine
./scripts/test-file.ps1 -BuildImage ai07-v19-maven:local -DatabaseImage postgres:16-alpine -MavenProfile core-only
./scripts/test-file.ps1 -BuildImage ai07-v19-maven:local -DatabaseImage postgres:16-alpine `
  -MavenProfile second-domain `
  -Tests 'RoomRequestIntegrationTest,TierBoundaryTest,CoreKnowledgeCatalogTest,CoreProcessCapabilitiesTest,WorkspaceActivityJournalTest,TrustedIntelligenceCapabilityTest,HttpClassificationTransportTest'
```

Lưu receipt và copy report trước khi chạy profile khác vì wrapper dùng Maven
`clean`. Receipt chỉ PASS nếu các suite bắt buộc có test chạy thật, không skipped.
Nếu Windows sandbox không cho dùng thư mục tạm, chạy bằng quyền kiểm thử được cấp;
không coi lỗi permission đó là lỗi logic hoặc tuyên bố PASS.

Kiểm tra wheel theo CI: build bằng `python -m build --no-isolation`, xác minh/cài
bằng `scripts/intelligence/install-built-wheel.py`, sau đó chạy
`python -I -m pytest -c scripts/intelligence/installed-pytest.ini intelligence/tests -q`.
Không dùng Python hệ thống thiếu dependency/pin để kết luận regression dự án.

Workflow mới `.github/workflows/context-intelligence.yml` chạy CPU và PostgreSQL,
không cần GPU hoặc weights. Trạng thái hosted CI vẫn `UNVERIFIED` cho tới khi có
run trên đúng SHA. Không tự đánh dấu `BLOCKED_BILLING` khi chưa có bằng chứng.

## Rollback

Đặt `SERVICE_DESK_CONTEXT_ENABLED=false`, `MODE=DISABLED`, khởi động lại backend.
Giữ nguyên bảng, migration, câu trả lời và audit. Không rollback dữ liệu người
dùng, không đổi workflow. Tạo ticket/manual review và API bổ sung vẫn hoạt động.
Các điều kiện production nằm ở [quality gates](QUALITY_AND_RELEASE_GATES.md).

## Kết quả kiểm tra trong phiên triển khai

- Regression Java/PostgreSQL: **513 test PASS, 0 failed/error/skipped**. Sau bổ sung
  guard actor ngay trước publication, chạy lại toàn bộ nhóm AI08: **45 test PASS**.
- Python đúng virtualenv/dependency dự án: source **1.479 PASS, 7 skipped**; wheel
  isolated **1.479 PASS, 7 skipped**. Các test opt-in cần model không được tuyên bố
  là đã chạy. Python hệ thống từng lỗi do môi trường, không dùng làm kết luận cuối.
- Frontend: typecheck, lint, **79 test**, build ứng dụng và platform đều PASS.
- Source boundary, **39 mutation test**, foundation **145 negative cases** PASS.
- Build tách tầng: **core-only 91/91**, **second-domain 26/26** PASS; loại Service
  Desk khỏi bản Room Request không kéo theo migration/policy mới của Service Desk.
- OpenAPI 3.1 và các JSON Schema ticket hợp lệ; contract test có negative cases cho
  authority, confidence số, nguồn không hợp lệ và thiếu điều kiện R10.
- `scripts/start.ps1` build/khởi động stack thành công; `scripts/smoke.ps1` PASS qua
  gateway. Smoke riêng với Keycloak thật xác nhận đọc revision, append/replay,
  giữ ticket/process và advisory mặc định tắt. Không có grant production được tạo.
- Synthetic: **200/200 slot checks, 590/590 literal spans, 80/80 missing-slot checks,
  100/100 pair decisions** trên 10 family. Đây không phải precision/recall trên Gold.

Đo CPU cuối trên Linux container, Java 17.0.15, 16 logical processors; 200 request
mỗi mức, mẫu text ngắn, 50 pair/request, warmup trước đo. Không gồm DB/HTTP và
không phải worst case cho toàn bộ giới hạn text/events:

| Concurrency | Extraction p95 (ms) | 50 pair p95 (ms) | Degraded |
| --- | ---: | ---: | ---: |
| 1 | 0,526 | 0,850 | 0/200 |
| 2 | 0,628 | 0,455 | 0/200 |
| 4 | 0,540 | 1,829 | 0/200 |

Receipt/report gốc nằm tại `.local/ai08/`, không commit log, secrets, artifact build
hoặc dữ liệu riêng. Gate production vẫn **NO_GO**; hosted CI **UNVERIFIED**. Không
có bằng chứng billing để ghi trạng thái `BLOCKED_BILLING` trong lần kiểm tra này.
