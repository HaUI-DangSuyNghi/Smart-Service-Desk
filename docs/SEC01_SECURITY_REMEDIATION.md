# SEC-01 — Khắc phục bảo mật và đánh giá release

Ngày đánh giá: **18/10/2026, Asia/Saigon**. Không merge, không release, không waiver.

## A. Baseline và phạm vi thay đổi

- PR #22 còn **OPEN / chưa merge** khi bắt đầu. HEAD chính xác:
  `0e034bf936595661fc71e55e31510c019c84f695`.
- Worktree riêng: `D:/Smart-Service-Desk-security`; branch:
  `fix/security-image-remediation`. Không sửa worktree `develop`, các worktree
  của #19/#20/#21, database hoặc volume của người dùng.
- PR bảo mật độc lập: https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/pull/23
  là **draft**, base `integration/be-ai-triage-retrieval` để diff chỉ chứa SEC-01.
  Chỉ retarget `develop` sau khi #22 được người có thẩm quyền merge.
- Không đổi kiến trúc, Java 17, Python 3.12, Flowable 7.1.0, wire contract
  BE-AI-TECH-0.1, schema/realm nguồn, business profile hoặc thuật toán AI.

## B. Nguồn evidence và giới hạn inventory baseline

Baseline workflow: https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/actions/runs/39116445393

- Artifact `integrated-current-head-evidence`, ID `12131894631`.
- SHA-256 archive tải về khớp digest GitHub:
  `155d02d8f2d8b94a9ea4a9e6a533ed9fc915975c82b9954dfee0006f88a43ca7`.
- Trivy **0.76.0**, scanner image cố định:
  `aquasec/trivy@sha256:2416d1f74045b57274df1a340eee6b510cacd73c66a6ccb8da092b117eb7d222`.
- Vulnerability DB v2 UpdatedAt `2026-10-18 07:12:07.771859408 +0000 UTC`;
  Java DB v1 UpdatedAt `2026-10-18 00:58:00.305003613 +0000 UTC`.
- **Artifact baseline KHÔNG chứa `image-0.json` … `image-4.json`.** Workflow cũ
  chỉ upload summary và scanner version. Vì vậy không thể khẳng định danh sách
  CVE/package/fixed-version baseline từ artifact này. Không suy diễn ID CVE từ
  số lượng, không dùng scan image rebuild khác làm evidence của image ID cũ.
- Hai image tự build không có registry RepoDigest; image ID/config digest
  được giữ riêng. Không gọi image ID là manifest digest, không khẳng định còn
  có thể tải image đó từ registry. Ba vendor image có digest được ghi trong
  `docs/security/sec01/evidence.json`.
- APISIX sau SEC-01 là image build từ official Red Hat digest và hai vendor
  RPM được khoá SHA-256; image ID của build được ghi riêng, không tự tạo một
  registry RepoDigest cho image chưa publish.
- Workflow đã bổ sung upload **full `image-*.json`**, không upload image tar,
  secret `.env`, realm chứa password hoặc database dump.

Inventory sau sửa nằm trong `docs/security/sec01/inventory.csv`: mỗi dòng là
một occurrence HIGH/CRITICAL theo image/package/path; không deduplicate làm
giảm số lượng. Có image ID, registry digest nếu có, OS/language ecosystem,
CVE, installed/fixed version, vendor severity, status, title, advisory URL và
references từ Trivy. Vendor fix trong DB là **candidate**, không mặc nhiên là
artifact có thể tải hoặc tương thích. JSON gốc đầy đủ nằm trong artifact CI.

## C–D. Remediation đã thực hiện và kiểm chứng version

