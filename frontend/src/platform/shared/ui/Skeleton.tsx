import type { ReactNode } from "react";
import { cn } from "cn";

// Độ rộng thanh xoay vòng theo vị trí cho giống dòng chữ thật, không đều tăm tắp
const BAR_WIDTHS = ["w-3/4", "w-1/2", "w-2/3", "w-2/5", "w-3/5"];

const range = (count: number) => Array.from({ length: count }, (_, index) => index);
const barWidth = (index: number) => BAR_WIDTHS[index % BAR_WIDTHS.length];

/** Một thanh xám nhấp nháy thay cho chữ đang tải; độ rộng/cao đặt bằng className. */
export const Skeleton = ({ className }: { className?: string }) => {
  return (
    <span
      aria-hidden
      className={cn("block h-3 rounded-crm-sm bg-crm-canvas motion-safe:animate-pulse", className)}
    />
  );
};

interface StatusProps {
  label?: string;
  className?: string;
  children: ReactNode;
}

// Khung chung: báo cho trình đọc màn hình biết đang tải, phần thanh xám bị ẩn
const SkeletonStatus = ({ label = "Đang tải", className, children }: StatusProps) => {
  return (
    <div role="status" aria-label={label} aria-busy className={className}>
      {children}
    </div>
  );
};

const TableBars = ({ columns, rows }: { columns: number; rows: number }) => {
  return (
    <>
      <div className="flex h-8 border-b border-crm-line">
        {range(columns).map((column) => (
          <div key={column} className="flex flex-1 items-center px-2">
            <Skeleton className="w-16" />
          </div>
        ))}
      </div>
      {range(rows).map((row) => (
        <div key={row} className="flex h-8 border-b border-crm-line-light">
          {range(columns).map((column) => (
            <div key={column} className="flex flex-1 items-center px-2">
              <Skeleton className={barWidth(row + column)} />
            </div>
          ))}
        </div>
      ))}
    </>
  );
};

interface TableProps {
  columns?: number;
  rows?: number;
  label?: string;
}

/** Thay cho DataTable đang tải: hàng tiêu đề + các dòng cao 32px. */
export const TableSkeleton = ({ columns = 4, rows = 8, label }: TableProps) => {
  return (
    <SkeletonStatus label={label}>
      <TableBars columns={columns} rows={rows} />
    </SkeletonStatus>
  );
};

/** Thay cho đoạn văn đang tải; dòng cuối ngắn hơn. */
export const TextSkeleton = ({ lines = 3, label }: { lines?: number; label?: string }) => {
  return (
    <SkeletonStatus label={label} className="flex flex-col gap-2.5 py-1">
      {range(lines).map((line) => (
        <Skeleton key={line} className={line === lines - 1 ? "w-2/3" : "w-full"} />
      ))}
    </SkeletonStatus>
  );
};

/** Thay cho danh sách nhãn – giá trị (trang chi tiết); điện thoại xếp dọc như FieldRow. */
export const FieldsSkeleton = ({ rows = 5, label }: { rows?: number; label?: string }) => {
  return (
    <SkeletonStatus label={label} className="flex flex-col gap-3 px-4 py-3">
      {range(rows).map((row) => (
        <div key={row} className="flex flex-col gap-1.5 sm:flex-row sm:items-center sm:gap-4">
          <Skeleton className="w-24 shrink-0 sm:w-32" />
          <Skeleton className={cn("sm:flex-1", barWidth(row))} />
        </div>
      ))}
    </SkeletonStatus>
  );
};

/** Cả trang đang tải (trang lazy, đang kiểm quyền): khung tiêu đề, toolbar và bảng. */
export const PageSkeleton = ({ label }: { label?: string }) => {
  return (
    <SkeletonStatus label={label} className="flex h-full flex-col bg-crm-surface">
      <div className="flex h-12 shrink-0 items-center gap-2 border-b border-crm-line px-4">
        <Skeleton className="size-4" />
        <Skeleton className="w-32" />
      </div>
      <div className="flex h-10 shrink-0 items-center border-b border-crm-line px-3">
        <Skeleton className="w-40" />
      </div>
      <TableBars columns={4} rows={8} />
    </SkeletonStatus>
  );
};
