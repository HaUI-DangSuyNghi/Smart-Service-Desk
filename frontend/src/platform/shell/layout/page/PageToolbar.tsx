import type { ReactNode } from "react";

interface Props {
  /** Bên trái, ví dụ tên view "Tất cả ticket · 12". */
  start?: ReactNode;
  /** Bên phải, ví dụ Lọc / Sắp xếp / Tùy chọn. */
  end?: ReactNode;
}

/** Hàng view dưới tiêu đề (như "All Companies · 9 … Filter Sort Options" của Twenty): cao 40px, viền dưới. */
export const PageToolbar = ({ start, end }: Props) => {
  return (
    <div className="flex h-10 shrink-0 items-center justify-between gap-2 border-b border-crm-line px-3 text-crm-text-2">
      <div className="flex min-w-0 items-center gap-1">{start}</div>
      <div className="flex shrink-0 items-center gap-1">{end}</div>
    </div>
  );
};
