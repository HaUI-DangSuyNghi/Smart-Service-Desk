# Chạy AI hybrid trên máy thành viên: Qwen, Gemini hoặc API tương thích OpenAI

Mỗi thành viên có thể chạy AI local với token riêng, không cần người khác cấp
processing grant. Chế độ này dành cho phát triển/thử nghiệm trên máy cá nhân.
Đăng nhập, quyền đọc ticket và phạm vi workspace vẫn được kiểm tra trước và sau
inference. Khi chọn Qwen local, dữ liệu không được gửi sang cloud. Khi chọn API,
title/description được gửi đến provider do thành viên cấu hình rõ ràng.

Luồng phân loại luôn là **model + rule engine** ở hai chế độ model/API. Rules
`service-desk@0.3.0` vẫn chạy, kết quả được đối chiếu với model, và impact/urgency/
priority tiếp tục có bằng chứng cùng ma trận chính sách. Model không được tự đặt
priority, confidence hay quyết định workflow. Hai nguồn mâu thuẫn thì không tự
chọn một loại sự cố; điều phối viên xác nhận.

## Chuẩn bị một lần

- Docker Desktop chạy Linux containers và truy cập được GPU NVIDIA.
- GPU phù hợp với bundle Qwen3.5-0.8B LoRA; bundle hiện có đã được chạy trên RTX
  3050 Laptop 4 GB. Để đủ VRAM trống, dừng bộ Qwen test-kit độc lập nếu đang chạy.
- Khoảng 6 GB RAM cấp cho container model và dung lượng Docker đủ chứa image.
- Có image **tự chứa model** từ bản bàn giao của nhóm. Image code-only của bộ kit
  cũ không thay thế image này. Không cần Python/Java trên host để khởi động.

```powershell
docker load -i D:\Model\qwen-full-image.tar
```

Nếu image dùng tên khác, đặt `LOCAL_MODEL_IMAGE=<tên hoặc image ID>` trong `.env`.
Mặc định là `ssd-ai07-qwen-full:a40d655f95f8ed6b`. Script yêu cầu image Linux có
`/opt/model`, `/opt/apps` và các label model/code/policy từ công cụ build hiện có.
Runtime tự kiểm checksum model đã đóng gói trước khi ready. Không tự tải weights
hoặc tải một model khác thay thế khi có lỗi.

## Hỏi đáp bằng API, giữ Qwen + bộ luật để phân loại

Trong `.env` ở thư mục gốc, điền provider, model có quyền sử dụng và key của bạn:

```dotenv
AI_API_PROVIDER=gemini
AI_API_MODEL=model-id-cua-ban
AI_API_KEY=key-rieng-cua-ban
AI_API_KEY_FILE=
```

Có thể chọn `openai` hoặc `groq` thay `gemini`. Với API chính thức, để trống
`AI_API_BASE_URL`; endpoint khác cần HTTPS và định dạng tương thích. Nếu dùng
file key, đặt `AI_API_KEY_FILE` và để trống key trực tiếp. Không gửi key cho người
khác, không đưa vào `VITE_` hay Git. `setup.ps1` dùng `.env.example` làm nguồn cấu
hình: máy mới nhận đủ biến và chú thích; máy cũ chỉ được thêm biến còn thiếu,
giữ nguyên giá trị đã có. Chỉ mật khẩu/token nội bộ bắt buộc còn trống được sinh
tự động; API key luôn do thành viên tự điền. Không cần xóa `.env` để đồng bộ.

```powershell
.\scripts\start.ps1 -Model -AskApi
```

Lệnh này giữ **Qwen + rule engine để phân loại**, chạy thêm adapter CPU để **hỏi
đáp qua API**. Không tải thêm model/GPU thứ hai. Khi khởi động thành công, script
lưu `AI_MODE=model` và `AI_ASSISTANCE_MODE=api`; lần sau chỉ chạy `start.ps1`.
Nếu muốn dùng API cho cả phân loại lẫn hỏi đáp và không dùng GPU, chọn `-Api`.

