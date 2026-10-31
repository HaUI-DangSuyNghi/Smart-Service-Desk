# Dùng Qwen trên máy GPU riêng (`AI_MODE=remote`)

Máy chạy Smart Service Desk không cần GPU: phân loại ticket gửi tới worker Qwen trên một máy GPU khác qua Cloudflare Access + Tunnel. Bên máy GPU chuẩn bị theo `gpu-worker-vps-integration.md` (mục 3); tài liệu này là phía máy chạy ứng dụng.

```text
platform → gpu-relay (alias intelligence-classification, Nginx trong Docker)
         → https://<REMOTE_MODEL_HOST> (Cloudflare Access + Tunnel)
         → gpu-classifier:8003 trên máy GPU → Qwen
```

Rule engine, workflow, ngữ cảnh và bổ sung thông tin vẫn chạy trên máy này như chế độ `model`; chỉ bước gọi Qwen đi sang máy GPU.

## 1. Nhận file từ người giữ máy GPU

Chép 4 file vào `.local/gpu-worker/` (thư mục `.local/` không commit):

| File | Nội dung |
| --- | --- |
| `classification-token` | Token Qwen của worker (chỉ giá trị, không có chữ `Bearer `) |
| `worker.env` | `QWEN_MODEL_DIGEST`, `QWEN_POLICY_DIGEST` của model đang chạy |
| `cf-access-client-id` | Client ID của Cloudflare Access Service Token cấp cho máy này |
| `cf-access-client-secret` | Client Secret đi kèm |

Mỗi file chỉ chứa giá trị, không có tên header, dấu nháy hay `<...>`. Không đặt các giá trị này vào `.env`, Git hay frontend.

Hostname mặc định `ai.smartdeskapp.me`; đổi bằng `REMOTE_MODEL_HOST` trong `.env` (chỉ hostname, không `https://`).

## 2. Chạy

```powershell
.\dx up remote          # hoặc: dx up remote ask  (thêm Hỏi AI qua API key)
```

Script `start-model.ps1 -Remote`:

1. Kiểm đủ 4 file và định dạng.
2. Gọi thử `GET https://<REMOTE_MODEL_HOST>/health/ready` từ máy này với đủ header; dừng kèm gợi ý nếu DNS/Access/token sai, worker chưa sẵn sàng, hoặc digest khác `worker.env`.
3. Sinh `.local/gpu-worker/relay.conf` (từ `infra/compose/gpu-relay.conf.template`) và `cloudflare-headers.conf` (chứa secret Access).
4. Dừng `ai07-qwen` nếu đang chạy, bật `gpu-relay` thay chỗ, khởi động backend với digest của máy GPU và timeout 20 giây.

Lưu `AI_MODE=remote` vào `.env`, lần sau chỉ cần `dx up`. Đổi credential hoặc model bên máy GPU: chép lại file rồi chạy lại `dx up remote`.

Backend chạy chế độ `LOCAL_DEV` (profile `local-dev`) như Qwen local nên không cần processing receipt; triển khai production (`EXTERNAL`) xem mục 6 của `gpu-worker-vps-integration.md`.

## 3. Kiểm tra

- `dx doctor`: báo thiếu file trong `.local/gpu-worker`.
- `/health/ready` thành công chỉ xác nhận worker sẵn sàng; chưa xác nhận model phân loại được. Healthcheck `gpu-relay` chỉ kiểm Nginx nội bộ, không kiểm TLS hay inference tới máy GPU.
- Tạo ticket thử: thẻ AI trên trang chi tiết có gợi ý loại/ưu tiên.
- Máy GPU tắt: ticket vẫn tạo được, AI báo tạm không khả dụng và luồng duyệt tay vẫn chạy.

| Lỗi khi chạy `dx up remote` | Xử lý |
| --- | --- |
| `HTTP 0` | DNS/tunnel: máy GPU có bật, Docker + worker + cloudflared đang chạy? |
| `HTTP 301/302` | Policy Service Auth của Cloudflare Access chưa nhận Service Token |
| `HTTP 401/403` | Sai `cf-access-*` hoặc `classification-token`; Access trả JSON `Forbidden. You don't have permission...` khi Service Token chưa được policy Service Auth chấp nhận |
| Cloudflare `error code: 1010` | Cloudflare chặn client không có User-Agent; relay đã gửi `smart-service-desk-relay/1.0`. Gọi tay bằng script thì đặt User-Agent |
| Relay trả `HTTP 502`, gọi trực tiếp máy GPU thành công | Xem `docker logs dx-platform-gpu-relay-1`. Nếu có `upstream SSL certificate verify error`, kiểm tra CA bundle và độ sâu chuỗi chứng chỉ; template dùng `proxy_ssl_verify_depth 4` và vẫn bật kiểm chứng TLS. Chạy lại `dx up remote` để sinh cấu hình mới, rồi `docker restart dx-platform-gpu-relay-1` để Nginx nạp lại |
| `HTTP 502/530` từ Cloudflare | Tunnel không tới được `gpu-classifier:8003` trên máy GPU |
| Inference trả `HTTP 504`, sau đó readiness trả `503 CLASSIFICATION_NOT_READY` | Worker dừng tiến trình model sau khi quá deadline. Trên máy GPU chạy `docker restart ssd-gpu-worker-qwen-1`, chờ model nạp xong rồi kiểm thử inference; restart relay/backend không khôi phục model trên máy GPU |
| `digest differs` | `worker.env` cũ: lấy bản mới từ máy GPU |
