# AI D2 — Tiến độ triển khai và bàn giao

Baseline khởi tạo: `050a32faf72c8306e719c8384a257b9b48fa2caa`, ngày 27/10/2026.
Đã fetch lại và tích hợp `origin/develop@abdbac293531e864c03535a126e3ee5e1fb11709`
gồm PR #52 routing/duty và PR #53 gợi ý phân công. Nhánh mới:
`feature/ai-d2-intake-portable-delivery`. Guard và gợi ý hiện có được mở rộng
cho process intake mới; không triển khai lại nghiệp vụ từ hai PR.

| Cụm | Nội dung | Trạng thái |
| --- | --- | --- |
| C0 | Pin baseline, ADR, phạm vi và acceptance | Đã ghi nhận |
| C1 | Server review admission, CAS, timeout/manual/rework | 17 PostgreSQL tests PASS (15 admission + 2 upgrades) |
| C2 | Sealed final contextual assessment và worker single-flight | 67 focused tests PASS; 13 final pipeline cases |
| C3 | Duplicate retrieval/incident, feedback và workflow/UI | Canonical + true-wait + final: 27 tests PASS; UI ở C6 |
| C3a | Process-key riêng: intake trước review, handoff và rework | 65 regression tests PASS, gồm 6 true-wait cases |
| C4 | Full model image, provenance, transport secret và Compose | Image offline + GPU HTTP synthetic PASS; transport/Compose tests PASS |
| C5 | Offline distribution/onboarding/acceptance | Tooling có tests; bàn giao thật chờ approval và máy nhận sạch |
| C6 | Intake/review/canonical UI và legacy compatibility | 83 unit + 19 browser tests PASS; build/typecheck/lint PASS |

ET vẫn là bảng mặc định và coordinator override. Không thêm effortSize hay model ET.
Auto canonical merge, chất lượng Gold, SLA/notification của toàn bộ ticket spec và
production GO cần quyết định/receipt độc lập; không dùng synthetic để thay thế.

Artifact local tìm thấy: adapter safetensors và tokenizer tại
`.local/ai07_qwen35_silver_lora_results`. Base serving snapshot đã tải và kiểm checksum
theo quyền người dùng cho phép từ Hugging Face, revision
`23c69c53358a07516b5827588b3fdb12ae78fd65`. Quyền phân phối adapter chờ issuer.
Không commit model,
prediction, dữ liệu hoặc secrets.

## C1 — Admission trước review

Migration V16 additive; V8–V13 không sửa. Bật cho ticket mới bằng
`SERVICE_DESK_INTAKE_NEW_TICKETS=true`, mặc định false. Ticket đã có intake vẫn
giữ guard và đường manual nếu tắt route mới. Deadline mặc định 86400 giây là
engineering bound, không phải SLA nghiệp vụ đã duyệt.

`GET tickets/{id}/intake` chỉ đọc metadata; `POST .../intake/manual-release` cần
coordinator độc lập, reason, operationId, expectedContextRevision/expectedVersion.
`ReviewDecision` thêm CAS cho ticket được enroll; legacy không có admission giữ
API cũ. Generic completion cũng kiểm admission. Timeout chuyển MANUAL_REQUIRED;
late answer sau review chỉ bật REVIEW_STALE_NOTICE. Task Flowable review vẫn có
thể tồn tại trước release; chưa claim true BPMN wait ở C1.

Kiểm chứng C1: `IntakeAdmissionIntegrationTest,ServiceDeskMigrationIntegrationTest`
trên PostgreSQL 16.6 tạm: 17 pass, 0 fail/error/skip; source boundary pass. Log giữ
trong `.local/task-execution-full-targeted`. Cụm C2 tiếp tục bổ sung auto progression
và final assessment; C1 đã chặn worker legacy trên ticket có admission.

## C2 — Final snapshot và feedback

V17 bổ sung grant digest và review task binding; không sửa V16 đã commit. CPU
screening kiểm readiness trước duplicate Stage A, sau đó seal revision/context/
policy/grants/review round/task ID. Policy duplicate DRAFT chỉ advisory, không hold
hoặc merge. Job background hiện có chỉ nhận ticket intake đã seal; inference ở
ngoài transaction. Unique final-attempt identity và ContextualAttemptStore giữ
logical single-flight, material cache tái dùng category nhưng rebind revision.

Endpoint classify/contextual cũ bị chặn cho ticket enrolled, GET không infer.
Model lỗi, abstain, grant thiếu/đổi hoặc worker interrupted chuyển manual; không
bịa OTHER/confidence. Actor kỹ thuật được kiểm quyền workspace và quyền requester
thật qua BackgroundProcessing; observer/reader role không cho claim review/handle.
Grant directory hỗ trợ file `<workspace>.OWN_TICKET.json` và
`<workspace>.WORKSPACE_REVIEW.json` do issuer cấp, thay vì giả danh người gửi.