| Thành phần | Baseline | Thay đổi | Lý do / giới hạn |
| --- | --- | --- | --- |
| Java runtime | Temurin 17.0.12_7 / Jammy | 17.0.20.1_1 / Jammy, pin digest | Giữ Java 17 và Ubuntu 22.04; bỏ runtime/OS cũ, không đổi major |
| Java builder | Maven 3.9.9 / Temurin 17 | Pin digest của chính dòng builder | Giảm tag drift; không khẳng định builder không còn CVE |
| Spring Boot | 3.3.4 | 3.3.13 | Cập nhật đồng bộ BOM trong dòng 3.3, vẫn còn CVE cần coordinated upgrade |
| Tomcat embedded | 10.1.42 sau BOM patch | 10.1.61 | Vá critical trong dòng 10.1, giữ Servlet API và Java 17 |
| PostgreSQL JDBC | 42.7.7 sau BOM patch | 42.7.12 | Cùng dòng 42.7; vá CVE-2026-42198 và CVE-2026-54291 được scanner báo |
| Python runtime | 3.12.12 / Bookworm | 3.12.15 / Bookworm, pin digest cả hai stage | Không đổi Python minor, không thay model hoặc dependency để né findings |
| PostgreSQL server | 16.6 / Bookworm | 16.15 / Bookworm, pin digest | Không đổi major/data format; test với database mới, không nâng DB của người dùng |
| Keycloak | 26.0.7 | 26.0.8, pin digest | Patch cùng dòng 26.0; **không coi đây là bản vá cho các CVE 2026 còn lại** |
| APISIX | 3.11.0 Debian 11 | 3.11.0 Red Hat + hai vendor RPM khoá hash | Giữ APISIX version, loại OS EOS; vá Unbound critical, kiểm chứng lại routing/JWT/healthcheck |
| Evidence | Chỉ scan summary | Full JSON + exporter stdlib | Giữ nguyên G9; không ignore CVE, không bỏ image, không dùng ignore-unfixed |

Các version image đã được kiểm tra bằng registry manifests, các version JAR
bằng Maven Central POM. Các manifest/tag và digest chính xác nằm trong diff
Dockerfile/Compose và evidence JSON. **10.1.58 được scanner nêu là fixed nhưng
POM trả 404**; không chọn version này. Metadata Maven Central và POM 10.1.61
đã được kiểm tra, sau đó chạy lại regression và scan.

Nguồn vendor/primary đã đối chiếu:

- Tomcat advisories: https://tomcat.apache.org/security-10.html
- pgJDBC 42.7.12: https://jdbc.postgresql.org/download/
- pgJDBC advisory: https://github.com/pgjdbc/pgjdbc/security/advisories/GHSA-j92g-9f8w-j867
- Keycloak release: https://www.keycloak.org/2025/01/keycloak-2608-released
- Official APISIX 3.11 Red Hat Dockerfile:
  https://raw.githubusercontent.com/apache/apisix-docker/release/apisix-3.11.0/redhat/Dockerfile
  dùng UBI8 và cùng `APISIX_VERSION=3.11.0`; không đổi sang APISIX major/minor mới.
- Vendor Unbound RPM: public Red Hat UBI AppStream CDN. Cả `unbound-libs`
  và `python3-unbound` version `1.16.2-5.14.el8_10.4` đã tải thành công cho
  x86_64/aarch64 và khoá SHA-256 trong `infra/apisix/Dockerfile`. `yum` chỉ
  install hai file đã kiểm hash với **mọi repo bị disable**, không kéo theo
  một loạt package version không khoá. Đây là vá CVE-2026-81642 (hai package
  occurrences), không khẳng định APISIX đã expose Unbound DNSSEC code path.
- Advisory, vendor severity và references riêng cho từng CVE: inventory CSV,
  không thay thế advisory của vendor bằng kết luận chỉ dựa vào NVD severity.

Đánh giá impact của nhóm đã vá: Tomcat thực sự là HTTP runtime, nhưng một số
critical cần HTTP/2 hoặc container DIGEST/FORM authentication. Lab không cấu
hình HTTP/2 và Core dùng Spring JWT resource server, không dùng container
DIGEST/FORM; không gọi số finding bị loại là số auth-bypass khai thác được.
JDBC có code path kết nối DB thật, nhưng CVE SCRAM-PLUS/channel-binding không
chứng minh đã có MITM trong lab không cấu hình verified TLS. Patch không thay
thế việc chốt TLS/channel-binding và threat model cho production.

## E. Trivy trước/sau

Final runtime SHA: `207b9ace6daf24e678e9fa35b49966997353571d`.
Workflow: https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/actions/runs/39121326727
Artifact ID `12134154564`, archive SHA-256 `57665913fa1b794a8cc92553ba42799f9d5c407276145e4c6e3cab1b06855b16` đã đối chiếu với GitHub.
Trivy 0.76.0 và cả hai DB UpdatedAt khớp baseline; DownloadedAt của từng run nằm trong evidence JSON.

