Phần này dành cho người vận hành trên máy cá nhân. Nếu sử dụng hệ thống đã được đơn vị triển khai, bắt đầu từ [Đăng nhập](#bat-dau).

## Chuẩn bị

- Có bản mã nguồn được cấp quyền truy cập.
- Windows với PowerShell theo luồng script hiện có của dự án.
- Docker Desktop chạy Linux containers, Docker Compose v2 hỗ trợ chờ dịch vụ sẵn sàng.
- Kết nối mạng cho lần tải image/thư viện đầu tiên.
- Chọn AI qua API, Qwen local hoặc máy GPU riêng phù hợp điều kiện triển khai.

Các lệnh bên dưới chạy ở thư mục gốc chứa `compose.yaml`. Dùng PowerShell:

```powershell
.\dx env
.\dx doctor
```

Lệnh `env` tạo/bổ sung cấu hình còn thiếu và sinh bí mật nội bộ cần thiết. Máy đã có `.env` không cần xóa file để nhận biến cấu hình mới.

## Chọn chế độ AI

| Chế độ                       | Lệnh                 | Cần chuẩn bị                                              |
| ---------------------------- | -------------------- | --------------------------------------------------------- |
| API cho phân loại và hỏi đáp | `.\dx up api`        | Provider, model và khóa API hợp lệ; không cần GPU local   |
| Qwen local cho phân loại     | `.\dx up model`      | GPU NVIDIA dùng được trong Docker và image model của nhóm |
| Qwen local + hỏi đáp API     | `.\dx up model ask`  | Điều kiện Qwen local và cấu hình API                      |
| Qwen trên máy GPU riêng      | `.\dx up remote`     | Máy GPU đang chạy và bộ cấu hình kết nối được cấp         |
| Qwen máy riêng + hỏi đáp API | `.\dx up remote ask` | Cấu hình máy GPU riêng và cấu hình API                    |

Lệnh `model` hoặc `remote` có thể giữ lựa chọn hỏi đáp API đã lưu trước đó. Kiểm tra `AI_ASSISTANCE_MODE` khi muốn biết hỏi đáp hiện bật hay tắt.

Để dùng API, cấu hình các trường sau trong `.env` bằng giá trị riêng của máy:

```dotenv
AI_API_PROVIDER=gemini
AI_API_MODEL=<model-ban-duoc-phep-su-dung>
AI_API_KEY=<khoa-rieng-cua-ban>
AI_API_KEY_FILE=
AI_API_BASE_URL=
```

Provider hiện hỗ trợ `gemini`, `openai`, `groq`. Để trống base URL khi dùng endpoint chính thức. Có thể dùng file khóa theo tài liệu cấu hình thay cho khóa trực tiếp. Các dấu `<...>` ở ví dụ là chỗ cần thay, không phải giá trị để chép nguyên.

Để dùng Qwen local, nạp đúng image được nhóm cung cấp:

```powershell
docker load -i D:\Model\qwen-full-image.tar
```

Thay đường dẫn bằng file thật. Nếu tên image khác cấu hình mặc định, đặt `LOCAL_MODEL_IMAGE` tương ứng. Clone repository không tự tải trọng số hoặc image model.

Chế độ remote cần làm theo [hướng dẫn máy GPU riêng](https://github.com/HaUI-DangSuyNghi/Smart-Service-Desk/blob/develop/docs/remote-model.md), không sao chép ví dụ credential vào file thật.

## Khởi động và đăng nhập

```powershell
.\dx up
```

Sau lần khởi động thành công, dự án giữ lựa chọn AI để sử dụng cho lần sau. Lần build đầu và thời gian làm nóng GPU có thể mất vài phút.

| Thành phần    | Địa chỉ phát triển mặc định |
| ------------- | --------------------------- |
| Giao diện web | `http://localhost:5173`     |
| API Gateway   | `http://localhost:9080`     |
| Keycloak      | `http://localhost:8081`     |

Trên dữ liệu demo, các tài khoản như `requester1`, `agent1`, `coordinator1` đại diện các vai trò trong `lab-a`. Mật khẩu demo được sinh riêng trong `DEMO_PASSWORD` của `.env`; tài khoản triển khai thật do đơn vị cấp. Không đưa mật khẩu vào tài liệu công khai.

## Các lệnh vận hành thường dùng

| Lệnh                 | Công dụng                                           |
| -------------------- | --------------------------------------------------- |
| `.\dx help`          | Xem lệnh được hỗ trợ                                |
| `.\dx start`         | Mở menu khởi động có hướng dẫn                      |
| `.\dx doctor`        | Kiểm tra Docker, cổng, điều kiện GPU/API và dịch vụ |
| `.\dx ps`            | Xem trạng thái container                            |
| `.\dx logs platform` | Xem log backend                                     |
| `.\dx logs keycloak` | Xem log đăng nhập                                   |
| `.\dx logs gateway`  | Xem log gateway                                     |
| `.\dx down`          | Dừng hệ thống và giữ dữ liệu theo luồng script      |
| `.\dx qwen`          | Khởi động lại worker Qwen local khi cần kiểm tra    |

`docker compose up` thuần không tự lựa chọn đầy đủ overlay theo chế độ AI đã lưu như luồng `dx up`.

## Khi đang phát triển giao diện

Với Vite đang chạy, sửa React/CSS thường được cập nhật tự động. Nếu thay biến môi trường frontend hoặc tiến trình dev bị lỗi, khởi động lại Vite:

```powershell
cd frontend
npm ci
npm run dev
```

`npm ci` dành cho lần cài đặt đầu hoặc khi lockfile/thư viện thay đổi; không cần chạy lại cho mỗi lần sửa giao diện. Cần Node đáp ứng `frontend/package.json`, hiện là `>=22.12`.

Nếu frontend đang chạy từ container build tĩnh, cần dựng lại container để nhận code mới; lệnh `dx up` thực hiện bước build theo cấu hình đã chọn. Không chạy Vite và container frontend cùng chiếm cổng 5173.

Thay đổi backend yêu cầu build/khởi động lại backend phù hợp. Chỉ reload trình duyệt không cập nhật mã Java đang chạy trong container cũ.
