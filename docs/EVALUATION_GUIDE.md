# Hướng dẫn đánh giá Smart Service Desk

## Bắt đầu trong 3 phút

Smart Service Desk giải quyết bài toán tiếp nhận và điều phối hỗ trợ CNTT trong
khoa, phòng máy và đơn vị đào tạo. Người yêu cầu, điều phối viên và kỹ thuật viên
cùng theo dõi một vòng xử lý có phân quyền, tiến độ và lịch sử.

1. Đọc [giới thiệu, ảnh và kiến trúc](../README.md).
2. Theo [kịch bản demo 10 phút](DEMO_EVALUATION.md) để quan sát một vòng xử lý.
3. Tra bảng bên dưới để đối chiếu tính năng với code, hợp đồng và minh chứng.
4. Kiểm tra [hồ sơ phát hành v1.0.0](publication/v1.0.0/README.md) và checksum.

Đây là bản đồ bằng chứng, không phải bảng tự chấm điểm hay rubric chính thức.
Nhóm tiêu chí PoF tham chiếu [hồ sơ đã ghi nhận](publication/POF_EVIDENCE.md);
quy định và cách tính điểm của ban tổ chức được ưu tiên nếu có khác biệt.

## Phiên bản và cách đọc bằng chứng

- Bản phát hành: `v1.0.0`, commit `f845ff3dd0e2e49cb2a94608daaf365777f58877`.
- Tài liệu này bổ sung sau phát hành; không di chuyển tag hay thay đổi mã nguồn tag.
- Có file code/test chứng minh có triển khai hoặc có bài kiểm tra, chưa chứng minh
  bài kiểm tra đã chạy thành công. Log phải ghi commit, lệnh và môi trường thực chạy.
- Log v0.1.0 và các báo cáo có ngày/SHA riêng chỉ có giá trị trong phạm vi đã ghi;
  không tự chuyển kết quả sang v1.0.0. Demo dưới đây là kế hoạch, chưa phải biên bản PASS.

## Bản đồ tính năng và kiến trúc

Các liên kết trỏ tới file trong checkout đang đọc. Khi đối chiếu bản phát hành,
chuyển trình duyệt GitHub sang tag `v1.0.0` hoặc checkout đúng commit ở trên.

| Nội dung cần quan sát | Phần triển khai / hợp đồng | Bằng chứng và cách kiểm tra |
| --- | --- | --- |
| Bài toán và giá trị thực tế | [Vòng đời yêu cầu](request-lifecycle.md), [controller ticket](../backend/src/main/java/vn/haui/dx/apps/servicedesk/TicketController.java) | Demo bước 1–6: giữ cùng mã ticket xuyên suốt ba vai trò; đối chiếu tiến độ sau tải lại trang |
| Core độc lập và kiến trúc ba tầng | [Ranh giới các tầng](architecture/tier-boundary-map.md), [Core OpenAPI](../contracts/openapi/core.yaml), [H-P-D-I OpenAPI](../contracts/hpdi/openapi.yaml) | Đối chiếu API và đường gọi; sơ đồ là bản đồ thiết kế, không thay thế việc đọc code |
| Định danh và phân quyền workspace | [Realm template](../infra/keycloak/realm-template.json), [kiểm tra ticket](../backend/src/test/java/vn/haui/dx/apps/servicedesk/TicketIntegrationTest.java) | Dùng tài khoản riêng; kiểm tra API bằng tài khoản không có quyền. UI ẩn nút không đủ chứng minh bảo vệ API |
| Quy trình và lịch sử xử lý | [BPMN review](../backend/src/main/resources/apps/servicedesk/process/service-desk-reviewed.bpmn20.xml), [BPMN intake](../backend/src/main/resources/apps/servicedesk/process/service-desk-intake-reviewed.bpmn20.xml), [Service Desk OpenAPI](../contracts/openapi/servicedesk.yaml) | Demo duyệt → giao → xử lý → xác nhận hoặc làm lại; kiểm tra tác nhân và trạng thái lịch sử |
| Khả năng dùng lại cho ứng dụng thứ hai | [Room Request controller](../backend/src/main/java/vn/haui/dx/apps/roomrequest/RoomRequestController.java), [BPMN đặt phòng](../backend/src/main/resources/apps/roomrequest/process/room-request.bpmn20.xml) | Demo bước 7; đối chiếu Core/H-P-D-I được dùng chung và phần nghiệp vụ riêng, không chỉ dựa trên tên thư mục |
| Dữ liệu và thư viện tri thức | [Knowledge controller](../backend/src/main/java/vn/haui/dx/hpdi/data/web/KnowledgeController.java), [hướng dẫn Knowledge Chat](KNOWLEDGE_CHAT.md) | Với tài liệu mẫu được phép sử dụng: mở thư viện, xem trước/tải theo quyền; AI hỏi đáp là phần tùy cấu hình |
| Intelligence có ranh giới, con người quyết định | [Intelligence](../intelligence/README.md), [triage](../intelligence/src/hdpi/services/triage.py) | Công bố chế độ đang chạy; không trình bày thao tác thủ công hay kết quả mẫu như kết quả mô hình trực tiếp |
| Trải nghiệm web và lịch điều phối | [Calendar](../frontend/src/platform/process/inbox/calendar/CoordinationCalendar.tsx), [Playwright calendar](../frontend/e2e/coordination-calendar.spec.ts), [Playwright lifecycle](../frontend/e2e/request-lifecycle.spec.ts) | Kiểm tra màn hình và kích thước thật. Các bài UI dùng fixture/mock không chứng minh E2E backend/Keycloak |

