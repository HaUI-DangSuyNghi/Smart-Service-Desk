import { useState } from "react";
import { IconBell, IconChecks } from "@tabler/icons-react";
import { Button } from "@/platform/shared/ui/button";
import { EmptyState } from "@/platform/shared/ui/EmptyState";
import { ErrorState } from "@/platform/shared/ui/ErrorState";
import { Page, PageBody, PageHeader, PageToolbar } from "@/platform/shared/ui/page";
import { Pagination } from "@/platform/shared/ui/Pagination";
import { SelectField } from "@/platform/shared/ui/SelectField";
import { TextSkeleton } from "@/platform/shared/ui/Skeleton";
import { useCurrentWorkspace } from "../workspace/useCurrentWorkspace";
import { NotificationRow } from "./NotificationRow";
import { useNotifications, useReadAllNotifications } from "./useNotifications";
import { useOpenNotification } from "./useOpenNotification";

const PAGE_SIZE = 20;

/** Mọi thông báo và cảnh báo của tôi (mọi vai trò), lọc chưa đọc, phân trang. */
const NotificationsPage = () => {
  const { current } = useCurrentWorkspace();
  const [filter, setFilter] = useState<"all" | "unread">("all");
  const [page, setPage] = useState(0);
  const list = useNotifications({ unreadOnly: filter === "unread", page, size: PAGE_SIZE });
  const unread = list.data?.unread;
  const readAll = useReadAllNotifications();
  const open = useOpenNotification();

  return (
    <Page>
      <PageHeader
        title="Thông báo"
        icon={IconBell}
        actions={
          <Button
            size="sm"
            variant="outline"
            disabled={!unread || readAll.isPending}
            onClick={() => readAll.mutate()}
          >
            <IconChecks size={14} stroke={2} />
            Đánh dấu tất cả đã đọc
          </Button>
        }
      />
      <PageToolbar
        start={
          <SelectField
            aria-label="Lọc thông báo"
            options={[
              { value: "all", label: "Tất cả" },
              {
                value: "unread",
                label: unread === undefined ? "Chưa đọc" : `Chưa đọc (${unread})`,
              },
            ]}
            value={filter}
            onValueChange={(value) => {
              setFilter(value as "all" | "unread");
              setPage(0);
            }}
          />
        }
        end={
          list.data && (
            <Pagination
              page={page}
              total={list.data.total}
              pageSize={PAGE_SIZE}
              onPageChange={setPage}
            />
          )
        }
      />
      <PageBody>
        {current === null && <EmptyState icon={IconBell} title="Chưa thuộc workspace nào" />}
        {current !== null && list.isPending && (
          <div className="p-4">
            <TextSkeleton lines={6} label="Đang tải thông báo" />
          </div>
        )}
        {list.error && <ErrorState error={list.error} />}
        {list.data && list.data.items.length === 0 && (
          <EmptyState icon={IconBell} title="Không có thông báo" />
        )}
        {list.data && list.data.items.length > 0 && (
          // Sát lề trái như các trang khác
          <div className="flex flex-col p-2">
            {list.data.items.map((item) => (
              <NotificationRow key={item.id} item={item} onOpen={open} />
            ))}
          </div>
        )}
      </PageBody>
    </Page>
  );
};

export default NotificationsPage;
