import type { ReactNode } from "react";
import { cn } from "cn";

interface Props {
  label: string;
  children: ReactNode;
  /** Nhãn luôn nằm trên giá trị; dùng ở cột hẹp (bảng Chi tiết bên phải) nơi nhãn cột trái làm tràn ngang. */
  stacked?: boolean;
}

/**
 * Một dòng nhãn – giá trị trong `<dl>` ở trang chi tiết.
 * Điện thoại: nhãn nằm trên giá trị; từ sm: nhãn cột trái rộng 128px (trừ khi `stacked`).
 */
export const FieldRow = ({ label, children, stacked = false }: Props) => {
  return (
    <div
      className={cn(
        "flex min-h-8 flex-col gap-0.5 py-1.5",
        !stacked && "sm:flex-row sm:items-start sm:gap-4",
      )}
    >
      <dt className={cn("shrink-0 text-crm-text-3", !stacked && "sm:w-32")}>{label}</dt>
      <dd className="min-w-0 break-words text-crm-text">{children}</dd>
    </div>
  );
};
