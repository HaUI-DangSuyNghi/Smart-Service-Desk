import type { ComponentType } from "react";

interface Props {
  label: string;
  value: string;
  icon: ComponentType<{ size?: number; stroke?: number; className?: string }>;
  /** Dòng phụ giải thích con số, ví dụ "hiện tại" hay "trong 7 ngày". */
  hint?: string;
}

/** Thẻ một con số trên dashboard: nhãn nhỏ, số lớn, dòng phụ. Chữ dùng màu chữ, không dùng màu dữ liệu. */
export const StatTile = ({ label, value, icon: Icon, hint }: Props) => {
  return (
    <div className="flex flex-col gap-1 rounded-crm-md border border-crm-line px-3 py-2.5">
      <span className="flex items-center gap-1.5 text-crm-sm text-crm-text-3">
        <Icon size={14} stroke={2} className="shrink-0" />
        <span className="truncate">{label}</span>
      </span>
      <span className="text-crm-xl font-medium text-crm-text tabular-nums">{value}</span>
      {hint && <span className="truncate text-crm-xs text-crm-text-3">{hint}</span>}
    </div>
  );
};
