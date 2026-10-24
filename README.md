# DX-Lab HaUI: DX-OS platform

Post-merge integration: see [BE-POSTMERGE-01](docs/BE_POSTMERGE01.md) for the
isolated Java/Python demo, FE proxy, acceptance evidence and release blockers.
The default startup assembles Core, HPDI and applications; enable the private Python runtime with the documented overlays.
Strict boundary recovery BND-00 through BND-07 is implemented; see
[ownership and gates](docs/architecture/BOUNDARY_DEBT.md) and
[runtime configuration](docs/architecture/STRICT_BOUNDARY_OPERATIONS.md).
Production security G9 remains FAIL; release remains BLOCKED.

<p align="center">
  <img src="https://img.shields.io/badge/Java-17-blue.svg" alt="Java 17" />
  <img src="https://img.shields.io/badge/Spring%20Boot-3.3-green.svg" alt="Spring Boot" />
  <img src="https://img.shields.io/badge/Keycloak-26.0.7-red.svg" alt="Keycloak" />
  <img src="https://img.shields.io/badge/APISIX-3.11-orange.svg" alt="APISIX" />
  <img src="https://img.shields.io/badge/PostgreSQL-16.6-blue.svg" alt="PostgreSQL" />
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

### Yêu cầu tiên quyết
- **Hệ điều hành:** Windows 10/11 với PowerShell 5.1+ (hoặc PowerShell Core).
- **Docker Desktop:** Thiết lập chạy Linux containers, Docker Compose v2 hỗ trợ cờ `--wait`.
- **Mạng:** Kết nối Internet trong lần chạy đầu tiên để tải container images và thư viện Maven (Java/Maven được chứa trong container build, host không cần cài đặt).

### 1. Khởi chạy toàn bộ hệ thống
Mở PowerShell tại thư mục gốc dự án và thực thi:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start.ps1
```

Script sẽ tự động:
1. Gọi `scripts/setup.ps1`: Tự sinh mật khẩu ngẫu nhiên lưu tại `.env` và xuất cấu hình realm an toàn tại `.local/dx-lab-realm.json`.
2. Kiểm tra tính hợp lệ của `compose.yaml`.
3. Build và khởi động các container với cơ chế chờ healthcheck hoàn tất.

#### Làm mới realm Keycloak (`-Reset`)

Keycloak chỉ import `dx-lab` **lần đầu**, khi realm chưa tồn tại. Máy đã chạy từ bản cũ sẽ giữ realm cũ và **không nhận** thay đổi mới trong `infra/keycloak/realm-template.json` (client `dx-web`, tài khoản, nhóm mới). Dấu hiệu: trang Keycloak báo *"Client not found"*, hoặc đăng nhập `user1` báo sai mật khẩu.

Khởi động kèm xóa và import lại realm bằng một lệnh:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start.ps1 -Reset
```

Stack đang chạy sẵn thì chỉ cần chạy riêng bước reset:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/reset-keycloak-realm.ps1
```

Cần chạy `-Reset` khi template có client, nhóm hoặc thuộc tính mới (ví dụ client `dx-directory` cho danh bạ và tên hiển thị workspace). `scripts/setup.ps1` tự bổ sung biến còn thiếu (như `DIRECTORY_CLIENT_SECRET`) vào `.env` mà không đổi mật khẩu đã có.

#### Quyền (`dx-permissions`)

Quyền nhỏ khai báo là role của client `dx-permissions` trong `infra/keycloak/realm-template.json`: **tên tiếng Anh** dạng `đối-tượng:hành-động` (`request:create`), **mô tả tiếng Việt** ("Tạo yêu cầu") hiện trong Keycloak. Mỗi nhóm `/workspaces/{ws}/{vai trò}` được gán sẵn bộ quyền mặc định.

Máy mới chỉ cần `scripts/start.ps1`: Keycloak import realm từ template nên có sẵn đủ quyền, nhóm và tài khoản quản trị. Máy đang có realm cũ (tạo trước khi có quyền) chạy **một lần** `scripts/start.ps1 -Reset` để import lại; sau đó quyền được lưu trong database của Keycloak, các lần sau chạy `scripts/start.ps1` như thường. Đổi quyền của một nhóm: Keycloak → realm `dx-lab` → **Groups** → nhóm → **Role mapping** → **Assign role** → lọc theo client → tích chọn quyền.

Script sẽ: sinh lại `.local/dx-lab-realm.json` từ template → xóa realm `dx-lab` → khởi động lại Keycloak để import → kiểm tra log có dòng `Realm 'dx-lab' imported` → khởi động lại backend để nạp khóa ký mới.

> [!WARNING]
> Chỉ xóa realm `dx-lab`; realm `master` và dữ liệu PostgreSQL của core giữ nguyên. Tuy vậy user được tạo lại với **ID (`sub`) mới**, nên ticket/quy trình tạo bằng tài khoản cũ trên máy đó sẽ không còn ai xem được. Chỉ dùng cho môi trường local/demo.

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
> **Mở rộng workspace:** Để thêm workspace hoặc role mới, chỉ cần tạo nhóm `/workspaces/<workspace>/<role>` trên giao diện Keycloak, không cần sửa đổi mã nguồn Core. Keycloak chỉ import cấu hình lần đầu khi realm chưa tồn tại; muốn nhận thay đổi mới trong template thì chạy `scripts/start.ps1 -Reset`, không xóa volume dùng chung.

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
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke.ps1
```

### Các lệnh vận hành thông dụng

```powershell
# Kiểm tra tính hợp lệ của file cấu hình Compose
docker compose config --quiet

# Build lại container backend platform
docker compose build platform

# Kiểm tra cơ sở dữ liệu PostgreSQL trực tiếp
Get-Content scripts/check-db.sql -Raw | docker compose exec -T postgres psql -U core -d core -v ON_ERROR_STOP=1

# Theo dõi nhật ký hệ thống (logs)
docker compose logs --tail 100 -f platform keycloak gateway

# Dừng cụm dịch vụ (giữ nguyên dữ liệu volumes)
docker compose stop

# Khởi động lại cụm dịch vụ
docker compose up -d --wait --wait-timeout 300
```

### Xử lý sự cố thường gặp (Troubleshooting)
- **Lỗi kết nối Docker:** Khởi động Docker Desktop trước khi chạy lệnh.
- **Lỗi 401 Unauthorized:** Kiểm tra JWT issuer (`http://localhost:8081/realms/dx-lab`), audience (`dx-core`), thời hạn token và kết nối mạng nội bộ tới JWKS.
- **Lỗi 403 Forbidden:** Kiểm tra phân nhóm người dùng và vai trò tương ứng với workspace đang truy cập.
- **Keycloak báo "Client not found" / tài khoản demo không đăng nhập được:** realm cũ, chạy `scripts/start.ps1 -Reset` (xem mục *Làm mới realm Keycloak*).
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
compose.yaml + compose.triage.yaml
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