Mở ticket → **Hỏi AI** → nhập câu hỏi → **Gửi câu hỏi**. Người dùng được xem
ticket có thể hỏi; backend lấy title/description của chính ticket đó, không nhận
context hoặc provider do trình duyệt tự gửi. Chỉ câu hỏi và hai trường này được
gửi ra provider; không gửi ticket khác, tệp đính kèm, credential hay danh sách
người dùng. Quyền truy cập, actor và nội dung ticket được kiểm tra lại trước khi
trả lời. Không lưu câu hỏi/câu trả lời và không sửa ticket hoặc kết quả phân loại.

Câu trả lời hiện là **gợi ý từ model, chưa đối chiếu tài liệu nội bộ**. Không tạo
trích dẫn giả hay giả là truy xuất RAG. Các chế độ grounded có sẵn vẫn giữ hợp đồng
và quyền nguồn riêng; phần guidance trong assessment có cấu trúc chỉ chấp nhận
trích dẫn đã xác minh, không gọi API chat để thay thế. Q&A trả public contract
`0.2.0` / `MODEL_ADVICE`; grounded giữ `0.1.0`.

API chờ tối đa 12 giây, backend 15 giây. `AI_ASSISTANCE_MAX_TOKENS` mặc định 2048,
giới hạn 64..2048, độc lập với giới hạn output classifier. Key sai/hết hạn mức
hoặc provider lỗi trả 503; hết deadline trả 504. UI cho phép thử lại và không tự
lặp các yêu cầu tính phí. Healthcheck không kiểm quyền model/quota bằng một lần
inference trả phí.

Tắt hỏi đáp riêng khi đang dùng Qwen: đặt `AI_ASSISTANCE_MODE=disabled` rồi chạy
`start.ps1 -Model`. `-NoAi` tắt cả hai phần. API-only (`AI_MODE=api`) luôn bật cả
phân loại và hỏi đáp; muốn giữ một classifier Qwen riêng hãy dùng chế độ mixed.

## Bật và lưu cấu hình

Tại thư mục dự án:

```powershell
.\scripts\start.ps1 -Model
```

Script kiểm GPU và metadata image, sinh token tại
`.local/model/classification-token`, tính các policy digest từ đúng source hiện
tại, khởi động Qwen và đợi warmup. Sau đó build/khởi động ứng dụng với overlay
`infra/compose/compose.hybrid.yaml`, `compose.model-local.yaml` và profile `local-dev`.

Khi thành công, script lưu `AI_MODE=model` vào `.env`. Những lần sau chỉ cần:

```powershell
.\scripts\start.ps1
```

`-Intelligence` lưu lựa chọn `AI_MODE=rules`; `-NoAi` lưu `AI_MODE=disabled`.
`-Api` lưu lựa chọn `AI_MODE=api` và bật hỏi đáp API. Khi đổi chế độ, script chỉ
dừng những service AI không còn được chọn trong đúng Compose project. Mixed mode
giữ cả Qwen và API; mỗi service có DNS riêng để không thay classifier ngoài ý muốn.
`AI_MODE` chỉ được script khởi động đọc; lệnh `docker compose up` thuần không tự
chọn overlay model. Không đổi các cờ model rời rạc trong `.env` để thay cho script.
Khi Compose thay container backend, script làm mới gateway để tránh tiếp tục
trỏ tới địa chỉ backend cũ trong DNS cache.
Các digest/image ID hiệu lực được lưu tại `.local/model/runtime.env`; không có
token trong file này. Mỗi máy giữ `.env` và `.local` riêng, không commit/chia sẻ.

Mặc định `LOCAL_MODEL_WORKSPACES=auto`: phân loại và hỏi đáp dùng được trong mọi
workspace người dùng được phép truy cập, bao gồm workspace tạo sau startup.
Không phải thêm từng workspace vào `.env` hoặc khởi động lại khi tạo workspace.

Trong local-dev, overlay `compose.workspaces-auto.yaml` chạy service bootstrap
nội bộ. Khi worker xử lý ticket lần đầu, bootstrap xác minh workspace đã tồn tại
trong Keycloak, tạo nhóm `assessment-worker`/`alert-worker` còn thiếu và gán riêng
tài khoản `dx-assessment-worker`. Backend vẫn lấy JWT thật và kiểm tra quyền
worker cùng quyền người tạo trước/sau inference; không có wildcard JWT hoặc mở
quyền đọc ticket cho người dùng khác. Bootstrap không tạo người dùng/workspace,
không đổi nhóm người dùng, không reset realm. Nếu quản trị viên thu hồi quyền
worker sau khi thiết lập, bootstrap không tự cấp lại; quyền phải được khôi phục
chủ động trước khi thử lại.

