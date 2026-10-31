import { Link } from "react-router";
import { IconArrowUpRight, IconClock } from "@tabler/icons-react";
import { cn } from "cn";
import type { QueueItem } from "@/platform/services";
import { formatDateTime } from "@/platform/shared/lib/format";
import { Skeleton } from "@/platform/shared/ui/Skeleton";
import { Tag } from "@/platform/shared/ui/Tag";
import { useProcessLabels } from "../../catalog/useProcessCatalog";
import { processLink } from "../../common/processLinks";
import { PriorityTag } from "../../work/PriorityTag";
import { WorkActions } from "../../work/WorkActions";
import { formatMinutes } from "../../work/workLabels";
import { WorkStateTag } from "../../work/WorkStateTag";
import { isWorkOverdue, workDeadline } from "./calendarModel";

interface Props {
  items: readonly QueueItem[];
  now: number;
  showDate: boolean;
}

export const WorkAgenda = ({ items, now, showDate }: Props) => {
  const labels = useProcessLabels();
  return (
    <div className="flex flex-col gap-3" aria-label="Lịch công việc">
      {items.map((item) => {
        const deadline = workDeadline(item);
        const isOverdue = isWorkOverdue(item, now);
        return (
          <article
            key={item.id}
            aria-label={item.label || item.name}
            className="flex min-w-0 gap-3 sm:gap-4"
          >
            <div className="flex w-12 shrink-0 flex-col items-end gap-1 pt-4 text-crm-sm tabular-nums text-crm-text-2 sm:w-16">
              {deadline ? (
                <time
                  dateTime={deadline}
                  title={`Hạn hoàn thành: ${formatDateTime(deadline)}`}
                  className={cn("text-right", isOverdue && "text-crm-danger")}
                >
                  {showDate && (
                    <span className="mb-1 block text-crm-xs">
                      {new Date(deadline).toLocaleDateString("vi-VN", {
                        day: "2-digit",
                        month: "2-digit",
                      })}
                    </span>
                  )}
                  {new Date(deadline).toLocaleTimeString("vi-VN", {
                    hour: "2-digit",
                    minute: "2-digit",
                  })}
                </time>
              ) : (
                <IconClock size={16} />
              )}
            </div>
            <div
              className={cn(
                "relative min-w-0 flex-1 rounded-crm-md border border-crm-line border-l-2 bg-crm-surface p-3 sm:p-4",
                isOverdue ? "border-l-crm-danger" : "border-l-crm-accent",
              )}
            >
              <div className="flex flex-wrap items-center gap-1.5">
                <WorkStateTag state={item.workState} pendingReason={item.pendingReason} />
                <PriorityTag
                  priority={item.priority}
                  escalated={item.priority !== item.basePriority}
                />
                {isOverdue && <Tag tone="red">Quá hạn</Tag>}
                {item.sla?.clockPaused && <Tag tone="gray">Tạm dừng SLA</Tag>}
              </div>
              <Link
                to={
                  processLink(item.processKey, item.businessKey) ??
                  `/processes/${item.processInstanceId}`
                }
                className="mt-2 flex min-w-0 items-start gap-2 rounded-crm-sm font-medium text-crm-text outline-none hover:text-crm-accent focus-visible:ring-2 focus-visible:ring-crm-focus"
              >
                <span className="min-w-0 flex-1 break-words">
                  {item.label || labels.taskName(item.processKey, item.key, item.name)}
                </span>
                <IconArrowUpRight size={16} className="mt-0.5 shrink-0 text-crm-text-3" />
              </Link>
              <div className="mt-1 flex flex-wrap gap-x-2 gap-y-1 text-crm-xs text-crm-text-3">
                <span>{labels.taskName(item.processKey, item.key, item.name)}</span>
                <span className="max-w-full truncate">{item.businessKey}</span>
              </div>
              <div className="mt-3 flex flex-wrap items-center justify-between gap-2 border-t border-crm-line-light pt-2.5">
                <span className="text-crm-xs text-crm-text-2">
                  Ước tính còn {formatMinutes(item.remainingMinutes)}
                </span>
                <div className="[&_button]:min-h-9 md:[&_button]:min-h-7">
                  <WorkActions item={item} />
                </div>
              </div>
            </div>
          </article>
        );
      })}
    </div>
  );
};

export const WorkAgendaSkeleton = () => (
  <div role="status" aria-label="Đang tải lịch công việc" aria-busy className="flex flex-col gap-3">
    {[0, 1, 2].map((row) => (
      <div key={row} className="flex gap-3">
        <Skeleton className="mt-4 w-12 shrink-0 sm:w-16" />
        <div className="flex min-w-0 flex-1 flex-col gap-3 rounded-crm-md border border-crm-line p-4">
          <Skeleton className="w-1/3" />
          <Skeleton className="w-3/4" />
          <Skeleton className="w-1/2" />
          <Skeleton className="mt-2 w-1/4" />
        </div>
      </div>
    ))}
  </div>
);
