# Bộ test Docker AI07 Qwen LoRA

Bộ chạy thử nội bộ cho adapter Qwen3.5-0.8B được fine-tune bằng machine-silver.
Không chứa dataset, bản xuất dự đoán, secret dùng chung hoặc giấy phép xử lý ticket.
Giấy phép model và thư viện được giữ nguyên trong `licenses/`; xem
`THIRD_PARTY_NOTICES.md` và manifest để tra nguồn, phiên bản và checksum.
Giữ thư mục này ngoài Git; việc phân phối model vẫn cần xác nhận quyền từ chủ sở hữu.

Yêu cầu: Docker Desktop chạy Linux containers, hỗ trợ GPU NVIDIA, khoảng 4 GB VRAM
khả dụng, 6 GB RAM cho container và đủ chỗ chứa image/model. Kết quả GPU và giới hạn
thời gian của đúng bundle được ghi riêng; chỉ gửi yêu cầu khi dịch vụ đã healthy.
Runtime không tải thêm model hoặc thư viện qua mạng.

Mở PowerShell tại thư mục bộ test:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\Start-Qwen.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\Test-Qwen.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\Stop-Qwen.ps1
```

Script Start kiểm checksum, nạp đúng Docker image nếu máy chưa có, tạo bearer token
riêng trên máy nhận, mount model read-only và chờ load/warmup. Script Test gửi một
ticket tổng hợp bằng contract HTTP hiện có của backend qua `docker exec` và HTTP
loopback trong container. Không publish cổng host; mạng Docker riêng không ra Internet.

Runtime xử lý một inference tại một thời điểm. Khi bận hoặc chưa sẵn sàng, dịch vụ trả
503; khi vượt deadline, trả 504 và dừng GPU worker. Khởi động lại dịch vụ để load/warmup
lại. Kết quả A–F được ánh xạ bằng policy của ứng dụng; F là abstain. Output sai định
dạng bị từ chối. Score luôn null và mọi đề xuất đều chưa được xác minh.

Bộ test không bật `/assistant/classify` của backend và không tạo processing grant.
Ticket thật vẫn cần receipt bên ngoài có chữ ký, ràng buộc workspace/model/policy.
SHADOW che phiếu model; PROMOTED tiếp tục bị chặn.

`package.json` pin image/model/prompt và checksum từng file. Đối chiếu SHA-256 của
file này với thông tin bàn giao từ người gửi. Revision base lúc huấn luyện Kaggle chưa
được ghi lại; snapshot dùng để serve là candidate được dựng lại. Xem báo cáo replay
riêng để biết parity/deadline. Gold và production vẫn NO_GO.