Token bootstrap tách khỏi token classifier. Mật khẩu admin Keycloak chỉ được
mount vào service bootstrap, không vào backend/model/API adapter. Trạng thái
thiết lập nằm trong volume riêng; chế độ này bị chặn ngoài profile local-dev.
`SERVICE_DESK_BACKGROUND_WORKSPACES` của các timer/workflow khác giữ cấu hình riêng.

Muốn cố ý giới hạn AI, đặt `LOCAL_MODEL_WORKSPACES=lab-a,lab-b`; danh sách này
giữ cơ chế cấp quyền worker thủ công như trước. Máy cũ còn giá trị `lab-a` sẽ được
giữ nguyên khi chạy setup; đổi một lần thành `auto` rồi chạy `start.ps1` để dùng
mặc định mới. Ticket trước đây bị loại bởi giới hạn workspace có thể phân tích
lại khi AI đã được bật tại workspace đó.

## Luồng người dùng

- Mở `http://localhost:5173`, đăng nhập tài khoản demo với `DEMO_PASSWORD` trong `.env`.
- Người gửi tạo ticket; worker gọi rule engine và model rồi lưu kết quả hybrid.
- Điều phối viên xem khối **Gợi ý của AI — Model + bộ luật** và kết luận của từng
  nguồn. Đồng thuận, mâu thuẫn và chưa đủ thông tin được phân biệt rõ.
- Loại sự cố của model không bị xóa chỉ vì thiếu impact/urgency. Ưu tiên chỉ có
  khi đủ dữ kiện; không dùng MEDIUM giả làm một kết luận đã xác nhận.
- Ticket cũ có kết quả rules có nút **Phân tích lại bằng model**. Ticket bị lỗi
  có nút thử lại; job đang chạy hoặc model result đã hoàn tất không được enqueue
  lặp. Poll/reload chỉ đọc kết quả đã lưu.
- Khi model lỗi/timeout, rules tiếp tục hoạt động. Kết quả lưu nói rõ model không
  khả dụng và phần nào đến từ rules; nút thử lại chỉ gọi lại khi người dùng yêu cầu.
  Khi cả pipeline lỗi, cơ chế retry có giới hạn rồi cho xử lý thủ công vẫn được giữ.
- Context/enrichment và đánh giá lại sau bổ sung được bật với policy pin chính xác.
  Luồng intake mới, canonical merge và quyết định nghiệp vụ tự động không được bật
  theo cờ model; chúng giữ cấu hình riêng của ứng dụng.

## Kiểm tra