| Image | HIGH trước → sau | CRITICAL trước → sau | Có candidate fix trước → sau | OS EOS trước → sau | G9 sau |
| --- | --- | --- | --- | --- | --- |
| Java Core | 39 → 14 | 10 → 2 | 49 → 16 | NO → NO | FAIL |
| Python Intelligence | 91 → 61 | 9 → 2 | 37 → 0 | NO → NO | FAIL |
| PostgreSQL | 211 → 95 | 24 → 4 | 161 → 25 | NO → NO | FAIL |
| Keycloak | 89 → 89 | 7 → 7 | 96 → 96 | NO → NO | FAIL |
| APISIX | 97 → 110 | 7 → 0 | 34 → 74 | YES → NO | FAIL |
| Tổng occurrences | 527 → 369 | 57 → 15 | 377 → 211 | 1 → 0 | FAIL |

**APISIX HIGH tăng 97 → 110**, không che giấu bằng tổng giảm. Variant chuyển distro và vendor advisory source (Debian → Red Hat), nên không suy ra mọi CVE giảm đã được vá. Lựa chọn này xử lý OS EOS; lớp RPM sau đó loại 20 HIGH và 2 CRITICAL occurrences Unbound so với official Red Hat variant, giữ APISIX 3.11.0. 110 HIGH còn lại vẫn làm G9 FAIL.

Phân tách findings của image cuối:

| Image | Ecosystem | HIGH | CRITICAL |
| --- | --- | --- | --- |
| Java Core | os-pkgs / ubuntu | 0 | 0 |
| Java Core | lang-pkgs / jar | 14 | 2 |
| Python Intelligence | os-pkgs / debian | 61 | 2 |
| Python Intelligence | lang-pkgs / python-pkg | 0 | 0 |
| PostgreSQL | os-pkgs / debian | 71 | 3 |
| PostgreSQL | lang-pkgs / gobinary | 24 | 1 |
| Keycloak | os-pkgs / redhat | 21 | 0 |
| Keycloak | lang-pkgs / jar | 68 | 7 |
| APISIX | os-pkgs / redhat | 110 | 0 |

Còn 211 occurrences có candidate FixedVersion và 173 occurrences chưa có fix được DB nêu. Core cần release train Spring/Jackson/Micrometer tương thích; Keycloak cần validated minor/schema migration; PostgreSQL cần vendor gosu/Go rebuild; gateway còn 74 HIGH candidate fixes cần batch native update được khoá và regression, chưa thực hiện bulk yum update không khoá. Đây là remaining work, không phải waiver.

Số liệu là package occurrences, **không phải số CVE duy nhất hoặc số đường khai
thác**. `scan=PASS` chỉ có nghĩa scanner chạy được. `productionGate=FAIL`
không trở thành PASS vì workflow dùng `-ReportOnly`. G9 vẫn áp dụng nguyên tắc
không có HIGH/CRITICAL và không có OS EOS theo policy hiện hành.

## F. Regression và real E2E

Mỗi nhóm thay đổi runtime được chạy lại workflow tích hợp trên clean checkout,
với database, token và Compose project mới có tên theo GitHub run ID. Workflow
kiểm tra project/volume chưa tồn tại trước khi start; cleanup chỉ stop container
của run đó. Không reset database hoặc xoá volume người dùng.

- Vòng 1: `848d64d9c76b8716b016e341de37e8f421340090`, run `39117947321`:
  G1–G8/G10 PASS, G9 FAIL; full JSON inventory đã được giữ.
- Vòng 2 (Tomcat/JDBC): `7cbc0c9c0db8ddc84e537901ee69b0db05fa5a3c`,
  run `39119585390`; kết quả cuối ghi trong evidence JSON.
- Vòng 3 (APISIX variant): `aabb19472625475449a3f9676186b6305c2f6c3f`,
  run `39119831630`; kết quả cuối ghi trong evidence JSON.
- Vòng multi-arch builder pin và exporter self-check:
  `ebffc039c7036ddf94d1588c4015c49873bd2666`, run `39120080272`.