Final result chỉ publish khi revision/policy/grants và quyền đọc vẫn hợp lệ.
Requester không nhận candidate IDs. Review cần CAS và task ID được seal; feedback
AI-vs-human append-only cùng transaction, đánh dấu NOT_REVIEWED_GOLD. Late answer
sau review không tự inference hoặc thay assignment.

Kiểm chứng C2: FinalAssessmentIntegrationTest, IntakeAdmissionIntegrationTest,
TicketClassificationOrchestratorTest, ContextProcessingGateTest: **67 pass**, gồm
13 final pipeline cases, 0 fail/error/skip. Bộ riêng trước đó gồm worker identity,
context guard, legacy contextual và admission: 48 pass. Model transport trong
JUnit là synthetic; bằng chứng Qwen thật được giữ riêng ở cụm image.

## C3a — True pre-review workflow

`SERVICE_DESK_INTAKE_TRUE_WAIT=false` mặc định. Khi bật cùng new-ticket admission,
ticket mới dùng `service-desk-intake-reviewed`: chưa có task review/handle lúc tạo.
Coordinator disposition ghi receipt và hoàn tất intake qua WorkInbox trong cùng
transaction; sau đó mới tạo review. Generic task completion không bypass receipt.
Rework quay về intake của cùng process, tăng review round. Flag rollback chỉ đổi
route mới; legacy process và handler mới vẫn được giữ. ET/execution projection
tái dùng backend hiện có, không có model ET.

Kiểm chứng IntakeWorkflowIntegrationTest, IntakeAdmissionIntegrationTest,
FinalAssessmentIntegrationTest, TicketIntegrationTest: **65 pass**, 0 fail/error/skip.
Follower branch chưa được phép complete trực tiếp khi canonical disposition chưa
được thực hiện; không coi việc có BPMN branch là R10 đã hoàn tất.

## C3b — Preflight và canonical do người quyết định

V19 bổ sung projection room/object, notice chờ xử lý, canonical link và lịch sử
quyết định. Preflight quét tối đa 500 ticket trong 90 ngày trước ticket đang xét,
lọc workflow đang mở trước khi chọn tối đa 50 candidate. Kết quả báo rõ incomplete;
không dùng danh sách bị cắt để cho phép liên kết. Projection chỉ ưu tiên truy hồi,
bằng chứng quyết định luôn đọc lại context hiện tại và trạng thái Core.

`SERVICE_DESK_CANONICAL_HUMAN_ENABLED=false` mặc định. Khi được bật có kiểm soát,
coordinator độc lập có thể liên kết v2 intake với root đã được người duyệt cho
revision hiện tại, cùng phòng, cùng thiết bị vật lý và triệu chứng tương thích.
Không dùng category do model dự đoán để chứng nhận trùng. Lệnh cần CAS, digest
candidate, operationId và lý do; chặn chain/cycle/cross-workspace và generic bypass.
Follower không tạo handle thứ hai. Split đang mở quay về intake trong cùng process.
Root phải kết thúc bình thường sau requester xác nhận; follower có late context
không tự đóng. Requester chỉ thấy tiến độ thô, không nhận ID/nội dung root.

Incident nhiều thiết bị/cùng khu vực chỉ tạo tín hiệu và notice PENDING, không merge.
Worker truyền trạng thái dùng service identity thật; realm template chỉ cấp
task:handle cho role kỹ thuật ở task follower, không cấp candidate review/handle.
Không reset realm đang chạy. Notice chưa có kênh gửi ngoài hệ thống; split sau khi
follower đã kết thúc cần workflow nghiệp vụ riêng. Auto merge và production GO
vẫn tắt, chờ policy/Gold và chủ sở hữu phê duyệt.

Kiểm chứng trên PostgreSQL/Flowable: 6 canonical cases (liên kết, redaction, split,
thiết bị khác, root chưa duyệt, đóng theo xác nhận, late context), 6 true-wait cases,
2 migration upgrade cases đều pass. Cùng lượt có 12 kiểm tra secret/HTTP transport
pass; tổng **26 pass**, không skip. Bộ full regression được ghi ở cuối tài liệu.

### Bổ sung độ bền và kiểm tra hồi quy

Replay canonical cũ sau split bị từ chối theo decision hiện tại và revision.
Worker xoay thứ tự follower chưa xử lý được để tránh chặn các link đến sau.
Đã kiểm tra root đang mở vẫn được tìm thấy sau hơn 50 predecessor đã hủy.
Nhãn intake/review/follower tiếng Việt chỉ đăng ký cho đúng process có task đó.

