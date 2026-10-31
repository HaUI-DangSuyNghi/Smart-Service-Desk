import type { ReactNode } from "react";

interface Props {
  /** Bên trái, ví dụ tên view "Tất cả ticket · 12". */
  start?: ReactNode;
  /** Bên phải, ví dụ Lọc / Tạo mới. Dưới sm xuống hàng riêng, tự ngắt dòng khi cần. */
  end?: ReactNode;
}

/** Hàng view dưới tiêu đề (như "All Companies · 9 … Filter Sort Options" của Twenty): cao 40px, viền dưới. */
export const PageToolbar = ({ start, end }: Props) => {
  return (
    // Điện thoại không đủ chỗ cho cả 2 phần trên 1 hàng (ô tìm kiếm + phân trang) nên cho xuống dòng
    <div className="flex min-h-10 shrink-0 flex-wrap items-center justify-between gap-x-2 gap-y-1 border-b border-crm-line px-3 py-1.5 text-crm-text-2">
      <div className="flex min-w-0 flex-wrap items-center gap-1">{start}</div>
      {end && (
        <div className="flex w-full flex-wrap items-center justify-end gap-1 sm:w-auto">{end}</div>
      )}
    </div>
  );
};
