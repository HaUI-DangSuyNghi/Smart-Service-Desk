import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { fileURLToPath, URL } from "node:url";
import { defineConfig } from "vite";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) },
  },
  server: {
    // Keycloak chỉ cho phép redirect về đúng cổng 5173
    port: 5173,
    strictPort: true,
    // Gọi API qua cùng origin để tránh CORS; APISIX chạy ở 9080
    proxy: {
      "/api": { target: "http://localhost:9080", changeOrigin: true },
    },
  },
});
