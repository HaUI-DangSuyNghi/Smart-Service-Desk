import type { CSSProperties } from "react";
import { Toaster as Sonner } from "sonner";

// Nối biến màu của sonner sang token Twenty: nền trắng, viền mảnh, bo 8px
const TWENTY_COLORS = {
  "--normal-bg": "var(--color-crm-surface)",
  "--normal-text": "var(--color-crm-text)",
  "--normal-border": "var(--color-crm-line)",
  "--border-radius": "var(--radius-crm-md)",
} as CSSProperties;

/** Nơi hiện thông báo ngắn; đặt một lần trong AppLayout, gọi bằng `toast` ở `shared/ui/toast`. */
export const Toaster = () => {
  return (
    <Sonner
      position="bottom-right"
      closeButton
      style={TWENTY_COLORS}
      toastOptions={{ className: "font-crm text-crm-md shadow-crm-strong" }}
    />
  );
};
