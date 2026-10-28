# MERGE-READY-01 — Review baseline DX-OS

Ngày xác minh: **18/10/2026, Asia/Saigon**. Repo HaUI-DangSuyNghi/Smart-Service-Desk.
Private develop review, không phải production/public release approval.
Không merge, retarget, đóng PR, đổi branch, reset worktree hoặc tác động database.

## A. PR status và commit SHA

Đã fetch origin và gọi GitHub REST API pulls/reviews/check-runs/status/run/jobs;
tải và đọc artifact hai run, không chỉ lặp lại handoff cũ.

| PR | State / draft | HEAD SHA | Base SHA | Base branch | Mergeable | Reviews / HEAD checks |
| --- | --- | --- | --- | --- | --- | --- |
| #19 | OPEN / false | c16598dd032b962b79a9100c80c61b79419c4749 | e39356cc44ef50421a9b81199756ec9784fe161a | develop | true/clean | 0 / 4 success |
| #20 | OPEN / false | 0050da1f4b840bde53b8b68f6e4dd74da50f960c | e39356cc44ef50421a9b81199756ec9784fe161a | develop | true/clean | 0 / 2 success |
| #21 | OPEN / false | 8bbee5212244cbd68ccfc9bb56cd4dda5366ac15 | e39356cc44ef50421a9b81199756ec9784fe161a | develop | true/clean | 0 / 5 success |
| #22 | OPEN / false | 0e034bf936595661fc71e55e31510c019c84f695 | e39356cc44ef50421a9b81199756ec9784fe161a | develop | true/clean | 0 / 6 success |
| #23 | OPEN / true | 43df9d745cbfb190cdfca8ac9c08f080c0851b36 | 0e034bf936595661fc71e55e31510c019c84f695 | integration/be-ai-triage-retrieval | true/clean | 0 / **0 checks** |

Checks #19: functional/core-integration/verify Ubuntu và Windows.
#20: functional/postgresql-regression. #21: functional/ai03-compatibility/
real-java-python/verify hai OS. #22: functional/postgresql-regression/
core-integration/combined-head/verify hai OS. #23 HEAD không có check-runs.

Branch protection API develop trả **403**: chưa xác minh required contexts,
approvals/rulesets. Không kết luận branch không được bảo vệ. Owner có quyền cần
kiểm tra trước merge. Mergeable/clean không thay approval. Legacy commit status
#22/#23 pending với total_count=0 chỉ là không có legacy contexts, không phải CI chạy dở.

Evidence tải vào TEMP, không thêm token/artifact vào repo:
- Run **39116445393**, pull_request, success, đúng HEAD #22; artifact
  integrated-current-head-evidence ID **12131894631**.
- Run **39121326727**, success, runtime SHA
  **207b9ace6daf24e678e9fa35b49966997353571d**; artifact cùng tên ID **12134154564**.
- Đã đọc integration-summary.json, java-regression.json, source/wheel XML,
  gateway-triage.json, authorized-retrieval.json, scan summary và SEC full image JSON.

Tám worktrees dưới D:/ giữ nguyên: Smart-Service-Desk f348beb (develop),
ai04 8bbee52, ai04-compat c16598d detached, integration 0e034bf, security 43df9d7,
statistics fbc9f92, ticket-foundation 90e8166, triage-bridge 0050da1.
Chỉ main có hai untracked docs từ trước: PROJECT_FROM_ZERO_TO_CONTRIBUTOR.md và
Smart_Service_Desk_BE_AI_Contract_v0.1.md; bảy worktree khác clean.
Local develop chậm origin/develop 5 commits. Không pull/checkout/ghi đè dữ liệu.

## B. Findings P0/P1/P2

- **P0:** Không phát hiện integration blocker mức P0 trong phạm vi review; không
  chứng nhận không có vulnerability/secret trong toàn Git history.
- **P1, release:** G9 FAIL, 369 HIGH + 15 CRITICAL = **384 occurrences**, không phải
  CVE duy nhất. Nguồn: docs/security/sec01/evidence.json, inventory.csv,
  scripts/scan-core.ps1 và artifact run 39121326727. Không cấp waiver.
