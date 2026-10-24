# process (khối P)

Công việc, tiến độ và tạo yêu cầu **dùng chung cho mọi quy trình** (`service-desk`, `equipment-approval`, `document-review`...). Không có chữ nào riêng của một app: nhãn tiếng Việt, kết quả của từng bước lấy từ `GET /hpdi/v1/workspaces/{ws}/processes`, do app khai báo ở backend (`ProcessDefinition.withLabels`).

| Route | Trang | Ghi chú |
|---|---|---|
| `/inbox` | Công việc | View "Việc của tôi" (ASSIGNED) và "Có thể nhận" (CANDIDATE); nhận việc, hoàn thành bước |
| `/processes/:id` | Tiến độ | Tóm tắt + dòng thời gian các bước; hoàn thành được bước đang giao cho mình |
| `/requests` | Yêu cầu | Mọi quy trình tôi đã tạo |

```text
process/
├─ index.ts      public API: các trang, ProcessTimeline, InboxBadge, configureProcessLinks
├─ common/       queryKeys, processLinks (link sang app), display (màu kết quả, tên người), BusinessKeyLink, CandidateGroups
├─ catalog/      danh mục quy trình + tra nhãn (createProcessLabels, useProcessLabels)
├─ tasks/        nhận/hoàn thành việc (useTaskActions), câu báo lỗi, CompleteTaskDialog
├─ inbox/        InboxPage, InboxTable, InboxScopeTabs, InboxBadge (số việc ở sidebar)
├─ timeline/     ProcessTimelinePage, ProcessTimeline, TimelineStep, TimelineItem
├─ start/        StartProcessDialog (chỉ quy trình canStart)
└─ requests/     MyRequestsPage
```

## Luật backend cần nhớ

- "Có thể nhận" không chứa việc của yêu cầu do chính mình tạo (nguyên tắc 4 mắt); nhận việc đó trả 403.
- Việc chưa nhận thì **chưa xem được tiến độ** (backend trả 404), nên dòng ở view "Có thể nhận" không bấm được.
- Hoàn thành luôn gửi `expectedTaskKey` = bước đang thấy; bước đã đổi thì backend trả 409.
- Nhận/hoàn thành xong (kể cả lỗi) đều làm mới mọi khóa `["process", workspace]`.

## App cắm vào

- **Link sang trang chi tiết**: app đăng ký `processLinks: { "<processKey>": (businessKey) => "/app/..." }`; `App.tsx` gọi `configureProcessLinks(registry.processLink)`. Không đăng ký thì mã yêu cầu hiện dạng chữ.
- **Nhúng tiến độ** vào trang của app: `import { ProcessTimeline } from "@/platform/process"`, truyền dữ liệu timeline của bản ghi.
- **Tạo yêu cầu** của quy trình gắn bản ghi (ticket...) phải đi qua API của app; form chung chỉ hiện quy trình `canStart`.
