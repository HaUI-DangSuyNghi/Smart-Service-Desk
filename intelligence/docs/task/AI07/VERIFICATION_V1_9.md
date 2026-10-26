# Kiểm chứng AI07 v1.9 — phân cấp và đánh giá vấn đề

Đây là receipt lịch sử của v1.9. Thay đổi sau review và số đo mới được ghi riêng
tại [AI07 v1.10](QUALITY_REVIEW_V1_10.md); không dùng hash/latency cũ để ký bộ policy mới.

Ngày chạy local: **26/10/2026**, Windows và Docker Linux, Java 17/Python 3.12.7.
Nhánh: `feature/ai07-hierarchical-assessment`. Không push, merge hoặc phát hành.

## Baseline và phạm vi

Đã fetch origin trước triển khai và kiểm tra lại cuối tác vụ:

- `origin/develop`: `c6d3933aaa8fad8e762037062aa5fb964b652e39`.
- `origin/feature/ai07-ticket-intelligence`: `ada3476e539301bf4bb9ebb1f746c7ac00c32f0b`.
- Nền AI07 sau rebase develop: `a3920b06ce5daf22b076230dc6bf8dfd54c01b11`.

Triển khai A–D: schema, taxonomy, resolver, I/U/risk, ma trận và endpoint chỉ đọc.
Phần E có công cụ chấm bản duyệt có chữ ký và quy trình chống giao thoa family;
chưa có Gold phân cấp thật. Phần F có kiểm chứng local, CI khai báo và runbook;
hosted CI cùng quyết định release còn chờ. `ticket.md` không có trong checkout;
16 ô kiểm thử được đối chiếu bảng trong plan và nguồn `triage-policy.json` hiện có.

## Kết quả đã chạy

| Nhóm | Kết quả | Bằng chứng local |
|---|---|---|
| Python nguồn, toàn bộ suite | 1.449 pass, 7 skip tùy chọn | `.local/ai07/v19-python-source.xml` |
| Wheel đã build và cài, chạy `python -I` | 1.449 pass, 7 skip tùy chọn | `.local/ai07/v19-python-wheel.xml` |
| Hierarchy/evaluation sau rà soát cuối, nguồn và wheel | 50/50 pass ở mỗi chế độ | `test_hierarchical_evaluation.py` |
| Java mặc định trên Linux/Postgres sạch | 433 pass, 0 failure/error/skip | `.local/ai07/v19-java-linux-reports/` |
| Java contract trên Windows | 119 pass, 0 failure/error/skip | `.local/ai07/v19-java-windows.log` |
| Core-only, Linux/Postgres cô lập | 91 pass, 0 failure/error/skip | `.local/ai07/v19-core-only.log` |
| Second-domain, không biên dịch Service Desk | 16 pass, 0 failure/error/skip | `.local/ai07/v19-second-domain.log` |
| Java → Python → Qwen thật, opt-in | 1 test, 10 request tổng hợp, pass | `.local/ai07/v19-qwen-e2e.log` |
| Khởi động stack theo `scripts/start.ps1` | Các service healthy | `.local/ai07/v19-stack-start.log` |
| Smoke qua Keycloak/APISIX/backend thật | Pass toàn bộ luồng | `.local/ai07/v19-smoke.log` |
| HTTP Core so với hợp đồng | 83 response JSON hợp lệ | `.local/core-integration-http.json` |
| Strict boundary / mutation | Pass; 39 mutation | `.local/ai07/v19-boundary-mutations-final.log` |
| Foundation | Pass; 145 ca âm tính | `.local/ai07/v19-foundation.log` |
| Build Docker và đóng gói Java | Pass | `.local/ai07/v19-maven-image.log`, `.local/ai07/v19-java-package.log` |

Hai suite Python đầy đủ được thu thập trước khi bổ sung các ca taxonomy và metric
cuối; 50 ca tập trung sau cùng kiểm chứng thay đổi đó trên cả nguồn và wheel.
Không cộng các lần chạy thành số mẫu độc lập. Bảy skip thuộc các kiểm tra model
tùy chọn hiện có; không gọi chúng là PASS. Java Windows là nhóm contract hỗ trợ
Windows, không phải tuyên bố toàn bộ kiểm tra quyền POSIX chạy trên Windows.

Các kiểm tra chính: đủ 16 ô priority; không suy impact từ NETWORK; không nâng
priority vì risk; nhãn con cùng parent; abstain khi thiếu/mâu thuẫn; Unicode có
emoji; schema từ chối confidence giả, nhãn lạ và priority bất khả thi; grant bundle,
actor/snapshot/grant drift; timeout/invalid/unavailable; SHADOW không rò nhãn/fact;
request khóa trùng, JSON nối đuôi, body quá lớn; không sửa ticket/workflow/audit.

