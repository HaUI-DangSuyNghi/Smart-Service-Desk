import type { ReactNode } from "react";

/**
 * Một dòng nhãn – giá trị trong `<dl>` ở trang chi tiết.
 * Điện thoại: nhãn nằm trên giá trị; từ sm: nhãn cột trái rộng 128px.
 */
export const FieldRow = ({ label, children }: { label: string; children: ReactNode }) => {
  return (
    <div className="flex min-h-8 flex-col gap-0.5 py-1.5 sm:flex-row sm:items-start sm:gap-4">
      <dt className="shrink-0 text-crm-text-3 sm:w-32">{label}</dt>
      <dd className="min-w-0 break-all text-crm-text">{children}</dd>
    </div>
  );
};