- Vòng RPM patch đầu: `881cdbfd4c8fe49a5709e6cc252690b0d9b1acec`,
  run `39120938074` **FAIL ở gateway build vì image không có wget**.
  G1–G5 PASS; G6–G10 BLOCKED, không có scan hợp lệ và không lấy zero count
  làm kết quả sạch. Sửa dùng curl đã có trong package inventory ở commit
  `207b9ace6daf24e678e9fa35b49966997353571d`, sau đó chạy lại toàn bộ gate.
- Vòng cuối `207b9ace6daf24e678e9fa35b49966997353571d`, run `39121326727`: **G1–G8/G10 PASS; G9 FAIL**, năm image scan đầy đủ.
- Java/PostgreSQL: **137 tests, 0 failures/errors/skips**; Python source **265** và installed wheel **265**, đều 0 failures/errors/skips. Java → Python transport **6 tests** thuộc năm scenario; gateway **11 cases**; authorized retrieval **8 scenarios**, PASS_WITH_DOCUMENTED_CORE_LIMITS.
- Python AI-02/AI-03/HTTP: source và installed wheel, dependency check,
  build wheel, Docker build và kiểm tra package/version thực sự cài trong image.
- Java → Python: success, wrong token, unavailable, timeout, malformed.
  Timeout/malformed là fault injection **sau real Python success**, không tự
  nhận là lỗi tự nhiên từ Python. MockMvc JWT không phải Keycloak E2E.
- Keycloak/APISIX E2E riêng: login thật, JWT verification, workspace/role
  isolation, requester không đọc ticket người khác, private Python route không
  expose qua gateway, manual workflow completion khi intelligence timeout.
- Authorized Retrieval/citation: ingest và download Core thật, authorized
  search, checksum/provenance, cross-workspace deny, refreshed JWT revocation.
  Giới hạn: JWT cũ còn hiệu lực tới expiry; Core không có delete API; checksum
  corruption được inject ở local guard. Không nói đã kiểm chứng delete thật.
- Retrieval fixture chỉ là regression do team biên soạn, không phải benchmark
  độc lập. Không sửa thuật toán để làm đẹp hit-rate, không suy ra data rights.
- **Browser UI E2E chưa chạy**, không thay bằng API E2E.
- Scan local không hoàn tất do Docker daemon không còn khả dụng; không dùng
  các summary lỗi/zero count local làm evidence sạch. Bảng trước/sau và gate
  trong báo cáo chỉ lấy từ artifact CI có SHA/digest được đối chiếu.
- Runtime E2E thực hiện trên Linux amd64. RPM aarch64 đã được tải và kiểm hash,
  nhưng chưa chạy ARM64 build/E2E; không tự nhận có evidence runtime ARM64.

## G. Runtime impact, exploitability và remaining risks

Ma trận này áp dụng cho **từng occurrence** qua các cột `image`, `type`,
`package`, `package_path` trong CSV; các exception CVE quan trọng ghi riêng.
Package có trong image không đồng nghĩa code path đã reachable. Không có
exploit PoC trong phạm vi này, không đánh dấu finding là false positive hoặc
risk accepted khi chưa có evidence tương ứng.

