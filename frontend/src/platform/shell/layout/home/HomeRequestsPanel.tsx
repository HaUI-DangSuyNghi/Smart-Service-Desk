import { Link } from "react-router";
import { IconChevronRight, IconSend } from "@tabler/icons-react";
import { processLink, useProcessLabels, useRequests } from "@/platform/process";
import { formatRelativeTime } from "@/platform/shared/lib/format";
import { Tag } from "@/platform/shared/ui/Tag";
import { HomePanel, HomePanelContent } from "./HomePanel";

export const HomeRequestsPanel = () => {
  const query = useRequests("mine", 0);
  const labels = useProcessLabels();
  return (
    <HomePanel
      title="Yêu cầu của tôi"
      icon={IconSend}
      to="/requests"
      total={query.isSuccess ? query.data.total : undefined}
    >
      <HomePanelContent
        isPending={query.isPending}
        error={query.error}
        isEmpty={!query.data?.items.length}
        emptyMessage="Chưa có yêu cầu nào."
        label="Đang tải yêu cầu"
        onRetry={() => void query.refetch()}
      >
        {query.data?.items.slice(0, 5).map((request) => (
          <Link
            key={request.id}
            to={processLink(request.processKey, request.businessKey) || `/processes/${request.id}`}
            className="flex min-w-0 items-center gap-3 px-4 py-3 outline-none transition-colors duration-100 hover:bg-crm-hover focus-visible:bg-crm-accent-soft focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-crm-focus"
          >
            <div className="min-w-0 flex-1">
              <p className="truncate font-medium text-crm-text">
                {labels.processName(request.processKey)}
              </p>
              <div className="mt-1 flex min-w-0 flex-wrap gap-x-2 gap-y-1 text-crm-xs text-crm-text-3">
                <span className="max-w-full truncate">{request.businessKey}</span>
                <span>{formatRelativeTime(request.startedAt)}</span>
              </div>
              <div className="mt-2 sm:hidden">
                <Tag tone={request.endedAt ? "gray" : "blue"}>
                  {request.endedAt ? "Đã kết thúc" : "Đang xử lý"}
                </Tag>
              </div>
            </div>
            <span className="hidden shrink-0 sm:block">
              <Tag tone={request.endedAt ? "gray" : "blue"}>
                {request.endedAt ? "Đã kết thúc" : "Đang xử lý"}
              </Tag>
            </span>
            <IconChevronRight size={16} className="shrink-0 text-crm-text-3" />
          </Link>
        ))}
      </HomePanelContent>
    </HomePanel>
  );
};
