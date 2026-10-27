# AI08-05A — Vòng đời bổ sung và reassessment an toàn

## Phần A.1 — Readiness

Commit riêng cho policy theo tác vụ và `POST .../assistant/readiness`.
Đã kiểm tra 27 test Java CPU/contract/grant; source boundary PASS.
Grant cũ không có `readinessPolicyDigest` không được tự dùng cho API mới.

Thiếu room/device để dò trùng không đồng nghĩa phải dừng triage. Readiness của
ASSESSMENT và DUPLICATE_CHECK được trả riêng, mọi response đều advisory. API v0.1
cũ giữ nguyên; không dùng boolean `blocking` legacy để điều khiển workflow.

## Phần A.2 — Case, outbox và timeout

Migration V12 thêm case, outbox, question-request và transition ledger trong app
Service Desk; clarification ở V11. V8/V9/V10 giữ nguyên theo backend review,
estimate và background assessment. Đây là số phiên bản sau PR #51; các kết quả
dưới đây là bằng chứng lịch sử trước đồng bộ, xem
[bằng chứng tích hợp mới](PR51_CONFLICT_RESOLUTION.md).
Tạo ticket và append câu trả lời phát event đồng bộ
trong transaction hiện có; listener chỉ ghi metadata/outbox, không gọi model.
Outbox lỗi thì rollback cùng câu trả lời/revision/audit. Replay không phát event.

`SERVICE_DESK_ENRICHMENT_ENABLED=false` mặc định không enroll case mới.
`SERVICE_DESK_ENRICHMENT_TIMERS_ENABLED=false` mặc định không chạy timer; cả hai
nhận true/false. Timer bật thì quét tối đa 50 case/đợt, mặc định 60 giây, khóa
ticket trước case với SKIP LOCKED. Deadline DB là nguồn sự thật, không dùng sleep.

Job sau create/append là durable. Xử lý bằng command có danh tính đã xác thực:
`POST /tickets/{id}/enrichment-state/evaluate`, body chỉ
`{"contractVersion":"ticket-enrichment-lifecycle@0.1.0"}`. Worker ngoài ứng dụng
có thể gọi cùng API bằng principal được cấp quyền; không giả làm actor từ outbox.
UI sẽ gọi command sau commit. Mỗi command nhận tối đa một generation; coalesce
revision cũ, debounce 750 ms, lease 60 giây, tối đa 2 attempts nếu worker chết.

Ứng dụng chưa có primitive danh tính background/notification sender. Scheduler
nội bộ vì thế chỉ chuyển metadata tới manual và tạo outbox PENDING, không gọi AI.
Không tuyên bố tự động gửi email/notification. Question ledger có requestInstanceId
riêng, khác questionId ổn định theo policy. API trả PENDING, không báo đã gửi.

`GET /tickets/{id}/enrichment-state` kiểm quyền ticket; dữ liệu suy luận chỉ trả
khi grant/actor/policy/snapshot còn hợp lệ. Thiếu quyền xử lý AI thì chỉ trả metadata
manual, không quote/câu hỏi cũ. `GET /enrichment-pending` chỉ dành coordinator trong
workspace, tối đa 50 case, vẫn dùng được khi tắt AI. Không có ticket ẩn vì timeout.

Timeout đặt TIMED_OUT + manualReviewRequired, không đóng ticket. Câu trả lời muộn
vẫn được lưu, gắn LATE_ANSWER_REVIEW_REQUIRED; kết quả mới không xóa quyết định
manual. Việc sửa trạng thái process, SLA, assignment hoặc canonical link không nằm
trong đường này. `workflowGateApplied=false` luôn đúng cho MVP-A.

Guard SQL đã được bổ sung để nhận đúng cú pháp PostgreSQL FOR UPDATE SKIP LOCKED /
NOWAIT, không nhầm SKIP thành tên bảng. Mutation test vẫn chặn truy cập bảng private.
Kiểm tra phần A.2: 61 test liên quan PASS, sau bổ sung audit và race/lease thì 11
test lifecycle PostgreSQL/Flowable PASS; source guard và 41 mutation test PASS.

## Phần A.3 — Reassessment có source map và admission bền vững

