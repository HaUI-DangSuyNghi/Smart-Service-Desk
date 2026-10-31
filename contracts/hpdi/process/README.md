# Process contract

ProcessCapability starts app-owned requests and returns ProcessHandle/ProcessTimeline. WorkInbox carries processKey, businessKey, assignment and timestamps. ProcessContribution registers app policies; ProcessObservations provides authorized batch technical state without engine tables. Apps own state classifications.

Public Java API: `vn.haui.dx.hpdi.process.api`. REST projections: [shared specification](../openapi.yaml). Existing error codes and visibility semantics are preserved.

## Danh mục quy trình và yêu cầu của tôi

| Endpoint | Dùng cho |
|---|---|
| `GET /workspaces/{ws}/processes` | Giao diện chung đọc nhãn tiếng Việt, kết quả hợp lệ theo bước, biến khởi tạo, `canStart` |
| `POST /workspaces/{ws}/processes/{key}/instances` | Tạo yêu cầu từ form chung; chỉ quy trình `selfService`, `businessKey` do server sinh |
| `GET /workspaces/{ws}/instances` | Yêu cầu người gọi đã tạo, mọi quy trình, mới nhất trước |

App khai báo nhãn và cờ tự phục vụ ngay trong `ProcessDefinition`:

```java
new ProcessDefinition("equipment-approval", starters, Set.of("title"), outcomes, Set.of(), Set.of("coordinator"))
    .allowSelfService()                       // chỉ khi quy trình không gắn bản ghi nghiệp vụ của app
    .withLabels(ProcessLabels.named("Mượn thiết bị")
        .task("approve", "Duyệt yêu cầu")
        .outcome("approved", "Duyệt")
        .variable("title", "Thiết bị cần mượn"));
```

- Nhãn đặt ở Java, **không sửa `name` trong BPMN**: sửa BPMN sinh phiên bản mới, instance đang chạy bị khóa.
- Key nhãn sai (bước, kết quả, biến không tồn tại) làm ứng dụng báo lỗi lúc khởi động.
- Thứ tự khai báo nhãn là thứ tự hiển thị (ví dụ nút Duyệt đứng trước Từ chối).
- Biến khởi tạo là chuỗi: `.variable` là ô một dòng (≤ 200 ký tự), `.longVariable` là ô nhiều dòng (≤ 2000).
  Trang tiến độ (`GET /instances/{id}/timeline`) trả lại nội dung đã nhập trong `variables`.
- Quy trình như ticket phải tạo qua API của app (bản ghi và process ra đời cùng lúc), nên không bật `allowSelfService()`.

## Giao việc, tất cả việc, tất cả yêu cầu, thống kê

| Endpoint | Quyền | Ghi chú |
|---|---|---|
| `GET /inbox?scope=ALL` | `task:assign` | Mọi việc đang mở trong workspace (Core: chỉ coordinator) |
| `POST /tasks/{id}/assign` `{assignee}` | `task:assign` | Chỉ giao cho người thuộc nhóm xử lý của bước; không giao cho người tạo yêu cầu; bước giao thẳng người tạo không giao lại được; ghi audit `task.assign` |
| `GET /instances?scope=mine` / `scope=all` | `request:create` / `request:view-all` | `all`: mọi yêu cầu mà vai trò của người gọi là reader của quy trình |
| `GET /processes/statistics?from&to&zone` | `dashboard:view` | Khoảng tối đa 366 ngày; Core: chỉ coordinator |
| `GET /inbox?scope=ASSIGNED|CANDIDATE`, `POST /tasks/{id}/claim` | `task:handle` | |
| `POST /tasks/{id}/complete` | — | Core chỉ cho người đang giữ việc (kể cả người tạo xác nhận kết quả) |

Audit `task.claim`, `task.complete`, `task.assign` ghi kèm `processKey` để nhật ký hiện tên quy trình.