| Nhóm inventory | Runtime / điều kiện khai thác | Remediation / remaining risk / người cần xử lý |
| --- | --- | --- |
| Core / `jar` | HTTP, JSON, Spring Security và DB client thực sự dùng; exposure qua APISIX, không publish Core port ra host | Backend owner: coordinated upgrade Spring Boot/Framework/Security/Jackson/Micrometer, giữ Java 17/Flowable và chạy đủ contract/E2E; các CVE còn lại chưa được waiver |
| Core / `ubuntu` | OS libs trong runtime image; reachability phụ thuộc JVM/native/tool invocation | DevSecOps: giữ base được pin và re-scan khi refresh; không coi OS sạch là JAR sạch |
| Intelligence / `python-pkg` | Thư viện AI/HTTP cài trong wheel runtime; endpoint token-auth, private network, nonroot/read-only/cap-drop | AI owner: giữ validation và safe local corpus; không load pickle từ input không tin cậy; không bỏ dependency đang dùng để giảm CVE |
| Intelligence / `debian` | SQLite/zlib/native libs có trong runtime; chưa chứng minh HTTP input gọi SQLite hoặc minizip | DevSecOps + AI owner: theo dõi vendor fix; không mặc nhiên kết luận RCE chỉ vì CRITICAL; finding không fix vẫn làm G9 FAIL |
| PostgreSQL / `debian` | DB không publish host port trong Compose; SQL/native extension có thể gọi native libs, cần DB credential/network | DBA + DevSecOps: vendor image refresh, least privilege/TLS cho production và staging migration plan; chưa có patch khả dụng thì giữ blocker |
| PostgreSQL / `gobinary`, `usr/local/bin/gosu` | Helper hạ quyền ở startup, không phải TLS listener của DB; compiled Go stdlib findings không tự chứng minh remote TLS exploit | DevSecOps/vendor: image chứa gosu rebuild bằng Go đã vá; không xoá helper đang dùng, không thay bằng binary không kiểm chứng |
| Keycloak / `jar` | Identity service có login/account/admin flows; loopback publish chỉ hạn chế exposure, không phải vá CVE | Identity owner: chọn supported Keycloak release và kiểm chứng upgrade schema/realm, backup/restore và rollback trên DB disposable trước khi production; không ghi đè JAR tuỳ tiện trong vendor distribution |
| Keycloak / `redhat` | OS packages trong identity image, chưa xác định từng native code path | Identity + DevSecOps: vendor-supported image update; OS EOS flag và app maintenance phải đánh giá riêng |
| APISIX / OS packages | Gateway là HTTP entry point; native crypto/system libs installed không đồng nghĩa mọi CLI/protocol được expose | Gateway owner: cùng-version official variant được regression; review application advisories/Lua/OpenResty riêng, vì OS scan không chứng minh mọi Lua/C module đã được audit |

`platform-python`, `python3-libs`, `python3-unbound` trong gateway thuộc RHEL
**OS RPM**, không phải Python 3.12 service hay `intelligence/requirements.lock`.
Không nâng system Python của distro lên 3.12 vì có thể phá package manager;
phải dùng vendor backport và đánh giá support policy của distro riêng.

Ưu tiên review remaining critical:

1. **CVE-2026-22732 / Spring Security**: headers/security policy bypass;
   scanner chỉ ra fixed 6.5.9/7.0.4, không phải patch 6.3 đang dùng. Không
   ép Spring Security minor mới vào BOM cũ rồi coi authentication đã an toàn.
   Nguồn: https://spring.io/security/cve-2026-22732 . Backend owner cần
   đánh giá eager header mitigation và coordinated supported release train.
2. **CVE-2026-47884 / Spring MVC**: XsltView path limitation/RCE. Static search
   không thấy XsltView/XSLT integration trong source/config hiện tại; điều đó
   giảm bằng chứng reachability, **không chứng minh miễn nhiễm**. Scanner nêu
   fixed 7.0.9, không tự nâng Spring major/Java để xoá finding.
   Nguồn: https://spring.io/security/cve-2026-47884 . Backend owner phải xác
   nhận vendor disposition/backport trước khi chấp nhận thay đổi dependency.
3. **Keycloak CVE-2026-18963**: reset-credentials flow/account takeover;
   scanner nêu fixed 26.4.15/26.6.6/26.7.2, ngoài patch line 26.0.
   Realm không khai báo rõ reset-password policy nên **không suy ra flow
   không thể khai thác**. Identity owner phải audit runtime realm và hạn chế
   reset/admin exposure ngay; đây là đề xuất, chưa tự đổi cơ chế auth.
4. Keycloak còn Netty/BouncyCastle/FreeMarker critical: phụ thuộc TLS/SNI,
   algorithm/certificate handling hoặc template locale path. Không kết luận
   unreachable chỉ vì local lab dùng HTTP; production TLS/theme có thể khác.
5. Python/PostgreSQL còn OS findings chưa có fixed version trong distro DB.
   Không tự build crypto/SQLite/zlib từ source hoặc đổi distro/major để né
   scanner. DBA/DevSecOps cần vendor response, staged image refresh và retest.

Production hardening vẫn chưa được phê duyệt/kiểm chứng đầy đủ: Compose là lab
(`start-dev`, realm `sslRequired=none`, password grant probe, upload unscanned
override). Không quảng bá Compose này là production-ready. Owner phải chốt TLS,
secret lifecycle, least-privilege DB, upload scanning, supported release train,
backup/restore, audit và deployment isolation trước release. Không tự bỏ test
probe hoặc thay auth flow làm baseline E2E mất giá trị.