- **P1, merge procedure:** Cả năm PR không có approvals; #23 draft, thiếu HEAD
  checks; required rules chưa đọc được (403). Không đồng nghĩa runtime integration lỗi.
- **P2, browser:** SecurityConfig.java và infra/apisix/apisix.yaml không có API
  CORS config. Keycloak dx-web webOrigins không mở CORS cho Java/APISIX. Browser
  5173 gọi trực tiếp 9080 với Bearer cần preflight. Dùng same-origin dev proxy /api
  hoặc owner cấu hình allowlist/test. Chưa tái hiện browser trong task này.
- **P2, handoff:** core-target.yaml là dự kiến assign task. AI-03 runtime chưa có
  public Java search endpoint. Không để FE gọi API chưa tồn tại/Python trực tiếp.

## C. PR #22 merge readiness

**READY FOR PRIVATE DEVELOP MERGE về kỹ thuật; chờ team approval và policy verification.**

- HEAD #19/#20/#21 đều là ancestor 0e034bf (merge-base exit 0); merge commits
  #20 2d9f0ea, #21 6fb27da, #19 03f9d68, không chỉ copy/cherry-pick vài file.
- Diff e39356c..0e034bf trong contracts/intelligence/v1 rỗng. BE-AI-TECH-0.1,
  schema 0.1.0 và profile service-desk@0.2.0 không đổi; package 0.3.0 không phải profile.
- Artifact đúng HEAD: **137 Java, 265 Python source, 265 wheel tests**, không fail/
  error/skip. G1–G8/G10 PASS; G9 FAIL; release BLOCKED.
- G7 thật qua Keycloak/APISIX/Java/Python, ba roles/hai workspaces, requester isolation,
  cross-workspace denial, không public Python, không mutate Ticket/workflow và manual
  completion khi Python tắt. Đây là HTTP acceptance, không phải browser E2E.
- G8 giữ authorized retrieval/citation, catalog rotation/checksum/404/outage guards,
  revocation với refreshed JWT, không tuyên bố thu hồi tức thời JWT đang sống.
- CI checkout actual PR HEAD và kiểm Java/Python SHA, package/image identity.
  Fix integration: credential validation, wheel discovery/current-head harness,
  readiness/outage assertions, không feature mới ngoài các PR nguồn.
- Rà diff 96 paths source/test/contracts/infra/docs, không thấy thay đổi ngoài scope.
  Pattern scan private key/GitHub token/AWS key không match; tracked env/key filenames
  chỉ có .env.example. Không thay full secret-history audit/data-rights approval.

Blockers thực thi: approval, required rules/checks, HEAD/base không trôi. Team có thể
cho phép private non-production baseline; G9/G0-B/LICENSE/rights/browser vẫn chưa đạt.
Synthetic records không chứng minh quyền công bố corpus/fixture.

## D. PR #23 merge readiness và release readiness