Backend full: **658 pass**, core-only: **104 pass**, second-domain: **30 pass**,
không fail/error/skip. Sau thay đổi nhãn/hardening, chạy lại ba bộ canonical,
true-wait và final assessment: **27 pass**. Unit test legacy được cập nhật để
kiểm thêm lời gọi guard admission; không bỏ guard để giữ kỳ vọng cũ.

## C6 — Giao diện Ticket Assistant

Ticket đã enroll đọc `GET intake` và poll metadata; reload không gọi classify,
assessment hoặc endpoint enrichment cũ. Bảng đề xuất hiển thị kết quả đã lưu,
câu hỏi readiness, deadline và cảnh báo late context. Form bổ sung giữ cách lưu
answer trên ticket hiện có, không khởi tạo ticket thứ hai. Legacy tiếp tục dùng
enrichment cũ khi GET xác nhận không có admission.

Coordinator độc lập có form reason/CAS để chuyển intake sang review và duyệt
category/priority. Canonical panel yêu cầu đối chiếu root và xác nhận bằng chứng
trước khi gửi lệnh; server vẫn kiểm policy, quyền và CAS. Worklist intake có phân
trang; link timeline hỗ trợ cả process cũ và mới. Chưa có grant thì dùng manual.

Frontend: typecheck, lint, production build pass; **83 unit tests**, **19 browser
tests** pass. Browser test chạy Vite source ở cổng riêng 5175, không tái dùng
frontend Docker cũ ở 5173. Các test browser dùng API/session synthetic, không
thay thế nghiệm thu Keycloak/gateway/model thật.

## C4 — Image model tự chứa và transport

Wrapper image chứa base/adapter/tokenizer, policy mapping/guard, license base và
supplement; chỉ copy file allowlist đã kiểm manifest. `runtimeIdentity` vẫn là
code Docker image ID, wrapper Docker image ID và registry digest được ghi bên ngoài
để tránh vòng hash. Offline verifier chạy không mạng/không mount model. Runtime
non-root, read-only, chỉ mount token file; Compose model nằm trên mạng internal,
chỉ backend dùng chung mạng đó và không có host port.

Java hỗ trợ token/key file có giới hạn kích thước, từ chối nguồn inline đồng thời
với file; lỗi không chứa secret. Giữ constructor cũ cho callers hiện có. Scope
HMAC grant vẫn do issuer cấp độc lập. Năm Python boundary tests, Compose resolution
và Java secret/HTTP tests pass. GPU HTTP thật: HARDWARE/PROPOSE, score null, 724.697
ms cho fixture bàn phím; bad bearer 403, unknown schema 502.

Hướng dẫn và lệnh cụ thể: [AI_D2_PORTABLE_DOCKER_VI.md](AI_D2_PORTABLE_DOCKER_VI.md).
Receipt private trong `.local/ai07/d2`; chưa publish registry và chưa phân phối kit.

## C5 — Kit offline và onboarding

Exporter chỉ nhận Docker image IDs đã kiểm tra và approval phân phối có chữ ký
độc lập. Đóng gói đủ model/code cha và bảy service, inventory SHA-256/size,
runtime notices, profile synthetic, Compose và lifecycle script theo allowlist.
Gói export chưa ký phải được issuer ký release manifest trước khi máy nhận cài.

Verifier kiểm hash manifest nhận độc lập, chữ ký, mọi file, image identity và
archive không có traversal/link/device. Install sinh secrets/realm ở máy nhận,
không đổi stack đang có; Start không build/pull, Stop không xóa volume và không
phụ thuộc approval còn hạn. Diagnostics chỉ lưu health/check names, không log thô.
Assisted activation cần chữ ký riêng bind chính xác grant/key bytes và policy,
không có API tự phát grant. Backend vẫn kiểm quyền và grant trong runtime.

Bốn test offline/Compose pass, gồm chữ ký OpenSSL thật với key fixture tạm,
tampered inventory/archive/unlisted secret, scope/policy/grant-key substitution và
network isolation. Đây là bằng chứng tooling; chưa có distribution approval thật
hoặc máy nhận sạch, nên export/publish/customer-processing acceptance còn PENDING.

## Đồng bộ develop có PR #52/#53

Giữ migration đã phát hành của develop nguyên bytes: HPDI V14 duty và Service Desk
V15 assignment. Bốn migration D2 chỉ được chạy trong DB kiểm thử tạm, chưa phát hành,
được đổi số từ V14–V17 sang **V16–V19** trước tích hợp. Không repair hay sửa history
DB dùng chung. Các số migration trong các mục phía trên là số cuối cùng của nhánh.