OS `EOSL=false` không chứng minh application/dependency còn được upstream
maintain. Chưa chứng minh được maintenance entitlement của Boot 3.3/Keycloak
26.0/APISIX 3.11/Flowable 7.1 vào ngày đánh giá; đó vẫn là việc cần Backend,
Identity và Gateway owners đối chiếu support policy. Không đánh dấu chúng là
supported chỉ vì image vẫn còn trên registry. Các DB test là fresh/disposable;
không tự nhận đã kiểm chứng upgrade/rollback của database người dùng hiện hữu.

## H. Merge readiness và release readiness

- **Merge readiness:** security PR vẫn draft, chờ review. Cần #22 merge trước
  khi retarget `develop`, rebase/merge-base reconciliation và kiểm tra lại HEAD
  cuối. Technical regression PASS không tự mang ý nghĩa team đã approve merge.
- **Release readiness: BLOCKED. G9 vẫn FAIL nếu còn một HIGH/CRITICAL hoặc OS
  EOS theo policy.** Không thay policy, không ignore image/CVE, không tạo waiver.
- G0-B Business Priority Profile: **PENDING Process Owner approval**; không
  sửa PROPOSED thành APPROVED bằng technical acceptance.
- Root `LICENSE`: chưa có formal grant; `LICENSING.md` ghi intended Apache-2.0
  không phải quyền cấp phép hiện hữu. Chỉ rights holders được phê duyệt grant.
- Fixture/data usage rights: **PENDING_OWNER_APPROVAL**; synthetic regression
  không chứng minh quyền công bố corpus hoặc tài liệu bên thứ ba.
- Browser UI E2E: **NOT RUN**; cần frontend/QA owner thực hiện riêng.
- Production security gate: **FAIL / chưa được phê duyệt**; cần security và
  deployment owners xử lý, không dùng status xanh của workflow ReportOnly.
- Các owner nêu trong báo cáo là vai trò cần được team phân công, **không phải
  xác nhận họ đã nhận trách nhiệm hoặc chấp nhận remaining risk**.

## I. Delivery, files và tái lập

- Runtime commits: `848d64d` (base images/BOM/evidence), `7cbc0c9` (Tomcat/JDBC),
  `aabb194` (APISIX variant/exporter), `ebffc03` (multi-arch pin/self-check).
  `881cdbf` bổ sung vendor RPM Unbound fix có hash lock, không đổi server version.
  `207b9ac` sửa download tool cho image thực tế; evidence cuối phải thuộc commit
  này, không dùng run RPM build lỗi để chứng nhận bản sửa.
  Commit documentation cuối xem HEAD PR #23;
  evidence chỉ rõ tested runtime SHA, không tự nhận documentation SHA đã rebuild.
- Runtime/config files: `backend/Dockerfile`, `backend/pom.xml`,
  `intelligence/Dockerfile`, `infra/apisix/Dockerfile`, `compose.yaml`.
- Đồng bộ pin ở test/scan consumers: `.github/workflows/intelligence-triage-e2e.yml`,
  `.github/workflows/servicedesk-triage.yml`, `scripts/scan-core.ps1`,
  `scripts/test-core-acceptance.ps1`, `scripts/test-file.ps1`, `scripts/test-workflow.ps1`.
  `scripts/acceptance/integration-compose.yaml` cấp tag/label riêng cho gateway
  của từng disposable run để không ghi đè image dùng trong project khác.
- Báo cáo/evidence: tài liệu này, `docs/security/sec01/evidence.json`,
  `docs/security/sec01/inventory.csv`; exporter `scripts/security/export-inventory.py`.
- Exporter dùng Python stdlib, kiểm tra image identity và số occurrence theo
  summary trước khi xuất; không sửa hoặc suppress findings.

```powershell
python scripts/security/export-inventory.py --self-test
python scripts/security/export-inventory.py <extracted-artifact/scan-int01-RUN-1> <inventory.csv>
git diff --check
```

Để chạy lại regression đầy đủ, dispatch workflow `intelligence-triage-e2e.yml`
trên branch/SHA cần kiểm chứng. Giữ Compose project và cache volume mới cho mỗi
run. Dùng full JSON/scanner version trong artifact của **chính run đó**; không
trộn summary từ nhiều build. Không dùng build cache hay DB của người khác làm
bằng chứng clean-checkout reproducibility.
