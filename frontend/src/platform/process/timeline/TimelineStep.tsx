import { IconCheck, IconClockHour4, IconProgress } from "@tabler/icons-react";
import { personName } from "@/platform/human/workspace";
import type { ProcessStep, WorkItem } from "@/platform/services";
import { formatDuration, formatRelativeTime } from "@/platform/shared/lib/format";
import { Button } from "@/platform/shared/ui/button";
import { Tag } from "@/platform/shared/ui/Tag";
import type { ProcessLabels } from "../catalog/processLabels";
import { CandidateGroups } from "../common/CandidateGroups";
import { outcomeTone } from "../common/display";
import { TimelineItem } from "./TimelineItem";

interface Props {
  step: ProcessStep;
  processKey: string;
  labels: ProcessLabels;
  names: ReadonlyMap<string, string>;
  /** Việc đang mở tương ứng (có nhóm xử lý); không có khi bước đã xong. */
  openTask?: WorkItem;
  /** Bước chưa ai nhận và người xem nhận được: hiện nút Nhận việc. */
  onClaim?: (task: WorkItem) => void;
  /** Đang gửi yêu cầu nhận việc của bước này: khóa nút để không nhận trùng. */
  claiming?: boolean;
  /** Bước đang giao cho người xem: hiện nút Xử lý. */
  onComplete?: (task: WorkItem) => void;
  /** Người xem có quyền giao việc: hiện nút Giao việc ở bước đang mở. */
  onAssign?: (task: WorkItem) => void;
}

/** "chờ nhận 3 phút · xử lý 1 giờ 5 phút" của bước đã xong. */
const durations = (step: ProcessStep) => {
  const parts: string[] = [];
  if (step.claimedAt) parts.push(`chờ nhận ${formatDuration(step.startedAt, step.claimedAt)}`);
  if (step.endedAt) {
    parts.push(`xử lý ${formatDuration(step.claimedAt ?? step.startedAt, step.endedAt)}`);
  }
  return parts.join(" · ");
};

/** Một bước của quy trình trên dòng thời gian: đã xong, đang xử lý hoặc đang chờ nhận. */
export const TimelineStep = ({
  step,
  processKey,
  labels,
  names,
  openTask,
  onClaim,
  claiming = false,
  onComplete,
  onAssign,
}: Props) => {
  const title = labels.taskName(processKey, step.key, step.name);
  const assignee = step.assignee ? personName(names, step.assignee) : null;
  // Bước giao thẳng người tạo (không có nhóm xử lý) thì không giao lại được
  const assignable = openTask && onAssign && openTask.candidateGroups.length > 0 ? openTask : null;
  const actions =
    (openTask && (onClaim || onComplete)) || assignable ? (
      <div className="flex flex-wrap gap-2">
        {openTask && onClaim && (
          <Button size="xs" disabled={claiming} onClick={() => onClaim(openTask)}>
            {claiming ? "Đang nhận..." : "Nhận việc"}
          </Button>
        )}
        {openTask && onComplete && (
          <Button size="xs" onClick={() => onComplete(openTask)}>
            Xử lý
          </Button>
        )}
        {assignable && onAssign && (
          <Button size="xs" variant="outline" onClick={() => onAssign(assignable)}>
            {assignable.assignee ? "Giao lại" : "Giao việc"}
          </Button>
        )}
      </div>
    ) : null;

  if (step.endedAt) {
    return (
      <TimelineItem
        icon={IconCheck}
        marker="done"
        title={title}
        badge={
          step.outcome && (
            <Tag tone={outcomeTone(step.outcome)}>
              {labels.outcomeLabel(processKey, step.outcome)}
            </Tag>
          )
        }
      >
        <p className="text-crm-sm text-crm-text-3">
          {assignee ?? "—"} · {formatRelativeTime(step.endedAt)} · {durations(step)}
        </p>
        {step.comment && (
          <blockquote className="mt-1 rounded-crm-sm border border-crm-line-light bg-crm-subtle px-3 py-2 wrap-break-word whitespace-pre-wrap text-crm-text">
            {step.comment}
          </blockquote>
        )}
      </TimelineItem>
    );
  }

  if (assignee) {
    return (
      <TimelineItem
        icon={IconProgress}
        marker="active"
        title={title}
        badge={<Tag tone="blue">Đang xử lý</Tag>}
      >
        <p className="text-crm-sm text-crm-text-3">
          {assignee} đang xử lý · từ {formatRelativeTime(step.claimedAt ?? step.startedAt)}
        </p>
        {actions}
      </TimelineItem>
    );
  }

  return (
    <TimelineItem icon={IconClockHour4} marker="waiting" title={title} badge={<Tag>Chờ nhận</Tag>}>
      <div className="flex flex-wrap items-center gap-1 text-crm-sm text-crm-text-3">
        Chờ nhóm <CandidateGroups groups={openTask?.candidateGroups ?? []} /> nhận ·{" "}
        {formatRelativeTime(step.startedAt)}
      </div>
      {actions}
    </TimelineItem>
  );
};
