# Frontend

Giao diện Smart Service Desk: React + TypeScript + Vite.

```text
src/
├─ App.tsx          ghép platform với các app (nơi duy nhất import cả hai)
├─ platform/        Tầng 2, dùng chung, không import apps/
│  ├─ router/       registry để app đăng ký route, menu, link process
│  ├─ layout/       khung trang, trang chủ, 404
│  └─ auth/ workspace/ process/ knowledge/ data/
└─ apps/
   └─ servicedesk/  Tầng 3, nghiệp vụ Service Desk
test/               test, cấu trúc giống src/
```

```bash
npm install
npm run dev        # http://localhost:5173, /api chuyển tới gateway :9080
npm run format     # format toàn bộ src
```

Trước khi push, chạy đủ như CI: `typecheck`, `lint`, `test`, `build`, `build:platform`.
