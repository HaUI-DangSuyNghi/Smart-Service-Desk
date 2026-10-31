import type { ReactNode } from "react";

/** Phần nội dung trang (bảng, form...): chiếm phần còn lại và tự cuộn. Có `relative` cho lớp phủ bên trong. */
export const PageBody = ({ children }: { children: ReactNode }) => {
  return <main className="relative min-h-0 flex-1 overflow-auto">{children}</main>;
};
