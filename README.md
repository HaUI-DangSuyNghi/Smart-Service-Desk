# Smart Service Desk

## v0.1.0 release preparation

The existing repository is public. Preserve its real Git history; do not create a
replacement repository or follow older history-filtering/publication proposals.
**Not released. Authorized team-owned work is licensed under Apache-2.0; see
root LICENSE. Supplied documents, photo, fixtures and third-party content are
not automatically covered. Asset rights still block release.** See `LICENSING.md`
and `RELEASE_NOTES_v0.1.0.md`.

Release baseline: the merged develop source, including Core / H-P-D-I,
Service Desk, Room Request and existing Knowledge/Intelligence integrations.
No unreviewed local changes or OCR experiments are included. Supported startup
baseline is no-AI/manual workflow; Qwen/GPU and external-provider operation are
optional integrations, not requirements or certified runtime modes for v0.1.0.
Existing merged code is retained. Do not claim all AI modes have passed.

### Minimal clean-source startup

Use Docker Desktop/Linux containers with Compose v2. In a fresh clone only:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/setup.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start.ps1 -NoAi
```

Setup generates private `.env`/realm configuration. Never share them or run setup,
realm resets or destructive volume commands against an existing demo installation.
Default browser 5173, Keycloak 8081 and gateway 9080. Demo usernames use the locally
generated DEMO_PASSWORD; no live password or API key is distributed.

The Dockerfiles build from source. Native toolchain: Java 17/Maven 3.9.9,
Node >=22.12 and Python >=3.12,<3.13. Core CI runs isolated PostgreSQL, real E2E,
restart/restore and contract checks. CI evidence must match the final source SHA;
optional full image-security scanning is not covered by a green functional job.
An unusable local image cache is an environment failure, not permission to reset
existing databases. Resolve image/runtime problems before the first installation.

<p align="center">
  <img src="https://img.shields.io/badge/Java-17-blue.svg" alt="Java 17" />
  <img src="https://img.shields.io/badge/Spring%20Boot-3.3-green.svg" alt="Spring Boot" />
  <img src="https://img.shields.io/badge/Keycloak-26.0.8-red.svg" alt="Keycloak" />
  <img src="https://img.shields.io/badge/APISIX-3.11-orange.svg" alt="APISIX" />
  <img src="https://img.shields.io/badge/PostgreSQL-16.15-blue.svg" alt="PostgreSQL" />
  <img src="https://img.shields.io/badge/Docker-Compose%20v2-blue.svg" alt="Docker" />
  <img src="https://img.shields.io/badge/License-Pending%20Approval-lightgrey.svg" alt="License approval pending" />
</p>

Nền tảng headless Tier-1 dành cho DX-OS Open-Core (Smart Service Desk). Hệ thống cung cấp dịch vụ định danh tập trung, kiểm soát truy cập đa không gian làm việc (multi-workspace RBAC), động cơ quy trình nghiệp vụ BPMN (Flowable), quản lý siêu dữ liệu/tệp tin bảo mật và ghi vết kiểm toán giao dịch (transactional audit).

> [!NOTE]
> Headless Core and Service Desk Backend are implemented, including ticket APIs and a real Flowable process. Java-Python AI Triage is integrated and tested through Keycloak/APISIX. See [Ticket Foundation](docs/servicedesk-ticket-foundation.md), [application OpenAPI](contracts/openapi/servicedesk.yaml) and [integration evidence](docs/BE_POSTMERGE01.md#validation).
> Frontend has a strict TypeScript platform/app scaffold with independent builds and import guards; rendered browser UI remains a frontend task. AI-03 Retrieval has no public Knowledge Search endpoint.
> Dashboard counts and shared time filters are documented in [Service Desk statistics](docs/servicedesk-statistics.md); dashboard UI remains a frontend task.
> Production Security G9 is FAIL; release is BLOCKED. Functional acceptance does not imply release readiness.

---

## 📑 Mục lục

- [Điểm nổi bật](#-điểm-nổi-bật)
- [Kiến trúc hệ thống](#-kiến-trúc-hệ-thống)
- [Khởi chạy nhanh](#-khởi-chạy-nhanh)
- [Tài khoản mẫu & Phân quyền](#-tài-khoản-mẫu--phân-quyền)
- [API & Hợp đồng giao tiếp](#-api--hợp-đồng-giao-tiếp)
- [Kiểm thử & Vận hành](#-kiểm-thử--vận-hành)
- [Cấu trúc thư mục](#-cấu-trúc-thư-mục)
- [Tài liệu liên quan](#-tài-liệu-liên-quan)
- [Đóng góp & Bản quyền](#-đóng-góp--bản-quyền)

---

## ✨ Điểm nổi bật

- **Xác thực & Phân quyền IAM:** Tích hợp Keycloak quản lý vai trò theo từng workspace (`/workspaces/{workspace}/{role}`). Xác thực JWT chặt chẽ (Issuer/Audience) và chuẩn hóa thông báo lỗi theo chuẩn RFC 7807 (Problem Details kèm `code` và `requestId`).
- **Cổng kết nối API (API Gateway):** Apache APISIX chạy chế độ standalone, đảm nhiệm định tuyến và giới hạn tần suất truy cập (rate limit), bảo vệ API nội bộ và không mở công khai Admin API.
- **Động cơ quy trình BPMN tích hợp:** Flowable Engine thực thi BPMN do ứng dụng đóng góp qua HPDI Process, hỗ trợ danh sách nhiệm vụ (inbox), tiếp nhận/xử lý độc lập (claim/complete) và tra cứu lịch sử luồng.
- **Quản lý dữ liệu & Lưu trữ tệp an toàn:** Lưu trữ siêu dữ liệu trên PostgreSQL kèm phân trang, checksum, quyền sử dụng; hỗ trợ upload/download tệp tin private kèm kiểm tra dung lượng, loại tệp, bảo mật header tải về và liên kết kiểm toán.
- **Kiểm toán giao dịch (Transactional Audit):** Ghi vết tự động mọi biến động dữ liệu/trạng thái qua `AuditService`, hỗ trợ chống tấn công phát lại (replay protection) và theo dõi luồng thông suốt với `X-Request-ID`.

---

## 🏗️ Kiến trúc hệ thống

```text
Tier 3: Service Desk / Room Request
        Business APIs, persistence, BPMN, application AI profiles
                         |
                         v
