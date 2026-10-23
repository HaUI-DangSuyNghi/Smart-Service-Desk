import type { ComponentType, ReactNode } from "react";

interface Props {
  icon: ComponentType<{ size?: number; stroke?: number; className?: string }>;
  title: string;
  description?: string;
  action?: ReactNode;
}

/** Trạng thái rỗng: icon xám, tiêu đề, mô tả và hành động gợi ý. */
export const EmptyState = ({ icon: Icon, title, description, action }: Props) => {
  return (
    <div className="flex flex-col items-center gap-2 px-4 py-16 text-center">
      <Icon size={24} stroke={1.6} className="text-crm-text-light" />
      <p className="font-medium text-crm-text">{title}</p>
      {description && <p className="max-w-sm text-crm-text-3">{description}</p>}
      {action && <div className="mt-2">{action}</div>}
    </div>
  );
};