Có thể kiểm tra riêng việc tạo/đồng bộ `.env` bằng fixture cô lập. Phép thử không
đọc `.env` thật của máy, không gọi Docker hoặc provider:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-setup-env.ps1
```

Nếu có Python trên host, chạy phép thử xuyên suốt bằng fixture synthetic cố định:

```powershell
python scripts/test-local-model.py --output .local/local-model-proof.json
```

Kiểm thử đăng nhập thật bằng Authorization Code + PKCE, tạo ticket qua gateway,
đợi kết quả Qwen, kiểm nhãn HARDWARE / I1 / U4 / HIGH, đọc lặp ổn định và từ chối
truy cập/retry khác workspace. Output không chứa credential hoặc nội dung ticket.
Mỗi lần chạy tạo một ticket thử; không chạy trên deployment dùng dữ liệu thật.

Status cấu hình: `GET /api/apps/servicedesk/v1/workspaces/lab-a/intelligence/status`
(cần đăng nhập). `modelEnabled` nói pipeline đã được chọn, không thay healthcheck
GPU. Sau khi GPU worker quá deadline và hạ readiness, khởi động lại:

```powershell
.\scripts\start.ps1 -Model
```

## Giới hạn chế độ local

`DX_AI_RUNTIME_MODE=LOCAL_DEV` chỉ hợp lệ với profile `local-dev`; ứng dụng từ chối
khi đồng thời có profile `prod`/`production`, hoặc endpoint model không phải
loopback/service classifier nội bộ đã allowlist. Token, model digest và policy pin vẫn bắt buộc.
Runtime không tạo hay giả mạo external processing grant.

Kiểm tra auto scope bằng `python scripts/test-auto-workspaces.py --output
.local/auto-workspaces-proof.json` trên máy DEV. Phép thử đăng nhập thật, tạo
ticket ở `lab-b`, tạo thêm workspace thử cho user4 sau startup, xác nhận cả hai
chạy model + rules và tài khoản khác bị từ chối. Nó cũng kiểm tra thu hồi/khôi
phục quyền worker và retry; không reset realm hoặc xóa dữ liệu. Workspace và
ticket synthetic được giữ lại để kiểm tra, không dùng trên dữ liệu thật.

Chế độ `EXTERNAL` giữ nguyên kiểm tra grant/chữ ký/scope/expiry. `PROMOTED` vẫn bị
chặn; local mode không cho model duyệt, phân công hay tự thay đổi workflow. Model
hiện tại là candidate machine-silver; chạy được trên máy không chứng minh độ đúng
cho mọi câu tiếng Việt hoặc thay thế việc người xử lý xác nhận.

## Dùng Gemini hoặc API tương thích OpenAI/Groq

Không cần GPU hoặc image Qwen cho chế độ API. Trong `.env`, đặt cấu hình riêng
của thành viên, ví dụ cấu trúc sau (thay `model-id-cua-ban` bằng model trong tài khoản):

```dotenv
AI_API_PROVIDER=gemini
AI_API_MODEL=model-id-cua-ban
AI_API_KEY_FILE=D:/Secrets/gemini-key.txt
```

Hoặc chọn `AI_API_PROVIDER=openai` / `groq`. API key có thể dùng `AI_API_KEY`, hoặc
biến provider sẵn có `GEMINI_API_KEY`, `GROQ_API_KEY`, `OPENAI_API_KEY`; file secret
được ưu tiên. Không đưa key vào biến `VITE_`, mã nguồn hay file chia sẻ.

```powershell
.\scripts\start.ps1 -Api
```

Script tạo file secret riêng trong `.local/model`, bật adapter CPU, kết nối backend
qua protocol classification hiện có và lưu `AI_MODE=api`. Lần sau dùng `start.ps1`
không cần cờ. Chỉ kiểm cấu hình lúc startup; không gửi yêu cầu tính phí để thử key.

Base URL mặc định: OpenAI `https://api.openai.com/v1`, Groq
`https://api.groq.com/openai/v1`, Gemini `https://generativelanguage.googleapis.com/v1beta`.
`AI_API_BASE_URL` cho endpoint HTTPS tương thích khác; điền base URL, không thêm
`/chat/completions`. Không hỗ trợ redirect hay key trong URL. Không đổi provider
ngầm khi lỗi và không gửi dữ liệu đồng thời cho nhiều vendor.

OpenAI/Groq mặc định dùng `AI_API_RESPONSE_FORMAT=json_object` cho tính tương thích;
chọn `json_schema` nếu model hỗ trợ Structured Outputs. Gemini dùng schema JSON
qua REST. Mọi output đều được kiểm tra lại: chỉ `decision` và `classId` trong
whitelist; từ chối thêm priority/routing/confidence hoặc gọi tool. Token đầu ra mặc
định 1024, có thể đặt `AI_API_MAX_TOKENS=64..2048`. Deadline provider 12 giây,
backend 15 giây; body/response và concurrency đều có giới hạn.

Digest cloud model pin **cấu hình provider/base URL/model ID**, không chứng minh
weights bất biến phía vendor. Thay model/prompt cần chạy lại script để đồng bộ pin.
`LOCAL_DEV` ở đây là quyền của môi trường phát triển, không có nghĩa API mode offline.

Có thể kiểm cấu hình trước khi bật, không thay container đang chạy và không gửi
yêu cầu ra provider:

```powershell
.\scripts\start-model.ps1 -Api -ValidateOnly
```

File kiểm tra được tạo trong thư mục `.local/model/validation-*`, tách khỏi cấu
hình đang hoạt động. Script hỗ trợ ghi log bằng PowerShell `*>` mà không coi dòng
tiến trình bình thường trên stderr của Docker là lỗi; exit code khác 0 vẫn làm
khởi động thất bại.