## Bản đồ hồ sơ nguồn mở / PoF

| Nhóm nội dung | Nơi đối chiếu | Phạm vi / việc còn cần xác minh |
| --- | --- | --- |
| Repository và đóng góp | [Nhóm phát triển](../README.md#nhóm-phát-triển), [lịch sử phát triển trong PoF](publication/POF_EVIDENCE.md) | Đối chiếu commit/PR; không dùng số commit để suy ra công sức hoặc điểm cá nhân |
| Giấy phép và tài sản | [LICENSE](../LICENSE), [LICENSING](../LICENSING.md), [manifest tài sản](publication/release-distribution-status.csv) | Apache-2.0 có phạm vi; xác nhận của trường cần được lưu đúng nội dung và thẩm quyền, không tự coi tin báo là giấy phép toàn bộ |
| Phiên bản và phát hành | [CHANGELOG](../CHANGELOG.md), [minh chứng v1.0.0](publication/v1.0.0/README.md) | Đối chiếu tag/commit và thời điểm phát hành; tài liệu bổ sung không thay đổi tag |
| Cài từ source | [Cài đặt](../README.md#cài-đặt), [xác minh lịch sử](publication/pof/FINAL_VERIFICATION.md) | Cần thực chạy đúng bản, ghi môi trường và mọi override; không suy ra default stack đã PASS từ log bản cũ |
| Dependencies và nguồn gốc | [SBOM, source-inputs và SHA256SUMS](publication/v1.0.0/README.md) | SBOM frontend production, 216 thành phần; không phải SBOM toàn hệ thống hoặc chứng nhận không có lỗ hổng |
| Hướng dẫn và cộng đồng | [CONTRIBUTING](../CONTRIBUTING.md), [SECURITY](../SECURITY.md), [CODE_OF_CONDUCT](../CODE_OF_CONDUCT.md) | Đối chiếu issue thực tế và tiêu chí nghiệm thu; không đóng issue chỉ để làm đẹp hồ sơ |

## Tái kiểm tra và ghi nhận

Trong checkout v1.0.0 sạch, có Node/npm phù hợp với package.json:

```powershell
git rev-parse HEAD
Set-Location frontend
npm ci
if ($LASTEXITCODE -ne 0) { throw 'Install failed' }
npm run lint
if ($LASTEXITCODE -ne 0) { throw 'Lint failed' }
npm test -- --maxWorkers=1 --no-file-parallelism
if ($LASTEXITCODE -ne 0) { throw 'Unit tests failed' }
npm run build
if ($LASTEXITCODE -ne 0) { throw 'Build failed' }
```

Các lệnh là hướng dẫn tái kiểm tra, không phải kết quả đã chạy trong lần cập nhật
này. Kiểm tra backend và tích hợp xem [Core acceptance](core-acceptance.md) và
[runbook kỹ thuật](DEMO_RUNBOOK.md): đọc điều kiện tiên quyết và SHA lịch sử trước
khi sử dụng; không chạy script tạo dữ liệu trên hệ thống đang phục vụ người dùng.

Với mỗi kết quả, ghi: commit thực chạy, ngày giờ/múi giờ, phiên bản công cụ,
cấu hình/override đã che bí mật, lệnh, exit code và log. Ghi FAIL/NOT RUN đúng
thực tế, kèm issue hoặc giải thích; không công bố token, mật khẩu hay dữ liệu thật.
