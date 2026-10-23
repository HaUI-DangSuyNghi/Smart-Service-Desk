import type { ComponentType, ReactNode } from "react";

interface Props {
  title: string;
  icon?: ComponentType<{ size?: number; stroke?: number; className?: string }>;
  /** Nút hành động bên phải, ví dụ nút xanh "Tạo ticket". */
  actions?: ReactNode;
}

/** Hàng tiêu đề trang kiểu Twenty: cao 48px, viền dưới, icon + tiêu đề trái, hành động phải. */
export const PageHeader = ({ title, icon: Icon, actions }: Props) => {
  return (
    <header className="flex h-12 shrink-0 items-center justify-between gap-2 border-b border-crm-line pr-3 pl-4">
      <div className="flex min-w-0 items-center gap-2 text-crm-text">
        {Icon && <Icon size={16} stroke={2} className="shrink-0 text-crm-text-2" />}
        <h1 className="truncate font-medium">{title}</h1>
      </div>
      {actions && <div className="flex shrink-0 items-center gap-2">{actions}</div>}
    </header>
  );
};