```powershell
docker compose -f compose.yaml -f infra/compose/compose.triage.yaml -f infra/compose/compose.hybrid.yaml -f infra/compose/compose.api-assistance.yaml -f infra/compose/compose.model-api.yaml --env-file .env --env-file .local/model/runtime.env logs --tail 30 intelligence-api
python -m pytest intelligence/tests/classification/test_api_provider.py -q
```

Log adapter chỉ ghi loại lỗi/HTTP status, không ghi key, ticket hay nội dung provider.
Các test adapter dùng server giả lập localhost; kiểm thử API thật cần key và model
có quyền truy cập của thành viên. Chưa có key thì không ghi nhận API thật là PASS.

## Kết quả kiểm tra trên nhánh này

Kiểm tra tại máy phát triển ngày 2026-10-29 với dữ liệu thử:

| Phạm vi | Kết quả |
| --- | --- |
| Qwen + rules qua Keycloak/gateway/backend thật | HARDWARE / I1 / U4 / HIGH cho câu máy số 15, phòng 304, cần thi trong 15 phút; cả hai nguồn được gọi |
| Model ngừng chạy | Rules vẫn trả HARDWARE/HIGH; modelStatus=UNAVAILABLE; bật lại và retry thành công |
| Kết quả lưu và quyền truy cập | Ba lần đọc giữ nguyên kết quả; từ chối đọc/retry khác workspace và retry kết quả model đã hoàn tất |
| Giao diện trình duyệt thật | Đăng nhập điều phối viên, hiển thị kết quả hybrid cùng hai nguồn |
| Enrichment và contextual assessment | HTTP 200, enrichment READY, model trả HARDWARE |
| Backend Maven verify | 928 tests: 598 chạy đạt, 330 được cấu hình bỏ qua; không lỗi |
| Background assessment với PostgreSQL riêng | 22/22 đạt, không bỏ qua; có kiểm tra auto scope ngoài danh sách legacy |
| Python classification / API Q&A / workspace bootstrap | 113/113 đạt, gồm cả ba vendor với upstream giả lập và kiểm tra IAM bootstrap |
| Frontend | TypeScript/lint/build đạt; 30/30 test AI/ticket liên quan đạt; lần kiểm toàn bộ suite trước thay đổi hỏi đáp có 149 đạt, 2 lỗi board/dropTargets tái hiện nguyên trạng trên develop |
| Gemini/OpenAI/Groq | Cấu hình cả ba hợp lệ; API container healthy không cần GPU; chưa kiểm inference thật bằng key của vendor |
| API hỏi đáp xuyên suốt qua HTTPS upstream giả lập | Đăng nhập user1, hỏi trên ticket thật, UI hiển thị câu trả lời; không trích dẫn giả, không đổi phân loại; key bị từ chối trả 503; Qwen + rules cùng hoạt động |
| Auto workspace qua Keycloak/Qwen thật | lab-b và workspace tạo sau startup đều trả hybrid HARDWARE/HIGH; giữ cách ly người dùng, tôn trọng thu hồi quyền worker, retry đạt sau khôi phục quyền |
| Hỏi đáp API trong auto scope | user4 hỏi trên ticket lab-b qua HTTPS upstream giả lập; không đổi phân loại, từ chối user1 khác workspace; chưa gọi API vendor thật |

Các script tái kiểm tra: `scripts/test-local-model.py` và
`scripts/test-local-model-ui.mjs`; Q&A dùng `scripts/test-api-assistance.py <UUID-ticket-thu>`
và `node scripts/test-local-model-ui.mjs <UUID-ticket-thu> --ask`.
Các phép thử Q&A sẽ gọi provider đang cấu hình; chỉ chạy với ticket thử và chấp
nhận chi phí API tương ứng. Bằng chứng cục bộ nằm trong `.local`, không
commit credential, nội dung ticket hay dữ liệu kiểm thử vào Git.

Định dạng request được đối chiếu với tài liệu chính thức:
[OpenAI Chat Completions](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create),
[Gemini Structured Outputs](https://ai.google.dev/gemini-api/docs/generate-content/structured-output),
[Groq Chat API](https://console.groq.com/docs/api-reference).
