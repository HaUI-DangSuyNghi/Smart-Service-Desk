> Historical evidence before the three-tier extraction. Package/profile paths in recorded commands refer to that baseline; current commands and ownership are in the Intelligence README and ADR-005.

# AI-03 — Bằng chứng tích hợp Core HTTP

Ngày: 17/10/2026. Trạng thái: **INTEGRATION VERIFIED — REVIEW PENDING**.
Core baseline: `e39356cc44ef50421a9b81199756ec9784fe161a`.
AI runtime cuối đã kiểm chứng: `34d91019900499a701b0100e184e6a8ec9c8a3cd`.
Package cài riêng: `smart-service-intelligence==0.3.0`, Python 3.12.7.
PR: [#19](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/pull/19), base `develop`.
Các commit tài liệu/CI tiếp theo không được coi là đã chạy lại Core acceptance
nếu không có bằng chứng mới tương ứng; trạng thái CI được cập nhật ở runtime report.

## Môi trường và lệnh thực thi

Docker Desktop Linux, Compose 2.40.3. Project riêng `dx-ai03-hardening` có
PostgreSQL 16.6, Keycloak 26.0.7, backend Core build từ baseline hiện tại và
APISIX 3.11.0. Volume acceptance tách khỏi project `dx-core`. Keycloak bind
18081, gateway bind 19080, chỉ loopback. Issuer của Core được cấu hình khớp
`http://localhost:18081/realms/dx-lab`; không tắt kiểm tra chữ ký/audience/issuer.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/setup.ps1
docker compose -p dx-ai03-hardening -f compose.yaml -f scripts/acceptance/intelligence-compose.yaml up -d --build --wait --wait-timeout 300
.local/ai03-venv/Scripts/python -I scripts/acceptance/intelligence-retrieval.py --output .local/ai03-hardening-core-final.json
```

Runner đọc credentials local từ `.env`, không in token/mật khẩu và không commit
file đó. Agent demo hiện có upload/create qua Core API. Principal requester test
riêng được tạo qua Keycloak Admin API bằng tài khoản admin local đã cấu hình,
không cấp credentials production cho Intelligence. Adapter runtime chỉ GET Core
Me/Metadata/File API; không truy cập DB Core/Keycloak/Flowable.

Không dùng 5 knowledge fixture còn chờ quyền. Nội dung acceptance là hai đoạn
synthetic độc lập, ghi rõ `INTERNAL_SYNTHETIC_ACCEPTANCE_ONLY`, không phải corpus
release hoặc quyết định license. Principal tạm đã xóa; metadata/file test còn
trong volume riêng vì Core không cung cấp API xóa. Không xóa volume hiện có.
Sau kiểm chứng đã dừng cả project acceptance và stack local được bật trong phiên;
không để container tiếp tục chạy và không xóa volume.

## Kết quả thực tế

Tổng: **8/8 PASS với giới hạn Core được ghi rõ**. Report JSON private tại
`.local/ai03-hardening-core-final.json`, trạng thái
`PASS_WITH_DOCUMENTED_CORE_LIMITS`, `temporaryIdentityRemoved=true`.

| ID | Kết quả đã thực thi | Giới hạn phép kiểm |
| --- | --- | --- |
| CORE-AI03-01 | PASS: đăng nhập thật, upload File API, tạo Metadata API, adapter tải bytes, build index, search, verifier đọc lại nguồn | Đối chiếu workspace/UUID/version/checksum/excerpt; không dùng FakeCore |
| CORE-AI03-02 | PASS: principal lab-b bị Core từ chối metadata lab-a; SearchService từ chối trước engine | ObservedEngine ghi số lần scoring bằng 0 |
| CORE-AI03-03 | PASS: tháo group của principal test ở Keycloak, lấy JWT mới, search bị từ chối trước engine | Không chứng minh token cũ bị thu hồi tức thời |
| CORE-AI03-04 | PASS: tạo metadata/file version v2 mới, đổi catalog revision/UUID; context/index v1 không được chọn hoặc score | Core metadata bất biến, không có API sửa record v1 tại chỗ |
| CORE-AI03-05 | PASS: checksum sai làm AuthorizedDocument bị từ chối với bytes vừa tải từ Core thật | Lỗi được tiêm ở guard local; không sửa DB/storage server |
| CORE-AI03-06 | PASS: Core trả 404 cho UUID không tồn tại; resolver trả None, không bịa citation | Không mô phỏng xóa metadata đã tồn tại vì Core không có delete API |
| CORE-AI03-07 | PASS: authority loopback không lắng nghe gây CorpusUnavailable, DEGRADED mặc định tắt | Không dừng dịch vụ Core đang chạy; đây là lỗi transport thực |
| CORE-AI03-08 | PASS: AI-02 triage vẫn SUCCESS sau khi retrieval authority không truy cập được | Triage độc lập, không cấp quyền truy xuất cho principal đã bị revoke |

Correlation an toàn: `ai03-e910159f9c874f1aa8e3afa8553ec557`.
Core cấp metadata v1 `b3fa755b-c2ca-47f9-b28d-f39d480bdb15`, v2
`24c827d3-a13b-4373-9ae9-8bdb674c68b3`. Source checksum v1:
`9d7930280228d79e50d0d98206c77835f8196d25d73517e811e0f51b6898c244`.
Không ghi UUID này vào fixture manifest; chỉ là record test trong môi trường riêng.

Một lần search có xác minh Core ở lần cuối đo được **175,985 ms** (lần trước
212,645 ms). Con số gồm các HTTP read
scope/citation và lexical search, chỉ trên một tài liệu rất nhỏ ở máy local;
không phải SLA, percentile production hoặc phép đo tải đồng thời.

## Các lỗi môi trường đã xử lý

Stack `dx-core` cũ trả invalid_grant với demo password trong `.env`; không thay
mật khẩu hoặc reset realm/volume đó. Dựng project riêng bằng cấu hình hiện có.
Ở lần đầu đổi port, Core vẫn nhận issuer mặc định 8081 nên trả 401; override
`JWT_ISSUER` đúng 18081 khắc phục, giữ nguyên kiểm tra issuer. Lần chạy qua
`localhost` còn gặp thời gian kết nối IPv6 fallback và admin token hết hạn;
runner dùng IPv4 loopback và refresh admin token trước revoke/cleanup. Principal
tạm từ lần thất bại đã được dọn riêng, không đụng user demo.

## Gate còn chờ

Job `core-integration` đã PASS trên GitHub ở runtime cuối
`34d91019900499a701b0100e184e6a8ec9c8a3cd`,
[Intelligence runtime #5](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/actions/runs/39048305509).
Đây là lần chạy stack Core/Keycloak thật trên runner Ubuntu, không phải unit HTTP
simulation. Các lần chạy trên HEAD mới hơn được ghi trong runtime report/PR.

BE phải duyệt [scope contract](../../architecture/access-scope-contract.md),
thời gian thu hồi JWT và semantics 404 che quyền. Core kiểm membership từ JWT
đã ký; adapter không tự truy vấn Keycloak để áp dụng quyền khác Core. Phát hiện
nhanh thay đổi group trên token cũ cần quyết định/triển khai từ BE/identity.
DEGRADED mặc định tắt, chưa coi là được phê duyệt. Không có actual delete/update
test cho endpoint Core chưa tồn tại. Các giới hạn này ngăn tuyên bố APPROVED/DONE
cho production; không cản review phần adapter/acceptance đã triển khai.