**Merge: NOT READY để thực thi hiện tại** (draft, thiếu approval/checks HEAD, chưa
retarget sau #22). Runtime đạt, là candidate cho private develop sau các gate.
**Release: BLOCKED, G9 FAIL.**

Diff 0e034bf..43df9d7: **16 files, 1511 additions/19 deletions**; image/BOM pins,
APISIX Dockerfile, test/scan consumers, exporter và docs/evidence. Không sửa business
controllers, Python engine/retrieval hay intelligence contracts.

| Thành phần | Thay đổi source | Căn cứ/giới hạn |
| --- | --- | --- |
| Java | Temurin 17.0.12_7 → 17.0.20.1_1; builder pin digest | Giữ Java 17; regression/runtime image evidence |
| Spring Boot | 3.3.4 → 3.3.13 | Patch cùng dòng/BOM |
| Tomcat / JDBC | Overrides 10.1.61 / 42.7.12 | Candidate fixes theo scan/SEC-01 report; giữ dòng, regression PASS |
| Python | 3.12.12 → 3.12.15 | Hai stage cùng digest; không thay dependency lock/model |
| PostgreSQL | 16.6 → 16.15, digest pin | Fresh/disposable DB đã test; chưa test upgrade/rollback DB có dữ liệu |
| Keycloak | 26.0.7 → 26.0.8, digest pin | Real login/JWT PASS; không giải quyết mọi CVE 2026 |
| APISIX | 3.11.0 Debian → 3.11.0 Red Hat + vendor RPM | Giữ server version, bỏ OS EOS, sửa Unbound occurrences |

infra/apisix/Dockerfile pin base digest; tải đúng unbound-libs và python3-unbound
1.16.2-5.14.el8_10.4 từ Red Hat UBI CDN. SHA-256 riêng x86_64/aarch64,
kiến trúc khác fail; sha256sum -c trước install; yum --disablerepo='*' không tải
dependency tùy ý. Không suy ra đã chạy E2E ARM64 từ CI trên kiến trúc runner.

Run 39121326727: 137 Java + 265 source + 265 wheel PASS, không skip; G7 real
Keycloak/APISIX và G8 retrieval PASS. Evidence thuộc **207b9ac**, không phải 43df9d7.
Diff 207b9ac..43df9d7 chỉ có:
- docs/SEC01_SECURITY_REMEDIATION.md
- docs/security/sec01/evidence.json
- docs/security/sec01/inventory.csv

Không có runtime/config/workflow diff. Có thể kế thừa runtime evidence sau review
docs, nhưng không gọi 43df9d7 là SHA đã rebuild hay required checks đã PASS.

Scanner không yếu đi: SEC-01 chỉ đổi image defaults trong scan-core.ps1, giữ
HIGH/CRITICAL/EOS policy, không ignore-unfixed, không bỏ CVE/image. ReportOnly đã
tồn tại ở #22, tách functional CI khỏi production gate; scanner failure vẫn fail.
SEC-01 bổ sung full JSON artifact. Exporter self-test local PASS; export lại artifact
ra 384 rows có SHA-256 CSV **khớp hệt file commit**. Không chỉnh findings cho xanh.

## E. Remaining security và release blockers

| Image cuối, run 39121326727 | HIGH | CRITICAL | OS EOS |
| --- | ---: | ---: | --- |
| Java Core | 14 | 2 | false |
| Python intelligence | 61 | 2 | false |
| PostgreSQL 16.15 | 95 | 4 | false |
| Keycloak 26.0.8 | 89 | 7 | false |
| APISIX custom | 110 | 0 | false |
| Tổng occurrences | **369** | **15** | **0** |

#22 baseline: 527 HIGH, 57 CRITICAL, một OS EOS. Giảm còn 369/15/0 không đạt
policy zero HIGH/CRITICAL/EOS. Baseline artifact thiếu per-CVE JSON nên chỉ so tổng,
không khẳng định đã đối chiếu mọi CVE trước/sau.

- Security/deployment owners: remaining findings; support/maintenance entitlement
  Boot/Flowable/Keycloak/APISIX chưa xác nhận. OS không EOS không chứng minh app supported.
- Production hardening: TLS, secret lifecycle, DB least privilege, upload scanning,
  backup/restore/audit/isolation. Compose start-dev và probe không phải production.
- Process Owner: G0-B Business Priority Profile vẫn PENDING approval.
- Rights holders: chưa có root LICENSE/formal grant; LICENSING.md intended Apache-2.0
  không thay grant. Fixture/data rights: PENDING_OWNER_APPROVAL.
- FE/QA: Browser UI E2E NOT RUN; password-login HTTP probe không thay browser PKCE.
- DB owner: chưa chứng minh upgrade/rollback dữ liệu hiện hữu; task không đụng DB đó.

## F. Exact merge order — đề xuất, chưa thực hiện

1. Team review #22 đúng HEAD/base mục A; owner có quyền xác minh branch protection,
   rulesets/required contexts và approve; rerun nếu head/base thay đổi.
2. Sau approval, merge #22 vào develop. **Ưu tiên merge commit, không squash/rebase**
   để giữ ancestry ba PR nguồn và base liên tục cho #23.
3. Fetch, kiểm HEAD #19/#20/#21 là ancestors develop mới; chỉ khi đó đóng như
   superseded và tham chiếu #22. Không xóa branch/worktree có dữ liệu.
4. Khi được người dùng cho phép, retarget #23 sang develop.
5. Review lại diff #23 trên base mới. Nếu merge commit và develop không đổi thêm,
   kỳ vọng chỉ 16 SEC-01 paths; không tự giả định diff đúng. Nếu squash/rebase,
   reconcile ancestry có kiểm soát, không force-reset worktree cũ.
6. Owner đồng ý mới chuyển #23 khỏi draft. Rerun required checks đúng final HEAD/base;
   dispatch exact SHA nếu docs-only/path filters không trigger. Xác nhận result gắn
   đúng HEAD, không dùng runtime ancestor thay required-check success.
7. Team approve rồi merge #23 khi review đạt; fetch, ghi SHA develop thực tế làm
   baseline FE/BE. SHA đó chưa tồn tại nên không gọi 43df9d7 là unified develop.

Mọi merge/retarget/close/draft-state change cần phép người dùng trước. Không tag/release;
các blockers E vẫn giữ nguyên sau merge.

## G. Backend API handoff cho Frontend

### Baseline, Keycloak và workspace actor

Trong lúc chờ: source #22 **0e034bf**, SEC runtime **207b9ac**, docs HEAD **43df9d7**.
origin/develop vẫn e39356c; local develop f348beb không phải baseline tích hợp.
Baseline chính thức là SHA develop được ghi sau F7.

Gateway dev localhost:9080. Keycloak realm **dx-lab**, public client **dx-web**,
Authorization Code + PKCE S256. core-probe password grant chỉ cho test scripts;
không dùng password grant/service secret trong FE. Gửi access token Bearer qua gateway.
GET /api/core/v1/me trả sub, username, memberships, platformAdmin.
Groups /workspaces/{workspace}/{requester|agent|coordinator}; platform-admin không
tự có quyền workspace. Backend là authority; không gửi role/trusted context tự tạo.
Tại 5173, dùng same-origin dev proxy /api tới gateway cho tới khi API CORS được test.

### OpenAPI và endpoint thật

contracts/openapi/core.yaml: server /api/core/v1.
contracts/openapi/servicedesk.yaml: server /api/apps/servicedesk/v1.
Đây là file contract, không có bằng chứng /v3/api-docs hoặc Swagger UI runtime.
core-target.yaml là dự kiến: không generate client để gọi assign.

Ký hiệu bảng: T=/api/apps/servicedesk/v1/workspaces/{workspace}/tickets;
C=/api/core/v1/workspaces/{workspace}. T/C không phải routes mới.

| Capability | Endpoint thật | Hành vi FE phải giữ |
| --- | --- | --- |
| Actor | GET /api/core/v1/me | Token hợp lệ; memberships cho workspace picker |
| Create Ticket | POST T | clientRequestId UUID, title max 200, description max 4000, đều bắt buộc; 201 |
| List / Detail | GET T, GET T/{id} | List q/from/to/page/size; requester own, agent/coordinator workspace |
| Statistics | GET T/statistics | q/from/to; workspace, visibility, total, awaitingHandling, awaitingConfirmation, completed, unclassified |
| Ticket timeline | GET T/{id}/progress | ProcessHistory thật, không dựng lịch sử từ AI text |
| Process definitions | GET /api/core/v1/process-definitions | Đọc policy, không invent transition |
| Inbox | GET C/tasks?scope=assigned hoặc scope=candidate | page/size; default assigned |
| Instance tasks | GET C/instances/{id}/tasks | Instance id từ Ticket.process.processInstanceId |
| Claim | POST C/instances/{id}/tasks/{taskId}/claim | Không cần body; trả TaskView |
| Complete | POST C/instances/{id}/tasks/{taskId}/complete | expectedTaskKey/outcome bắt buộc, comment tùy chọn max 1000; 204 |
| Process history | GET C/instances/{id}/history | Server kiểm quyền workspace/instance |
| Triage | POST T/{id}/triage | **Không body**, kể cả {}; advisory, không tự mutate/claim/complete |
| Knowledge metadata | POST/GET C/metadata, GET C/metadata/{id} | Search metadata q/source/sort/page/size, không phải AI retrieval |
| Authorized files | POST C/files, GET C/files/{fileId}, GET C/files/{fileId}/content | Upload multipart field file; metadata/file đều kiểm quyền |
| Audit | GET C/audit | Đúng filters/schema core.yaml |

Page thật {items,page,size,total}, page từ 0, size default 20, max 100.
Statistics là trạng thái workflow hiện tại của Ticket có createdAt trong khoảng lọc,
không phải biểu đồ lịch sử số hoàn tất. Đọc visibility; không suy quyền từ count.
Manual task keys/outcomes lấy từ policy và TaskView thật.

Triage schema: contracts/intelligence/v1/triage-*.schema.json và servicedesk.yaml,
BE-AI-TECH-0.1 giữ nguyên; FE render advisory response, không gọi Python trực tiếp.
Mapping transport errors public 502/503/504 giữ nguyên; hiển thị manual fallback,
không giả SUCCESS/DEGRADED. Xử lý Problem JSON và 400/401/403/404/409 theo OpenAPI;
429 có thể từ gateway.

### Authorized retrieval và API chưa tồn tại

AI-03 có TF-IDF partitioned runtime, CoreAuthorizedCorpusAdapter và citation
revalidation, được G8 kiểm bằng Core metadata/file thật có quyền. Đây là Python
library/harness, **chưa có Java public search API** cho browser. Python HTTP chỉ
nhận /internal/intelligence/v1/triage, không public qua gateway; triage không gọi
retrieval. Metadata search không tương đương AI ranking/citations.

Chưa có public knowledge/retrieval search; assign task; Ticket PUT/PATCH/DELETE;
comment/attachment-link API; metadata/file delete trong controller hiện tại.
Không phát minh URL cho những khả năng này.

### Khoảng trống H-P-D-I cho bài HaUI

| Nhóm | Nền hiện có | Còn thiếu để chứng minh end-user product |
| --- | --- | --- |
| H | Keycloak, actor/memberships, workspace roles | Browser PKCE/workspace shell, accessibility, screens thực tế và browser E2E |
| P | Flowable definitions/inbox/claim/complete/history, Ticket process | UI reusable, nghiệm thu xuyên suốt thay vì chỉ HTTP |
| D | Metadata/files/audit có quyền, Ticket list/statistics | Knowledge/data presentation/search UI và quyền nguồn dữ liệu được duyệt |
| I | AI-02 triage API, AI-03 runtime/evaluation/citations | Public authorized retrieval bridge, recommendation/citation UI, G0-B approval |

Giữ ba tầng. Không coi assign/CRUD đầy đủ, vector DB/LLM hay số lượng fixture tự đặt
là bắt buộc đề HaUI. Nguồn đối chiếu: docs/requirements-mapping.md và PDF chính thức
docs/references/De_thi_PMNM_HaUI_2026_BAN_CHINH_THUC.pdf. Mapping cũ ghi future/no app
không ghi đè evidence mới của Ticket/triage; chưa chứng minh final full H-P-D-I submission.

## H. Task Backend tiếp theo có giá trị demo cao nhất

**Đề xuất sau baseline: public authorized retrieval bridge; không triển khai trong task này.**
FE query qua Java với user/workspace đã xác thực, tái dùng AI-03 và trả ranked knowledge
có citations kiểm chứng. Không mở Python public, không đổi triage contract/domain ownership.

Trước code: review contract mới cùng FE/AI/security, identity propagation/trusted scope
và source rights. Acceptance: same-workspace success, cross-workspace/thiếu quyền chặn
trước ranking, stale citation loại bỏ, Core/Python lỗi có fallback, không mutate Ticket/
workflow, OpenAPI cùng PR và integration test tái chạy được. Đây là capability đề xuất,
chưa có endpoint FE được gọi. Nếu mục tiêu là release thay vì demo, security owners
vẫn phải ưu tiên đóng G9.

## Validation và giới hạn

- GitHub API/artifact đúng SHA/run; Git ancestry/diff/worktree status đã đối chiếu.
- Exporter self-test PASS; export full scan JSON 384 rows, CSV hash khớp file commit.
- git diff --check kiểm tra whitespace sau khi ghi tài liệu.
- Không rerun full Docker/Maven/browser tại local. Regression PASS trong báo cáo là
  CI evidence đã tải và đọc, không phải local tests vừa chạy.
- Không sửa runtime/workflow/scanner/contract; không tự approve/merge/release.
