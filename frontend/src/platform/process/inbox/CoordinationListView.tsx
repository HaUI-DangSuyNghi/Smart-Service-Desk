import { useState } from "react";
import { useNavigate } from "react-router";
import { useQuery } from "@tanstack/react-query";
import { IconInbox, IconRefresh } from "@tabler/icons-react";
import { useCurrentWorkspace } from "@/platform/human/workspace";
import type { WorkItem } from "@/platform/services";
import { getCoordinationWork } from "@/platform/services/workCalendarService";
import { useFallbackInterval } from "@/platform/shared/realtime";
import { Button } from "@/platform/shared/ui/button";
import { EmptyState } from "@/platform/shared/ui/EmptyState";
import { ErrorState } from "@/platform/shared/ui/ErrorState";
import { PageBody, PageToolbar } from "@/platform/shared/ui/page";
import { Pagination } from "@/platform/shared/ui/Pagination";
import { TableSkeleton } from "@/platform/shared/ui/Skeleton";
import { processLink } from "../common/processLinks";
import { processKeys } from "../common/queryKeys";
import { AssignTaskDialog } from "../tasks/AssignTaskDialog";
import { InboxTable } from "./InboxTable";
import { INBOX_PAGE_SIZE } from "./useInbox";

const TABS = [
  { key: "all", label: "Tất cả việc" },
  { key: "intake", label: "Tiếp nhận" },
  { key: "review", label: "Duyệt và giao" },
  { key: "work", label: "Đang xử lý" },
] as const;
type Tab = (typeof TABS)[number]["key"];
const matches = (task: WorkItem, tab: Tab) =>
  tab === "all" || (tab === "work" ? !["intake", "review"].includes(task.key) : task.key === tab);

/** Các tab điều phối dùng cùng nguồn việc đang mở, không giới hạn theo ngày. */
export const CoordinationListView = () => {
  const { current } = useCurrentWorkspace();
  const navigate = useNavigate();
  const interval = useFallbackInterval(30_000);
  const [tab, setTab] = useState<Tab>("all");
  const [page, setPage] = useState(0);
  const [assigning, setAssigning] = useState<WorkItem | null>(null);
  const query = useQuery({
    queryKey: [...processKeys.all(current), "coordination-calendar"],
    queryFn: ({ signal }) => getCoordinationWork(current!, signal),
    enabled: current !== null,
    retry: false,
    refetchInterval: interval,
  });
  const tasks = (query.isSuccess ? query.data : []).filter(
    (task) => task.candidateGroups.length > 0,
  );
  const filtered = tasks.filter((task) => matches(task, tab));
  const lastPage = Math.max(0, Math.ceil(filtered.length / INBOX_PAGE_SIZE) - 1);
  const currentPage = Math.min(page, lastPage);
  const items = filtered.slice(currentPage * INBOX_PAGE_SIZE, (currentPage + 1) * INBOX_PAGE_SIZE);

  return (
    <>
      <PageToolbar
        start={
          <div role="tablist" aria-label="Loại việc" className="flex flex-wrap items-center gap-1">
            {TABS.map((item) => (
              <Button
                key={item.key}
                role="tab"
                aria-selected={tab === item.key}
                variant={tab === item.key ? "info" : "outline"}
                size="sm"
                className="min-h-9 md:min-h-7"
                onClick={() => {
                  setTab(item.key);
                  setPage(0);
                }}
              >
                {item.label}
                {query.isSuccess && (
                  <span className="tabular-nums opacity-70">
                    {tasks.filter((task) => matches(task, item.key)).length}
                  </span>
                )}
              </Button>
            ))}
          </div>
        }
        end={
          <>
            {query.isSuccess && (
              <Pagination
                page={currentPage}
                total={filtered.length}
                pageSize={INBOX_PAGE_SIZE}
                onPageChange={setPage}
              />
            )}
            <Button
              variant="outline"
              size="icon-sm"
              className="size-9 md:size-7"
              aria-label="Làm mới danh sách"
              disabled={query.isFetching}
              onClick={() => void query.refetch()}
            >
              <IconRefresh size={16} />
            </Button>
          </>
        }
      />
      <PageBody>
        {query.isPending ? (
          <TableSkeleton columns={7} label="Đang tải công việc" />
        ) : query.error ? (
          <ErrorState error={query.error} />
        ) : items.length === 0 ? (
          <EmptyState icon={IconInbox} title="Không có công việc trong nhóm này." />
        ) : (
          <div className="min-w-0 max-w-full overflow-x-auto [&_button]:min-h-9 md:[&_button]:min-h-7">
            <InboxTable
              scope="ALL"
              items={items}
              onClaim={() => {}}
              onComplete={() => {}}
              onAssign={setAssigning}
              onOpen={(task) =>
                navigate(
                  processLink(task.processKey, task.businessKey) ??
                    `/processes/${task.processInstanceId}`,
                )
              }
            />
          </div>
        )}
      </PageBody>
      <AssignTaskDialog task={assigning} onClose={() => setAssigning(null)} />
    </>
  );
};
