import type { ComponentType, ReactNode } from "react";
import { cn } from "cn";

export type TimelineMarker = "start" | "done" | "active" | "waiting" | "end";

// Viết đủ tên class để Tailwind sinh ra được
const MARKER_CLASS: Record<TimelineMarker, string> = {
  start: "text-crm-text-2",
  done: "text-crm-tone-green",
  active: "text-crm-tone-blue",
  waiting: "text-crm-text-3",
  end: "text-crm-tone-green",
};

interface Props {
  icon: ComponentType<{ size?: number; stroke?: number }>;
  marker: TimelineMarker;
  title: ReactNode;
  /** Tag trạng thái/kết quả cạnh tiêu đề. */
  badge?: ReactNode;
  children?: ReactNode;
}

/** Một mốc trên dòng thời gian dọc: vòng tròn icon, đường nối xuống mốc sau, nội dung bên phải. */
export const TimelineItem = ({ icon: Icon, marker, title, badge, children }: Props) => {
  return (
    // before: là đường nối dọc; mốc cuối không có đường nối
    <li className="relative flex gap-3 pb-5 before:absolute before:top-8 before:bottom-1 before:left-3.5 before:w-px before:bg-crm-line last:pb-0 last:before:hidden">
      <span
        className={cn(
          "grid size-7 shrink-0 place-items-center rounded-full border border-crm-line bg-crm-surface",
          MARKER_CLASS[marker],
        )}
      >
        <Icon size={14} stroke={2} />
      </span>
      <div className="flex min-w-0 flex-1 flex-col gap-1 pt-1">
        <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
          <span className="font-medium text-crm-text">{title}</span>
          {badge}
        </div>
        {children}
      </div>
    </li>
  );
};