`POST /tickets/{id}/assistant/assessment-contextual` nhận duy nhất
`{"contractVersion":"ticket-assessment-contextual@0.1.0"}`. Backend tự đọc
snapshot, không nhận title/description/revision hoặc model output từ client.
AI07 assessment v0.2 giữ nguyên nghĩa cũ, không bị biến thành augmented-input.

Facts AI07 chạy theo từng nguồn gốc, cùng typed answers đã lưu. I/U và evidence
được tính lại trên revision hiện tại. Đính chính explicit loại dữ kiện lịch sử của
trục bị sửa; không âm thầm lấy impact/urgency từ v0. Quote giữ Unicode code-point
offset trên đúng source. Source-map chỉ nhận các đoạn nguyên văn; delimiter và
giá trị chuẩn hóa trong model description không được công bố như literal evidence.

Model input chỉ có title/description theo neutral capability hiện có, tối đa 8192
ký tự và request byte budget. Phần bổ sung gồm giá trị USER_REPORTED đã giải quyết
được theo policy; room/mã thiết bị không làm tăng lượt coarse classification.
Khi input model không đổi, category có thể được tái dùng với trạng thái
MODEL_REUSED_MATERIAL; facts, digest và revision vẫn được tính lại, không gọi là
model đã inference lần nữa. Không tái dùng assessment v0.2 làm kết quả contextual.

Migration V13 giữ admission theo workspace/ticket/input/model/policy/quyền xử lý.
Chỉ một caller được gọi transport; caller đồng thời hoặc attempt bỏ dở không
được tự gọi model lần nữa. Cache chỉ chứa category chưa xác minh, không chứa raw
prompt, answer, evidence hay token. Snapshot/actor/grant/process thay đổi sau
inference thì toàn response bị loại. Không giữ JDBC transaction trong transport.

`SERVICE_DESK_CONTEXTUAL_ASSESSMENT_ENABLED=false` mặc định chỉ tính facts CPU.
Để gọi model phải có flag true, classifier ASSISTED, policy digest cấu hình đúng,
grant context có `readinessPolicyDigest` và `contextualPolicyDigest`, cùng grant
AI07 pin assessment/model/prompt. Grant không được ứng dụng tự cấp.

Policy contextual còn DRAFT, model augmented chưa được benchmark/duyệt trên Gold.
Test transport synthetic chỉ chứng minh wiring, nguồn dữ liệu, call budget và
chống stale, không phải chất lượng thực tế của Qwen. Chưa được dùng synthetic
category để mở production. Model lỗi thì DEGRADED/manual, không vòng retry storm.

Câu trả lời đến sau deadline vẫn là late dù timer chưa kịp poll. Câu trả lời đúng
hạn đưa generation mới vào budget xử lý DRAFT 120 giây; deadline chờ cũ không làm
timeout nhầm trong lúc đang xử lý dữ kiện mới. Timeout và answer dùng cùng khóa
aggregate; câu trả lời không bị mất và không tự hủy quyết định manual.
Kiểm tra A.3: 29 test nền PASS; vòng cuối 26 test input/source-map, contextual,
lifecycle và quyền/stale/SHADOW PASS trên PostgreSQL/Flowable. Chưa chạy Qwen thật
trên input augmented vì chưa có benchmark/grant phát hành cho policy này.

## Phần A.4 — Giao diện và bằng chứng kiểm thử

Trang chi tiết ticket có form bổ sung ngay trên ticket hiện tại. Có thể trả lời câu
hỏi hoặc tự chọn loại thông tin; đính chính cần thao tác xác nhận riêng. Nội dung
chưa gửi được giữ khi mất kết nối hoặc gặp HTTP 409. Khi mất response, gửi lại cùng
payload dùng cùng clientAnswerId và revision ban đầu, kể cả GET đã thấy revision
mới. Khi server xác nhận xung đột, form tải revision mới và chờ người dùng gửi lại.
Không lưu nháp chứa nội dung người dùng vào localStorage.

