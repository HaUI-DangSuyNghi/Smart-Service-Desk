import type { ReactNode } from "react";

interface Props {
  title: string;
  collapsed: boolean;
  children: ReactNode;
}

/** Nhóm menu ("Nền tảng", "Ứng dụng") có tiêu đề nhỏ màu xám; thu gọn thì chỉ còn icon. */
export const NavSection = ({ title, collapsed, children }: Props) => {
  return (
    <section className="flex flex-col gap-0.5">
      {!collapsed && (
        <h2 className="flex h-7 items-center px-2 text-crm-sm text-crm-text-3">{title}</h2>
      )}
      {children}
    </section>
  );
};
