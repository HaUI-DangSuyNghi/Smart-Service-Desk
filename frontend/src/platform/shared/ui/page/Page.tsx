import type { ReactNode } from "react";

/** Khung một trang trên nền trắng: PageHeader → PageToolbar (không bắt buộc) → PageBody. */
export const Page = ({ children }: { children: ReactNode }) => {
  return <div className="flex h-full min-h-0 flex-col bg-crm-surface">{children}</div>;
};