Kết quả contextual chỉ hiển thị ở revision tương ứng. Sau bổ sung, giao diện ẩn
gợi ý phân loại legacy dựa trên nội dung ban đầu. Query key chứa workspace, ticket
và subject; đổi người dùng hoặc ticket tạo lại panel. Lỗi quyền không hiển thị câu
hỏi hoặc kết quả cũ. Danh sách `/servicedesk/enrichment-pending` dành cho coordinator,
chỉ tải metadata trong workspace và liên kết tới ticket; API kiểm quyền độc lập.
Danh sách có phân trang 50 case/trang (`page` từ 0 đến 10000), không dừng ở 50
case đầu. Thứ tự manual/updatedAt/id ổn định khi dữ liệu không đổi; nếu có cập nhật
đồng thời, tải lại từ trang đầu để rà soát. Integration test tạo 51 case và kiểm
không thiếu/lặp qua các trang, cùng quyền coordinator và giới hạn page.
Giao diện ghi rõ bổ sung không tạm dừng quy trình hay thay đổi hạn xử lý.

Schema mới tự chứa định nghĩa; integration test kiểm response Java thật bằng JSON
Schema. OpenAPI ghi rõ method, quyền, phiên bản và lỗi của các API mới. Kiểm tra
trình duyệt cũ được giới hạn locator trạng thái trong đúng vùng trợ lý tri thức,
vì trang hiện có thêm vùng trạng thái bổ sung độc lập.

Hai meter `servicedesk.enrichment.evaluations` và
`servicedesk.enrichment.processing` đếm lượt xử lý và thời lượng command đã claim.
Tag outcome chỉ nhận tập giá trị cố định, không ghi ticket, workspace, actor, raw
text hoặc token. Đây chưa phải benchmark queue/GPU hay bằng chứng SLO production.

Kết quả local ngày 27/10/2026:

| Kiểm tra | Kết quả và phạm vi |
| --- | --- |
| Java full trên PostgreSQL 16 / Flowable thật | 550 PASS, 0 lỗi, 0 skipped |
| Java core-only | 91 PASS, 0 lỗi, 0 skipped |
| Java second-domain | 26 PASS, 0 lỗi, 0 skipped; app đặt phòng không phụ thuộc Service Desk |
| Python source bằng virtualenv dự án | 1.479 PASS, 7 skipped theo bộ test hiện có |
| Frontend | typecheck, lint, 83 unit test, build, build:platform PASS |
| Chromium | 17 PASS, gồm 6 ca bổ sung mới; session/API synthetic |
| Source boundary và mutation | PASS; 41 mutation test |
| Foundation và contract | validator foundation, OpenAPI 3.1 và JSON Schema PASS |
| Stack thật | start.ps1, smoke.ps1, clarification/replay qua Keycloak + gateway PASS |
| Khởi động lại backend thật | Case/revision/deadline giữ nguyên; AI tắt chuyển manual; trả cấu hình về mặc định |
| DB consistency | engine/scope rollback và audit consistency PASS |

Full Java ban đầu gặp giới hạn connection PostgreSQL khi nhiều Spring context
được cache. `backend/src/test/resources/application.properties` giới hạn pool
test ở 3 connection/context, minimum-idle=0; chạy lại 549 test PASS, vòng cuối
thêm kiểm thử phân trang đạt đủ 550 test PASS. Cấu hình
pool của ứng dụng triển khai giữ nguyên.

Fixture readiness gồm 100 ca synthetic theo các mẫu tình huống và biến thể tham
số; không phải 100 mẫu người dùng độc lập hay tập Gold được duyệt. Kết quả:
40/40 safety bypass, 30/30 ca tiếp tục assessment, 180/180 literal span hợp lệ,
0 chặn toàn ticket sai. CPU p95 của lần chạy cuối là 0,718 ms, chỉ đo extractor +
readiness trên fixture, không gồm DB, queue, network hay model. Không suy ra
precision/recall thực tế hoặc tốc độ model từ các số này.

Lệnh tái kiểm tra bằng wrapper và công cụ có sẵn:

