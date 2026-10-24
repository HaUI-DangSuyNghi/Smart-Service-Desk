import { useState } from "react";
import { IconFlag, IconFlagCheck } from "@tabler/icons-react";
import { useActor, useMemberNames } from "@/platform/human/workspace";
import type { ProcessTimeline as Timeline, WorkItem } from "@/platform/services";
import { formatDateTime, formatDuration, formatRelativeTime } from "@/platform/shared/lib/format";
import { FieldRow } from "@/platform/shared/ui/FieldRow";
import { Tag } from "@/platform/shared/ui/Tag";
import { useProcessLabels } from "../catalog/useProcessCatalog";
import { BusinessKeyLink } from "../common/BusinessKeyLink";
import { personName } from "../common/display";
import { CompleteTaskDialog } from "../tasks/CompleteTaskDialog";
import { TimelineItem } from "./TimelineItem";
import { TimelineStep } from "./TimelineStep";

interface Props {
  timeline: Timeline;
}

/**
 * Tiến độ của một quy trình bất kỳ: tóm tắt và dòng thời gian các bước.
 * App nhúng lại component này ở trang chi tiết bản ghi của mình.
 */
export const ProcessTimeline = ({ timeline }: Props) => {
  const labels = useProcessLabels();
  const names = useMemberNames();
  const { actor } = useActor();
  const [completing, setCompleting] = useState<WorkItem | null>(null);

  const { processKey, startedAt, endedAt } = timeline;
  const initiator = personName(names, timeline.initiator);
  // Việc đang mở theo id: có nhóm xử lý và id để hoàn thành ngay từ trang này
  const openTasks = new Map(timeline.currentTasks.map((task) => [task.id, task]));
  const isMine = (task?: WorkItem) => task !== undefined && task.assignee === actor?.subject;

  return (
    <>
      <dl className="border-b border-crm-line px-4 py-3">
        <FieldRow label="Trạng thái">
          {endedAt ? <Tag tone="green">Đã kết thúc</Tag> : <Tag tone="blue">Đang chạy</Tag>}
        </FieldRow>
        <FieldRow label="Mã yêu cầu">
          <BusinessKeyLink processKey={processKey} businessKey={timeline.businessKey} />
        </FieldRow>
        <FieldRow label="Người tạo">{initiator}</FieldRow>
        <FieldRow label="Bắt đầu">{formatDateTime(startedAt)}</FieldRow>
        {endedAt && <FieldRow label="Tổng thời gian">{formatDuration(startedAt, endedAt)}</FieldRow>}
      </dl>

      <section className="px-4 py-4">
        <h2 className="mb-3 font-medium">Các bước</h2>
        <ol>
          <TimelineItem icon={IconFlag} marker="start" title="Tạo yêu cầu">
            <p className="text-crm-sm text-crm-text-3">
              {initiator} · {formatRelativeTime(startedAt)}
            </p>
          </TimelineItem>
          {timeline.steps.map((step) => {
            const openTask = openTasks.get(step.id);
            return (
              <TimelineStep
                key={step.id}
                step={step}
                processKey={processKey}
                labels={labels}
                names={names}
                openTask={openTask}
                onComplete={isMine(openTask) ? setCompleting : undefined}
              />
            );
          })}
          {endedAt && (
            <TimelineItem icon={IconFlagCheck} marker="end" title="Kết thúc">
              <p className="text-crm-sm text-crm-text-3">{formatRelativeTime(endedAt)}</p>
            </TimelineItem>
          )}
        </ol>
      </section>

      <CompleteTaskDialog task={completing} onClose={() => setCompleting(null)} />
    </>
  );
};
