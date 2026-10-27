import type { ComponentType, ReactNode } from "react";

interface Props {
  /** Chỉ là tiêu đề ngắn, không kèm giải thích. */
  title: string;
  icon: ComponentType<{ size?: number; stroke?: number; className?: string }>;
  children: ReactNode;
}

/** Nhóm ô nhập của form cấu hình: icon + tiêu đề, nội dung bên dưới. */
export const FieldGroup = ({ title, icon: Icon, children }: Props) => (
  <fieldset className="flex flex-col gap-3">
    <legend className="mb-2 flex items-center gap-2 font-medium">
      <Icon size={16} stroke={2} className="shrink-0 text-crm-text-3" />
      {title}
    </legend>
    {children}
  </fieldset>
);