```powershell
./scripts/test-file.ps1 -BuildImage ai07-v19-maven:local -DatabaseImage postgres:16-alpine

# Chạy trước các profile khác vì wrapper dùng Maven clean.
python scripts/apps/servicedesk/ai08/evaluate-context.py --output .local/acceptance/context-receipt.json
python scripts/apps/servicedesk/ai08/verify-lifecycle-evidence.py --output .local/acceptance/lifecycle-receipt.json

./scripts/test-file.ps1 -BuildImage ai07-v19-maven:local -DatabaseImage postgres:16-alpine -MavenProfile core-only
./scripts/test-file.ps1 -BuildImage ai07-v19-maven:local -DatabaseImage postgres:16-alpine -MavenProfile second-domain -Tests 'RoomRequestIntegrationTest,TierBoundaryTest,CoreKnowledgeCatalogTest,CoreProcessCapabilitiesTest,WorkspaceActivityJournalTest,TrustedIntelligenceCapabilityTest,HttpClassificationTransportTest'

python scripts/check-tier-boundaries.py
python scripts/test-tier-boundaries.py
python scripts/intelligence/validate-foundation.py
python -m pytest intelligence/tests -q

cd frontend
npm run typecheck
npm run lint
npm test
npm run build
npm run build:platform
npm run test:e2e
```

Image và dependency phải được provision đúng môi trường dự án. Các lệnh Python
cần virtualenv đúng phiên bản; không dùng interpreter có dependency khác rồi coi
lỗi môi trường là lỗi ứng dụng. Kiểm thử browser cần Vite dev server theo cấu hình
Playwright vì synthetic session thay module auth ở source; server static đang chạy
có thể phải dùng cổng dev riêng. Lần local này dùng cổng 5183 để giữ server hiện có.

Verifier lifecycle kiểm hash fixture/policy và kết quả suite thực thi, xuất receipt
không chứa nội dung ticket, luôn giữ SYNTHETIC_ONLY / NO_GO / hostedCI UNVERIFIED.
Output tạo bằng chế độ exclusive, không âm thầm ghi đè bằng chứng cũ. Workflow
`context-intelligence.yml` chạy thêm lifecycle/contextual và lưu cả hai receipt;
việc sửa workflow không chứng minh GitHub Actions đã chạy trên SHA này.

## Cấu hình và vận hành

Các cờ đều mặc định `false`; tên mô tả chức năng, không dùng mã đầu việc AI08:

| Biến | Ý nghĩa khi true |
| --- | --- |
| SERVICE_DESK_CONTEXT_ENABLED | Cho phép lớp context advisory theo mode và grant đã cấp |
| SERVICE_DESK_ENRICHMENT_ENABLED | Enroll case/outbox khi create hoặc append clarification |
| SERVICE_DESK_ENRICHMENT_TIMERS_ENABLED | Quét metadata reminder/timeout; cần enrichment enabled |
| SERVICE_DESK_CONTEXTUAL_ASSESSMENT_ENABLED | Cho phép model đọc input bổ sung; vẫn cần policy/grant mới |

Mode context nhận DISABLED, SHADOW, ASSISTED; PROMOTED hiện bị chặn. Các biến bật
tính năng nhận true/false. Poll delay là số mili giây, mặc định 60000. Digest là
SHA-256; receipt/key do bên có thẩm quyền cấp, không tự phát hành khi thiếu approval.
Chi tiết comment đặt ngay trong `.env.example` và cấu hình ứng dụng.

Chỉ bật timer sau khi có điều phối viên theo dõi danh sách manual. Không có adapter
gửi thông báo trong MVP-A: PENDING là đang chờ tích hợp sender, không có nghĩa đã gửi.
Worker AI ngoài ứng dụng phải có danh tính/quyền thật; scheduler nội bộ không chạy
inference thay người dùng. UI có command sau commit; nếu người dùng rời trang trước
đó và chưa có worker được ủy quyền, case vẫn durable rồi chuyển manual khi hết hạn
với timer đã bật. Không mô tả đường này là pipeline AI tự động hoàn toàn.

Rollback bằng cách tắt các cờ, giữ dữ liệu clarification, case, ledger và migration.
Danh sách manual vẫn đọc được khi AI tắt. Không tự hạ schema hoặc xóa lời khai để
rollback. Trạng thái manual không tự được gỡ bởi câu trả lời muộn.

## Gate chưa phải quyết định production

Mốc nhắc/hết hạn/số vòng là DRAFT trong readiness policy; cần process owner duyệt.
MVP-B (BPMN review/await-info, pause SLA, migration instance) chưa được triển khai.
Không nâng model hoặc dùng kết quả synthetic làm Gold. Hosted CI chưa được xác minh.
