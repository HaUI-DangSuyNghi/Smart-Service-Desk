import { useState } from "react";
import { Link } from "react-router";
import { IconChevronRight, IconInbox } from "@tabler/icons-react";
import { processLink, useInbox, useProcessLabels } from "@/platform/process";
import { formatRelativeTime } from "@/platform/shared/lib/format";
import { Button } from "@/platform/shared/ui/button";
import { HomePanel, HomePanelContent } from "./HomePanel";

export const HomeWorkPanel = () => {
  const [scope, setScope] = useState<"ASSIGNED" | "CANDIDATE">("ASSIGNED");
  // Hai observer riêng để đổi nhóm không hiện tạm công việc của nhóm trước.
  const assigned = useInbox("ASSIGNED", 0);
  const candidate = useInbox("CANDIDATE", 0, scope === "CANDIDATE");
  const query = scope === "ASSIGNED" ? assigned : candidate;
  const labels = useProcessLabels();
  return (
    <HomePanel
      title="Công việc"
      icon={IconInbox}
      to={`/inbox?view=list&scope=${scope}`}
      total={query.isSuccess ? query.data.total : undefined}
      actions={
        <div
          role="group"
          aria-label="Nhóm công việc"
          className="flex gap-1 border-b border-crm-line-light px-3 py-2"
        >
          {(["ASSIGNED", "CANDIDATE"] as const).map((value) => (
            <Button
              key={value}
              type="button"
              aria-pressed={scope === value}
              onClick={() => setScope(value)}
              variant={scope === value ? "info" : "ghost"}
              size="sm"
              className="min-h-9 md:min-h-7"
            >
              {value === "ASSIGNED" ? "Được giao cho tôi" : "Có thể nhận"}
            </Button>
          ))}
        </div>
      }
    >
      <HomePanelContent
        isPending={query.isPending}
        error={query.error}
        isEmpty={!query.data?.items.length}
        emptyMessage={
          scope === "ASSIGNED" ? "Không có công việc được giao." : "Không có công việc có thể nhận."
        }
        label="Đang tải công việc"
        onRetry={() => void query.refetch()}
      >
        {query.data?.items.slice(0, 5).map((task) => (
          <Link
            key={task.id}
            to={
              processLink(task.processKey, task.businessKey) ||
              `/processes/${task.processInstanceId}`
            }
            className="flex min-w-0 items-center gap-3 px-4 py-3 outline-none transition-colors duration-100 hover:bg-crm-hover focus-visible:bg-crm-accent-soft focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-crm-focus"
          >
            <div className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-crm-tone-blue-soft text-crm-tone-blue">
              <IconInbox size={16} />
            </div>
            <div className="min-w-0 flex-1">
              <p className="truncate font-medium text-crm-text">
                {labels.taskName(task.processKey, task.key, task.name)}
              </p>
              <div className="mt-1 flex min-w-0 flex-wrap gap-x-2 gap-y-1 text-crm-xs text-crm-text-3">
                <span className="truncate">{labels.processName(task.processKey)}</span>
                <span className="max-w-full truncate">{task.businessKey}</span>
                <span>{formatRelativeTime(task.createdAt)}</span>
              </div>
            </div>
            <IconChevronRight size={16} className="shrink-0 text-crm-text-3" />
          </Link>
        ))}
      </HomePanelContent>
    </HomePanel>
  );
};
