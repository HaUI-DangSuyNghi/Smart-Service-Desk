import type { ComponentType, CSSProperties, ReactNode } from "react";
import { cn } from "cn";

export interface DataTableColumn<Row> {
  key: string;
  header: string;
  icon?: ComponentType<{ size?: number; stroke?: number; className?: string }>;
  /** Độ rộng cột, ví dụ "240px"; bỏ trống = tự giãn. */
  width?: string;
  /** Co theo nội dung, không giãn để lấp khoảng trống của bảng (ví dụ cột thao tác). */
  fit?: boolean;
  cell: (row: Row) => ReactNode;
}

interface Props<Row> {
  columns: readonly DataTableColumn<Row>[];
  rows: readonly Row[];
  getRowKey: (row: Row) => string;
  onRowClick?: (row: Row) => void;
}

// Truyền độ rộng qua biến CSS thay vì style width: style inline thì class max-sm: không ghi đè được
const columnWidth = (width?: string) =>
  width ? ({ "--column-width": width } as CSSProperties) : undefined;

// Cột đầu dính khi cuộn ngang; trên điện thoại giới hạn ~45% màn để còn thấy các cột khác
const STICKY_CELL = "sticky left-0 z-10 bg-crm-surface";
const STICKY_WIDTH = "max-sm:w-[45vw] max-sm:max-w-[45vw]";

/**
 * Bảng kiểu Twenty: dòng cao 32px, đầu cột icon + chữ xám, kẻ dọc giữa các cột,
 * cột đầu dính khi cuộn ngang, hover phủ lớp mờ.
 */
export const DataTable = <Row,>({ columns, rows, getRowKey, onRowClick }: Props<Row>) => {
  return (
    <table className="w-max min-w-full border-separate border-spacing-0">
      <thead>
        <tr>
          {columns.map((column, index) => (
            <th
              key={column.key}
              style={column.fit ? undefined : columnWidth(column.width)}
              className={cn(
                "h-8 border-r border-b border-crm-line px-2 text-left font-normal whitespace-nowrap text-crm-text-3",
                column.fit ? "w-px" : "w-(--column-width)",
                index === 0 && cn(STICKY_CELL, STICKY_WIDTH),
              )}
            >
              <span className="flex items-center gap-1">
                {column.icon && <column.icon size={16} stroke={2} className="shrink-0" />}
                <span className="truncate">{column.header}</span>
              </span>
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {rows.map((row) => (
          <tr
            key={getRowKey(row)}
            onClick={onRowClick && (() => onRowClick(row))}
            className={cn("group", onRowClick && "cursor-pointer")}
          >
            {columns.map((column, index) => (
              <td
                key={column.key}
                className={cn(
                  "h-8 border-r border-b border-crm-line-light px-2 text-crm-text",
                  // Hover là lớp phủ trên nền, không thay màu nền: cột dính vẫn giữ nền đặc,
                  // không lộ các cột đang cuộn bên dưới (màu hover trong suốt)
                  "group-hover:bg-[image:linear-gradient(var(--color-crm-hover),var(--color-crm-hover))]",
                  index === 0 && STICKY_CELL,
                  column.fit && "w-px whitespace-nowrap",
                )}
              >
                {/* Cắt chữ ở thẻ div bọc trong: max-width trên td không chạy ổn định ở mọi trình duyệt */}
                <div
                  className={cn(
                    column.fit ? "w-max" : "max-w-[320px] truncate",
                    index === 0 && STICKY_WIDTH,
                  )}
                >
                  {column.cell(row)}
                </div>
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  );
};
