import { IconAlertTriangle, IconBell, IconInfoCircle } from "@tabler/icons-react";
import { cn } from "cn";
import type { NotificationItem } from "@/platform/services";
import { formatDateTime, formatRelativeTime } from "@/platform/shared/lib/format";

// Màu theo mức độ: khẩn cấp đỏ, cảnh báo cam, thông báo thường xám
const TONE: Record<NotificationItem["severity"], string> = {
  CRITICAL: "text-crm-tone-red",
  WARNING: "text-crm-tone-orange",
  INFO: "text-crm-text-3",
};

interface Props {
  item: NotificationItem;
  onOpen: (item: NotificationItem) => void;
}

/** Một thông báo: icon theo mức độ, tiêu đề (đậm khi chưa đọc), nội dung ngắn, thời gian; chấm xanh = chưa đọc. */
export const NotificationRow = ({ item, onOpen }: Props) => {
  const unread = !item.readAt;
  const Icon =
    item.severity === "INFO"
      ? item.category === "ALERT"
        ? IconInfoCircle
        : IconBell
      : IconAlertTriangle;
  return (
    <button
      type="button"
      onClick={() => onOpen(item)}
      className={cn(
        "flex w-full items-center gap-2.5 rounded-crm-sm px-2 py-1.5 text-left outline-none",
        "hover:bg-crm-hover focus-visible:bg-crm-hover",
      )}
    >
      <Icon size={16} stroke={2} className={cn("shrink-0", TONE[item.severity])} />
      {/* Cỡ chữ đặt ở khối cha: cn coi text-crm-sm và text-crm-text cùng nhóm nên gộp sẽ mất cỡ chữ */}
      <span className="flex min-w-0 flex-1 flex-col gap-0.5 text-crm-sm">
        <span
          className={cn(
            "line-clamp-2",
            unread ? "font-semibold text-crm-text" : "font-medium text-crm-text-2",
          )}
        >
          {item.title}
        </span>
        {item.body && <span className="truncate text-crm-sm text-crm-text-3">{item.body}</span>}
        <time
          dateTime={item.createdAt}
          title={formatDateTime(item.createdAt)}
          className="text-crm-xs text-crm-text-3"
        >
          {formatRelativeTime(item.createdAt)}
        </time>
      </span>
      {unread && (
        <span aria-label="Chưa đọc" className="size-2 shrink-0 rounded-full bg-crm-accent" />
      )}
    </button>
  );
};