Tier 2: Human / Process / Data / Intelligence public capabilities
        Generic Python hdpi runtime + TypeScript frontend platform
                         |
                         v
Tier 1: Identity / Workflow engine / Metadata / Files / Audit
                         |
                PostgreSQL / Keycloak / Storage

assembly/DxOsApplication composes backend tiers.
frontend/src/main.ts registers application extensions.
APISIX routes /api/apps/*, /api/hpdi/v1/* and /api/core/v1/*.
```

---

## 🚀 Khởi chạy nhanh

### Lệnh ngắn

Tại thư mục gốc repo, gõ `.\dx` (PowerShell, cmd) hoặc `./dx` (Git Bash). Không cần cài gì thêm.

```powershell
.\dx start
```

Menu hiện ra, chọn bằng phím ↑/↓ rồi Enter. Mục **Start project** chạy trọn quy trình khởi động:

1. **Kiểm máy:** tạo `.env` nếu chưa có, kiểm Docker, ổ đĩa, cổng, GPU, API key. Có lỗi thì hỏi dừng hay chạy tiếp.
2. **Chọn chế độ AI (bắt buộc có AI):** theo chế độ đã lưu, Qwen local, Qwen local + Ask AI, hoặc chỉ API. Máy không có GPU + image Qwen lẫn API key thì dừng và báo cần bổ sung.
3. **Khởi động:** build, chạy và đợi mọi dịch vụ healthy.
4. **Sẵn sàng:** in địa chỉ web, API, Keycloak, tài khoản demo, rồi hỏi có mở web luôn không.

Các lệnh trực tiếp, không qua menu:

```powershell
.\dx launch         # quy trình khởi động đầy đủ như trên
.\dx doctor         # kiểm máy; lỗi nào cũng kèm cách sửa
.\dx up             # khởi động nhanh theo chế độ AI đã lưu (lần đầu tự chọn theo máy)
.\dx down           # dừng, giữ dữ liệu
.\dx help           # xem mọi lệnh
```

Lần đầu chạy `.\dx` (lệnh nào cũng được), dự án tự được thêm vào PATH của tài khoản. Từ terminal mở sau đó gõ thẳng `dx start` ở bất kỳ thư mục nào. Không muốn tự thêm thì đặt biến môi trường `DX_NO_PATH=1`; thêm tay bằng `.\dx install`.

| Lệnh | Việc |
| --- | --- |
| `.\dx up model` | Phân loại bằng Qwen local (GPU) + bộ luật |
| `.\dx up model ask` | Như trên, thêm Hỏi AI qua API |
| `.\dx up api` | Phân loại và Hỏi AI đều qua API (không cần GPU) |
| `.\dx restart` | Dừng rồi khởi động lại |
| `.\dx ps`, `.\dx logs [service]` | Trạng thái container, xem log |
| `.\dx qwen` | Khởi động lại Qwen khi thẻ AI báo "AI tạm không khả dụng" |
| `.\dx smoke`, `.\dx check` | Kiểm thử nhanh, kiểm cấu hình Compose và CSDL |
| `.\dx help` | Xem mọi lệnh |

Các mục dưới giải thích chi tiết từng bước; lệnh dài `powershell -File scripts/...` vẫn dùng được như cũ.

### Yêu cầu tiên quyết

- **Hệ điều hành:** Windows 10/11 với PowerShell 5.1+ (hoặc PowerShell Core).
- **Docker Desktop:** Thiết lập chạy Linux containers, Docker Compose v2 hỗ trợ cờ `--wait`.
- **Mạng:** Kết nối Internet trong lần chạy đầu tiên để tải container images và thư viện Maven (Java/Maven được chứa trong container build, host không cần cài đặt).
- **Nếu dùng Qwen local:** GPU NVIDIA truy cập được từ Docker và image chứa model do nhóm bàn giao. Chế độ chỉ dùng API không cần GPU hoặc image Qwen.

### 1. Khởi chạy toàn bộ hệ thống

#### Bước 1 — Chuẩn bị cấu hình riêng của máy

Use the approved release tag/SHA in a fresh clone; never switch over uncommitted work.
Mở PowerShell tại **thư mục gốc repo đã clone**, nơi có `compose.yaml`, rồi chạy:

```powershell
.\dx env
notepad .env
```

Script tạo `.env` từ **toàn bộ `.env.example`**, giữ cả chú thích và giá trị mặc định,
rồi sinh mật khẩu nội bộ còn trống. Với máy đã có `.env`, script chỉ thêm biến
còn thiếu từ template; giữ nguyên mật khẩu, API key, giá trị trống có sẵn và lựa
chọn AI đã lưu (trừ mật khẩu nội bộ bắt buộc còn trống sẽ được sinh tự động).
Khi cập nhật source, chạy lại `setup.ps1` hoặc `start.ps1` để nhận các biến mới,
không cần xóa `.env`. Mỗi thành viên dùng `.env` riêng, không cần sao chép file chứa
mật khẩu/key của người khác. Không sửa `.env.example` để cấu hình máy đang chạy.

#### Bước 2 — Điền API để dùng “Hỏi AI”

Trong `.env`, điền các dòng sau nếu muốn hỏi đáp qua API:

```dotenv
AI_API_PROVIDER=gemini
AI_API_MODEL=model-id-cua-ban
AI_API_KEY=key-rieng-cua-ban
AI_API_KEY_FILE=
AI_API_BASE_URL=
```

Thay model/key bằng thông tin trong tài khoản của thành viên. Provider hỗ trợ
`gemini`, `openai`, `groq`. Để trống `AI_API_BASE_URL` khi dùng API chính thức;
endpoint tương thích khác cần HTTPS. Có thể dùng `AI_API_KEY_FILE` trỏ đến file
key thay cho key trực tiếp; file được ưu tiên. Không đưa key vào biến `VITE_`
hoặc commit `.env`/`.local` vào Git. Bỏ qua bước này nếu chỉ dùng bộ luật hoặc
Qwen để phân loại.

#### Bước 3 — Chọn cách chạy

**Không có GPU, dùng API để phân loại và hỏi đáp:**

```powershell
.\dx up api
```

**Giữ Qwen + bộ luật phân loại, dùng API để hỏi đáp:** nạp image chứa model một
lần từ bộ bàn giao của nhóm (thay đường dẫn bằng file thực tế), rồi khởi động:

```powershell
docker load -i D:\Model\qwen-full-image.tar
.\dx up model ask
```

Clone source không tự tải model. Nếu image có tên khác mặc định, điền
`LOCAL_MODEL_IMAGE` trong `.env`; xem yêu cầu image/GPU ở
[hướng dẫn model và API](docs/local-model.md).

| Lệnh | Cờ của `start.ps1` | Phân loại ticket | Hỏi AI |
| --- | --- | --- | --- |
| `.\dx up api` | `-Api` | API + bộ luật | API |
| `.\dx up model ask` | `-Model -AskApi` | Qwen + bộ luật | API |
| `.\dx up model` | `-Model` | Qwen + bộ luật | Theo `AI_ASSISTANCE_MODE` đã lưu |

**Trên máy mới, `.\dx up` không kèm gì sẽ tự chọn chế độ AI theo máy; gọi thẳng `scripts/start.ps1` không có cờ thì AI mặc định tắt.** `-Model` một mình không
tự bật hỏi đáp API nếu chưa cấu hình trước đó. Trong cả chế độ Qwen và API,
model vẫn phối hợp với rule engine; không thay bộ luật bằng model.

Sau khi khởi động thành công, script lưu lựa chọn vào `.env`: `AI_MODE` cho
phân loại và `AI_ASSISTANCE_MODE` cho hỏi đáp. Các lần sau chỉ cần:

```powershell
.\dx up
```

Script tự chuẩn bị cấu hình, chọn Compose overlay, build ứng dụng và đợi các
dịch vụ healthy. Lần đầu build hoặc warmup Qwen có thể mất vài phút.
`docker compose up` thuần không đọc lựa chọn AI để tự chọn overlay. Mỗi máy tự sinh token
nội bộ; chế độ phát triển `local-dev` không cần người khác cấp processing grant.

#### Bước 4 — Đăng nhập và thử hỏi đáp

Mở `http://localhost:5173`. Đăng nhập `user1` để tạo ticket trong `lab-a`, hoặc
`user3` để xem gợi ý phân loại với vai trò điều phối viên. Mật khẩu nằm ở
**`DEMO_PASSWORD` trong `.env`**; các file JSON trong `.local` là cấu hình tự sinh.
Xem đầy đủ vai trò tại [Tài khoản mẫu & Phân quyền](#-tài-khoản-mẫu--phân-quyền).

Mở ticket → **Hỏi AI** → nhập câu hỏi → **Gửi câu hỏi**. Nếu vừa đổi cấu hình,
tải lại trang. API nhận câu hỏi và title/description của ticket người dùng được
phép xem; câu trả lời là gợi ý từ model, chưa đối chiếu tài liệu nội bộ. Hỏi đáp
không sửa ticket hoặc kết quả phân loại. Mặc định `LOCAL_MODEL_WORKSPACES=auto`:
AI áp dụng trong mọi workspace người dùng có quyền truy cập, kể cả workspace tạo
sau khi ứng dụng đã chạy. Quyền worker được chuẩn bị tự động ở lần xử lý đầu tiên,
không cần sửa danh sách hoặc reset realm. Có thể cố ý giới hạn bằng danh sách ID;
xem [cấu hình phạm vi](docs/local-model.md).

Container healthy xác nhận dịch vụ đã khởi động, chưa xác nhận key/model/quota
ở provider hoạt động: startup không gửi lần hỏi thử tính phí.

#### Existing Keycloak realms

Keycloak imports only into a missing realm. Migrate required clients/groups/roles
through the Admin API or console, preserving user IDs. Do not reset a shared realm
or remove shared volumes: changed user subjects can orphan existing tickets.

### 2. Kiểm tra trạng thái dịch vụ

```powershell
docker compose ps
```

| Dịch vụ | Địa chỉ truy cập | Ghi chú |
| --- | --- | --- |
| **Giao diện web** | `http://localhost:5173` | Bản build của frontend (nginx), gọi API qua gateway |
| **API Gateway** | `http://localhost:9080` | Điểm tiếp nhận API chính |
| **Gateway Health** | `http://localhost:9080/health` | Kiểm tra trạng thái sẵn sàng của APISIX |
| **Keycloak IdP** | `http://localhost:8081` | Quản trị định danh (Realm: `dx-lab`) |
| **Backend & Database** | *Không mở port host* | Giao tiếp hoàn toàn qua mạng nội bộ Docker |

> [!WARNING]
> Cần giữ trống port `5173`, `8081` và `9080` trên máy host. Thay đổi địa chỉ Keycloak đòi hỏi phải đồng bộ lại JWT issuer, realm config và healthcheck probes.

---

## 🔑 Tài khoản mẫu & Phân quyền

Mật khẩu của tất cả tài khoản mẫu được sinh ngẫu nhiên tại biến `DEMO_PASSWORD` trong file `.env`. Mật khẩu tài khoản admin Keycloak được lưu tại `KC_ADMIN_PASSWORD`. Không có mật khẩu tĩnh lưu trong mã nguồn.

| Tài khoản | Phân quyền (Keycloak Group) | Không gian làm việc (Workspace) |
| --- | --- | --- |
| `user1` | `/workspaces/lab-a/requester` | `lab-a` (Người gửi yêu cầu) |
| `user2` | `/workspaces/lab-a/agent` | `lab-a` (Chuyên viên giải quyết) |
| `user3` | `/workspaces/lab-a/coordinator` | `lab-a` (Điều phối viên) |
| `user4` | `/workspaces/lab-b/requester` | `lab-b` (Người gửi yêu cầu) |
| `user5` | `/workspaces/lab-b/agent` | `lab-b` (Chuyên viên giải quyết) |
| `user6` | `/workspaces/lab-b/coordinator` | `lab-b` (Điều phối viên) |
| `user7` | Realm role `platform-admin` | Quản trị hệ thống (Toàn cục, không thuộc workspace) |
| `superadmin` | Cả 3 vai trò ở `lab-a`, `lab-b` + `platform-admin` | Tài khoản thử nghiệm đầy đủ quyền; vẫn tuân thủ quy tắc tách quyền (không tự xử lý ticket mình tạo) |

> [!TIP]
> Existing realm: migrate through the Admin API/console; preserve user IDs and shared data.

---

## 🔌 API & Hợp đồng giao tiếp

- **Tài liệu OpenAPI Spec:** Tra cứu chi tiết tại [`contracts/openapi/core.yaml`](contracts/openapi/core.yaml).
- **Đường dẫn gốc:** `/api/core/v1` (Core), `/api/hpdi/v1` (capabilities), `/api/apps/<app>/v1` (applications)
- **Các nhóm endpoint tiêu biểu:**
  - `GET /api/core/v1/me`: Trả về danh tính người dùng và danh sách nhóm/workspace trực thuộc.
  - `GET /api/hpdi/v1/workspaces`: Workspace của tôi kèm tên hiển thị và vai trò.
  - `GET /api/hpdi/v1/workspaces/{workspace}/members`: Danh bạ thành viên workspace (đọc từ Keycloak qua service account `dx-directory`, chỉ có quyền xem).
  - `GET /api/hpdi/v1/workspaces/{workspace}/knowledge`: Knowledge Hub, tài liệu văn bản kèm tên người tạo.
  - `POST /api/core/v1/workspaces/{workspace}/processes`: Khởi tạo quy trình xử lý công việc.
  - `GET /api/core/v1/workspaces/{workspace}/tasks`: Lấy hộp thư nhiệm vụ (inbox) theo quyền hạn.
  - `POST /api/core/v1/workspaces/{workspace}/tasks/{taskId}/claim`: Nhận quyền xử lý nhiệm vụ.
  - `POST /api/core/v1/workspaces/{workspace}/tasks/{taskId}/complete`: Hoàn thành nhiệm vụ kèm dữ liệu đầu ra.
  - `GET /api/core/v1/workspaces/{workspace}/audit`: Tra cứu nhật ký kiểm toán (dành cho `coordinator` và `platform-admin`).
  - `POST /api/core/v1/workspaces/{workspace}/files`: Tải lên tệp tin mã hóa/bảo mật.

---

## 🧪 Kiểm thử & Vận hành

### Chạy kiểm thử tự động (Smoke Test)
Thực thi kiểm thử tích hợp toàn diện thông qua Keycloak, APISIX, PostgreSQL và Flowable:

```powershell
.\dx smoke
```

### Các lệnh vận hành thông dụng

```powershell
.\dx check              # cấu hình Compose + kiểm CSDL PostgreSQL
.\dx ps                 # trạng thái container
.\dx logs               # log platform, keycloak, gateway (hoặc: .\dx logs platform)
.\dx down               # dừng toàn bộ, kể cả service AI, giữ dữ liệu volumes
.\dx up                 # khởi động lại với chế độ AI đã lưu trong .env
docker compose build platform   # chỉ build lại backend
```

### Xử lý sự cố thường gặp (Troubleshooting)

- **Lỗi kết nối Docker:** Khởi động Docker Desktop trước khi chạy lệnh.
- **“Hỏi AI” chưa bật:** Điền provider/model/key vào `.env`, chạy `scripts/start.ps1 -Api` hoặc `scripts/start.ps1 -Model -AskApi`, rồi tải lại trang. Chỉ bật bộ luật không bật hỏi đáp.
- **Hỏi đáp trả 503/504:** Kiểm tra key, quyền sử dụng model, hạn mức và kết nối provider; xem log bằng `docker logs --tail 50 dx-platform-intelligence-api-1` (tên project mặc định). Không gửi key khi chia sẻ lỗi.
- **Không có image Qwen / GPU không sẵn sàng:** Nạp image chứa model từ bộ bàn giao và kiểm tra GPU Docker, hoặc dùng `scripts/start.ps1 -Api` để chạy bằng API mà không cần Qwen.
- **`No such image: dx-apisix:3.11.0-sec01`:** Chạy `docker compose build gateway`, rồi khởi động lại bằng `scripts/start.ps1`; đây là image được build từ source trong repo.
- **Lỗi 401 Unauthorized:** Kiểm tra JWT issuer (`http://localhost:8081/realms/dx-lab`), audience (`dx-core`), thời hạn token và kết nối mạng nội bộ tới JWKS.
- **Lỗi 403 Forbidden:** Kiểm tra phân nhóm người dùng và vai trò tương ứng với workspace đang truy cập.
> Existing realm: migrate through the Admin API/console; preserve user IDs and shared data.
- **Lỗi khởi động container:** Kiểm tra log bằng `docker compose logs`; tuyệt đối không tắt xác thực bảo mật để khắc phục lỗi.

---

## 📂 Cấu trúc thư mục

```text
backend/src/main/java/vn/haui/dx/
├── core/                       # Tier 1: technical mechanisms and public APIs
├── hpdi/                       # Tier 2: Human, Process, Data, Intelligence
├── apps/                       # Tier 3: Service Desk, Room Request, samples
└── assembly/DxOsApplication    # Platform bootstrap
backend/src/main/resources/
├── apps/<app>/process/         # App-owned BPMN
└── db/migration/               # Immutable history and forward app migrations
intelligence/src/
├── hdpi/                       # Generic Tier-2 Python runtime
└── apps/<app>/intelligence/    # External Tier-3 profiles; excluded from wheel/image
frontend/src/
├── platform/                   # Shared TypeScript clients and extension registry
├── apps/servicedesk/            # Application routes, clients and projections
└── main.ts                     # Frontend composition root
contracts/
├── openapi/                    # Stable Core and Service Desk wire contracts
├── hpdi/                       # Tier-2 capability contracts
├── apps/                       # Tier-3 ownership and Room Request API
└── intelligence/v1/            # Shared Intelligence schemas
scripts/                        # Boundary, regression and acceptance checks
infra/                          # Keycloak, APISIX and PostgreSQL configuration
docs/architecture/              # ADR, ownership map and validation evidence
compose.yaml + infra/compose/compose.triage.yaml
```

---

## 📚 Tài liệu liên quan

- [Core Integration Handover](docs/core-integration.md): Ranh giới tích hợp, phạm vi xác minh và các cổng an toàn.
- [Core Architecture](docs/core-architecture.md): Kiến trúc phân tầng, cơ chế ủy quyền và kiểm toán dữ liệu.
- [Core Acceptance](docs/core-acceptance.md): Tiêu chí nghiệm thu chức năng, quy trình sao lưu và phục hồi.
- [Requirements Mapping](docs/requirements-mapping.md): Đối chiếu yêu cầu kỹ thuật và quyết định kiến trúc.
- [Dependency Licenses](docs/dependency-licenses.md): Danh mục giấy phép và nguồn gốc các thư viện sử dụng.

---

## 🤝 Đóng góp & Bản quyền

- **Quy trình đóng góp (Branching Model):** Nhánh cơ sở phát triển là `develop`. Mọi nhánh tính năng bắt đầu từ `develop` và tạo Pull Request để review. Nhánh `main` được bảo vệ, chỉ dành cho các bản phát hành đã phê duyệt. Chi tiết xem tại [`CONTRIBUTING.md`](CONTRIBUTING.md).
- **Bản quyền:** Xem chi tiết tại [`LICENSING.md`](LICENSING.md). Mọi hình thức công bố mã nguồn cần có sự đồng thuận từ ban quản trị dự án.
