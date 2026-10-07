# Báo cáo AI-01 / N1 — Data & Provenance

Ngày thực hiện: **07/10/2026, Asia/Saigon**.
Baseline: `c92b38733d00c77bd90b96318f8c70d7b19be5c0`.
Branch: `feature/ai01-data-provenance`.
Kế hoạch tham chiếu: `intelligence/.backup/AI_N1_DATA_PROVENANCE_DETAILED_PLAN.md`
(tài liệu local, không đưa vào Git).

**Kết quả: hoàn thành tài liệu và kiểm chứng trong phạm vi AI được phép;
chưa đạt toàn bộ Definition of Done N1 vì còn thay đổi DX core và review của team.**
Theo yêu cầu của chủ repository, không sửa trực tiếp DX core trước review. Các điểm
cần trao đổi nằm trong báo cáo này; không tạo thư mục/tài liệu review riêng.

## Phần đã bàn giao

| Artifact | Kết quả |
| --- | --- |
| [Data/provenance contract](data-provenance-contract.md) | Quyền sở hữu từng field, identity ổn định, immutable API, source/version/rights/checksum, workspace isolation, audit và ranh giới Intelligence. Là dự thảo, chưa có BE ratify. |
| [API acceptance probe](../tests/metadata-provenance.ps1) | 53 assertion chạy thật qua APISIX, dùng JWT của demo account hiện có; không sửa Core/realm. |
| [Audit acceptance SQL](../tests/check-metadata-audit.sql) | Transaction read-only; đối chiếu đúng một audit với metadata và request ID từ HTTP response. |
| [Hướng dẫn chạy](../README.md) | Lệnh tái chạy, vị trí evidence, tác động tạo dữ liệu synthetic qua API. |

Không thêm model, FastAPI, parser, chunking, vector/index, File I/O hay dependency
ứng dụng. Không sửa backend, migration V1, OpenAPI, Compose, infra, script Core hay
requirements mapping. File `intelligence/.gitignore` có sẵn được giữ nguyên,
không đưa vào các commit của công việc này.

## Kiểm tra đã chạy

| Lệnh / kiểm tra | Kết quả thực tế |
| --- | --- |
| `git status --short` trước triển khai | Chỉ có `intelligence/.gitignore` untracked; nội dung `.backup/`, nguồn gốc rõ, giữ nguyên. |
| `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start.ps1` | PASS, exit 0; build từ backend hiện tại, bốn container healthy. Các bước Maven trong build dùng cache. |
| `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke.ps1` | PASS, exit 0; identity, metadata, isolation, hai workflow thực và concurrent completion. |
| `powershell -NoProfile -ExecutionPolicy Bypass -File intelligence/tests/metadata-provenance.ps1` | PASS, exit 0; **53/53 assertion**. |
| SQL audit mới, truyền ID/owner/workspace/request ID từ JSON evidence | PASS, đúng một `metadata.create`, đầy đủ timestamp và khớp context. |
| SQL audit mới với request ID cố ý sai | PASS kiểm tra nhánh lỗi: từ chối và trả exit code khác 0. Đã sửa cách thoát lỗi của checker trước commit vì psql 16 bỏ qua tham số `\quit 1`. |
| `Get-Content scripts/check-db.sql -Raw \| docker compose exec -T postgres psql -U core -d core -v ON_ERROR_STOP=1` | PASS consistency metadata/audit và engine/scope. |
| `docker build --target build -t dx-core-ai-n1-test:0.1.0 backend` | PASS; build stage hiện có, không thay dependency/source. |
| `docker run --rm dx-core-ai-n1-test:0.1.0 mvn -B -o test` | PASS; **3 tests, 0 failures/errors/skips**. Test được chạy lại trong container; đây là Security helper tests có sẵn, không phải MetadataApi regression tests. |
| OpenAPI validation | SKIP: cả Python mặc định và Python311 được ghi trong baseline cũ đều thiếu module `openapi_spec_validator`. Không cài thêm; không đổi OpenAPI. |
| `git diff --check` và staged diff check | PASS. |
| Diff baseline với `backend`, `contracts`, `infra`, `scripts`, `docs`, `compose.yaml` | Không có thay đổi. |

Không có `mvn` trên PATH của host; Maven được chạy trong build container Java 17
theo Dockerfile hiện có. Đây là kiểm chứng local trên dữ liệu/volume đang có,
không phải clean clone hoặc independent clean run.

## Coverage provenance / security

