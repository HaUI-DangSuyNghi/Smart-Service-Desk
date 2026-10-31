# data (khối D)

| Thư mục      | Trang                          | Quyền                                        | Nội dung                                                                                                         |
| ------------ | ------------------------------ | -------------------------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| `knowledge/` | `/knowledge`, `/knowledge/:id` | `document:view` (tải lên: `document:upload`) | Knowledge Hub: danh sách, tìm kiếm, provenance (qua `GET /knowledge/{id}/record`), xem nội dung, tải về          |
| `dashboard/` | `/dashboard`                   | `dashboard:view`                             | Thẻ số liệu, cột yêu cầu mới theo ngày (có dạng bảng), bảng theo quy trình, khối số liệu do app đăng ký          |
| `audit/`     | `/audit`                       | `audit:view`                                 | Nhật ký cả workspace, lọc theo thời gian, thao tác, người; dùng chung `describeActivity` với "Hoạt động của tôi" |

## Dashboard

- Số liệu từ `GET /hpdi/v1/workspaces/{ws}/processes/statistics?from&to&zone`. "Mới / Đang chạy / Đã kết thúc" tính trên yêu cầu **tạo trong khoảng**; "Chờ nhận / Đang xử lý" là **hiện tại**.
- Khoảng thời gian tính theo giờ máy người xem (`lastDays`), gửi kèm múi giờ để backend chia theo ngày đúng.
- App góp khối số liệu: khai báo `dashboardWidgets` khi `registry.register(...)`; `App.tsx` gọi `configureDashboardWidgets(registry.dashboardWidgets())`. Widget nhận `{ from, to }` và tự gọi API của app (ví dụ `apps/servicedesk/dashboard/TicketStatsWidget`).
