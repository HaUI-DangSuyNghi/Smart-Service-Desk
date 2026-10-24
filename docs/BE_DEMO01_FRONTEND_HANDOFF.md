# BE-DEMO-01 — Frontend Integration & Demo Handoff

Ngay lap: 2026-10-18 (Asia/Saigon).
Baseline da kiem chung: `origin/develop = da955d24eddf9004fea12aa45d51cdc58d5a537d` (merge PR #22).
Pham vi: **Backend Integration Hand-off cho Frontend**. Khong thay doi AI runtime, khong refactor Core, khong chua bi mat/credentials that.

---

## 1. Tong quan kien truc va vai tro 3 tang theo de thi HaUI 2026

He thong Smart Service Desk duoc to chuc theo dung **khung 3 tang va chuoi nang luc H-P-D-I** cua de thi DX-Lab HaUI (VFOSSA OLP 2026):

```
+---------------------------------------------------------------------------------------+
| TANG 3 — Ung dung Nghiep vu Smart Service Desk (Layer 3: apps/servicedesk)            |
|   - So huu bai toan Ho tro CNTT / Service Desk cua don vi                             |
|   - REST API: /api/apps/servicedesk/v1/workspaces/{workspace}/...                     |
|   - Quyen doc/ghi Ticket rieng cho REQUESTER, AGENT, COORDINATOR                       |
|   - Tich hop khong gian nang luc H-P-D-I; khong ghi de hay so huu quyen he thong     |
+---------------------------------------------------------------------------------------+
| TANG 2 — Khong gian Nang luc So hoa H-P-D-I (Layer 2)                                 |
|   [H - Human]        Keycloak OIDC PKCE + RBAC + /api/core/v1/me                      |
|   [P - Process]      Flowable BPMN 2.0 (service-desk, document-review, equipment)      |
|   [D - Data]         PostgreSQL + Core Metadata API + Core File Storage                |
|   [I - Intelligence] Service Desk Ticket Triage (Python scikit-learn / Rule Engine)   |
+---------------------------------------------------------------------------------------+
| TANG 1 — Nen tang Dich vu Loi So DX-OS Open-Core (Layer 1 Headless / PaaS)           |
|   - API Gateway:     Apache APISIX (Routing, body size cap, rate limit, proxy rewrite)|
|   - Identity:        Keycloak (OIDC Discovery, JWKS, Realm dx-lab, RBAC Group Claims) |
|   - Data Core:       PostgreSQL 16 (core schema, process_scope, file_object, audit)   |
|   - Workflow Engine: Flowable BPMN Engine (lich su chay, quan ly task, transaction)   |
|   - Audit Service:   Ghi nhat ky giao dich dong thoi voi nghiep vu (RFC-compliant)    |
+---------------------------------------------------------------------------------------+
```

FE chi can giao tiep voi hai diem duy nhat:
1. **Keycloak IdP**: dang nhap browser bang Authorization Code flow + PKCE S256.
2. **APISIX Gateway** (`/api/...`): mang JWT bearer token de goi toan bo Core va Service Desk API.

---

## 2. Thong tin ket noi va moi truong Demo

| Thanh phan | URL goc (Docker Mac dinh) | Vai tro |
| --- | --- | --- |
| **APISIX API Gateway** | `http://localhost:9080` | Diem goi API duy nhat tu Browser/Frontend |
| **Keycloak IdP** | `http://localhost:8081` | Authorization Server, OIDC discovery, login page |
| Realm OIDC | `http://localhost:8081/realms/dx-lab` | OIDC Realm cua he thong |
| Backend Core | `http://localhost:8080` | Noi bo (nam sau APISIX, FE khong goi truc tiep) |
| Python Intelligence | `http://intelligence:8000` | Noi bo (khong expose ra ngoai, FE khong goi truc tiep) |

> **Luu y ve cong**: Khi chay kiem thu bang Compose project rieng (vi du script int01), cong gateway co the duoc map sang `19080`/`29080` va Keycloak sang `18081`/`28081` theo bien moi truong `.env`.

---

## 3. Huong dan tich hop Authentication (Keycloak PKCE S256)

### 3.1 OIDC Client da duoc cau hinh san
- **Client ID**: `dx-web`
- **Client Type**: Public client (khong co client secret)
- **Grant Type**: Authorization Code with **PKCE (S256 bat buoc)**
- **Redirect URIs da duyet**: `http://localhost:5173/*`, `http://localhost:9080/*`
- **Web Origins da duyet**: `http://localhost:5173`, `http://localhost:9080`
- **Token lifespan**: 300 giay (5 phut). FE can refresh token theo co che chuan OIDC.

### 3.2 Luong dang nhap chuan cua Frontend
1. FE sinh ngau nhien `code_verifier` (chuoi URL-safe, do dai 43-128 ky tu).
2. Tinh `code_challenge = BASE64URL(SHA256(code_verifier))` bo dau `=`.
3. Sinh `state` ngau nhien de chong CSRF.
4. Chuyen huong trinh duyet sang Keycloak:
   ```http
   GET http://localhost:8081/realms/dx-lab/protocol/openid-connect/auth?
       client_id=dx-web&
       response_type=code&
       scope=openid&
       redirect_uri=http://localhost:5173/auth/callback&
       state=<random_state>&
       code_challenge=<challenge>&
       code_challenge_method=S256
   ```
5. Nguoi dung dang nhap qua giao dien Keycloak. Keycloak chuyen huong ve:
   `http://localhost:5173/auth/callback?code=<code>&state=<state>`
6. FE goi POST doi authorization code lay access token:
   ```http
   POST http://localhost:8081/realms/dx-lab/protocol/openid-connect/token
   Content-Type: application/x-www-form-urlencoded
   Origin: http://localhost:5173

   grant_type=authorization_code&
   client_id=dx-web&
   redirect_uri=http://localhost:5173/auth/callback&
   code=<code>&
   code_verifier=<verifier>
   ```
7. Luu `access_token` vao memory cua ung dung FE va dinh kem vao header `Authorization: Bearer <access_token>` trong moi request API.

### 3.3 Tai khoan thu nghiem (Demo Test Accounts)
Tat ca mat khau nguoi dung duoc sinh tu dong vao file `.env` (bien `DEMO_PASSWORD`) khi chay `scripts/setup.ps1`. Khi chay demo, lien he nguoi quan tri lay gia tri `DEMO_PASSWORD` hien tai. **Khong hard-code mat khau vao ma nguon FE**.

| Username | Ho va Ten | Workspace | Vai tro (Role) | Chuc nang phu hop khi Demo |
| --- | --- | --- | --- | --- |
| `user1` | Demo Requester | `lab-a` | `requester` | Tao Ticket, xem tien do, xac nhan hoan thanh |
| `user2` | Demo Agent | `lab-a` | `agent` | Nhan ticket (claim), xu ly ticket (complete handle) |
| `user3` | Demo Coordinator | `lab-a` | `coordinator` | Xem toan bo ticket cua workspace, xem bao cao thong ke |
| `user4` | Demo Requester B | `lab-b` | `requester` | Thu nghiem co lap giua cac workspace (cross-workspace) |
| `user5` | Demo Agent B | `lab-b` | `agent` | Agent thuoc workspace khac |
| `user6` | Demo Coordinator B| `lab-b` | `coordinator` | Coordinator thuoc workspace khac |
| `user7` | Demo Admin | *(none)* | `platform-admin` | Quan tri he thong (khong co quyen xem du lieu nghiep vu) |

---

## 4. Danh sach API va Contract chi tiet cho Frontend

Moi request goi qua APISIX phai co header:
```http
Authorization: Bearer <access_token>
X-Request-ID: <UUID-v4>
Accept: application/json
```
*(X-Request-ID giup tra cuu log nhat quan giua Gateway, Core va Intelligence).*

---

### A. API Nguoi dung & Dinh danh (Core Identity)

#### 1. Lay thong tin nguoi dung hien tai
- **Route**: `GET /api/core/v1/me`
- **Quyen**: Moi nguoi dung co JWT hop le
- **Response 200**:
  ```json
  {
    "sub": "2c67b2d5-...",
    "username": "user1",
    "memberships": [
      {
        "workspace": "lab-a",
        "role": "requester"
      }
    ],
    "platformAdmin": false
  }
  ```
- **FE dung de**: Xac dinh nguoi dung dang o workspace nao va co quyen gi (`requester`, `agent`, `coordinator`) de render menu/nut bam tuong ung.

---

### B. API Nghiep vu Service Desk (Service Desk Tickets)

Tat ca cac endpoint duoi day nam duoi prefix:
`/api/apps/servicedesk/v1/workspaces/{workspace}/tickets`

#### 1. Tao Ticket moi
- **Route**: `POST /api/apps/servicedesk/v1/workspaces/{workspace}/tickets`
- **Quyen**: `requester`, `agent`, `coordinator` trong workspace
- **Request Body** (`Content-Type: application/json`):
  ```json
  {
    "clientRequestId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
    "title": "May in phong Lab A khong ket noi mang",
    "description": "Thiet bi may in HP tai phong Lab A bao loi IP tu 8h sang."
  }
  ```
  - `clientRequestId` (UUID bat buoc): Khac voi `X-Request-ID`, day la idempotent submission key. Neu submit trung clientRequestId cua cung user/workspace, backend tra **409 Conflict** (chong bam trung/gui 2 lan).
  - `title`: 1 - 200 ky tu, khong chua control character.
  - `description`: 1 - 4000 ky tu (cho phep xuong dong `\n`, tab).
- **Response 201 Created**:
  ```json
  {
    "id": "e4b6c3d2-...",
    "workspace": "lab-a",
    "creator": "2c67b2d5-...",
    "clientRequestId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
    "title": "May in phong Lab A khong ket noi mang",
    "description": "Thiet bi may in HP tai phong Lab A bao loi IP tu 8h sang.",
    "createdAt": "2026-10-18T10:00:00Z",
    "process": {
      "processInstanceId": "10523",
      "processKey": "service-desk",
      "businessKey": "e4b6c3d2-...",
      "workspace": "lab-a"
    }
  }
  ```

#### 2. Danh sach / Tim kiem Ticket
- **Route**: `GET /api/apps/servicedesk/v1/workspaces/{workspace}/tickets`
- **Quyen & Pham vi nhin thay (Visibility)**:
  - `requester`: **Chi nhin thay ticket do chinh minh tao** (`creator == user.sub`).
  - `agent` hoac `coordinator`: **Nhin thay toan bo ticket trong workspace**.
  - `user` khac workspace: **403 Forbidden**.
- **Query Parameters**:
  - `q` (string, max 200): Tim kiem khong phan biet hoa thuong trong ca `title` va `description`.
  - `from`, `to` (ISO 8601 Instant, vi du: `2026-10-01T00:00:00Z`).
  - `page` (integer, >= 0, mac dinh 0).
  - `size` (integer, 1 - 100, mac dinh 20).
- **Response 200**:
  ```json
  {
    "items": [
      {
        "id": "e4b6c3d2-...",
        "workspace": "lab-a",
        "creator": "2c67b2d5-...",
        "clientRequestId": "...",
        "title": "...",
        "description": "...",
        "createdAt": "2026-10-18T10:00:00Z",
        "process": {
          "processInstanceId": "10523",
          "processKey": "service-desk",
          "businessKey": "e4b6c3d2-...",
          "workspace": "lab-a"
        }
      }
    ],
    "page": 0,
    "size": 20,
    "total": 1
  }
  ```

#### 3. Chi tiet Ticket
- **Route**: `GET /api/apps/servicedesk/v1/workspaces/{workspace}/tickets/{id}`
- **Quyen**: Nhu danh sach (requester chi xem cua minh, agent/coordinator xem cua workspace; khong ton tai hoac khong co quyen -> **404 Not Found**).
- **Response 200**: Doi tuong `Ticket` (nhu tren).

#### 4. Tien do Quy trinh cua Ticket (Live Flowable Progress)
- **Route**: `GET /api/apps/servicedesk/v1/workspaces/{workspace}/tickets/{id}/progress`
- **Quyen**:
  - `requester` (creator) va `coordinator`: Luon doc duoc tien do.
  - `agent`: **Chi doc duoc sau khi da claim task** hoac tung xu ly task trong instance nay. Neu chua nhan viec, tra ve **404 Not Found** (Agent phai tim task tren Candidate Inbox cua minh truoc).
- **Response 200**:
  ```json
  {
    "id": "10523",
    "processKey": "service-desk",
    "businessKey": "e4b6c3d2-...",
    "workspace": "lab-a",
    "initiator": "2c67b2d5-...",
    "startedAt": "2026-10-18T10:00:00Z",
    "endedAt": null,
    "ended": false,
    "currentTasks": [
      {
        "id": "5001",
        "processInstanceId": "10523",
        "key": "handle",
        "name": "Handle support request",
        "candidateGroups": ["lab-a:agent"],
        "assignee": null,
        "createdAt": "2026-10-18T10:00:01Z"
      }
    ],
    "steps": [
      {
        "id": "5001",
        "key": "handle",
        "name": "Handle support request",
        "assignee": null,
        "actor": null,
        "outcome": null,
        "comment": null,
        "startedAt": "2026-10-18T10:00:01Z",
        "claimedAt": null,
        "endedAt": null
      }
    ]
  }
  ```

#### 5. Thong ke Ticket (Ticket Statistics)
- **Route**: `GET /api/apps/servicedesk/v1/workspaces/{workspace}/tickets/statistics`
- **Parameters**: `q`, `from`, `to` (tuong tu search ticket).
- **Quyen**:
  - `requester`: Thong ke tren tap ticket cua rieng minh (`visibility: "own"`).
  - `agent` / `coordinator`: Thong ke toan bo ticket cua workspace (`visibility: "workspace"`).
- **Response 200**:
  ```json
  {
    "workspace": "lab-a",
    "visibility": "workspace",
    "total": 10,
    "awaitingHandling": 6,
    "awaitingConfirmation": 2,
    "completed": 2,
    "unclassified": 0
  }
  ```
  - `total = awaitingHandling + awaitingConfirmation + completed + unclassified`.
  - Du lieu lay truc tiep tu snapshot database/Flowable, khong dung cache.

---

### C. API Xu ly Quy trinh Cong viec (Core Workflow Inbox & Task)

Cac thao tac nhan viec va xu ly cong viec duoc thuc hien qua API chuan cua T1 Core Workflow:

#### 1. Hop thu Cong viec (Task Inbox)
- **Route**: `GET /api/core/v1/workspaces/{workspace}/tasks`
- **Query parameter `scope`**:
  - `scope=candidate`: **Viec cho nhan**. Liet ke cac task ma group cua user duoc phep lam nhung chua ai claim, va user **khong phai nguoi tao ticket** (Agent khong duoc tu xu ly ticket cua chinh minh).
  - `scope=assigned` (mac dinh): **Viec toi dang giu**. Liet ke cac task da duoc user hien tai claim.
- **Phan trang**: `page` (>=0), `size` (1-100).
- **Response 200**:
  ```json
  {
    "items": [
      {
        "id": "5001",
        "processInstanceId": "10523",
        "key": "handle",
        "name": "Handle support request",
        "candidateGroups": ["lab-a:agent"],
        "assignee": null,
        "createdAt": "2026-10-18T10:00:01Z"
      }
    ],
    "page": 0,
    "size": 20,
    "total": 1
  }
  ```

#### 2. Nhan viec (Claim Task)
- **Route**: `POST /api/core/v1/workspaces/{workspace}/instances/{instanceId}/tasks/{taskId}/claim`
- **Quyen**: Nguoi dung phai thuoc `candidateGroups` cua task (vi du `agent`) va khong phai creator.
- **Response 200**: Doi tuong `Task` sau khi da duoc gan `assignee = user.sub`.
- **Loi 409 Conflict**: Neu hai Agent bam claim cung luc, nguoi den sau nhan 409 (Flowable concurrency lock an toan).

#### 3. Hoan thanh Task (Complete Task)
- **Route**: `POST /api/core/v1/workspaces/{workspace}/instances/{instanceId}/tasks/{taskId}/complete`
- **Quyen**: Chi nguoi dang giu task (`assignee == user.sub`) moi duoc hoan thanh.
- **Request Body**:
  ```json
  {
    "expectedTaskKey": "handle",
    "outcome": "resolved",
    "comment": "Da cau hinh lai dia chi IP may in thanh cong."
  }
  ```
  - `expectedTaskKey`: Bat buoc khop voi `key` cua task hien tai (chong stale submit).
  - `outcome`: Gia tri hop le duoc dinh nghia trong `ServiceDeskProcess`:
    - Voi buoc `handle`: gia tri duy nhat la `"resolved"`.
    - Voi buoc `confirm`: gia tri la `"accepted"` (dong ticket) hoac `"rework"` (yeu cau sua lai -> quay ve buoc handle cho Agent).
  - `comment`: Tuy chon, toi da 1000 ky tu.
- **Response 204 No Content**: Xu ly thanh cong.

---

### D. API Tri tue nhan tao (AI Ticket Triage Bridge)

Nang luc Triage ho tro phan loai tu dong ticket su dung mo hinh scikit-learn / Rule Engine tu tang 2.

#### Goi goi y phan loai Ticket
- **Route**: `POST /api/apps/servicedesk/v1/workspaces/{workspace}/tickets/{id}/triage`
- **Quyen**: Requester xem ticket cua minh, Agent/Coordinator xem ticket cua workspace.
- **Request Body**: **TUYET DOI DE TRONG (NO BODY)**.
  - Moi request co body (ke ca `{}` hoac space) se bi tra ve **400 Bad Request**. Toan bo tieu de va mo ta duoc Backend tu dong lay tu ban ghi Ticket da luu trong PostgreSQL de dam bao tinh xac thuc.
- **Response 200 — Truong hop SUCCESS (Co ket qua goi y)**:
  ```json
  {
    "contractVersion": "0.1.0",
    "requestId": "5a4ef213-...",
    "profile": "service-desk",
    "workspaceId": "lab-a",
    "status": "SUCCESS",
    "needsReview": false,
    "result": {
      "category": "HARDWARE",
      "priority": "HIGH",
      "suggestedGroup": "lab-a:agent"
    },
    "evidence": [
      {
        "type": "INPUT",
        "field": "title",
        "text": "May in phong Lab A khong ket noi mang"
      }
    ],
    "explanation": {
      "matchedRules": ["RULE-HARDWARE-01"],
      "impact": "HIGH",
      "urgency": "HIGH",
      "reasonCode": "CLASSIFIED",
      "text": "Phat hien tu khoa lien quan den thiet bi ngoai vi phong thi nghiem."
    },
    "runtime": {
      "engine": "rules-engine",
      "profileVersion": "service-desk@0.2.0",
      "rulesVersion": "0.2.0"
    },
    "error": null
  }
  ```
- **Response 200 — Truong hop DEGRADED (Ket qua suy giam)**:
  - Khi du lieu mo ho hoac xung dot luat, AI tra ve envelope voi `status: "DEGRADED"`, `result: null`, `evidence: []`, `needsReview: true`.
- **Cac ma loi khi AI gap su co**:
  - `502 Bad Gateway` (`INTELLIGENCE_CONTRACT_FAILED` / `INTELLIGENCE_INTEGRATION_FAILED`): Sai hop dong noi bo hoac token ket noi giua Java va Python khong hop le.
  - `503 Service Unavailable` (`INTELLIGENCE_UNAVAILABLE`): Container Python chua bat, bi tat hoac qua tai.
  - `504 Gateway Timeout` (`INTELLIGENCE_TIMEOUT`): Python xu ly qua deadline (4 giay).
- **Luu y giao dien (FE Guidelines)**:
  - Cac loi 502/503/504 **KHONG PHAI LA LOI DANG NHAP** cua nguoi dung, khong xoa token cua nguoi dung.
  - Khi AI gap loi hoac tra ve DEGRADED, **tat ca cac nut thao tac thu cong cua nguoi dung (Claim, Complete, Xem tien do) van phai hoat dong 100% binh thuong**. AI chi dong vai tro goi y ho tro (Decision Support).

---

## 5. Dinh dang Loi chuan (RFC 9457 ProblemDetail)

Khi xay ra loi HTTP >= 400, Backend luon tra ve content-type `application/problem+json`:

```json
{
  "type": "about:blank",
  "title": "Forbidden",
  "status": 403,
  "detail": "Workspace access denied",
  "instance": "/api/apps/servicedesk/v1/workspaces/lab-a/tickets",
  "code": "FORBIDDEN",
  "requestId": "9c1a2e3b-..."
}
```

Bang ma loi `code`:
| Code | HTTP Status | Y nghia |
| --- | --- | --- |
| `VALIDATION_FAILED` | 400 | Sai du lieu dau vao, thieu truong bat buoc, qua gioi han ky tu |
| `UNAUTHENTICATED` | 401 | Thieu header Authorization hoac JWT da het han |
| `FORBIDDEN` | 403 | Khong thuoc workspace hoac khong du role de thuc hien hanh dong |
| `NOT_FOUND` | 404 | Ban ghi khong ton tai hoac bi che giau do khong du tham quyen xem |
| `CONFLICT` | 409 | Duplicate clientRequestId, hoac task da bi nguoi khac claim/complete |
| `PAYLOAD_TOO_LARGE` | 413 | Dung luong body vuot qua gioi han gateway (6 MiB) hoac file (>5 MiB) |
| `INTELLIGENCE_UNAVAILABLE` | 503 | Dich vu Python Intelligence dang tat hoac khong the ket noi |
| `INTELLIGENCE_TIMEOUT` | 504 | Python xu ly vuot qua 4 giay |

---

## 6. Kich ban Demo Mau hoan chinh cho Buoi Trinh Dien (10 Phut)

Khi trinh dien truoc Hoi dong giam khao HaUI, FE nen thuc hien theo chuoi kich ban sau de the hien day du ca 3 tang va chuoi H-P-D-I:

1. **Buoc 1 (H - Human)**:
   - Dang nhap bang tai khoan `user1` (Requester) qua Keycloak OIDC PKCE.
   - Goi `/api/core/v1/me` de hien thi thong tin nguoi dung va role `requester` tai `lab-a`.
2. **Buoc 2 (D - Data & P - Process)**:
   - `user1` tao mot Ticket yeu cau ho tro moi: "May in phong Lab A loi ket noi".
   - Backend luu Ticket vao PostgreSQL dong thoi khoi tao mot Flowable Process Instance (`service-desk`).
   - FE hien thi Ticket trong danh sach va xem duoc trang thai `awaitingHandling`.
3. **Buoc 3 (I - Intelligence)**:
   - Nhan nut "Phan tich / AI Triage".
   - Backend goi sang Python Intelligence runtime that, phan loai thanh cong danh muc `HARDWARE`, do uu tien `HIGH`, kem bang chung trich xuat tu tieu de va luat phu hop.
4. **Buoc 4 (P - Process & H - Human)**:
   - Dang xuat, dang nhap bang `user2` (Agent).
   - `user2` vao trang "Viec cho nhan" (Candidate Inbox: `GET /tasks?scope=candidate`).
   - `user2` thay task cua ticket tren va bam nut "Nhan viec" (Claim Task).
   - Sau khi claim, task chuyen sang "Viec cua toi" (Assigned Inbox).
   - `user2` mo chi tiet ticket, nhap ket qua "Da cau hinh lai mang" va bam "Hoan thanh" (`outcome: "resolved"`).
5. **Buoc 5 (P - Process Completion)**:
   - Dang nhap lai bang `user1` (Requester).
   - `user1` vao chi tiet ticket, thay tien do da sang buoc `Confirm resolution`.
   - `user1` chon outcome `"accepted"` de xac nhan hai long. Ticket ket thuc thanh cong.
6. **Buoc 6 (D - Data Dashboard)**:
   - Dang nhap bang `user3` (Coordinator).
   - Xem dashboard thong ke (`/statistics`): so luong ticket completed tang len 1, awaitingHandling giam xuong 0.
7. **Buoc 7 (Minh chung doc lap & kien truc)**:
   - Mo Audit log (`/api/core/v1/workspaces/lab-a/audit`) de chung minh moi buoc deu duoc T1 ghi nhat ky.
   - Thu lay tai khoan `user4` (thuoc `lab-b`) de truy cap ticket cua `lab-a` -> Chung minh he thong chan **403/404 Cross-workspace** tuyet doi.

---

## 7. Phan cong phoi hop va Blocker hien tai

### 7.1 Viec FE can chu y
- **Chua co CORS tren APISIX**: Neu ung dung FE chay tren port khac (vi du Vite `http://localhost:5173`) ma goi truc tiep `http://localhost:9080` qua `fetch()`, trinh duyet se chan preflight OPTIONS do APISIX chua cau hinh plugin `cors`.
  - *Giai phap khuyen nghi cho Demo*: FE su dung tinh nang dev server proxy cua Vite:
    ```javascript
    // vite.config.js
    server: {
      proxy: {
        '/api': {
          target: 'http://localhost:9080',
          changeOrigin: true
        }
      }
    }
    ```
    Khi do moi request FE deu goi `/api/...` tren cung origin `localhost:5173`, khong bi vuong CORS.
  - *Neu muon APISIX bat CORS*: Can thong nhat origin chinh xac de cau hinh plugin `cors` tren `infra/apisix/apisix.yaml`, khong mo wildcard `*`.
- **Tuan thu quy uoc Text**: Tieu de va mo ta ticket hien thi bang Plain Text an toan (tranh loi XSS, khong dung `v-html` / `dangerouslySetInnerHTML`).

### 7.2 Viec AI can phoi hop
- Triage model va profile `service-desk@0.2.0` da on dinh va san sang tren baseline develop.
- Tiep tuc giu contract HTTP hien tai, khong tu y thay doi envelope schema 0.1.0.

### 7.3 Huong dan chay toan bo Stack Backend cho buoi Demo
Dung terminal chay:
```powershell
# 1. Chuan bi secret va realm Keycloak
./scripts/setup.ps1

# 2. Khoi dong toan bo he thong gom Database, Keycloak, Backend Core, Gateway va AI
docker compose -f compose.yaml -f infra/compose/compose.triage.yaml --profile intelligence up -d --build
```
Kiem tra tat ca container deu o trang thai `healthy` qua lenh: `docker ps`.