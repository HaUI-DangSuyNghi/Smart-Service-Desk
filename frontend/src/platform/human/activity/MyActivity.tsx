import { useState, type ComponentType } from "react";
import { Link } from "react-router";
import {
  IconChecks,
  IconDots,
  IconFileText,
  IconHandGrab,
  IconHistory,
  IconPaperclip,
  IconSend,
} from "@tabler/icons-react";
import { cn } from "cn";
import { outcomeTone, useProcessLabels, type ProcessLabels } from "@/platform/process";
import type { ActivityEntry } from "@/platform/services";
import { formatDateTime, formatRelativeTime } from "@/platform/shared/lib/format";
import { EmptyState } from "@/platform/shared/ui/EmptyState";
import { ErrorState } from "@/platform/shared/ui/ErrorState";
import { Pagination } from "@/platform/shared/ui/Pagination";
import { TextSkeleton } from "@/platform/shared/ui/Skeleton";
import { Tag } from "@/platform/shared/ui/Tag";
import { describeActivity, type ActivityKind } from "./describeActivity";
import { ACTIVITY_PAGE_SIZE, useMyActivity } from "./useMyActivity";

const KIND_ICON: Record<ActivityKind, ComponentType<{ size?: number; stroke?: number; className?: string }>> = {
  request: IconSend,
  claim: IconHandGrab,
  complete: IconChecks,
  document: IconFileText,
  file: IconPaperclip,
  other: IconDots,
};

const ActivityRow = ({ entry, labels }: { entry: ActivityEntry; labels: ProcessLabels }) => {
  const view = describeActivity(entry, labels);
  const Icon = KIND_ICON[view.kind];

  const content = (
    <>
      <Icon size={16} stroke={2} className="mt-0.5 shrink-0 text-crm-text-3" />
      <span className="flex min-w-0 flex-1 flex-col gap-0.5 sm:flex-row sm:items-center sm:gap-2">
        <span className="truncate text-crm-text">{view.title}</span>
        {view.detail && <span className="truncate text-crm-sm text-crm-text-3">{view.detail}</span>}
        {view.outcome && entry.processKey && (
          <span className="sm:ml-auto">
            <Tag tone={outcomeTone(view.outcome)}>{labels.outcomeLabel(entry.processKey, view.outcome)}</Tag>
          </span>
        )}
      </span>
      <time
        dateTime={entry.occurredAt}
        title={formatDateTime(entry.occurredAt)}
        className="shrink-0 text-crm-sm text-crm-text-3"
      >
        {formatRelativeTime(entry.occurredAt)}
      </time>
    </>
  );

  const rowClass = "flex items-start gap-2 px-3 py-2 sm:items-center";
  return (
    <li>
      {view.href ? (
        <Link to={view.href} className={cn(rowClass, "transition-colors duration-100 hover:bg-crm-hover")}>
          {content}
        </Link>
      ) : (
        <div className={rowClass}>{content}</div>
      )}
    </li>
  );
};

/** Hoạt động của tôi: những gì chính người dùng đã làm ở workspace đang chọn (lấy từ audit). */
export const MyActivity = () => {
  const [page, setPage] = useState(0);
  const activity = useMyActivity(page);
  const labels = useProcessLabels();
  const items = activity.data?.items ?? [];

  return (
    <section className="flex flex-col gap-2">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="font-medium">Hoạt động của tôi</h2>
        {activity.data && activity.data.total > ACTIVITY_PAGE_SIZE && (
          <Pagination
            page={page}
            total={activity.data.total}
            pageSize={ACTIVITY_PAGE_SIZE}
            onPageChange={setPage}
          />
        )}
      </div>
      {activity.isPending && <TextSkeleton lines={5} label="Đang tải hoạt động" />}
      {activity.error && <ErrorState error={activity.error} />}
      {activity.data && items.length === 0 && (
        <EmptyState
          icon={IconHistory}
          title="Chưa có hoạt động nào"
          description="Yêu cầu bạn tạo, việc bạn nhận và hoàn thành sẽ hiện ở đây."
        />
      )}
      {items.length > 0 && (
        <ul className="divide-y divide-crm-line-light rounded-crm-sm border border-crm-line">
          {items.map((entry) => (
            <ActivityRow key={entry.id} entry={entry} labels={labels} />
          ))}
        </ul>
      )}
    </section>
  );
};
