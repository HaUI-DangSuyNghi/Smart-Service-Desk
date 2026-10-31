# human

H: đăng nhập (`auth/`), người dùng, workspace và vai trò (`workspace/`: `useMe`, `useCurrentWorkspace`, `useActor`, `RoleGate`), trang tài khoản (`profile/`).

`activity/`: **Hoạt động của tôi** trong trang Tài khoản – thao tác chính người dùng đã làm ở workspace đang chọn, lấy từ audit qua `GET /hpdi/v1/workspaces/{ws}/activity` (người thực hiện luôn lấy từ token). `describeActivity` dịch mã thao tác (`process.start`, `task.complete`...) sang câu tiếng Việt; thao tác riêng của app chưa có nhãn thì hiện nguyên mã.
