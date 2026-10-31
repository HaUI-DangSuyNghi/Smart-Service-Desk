import { useState } from "react";
import { useNavigate } from "react-router";
import { IconBell, IconChecks } from "@tabler/icons-react";
import { cn } from "cn";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from "@/platform/shared/ui/dropdown-menu";
import { ErrorState } from "@/platform/shared/ui/ErrorState";
import { TextSkeleton } from "@/platform/shared/ui/Skeleton";
import { ViewTabs } from "@/platform/shared/ui/ViewTabs";
import { NotificationRow } from "./NotificationRow";
import { useNotifications, useReadAllNotifications } from "./useNotifications";
import { useOpenNotification } from "./useOpenNotification";

type Filter = "all" | "unread";

/** Chuông bên phải header: số chưa đọc, xổ ra 10 thông báo mới nhất và liên kết đến trang đầy đủ. */
export const NotificationBell = () => {
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [filter, setFilter] = useState<Filter>("all");
  // Cùng khoá truy vấn với toast thông báo mới (size 10, mọi thông báo) để dùng chung một lần tải
  const latest = useNotifications({ size: 10 });
  const unreadList = useNotifications({ size: 10, unreadOnly: true });
  const list = filter === "unread" ? unreadList : latest;
  const unread = latest.data?.unread ?? 0;
  const readAll = useReadAllNotifications();
  const openItem = useOpenNotification();

  return (
    <DropdownMenu open={open} onOpenChange={setOpen}>
      <DropdownMenuTrigger
        aria-label={unread > 0 ? `Thông báo, ${unread} chưa đọc` : "Thông báo"}
        title="Thông báo"
        className={cn(
          "relative grid size-8 shrink-0 place-items-center rounded-crm-sm text-crm-text-3 outline-none sm:size-7",
          "transition-colors duration-100 hover:bg-crm-hover hover:text-crm-text data-[state=open]:bg-crm-hover",
        )}
      >
        <IconBell size={16} stroke={2} />
        {unread > 0 && (
          <span className="absolute -top-1 -right-1 min-w-4 rounded-full bg-crm-tone-red px-1 text-center text-[10px] leading-4 font-semibold text-white tabular-nums">
            {unread > 99 ? "99+" : unread}
          </span>
        )}
      </DropdownMenuTrigger>

      <DropdownMenuContent
        side="bottom"
        align="end"
        // Portal nằm ngoài .crm-app nên phải đặt lại cỡ chữ của app (13px), không thì về 16px mặc định
        className="flex w-88 max-w-[calc(100vw-2rem)] flex-col p-0 text-crm-md text-crm-text"
      >
        <div className="flex items-center justify-between gap-2 px-3 pt-2.5 pb-1.5">
          <span className="font-semibold">Thông báo</span>
          <button
            type="button"
            disabled={unread === 0 || readAll.isPending}
            onClick={() => readAll.mutate()}
            title="Đánh dấu tất cả đã đọc"
            className="flex items-center gap-1 rounded-crm-sm px-1.5 py-0.5 text-crm-sm text-crm-text-3 hover:bg-crm-hover hover:text-crm-text disabled:opacity-40"
          >
            <IconChecks size={14} stroke={2} />
            Đã đọc hết
          </button>
        </div>
        <div className="border-b border-crm-line px-3 pb-2 text-crm-sm">
          <ViewTabs
            label="Lọc thông báo"
            tabs={[
              { key: "all", label: "Tất cả" },
              { key: "unread", label: "Chưa đọc", count: unread },
            ]}
            value={filter}
            onChange={setFilter}
          />
        </div>

        <div className="max-h-[60vh] overflow-y-auto p-1">
          {list.isPending && (
            <div className="p-2">
              <TextSkeleton lines={4} label="Đang tải thông báo" />
            </div>
          )}
          {list.error && <ErrorState error={list.error} fallback="Không tải được thông báo." />}
          {list.data && list.data.items.length === 0 && (
            <p className="px-2 py-6 text-center text-crm-sm text-crm-text-3">Không có thông báo</p>
          )}
          {list.data?.items.map((item) => (
            <NotificationRow
              key={item.id}
              item={item}
              onOpen={(target) => {
                setOpen(false);
                openItem(target);
              }}
            />
          ))}
        </div>

        <button
          type="button"
          onClick={() => {
            setOpen(false);
            navigate("/notifications");
          }}
          className="border-t border-crm-line py-2 text-center text-crm-sm font-medium text-crm-accent hover:bg-crm-hover"
        >
          Xem tất cả
        </button>
      </DropdownMenuContent>
    </DropdownMenu>
  );
};