Smoke được đồng bộ với hardening trên develop: requester thiếu `document:view`
phải bị 403, platform admin không có membership không đọc audit workspace, agent
được cấp quyền dùng để kiểm tra đọc/download. Security ghi header sớm nên kiểm
tra `no-store` theo directive và chấp nhận nhiều giá trị `nosniff` giống nhau;
vẫn từ chối giá trị khác. Không sửa quyền production để làm test qua.

## Receipt phục vụ Qwen thật

Dùng bundle và image đã được kit Qwen trước đó kiểm hash, mount read-only trong
mạng riêng, không mở cổng model ra host. Đầu vào hoàn toàn tổng hợp thuộc dự án.
Processing gate trong IT được mock rõ ràng cho fixture; đây không phải grant cho
dữ liệu khách hàng và không chứng minh phê duyệt nghiệp vụ.

- Model bundle: `a40d655f95f8ed6b6dfa89262b9bc1c99c014233e8ca7b23fe29c4f4dee9576d`.
- Model policy: `ac07759e7e3cfdcddd5e1259fea78564b270aa46e8a680dc9d0df5ce7b661753`.
- 10 request tuần tự: **p50 585,45 ms; p95/p99 2.088,79 ms** theo nearest rank.
- Mẫu đầu 2.088,79 ms có khởi tạo client/kết nối; sidecar đã ready trước khi đo.
- Ticket, runtime task và audit count trước/sau bằng nhau; response đúng schema.

Receipt có provenance từng artifact nằm ở `.local/ai07/v19/qwen-e2e-*.json`;
không đưa raw request hoặc secret vào tài liệu. Đây là baseline end-to-end qua
MockMvc + DB + HTTP provider + model cho một fixture, chưa gồm Keycloak/gateway
trên đường gọi assessment. Không dùng mẫu 10 request để kết luận SLO, Gold,
hiệu năng đồng thời, peak VRAM hoặc cold-start. Snapshot GPU sau chạy: RTX 3050
Laptop 4 GiB, tổng GPU đang dùng 1.837 MiB; không phải phép đo bộ nhớ riêng model.

## Chạy lại

```powershell
python scripts/check-tier-boundaries.py
python scripts/test-tier-boundaries.py
python scripts/intelligence/validate-foundation.py
python -m pytest intelligence/tests -q
python -m build --no-isolation --outdir .local/ai07/v19-dist-new intelligence
python scripts/intelligence/install-built-wheel.py --dist .local/ai07/v19-dist-new
python -I -m pytest -c scripts/intelligence/installed-pytest.ini intelligence/tests -q

docker build -f backend/Dockerfile --target build -t ai07-v19-maven:local .
./scripts/test-file.ps1 -BuildImage ai07-v19-maven:local
./scripts/test-file.ps1 -BuildImage ai07-v19-maven:local -MavenProfile core-only
./scripts/test-file.ps1 -BuildImage ai07-v19-maven:local -MavenProfile second-domain `
  -Tests 'RoomRequestIntegrationTest,TierBoundaryTest,CoreKnowledgeCatalogTest,CoreProcessCapabilitiesTest,WorkspaceActivityJournalTest,TrustedIntelligenceCapabilityTest,TrustedClassificationCapabilityTest'
```

Qwen IT không chạy trong suite mặc định. Chỉ chạy với dữ liệu tổng hợp, sidecar
đã ready và Postgres test. Cấp các biến `AI07_REAL_QWEN_E2E=true`,
`AI07_QWEN_BASE_URL` (localhost hoặc alias được transport cho phép),
`AI07_QWEN_TOKEN`, `AI07_QWEN_MODEL_DIGEST`, `AI07_QWEN_POLICY_DIGEST`,
`FILE_TEST_JDBC_URL`, `FILE_TEST_DB_PASSWORD`; không in token vào log:

```powershell
mvn -B -f backend/pom.xml '-Dtest=TicketAssessmentQwenIT' test
```

Mọi lần chạy DB toàn bộ suite phải dùng DB mới, vì các kiểm tra migration/workflow
chủ động tạo schema riêng. Docker service thử nghiệm được dừng sau khi kiểm chứng;
không xóa model bundle, receipt hoặc volume dữ liệu lâu dài.

## Các gate còn mở

Production **NO_GO**, `selectedModel=null`, DISABLED mặc định, PROMOTED bị chặn.
Chưa thay thế việc duyệt taxonomy/risk, quyền dữ liệu/license, double-review và
adjudication Gold, HOLDOUT niêm phong, ngưỡng định trước, load/fault/cold-start
phục vụ thật, hosted CI đúng SHA và ký duyệt Process Owner/Security/Operations.
Không có claim accuracy Gold hoặc calibrated confidence từ các kiểm tra trên.
