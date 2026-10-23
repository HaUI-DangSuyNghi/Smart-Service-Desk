import "./index.css";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import App from "./App.tsx";
import { completeSilentSignin, isSilentCallback } from "./platform/human/auth";

// Trong iframe đăng nhập ngầm chỉ cần trả token về trang cha, không render app
if (isSilentCallback()) {
  void completeSilentSignin();
} else {
  createRoot(document.getElementById("root")!).render(
    <StrictMode>
      <App />
    </StrictMode>,
  );
}
