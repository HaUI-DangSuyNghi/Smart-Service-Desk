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
- Quy trình như ticket phải tạo qua API của app (bản ghi và process ra đời cùng lúc), nên không bật `allowSelfService()`.
