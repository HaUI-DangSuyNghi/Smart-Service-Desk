# Frontend

Giao diện Smart Service Desk: React + TypeScript + Vite.

```text
src/
├─ App.tsx          ghép platform với các app (nơi duy nhất import cả hai)
├─ platform/        Tầng 2, dùng chung, không import apps/
│  ├─ human/        H: auth/, workspace/ (vai trò), profile/
│  ├─ process/      P: hộp việc, tiến độ, tạo yêu cầu, yêu cầu của tôi
│  ├─ data/         D: knowledge/ (Knowledge Hub)
│  ├─ intelligence/ I: hiển thị gợi ý AI
│  ├─ services/     mọi lời gọi API của Tầng 2 (axios)
│  ├─ shell/        vỏ ứng dụng: router/, layout/, landing/
│  └─ shared/       dùng chung: api/ (axios client), ui/ (shadcn, page/, DataTable, Skeleton, toast...), lib/
└─ apps/
   └─ servicedesk/  Tầng 3, nghiệp vụ Service Desk
test/               test, cấu trúc giống src/
```

```bash
npm install
cp .env.example .env  # cấu hình đăng nhập Keycloak
npm run dev        # http://localhost:5173, /api chuyển tới gateway :9080
npm run format     # format toàn bộ src
```

Trước khi push, chạy đủ như CI: `typecheck`, `lint`, `test`, `build`, `build:platform`.

## Chạy bằng Docker

`scripts/start.ps1` (hay `docker compose up -d --build`) dựng luôn frontend ở `http://localhost:5173`:

- Image chỉ build để chạy (`vite build`), **không** chạy typecheck, lint, test; muốn kiểm thì tự chạy các lệnh npm ở trên.
- Không sửa gì trong `frontend/` thì Docker dùng lại cache, build gần như tức thì; chỉ sửa code thì không cài lại thư viện.
- nginx phục vụ file tĩnh và chuyển `/api` sang gateway (`nginx.conf`), giống proxy của Vite.
- Đang sửa giao diện cần hot reload: `docker compose stop frontend` rồi `npm run dev` (cùng cổng 5173, vì Keycloak chỉ cho redirect về 5173/9080).