Review giữ eligibility theo support group/duty; cả claim và assign v2 handle đi qua
hook giống workflow reviewed. Gợi ý phân công nhận cả hai process, vẫn chỉ đọc,
không tự assignment và không dùng model ET. Test bổ sung: preview/task suggestion
v2, agent off duty bị chặn claim, không phát assignment event khi claim thất bại.
Test upgrade riêng V15 giới hạn target V15, test nâng cấp toàn bộ kiểm đến V19.

## Kiểm chứng model thật trong final pipeline

`scripts/ai-d2/prove-real-qwen-intake.py` chạy container Qwen tự chứa trên GPU,
mạng internal riêng; runner Java/PostgreSQL tạm chỉ mount token, không mount model.
`RealQwenIntakeProof` là opt-in riêng, không làm các bộ mặc định skip khi không có GPU.
Đã PASS: đúng một Java HTTP inference cho revision đã seal, HARDWARE với confidence
null, final-attempt DONE duy nhất, GET lặp không infer, coordinator handoff/review,
feedback được ghi. Worklist vẫn hiển thị v2 intake đã có kết quả nhưng chờ handoff.

Kiểm chứng thật phát hiện thứ tự class trong contextual request không đúng wire
policy. Đã sửa thành ACCOUNT/HARDWARE/NETWORK/OTHER/SOFTWARE, khớp mapping A–E đã
pin; không đổi model bytes/policy để nới verifier. Fixture review gửi explicit
assignee null theo contract. Receipt: `.local/ai07/d2/real-java-intake-proof.json`.
JWT/processing grants trong JUnit vẫn là test doubles cho fixture owned synthetic;
không dùng bằng chứng này cấp quyền xử lý ticket khách hàng.

Python source kiểm lại với đúng dependency pins: **306 pass**, một test Ollama
filesystem chỉ chạy trên Linux được skip trên Windows. GPU Qwen đã có bằng chứng
riêng ở trên. Tier boundary: source check và **43 mutation tests pass**. Hai
OpenAPI mới validate pass; subset live Service Desk có **14 positive/negative
contract checks pass**, không miễn trừ lỗi baseline của toàn bộ HPDI spec.

## Kết quả cuối sau đồng bộ

Trên source có develop `abdbac2`: **689 backend full tests PASS**, **106 core-only
PASS**, **33 second-domain PASS**, không fail/error/skip. Bộ tập trung phân công,
final assessment và true-wait: **28 PASS**. Real Qwen final pipeline: **1 PASS**
riêng với GPU/Java HTTP/PostgreSQL/Flowable thật và test doubles chỉ ở rights/JWT.

Hai điểm test được làm bền sau khi thêm dữ liệu regression: kiểm phân trang queue
theo hai lượt scan liên tiếp thay vì giả định toàn DB luôn dưới 100 task; endpoint
không kết nối được phải trả unavailable hoặc finite timeout, không giả thành công.
Không nới guard quyền, policy, model identity hoặc CAS để làm test pass.

Docker context backend bổ sung đúng allowlist contract test mà Dockerfile đang
COPY; `.local`, weights và secrets vẫn bị loại khỏi context. Tài liệu triển khai
và trạng thái pending bên ngoài nằm trong runbook portable ở trên.

Ba image backend/frontend/CPU sidecar mới đã build thành công với tag riêng
`ssd-ai-d2-{platform,frontend,triage}:develop-abdbac2-local`; không thay stack đang
chạy. Trên Docker 29/containerd, `.Id` là OCI index digest, khác config digest.
Release mapping tách source image ID/descriptor, config digest trong archive,
registry ref và ID sau import. Runtime identity gốc không bị đổi theo wrapper.
Có thêm test phân biệt index/config; tổng boundary release/offline/Compose:
**11 tests PASS**. Đã kiểm `docker save` trên frontend thật, trích config digest
khớp build output và giữ receipt ở `.local/ai-d2-frontend-archive-proof.json`.
Plan đủ bảy image sẵn tại `.local/ai-d2-release-images.json`. Chưa có approval nên
chưa export kit chứa weights hoặc push.


## D2-A + E5 image extension (follow-up PR #54)

D2-A giữ Qwen classifier đóng gói full weights. E5 bổ sung image ONNX CPU
self-contained, SHA-256/size pinned, verify in Docker `--network none`,
receipt có provenance và signed offline inventory. Export/install reject E5
missing/tampered; danh sách archive là 7 services + 2 parent images.
Cloud LLM dùng integration API hiện có opt-in, không bundle LLM weights hay API keys.

**Status:** source/package contract prepared. E5 actual image build, clean-machine
acceptance, dense/hybrid app-route integration, independent signing and approval
remain PENDING; do not claim runtime E2E PASS until verified.