| Matrix kế hoạch | Evidence / giới hạn |
| --- | --- |
| META-01 | POST 201 bằng member có cả writer + reader. **Writer-only chưa kiểm thử** vì realm hiện tại không có account đó; không tự sửa realm để tạo fixture. |
| META-02/03 | Owner khớp `/me.sub`, workspace khớp route; body owner/workspace/id/created_at bị từ chối 400. |
| META-04/05/06 | Reader write 403; foreign workspace write và direct read 403. |
| META-07 | Member lab-b lookup ID của lab-a trong route lab-b nhận 404; ID không tồn tại cũng 404. |
| META-08 | Blank, missing, oversized title/source/version/usageRights đều 400. |
| META-09/10 | Checksum thiếu, blank, ngắn/dài, uppercase, non-hex đều 400. |
| META-11 | Owner và unknown property bị từ chối 400. |
| META-12 | DB read-only xác nhận đúng một audit cho record thành công và request ID khớp HTTP. **Chưa có failure-injection chứng minh rollback khi audit lỗi hoặc writer-only bị từ chối.** |
| META-13 | Create/read giữ nguyên cả 9 field response và toàn bộ 5 giá trị caller cung cấp; reader khác trong cùng workspace đọc được. |

JWT thiếu/sai nhận 401. Checksum N1 chỉ là **caller-declared SHA-256 trên exact
source bytes**, Core chưa xác minh file integrity. Metadata UUID là identity ổn
định; title/source/checksum không thay thế identity. API chỉ create/read, không có
update; immutability ở đây là ở public API, không phải database-level enforcement.

Evidence chi tiết không chứa token/password:
`.local/ai-n1/metadata-provenance.json` (Git ignored).
Lần chạy API: **23:19:54–23:19:59 +07:00**.
Metadata synthetic: `529e4be2-53f5-4b45-84f4-aed6c2d82c0c` trong `lab-a`.
Request ID: `55c22e0e-1fa2-425b-9847-f053c70b9ff5`.
Một record synthetic được tạo bởi probe và giữ lại để team kiểm tra; smoke baseline
cũng tạo dữ liệu demo như thiết kế của script. Không xóa/reset dữ liệu.

## Phần DX core cần team trao đổi trước khi triển khai

1. **POST đang phụ thuộc quyền GET.** Trong `MetadataApi.java`, `create()` gọi
   `get()` sau insert/audit, nên cần thêm `core-reader`. Đề xuất chốt POST = member
   + writer, GET = member + reader; tách private loader dùng nội bộ, giữ transaction.
   Hiện OpenAPI ghi cả hai role là đúng với baseline, chưa đổi sang policy đề xuất.
2. **Response đang là raw DB row.** Thay `SELECT *` bằng đúng 9 cột explicit và
   response DTO/mapper. Giữ nguyên `usage_rights`, `created_at` trên wire; UUID và
   timezone timestamp do server cung cấp. Không cần sửa schema/V1.
3. **Core regression tests còn thiếu.** Thêm `MetadataApiTest.java` bằng dependency
   có sẵn: MVC validation/unknown fields, workspace/role, provenance round trip,
   stable response fields. Bổ sung real-service evidence cho rollback; mock tests
   không chứng minh transaction DB.
4. **Đồng bộ sau thay đổi.** BE tích hợp OpenAPI, FE phối hợp smoke/signed writer-only
   fixture; promote contract sang `docs/architecture/data-provenance-contract.md`
   và cập nhật requirements mapping đúng phạm vi Data/File/Audit vẫn thuộc Core.

Đây là các thay đổi chưa thực hiện. Team tự trao đổi review; báo cáo không ghi nhận
BE/FE đã phê duyệt. Không merge/push, không đánh dấu N1 completed hoặc chuyển N2.

## Commit từng phần

| Commit | Nội dung |
| --- | --- |
| `1cf9465` — `docs(ai-n1): define metadata provenance contract` | Contract draft. |
| `049bb95` — `test(ai-n1): verify metadata provenance and audit through core APIs` | API/SQL acceptance probes và hướng dẫn chạy. |
| `docs(ai-n1): report acceptance evidence and remaining core work` | Báo cáo này; hash xem trong Git log. |

## Gate còn lại và công việc deferred

N1 chỉ DONE khi team ratify contract/role, Core hardening và Metadata regression
tests được triển khai, acceptance chạy lại xanh và BE review hoàn tất. Các field
AI không được thêm vào Core; future Intelligence chỉ consume dữ liệu Core đã
authorize và giữ derived state có thể rebuild từ Core + AI configuration version.

File upload/download, server checksum verification, metadata list/search/update,
audit read/retention và Intelligence runtime vẫn thuộc ngày/phase sau.
