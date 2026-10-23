# Frontend

Giao diện Smart Service Desk: React + TypeScript + Vite.

```text
src/
├─ App.tsx          ghép platform với các app (nơi duy nhất import cả hai)
├─ platform/        Tầng 2, dùng chung, không import apps/
│  ├─ human/        H: auth/, workspace/
│  ├─ process/      P: inbox, timeline
│  ├─ data/         D: dashboard, audit, knowledge/
│  ├─ intelligence/ I: hiển thị gợi ý AI
│  ├─ services/     mọi lời gọi API của Tầng 2 (axios)
│  ├─ shell/        vỏ ứng dụng: router/, layout/, landing/
│  └─ shared/       dùng chung: api/ (axios client), ui/ (shadcn), lib/
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
