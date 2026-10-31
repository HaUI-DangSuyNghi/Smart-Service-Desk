import { useState } from "react";
import { IconFlag, IconFlagCheck, IconEdit, IconX } from "@tabler/icons-react";
import {
  PERMISSIONS,
  useActor,
  usePeopleNames,
  usePermissions,
  personName,
} from "@/platform/human/workspace";
import type { ProcessTimeline as Timeline, WorkItem } from "@/platform/services";
import { formatDateTime, formatDuration, formatRelativeTime } from "@/platform/shared/lib/format";
import { FieldRow } from "@/platform/shared/ui/FieldRow";
import { Tag } from "@/platform/shared/ui/Tag";
import { toast, toastError } from "@/platform/shared/ui/toast";
import { useProcessLabels } from "../catalog/useProcessCatalog";
import { BusinessKeyLink } from "../common/BusinessKeyLink";
import { groupRole } from "../common/display";
import { AssignTaskDialog } from "../tasks/AssignTaskDialog";
import { CompleteTaskDialog } from "../tasks/CompleteTaskDialog";
import { claimErrorMessage } from "../tasks/taskErrors";
import { useClaimTask } from "../tasks/useTaskActions";
import { requestContent } from "./requestContent";
import { TimelineItem } from "./TimelineItem";
import { TimelineStep } from "./TimelineStep";
import { useRequestManagement } from "../requests/useRequestManagement";

interface Props {
  timeline: Timeline;
  /** Chỉ dòng thời gian các bước, không tóm tắt và nội dung: cho cột hẹp bên cạnh trang của app. */
  compact?: boolean;
  /** Các bước chỉ theo dõi tiến độ, không nhận/giao việc tại đây. */
  allocationHiddenSteps?: readonly string[];
}

/**
 * Tiến độ của một quy trình bất kỳ: tóm tắt và dòng thời gian các bước.
 * App nhúng lại component này ở trang chi tiết bản ghi của mình.
 */
export const ProcessTimeline = ({
  timeline,
  compact = false,
  allocationHiddenSteps = [],
}: Props) => {
  const labels = useProcessLabels();
  const management = useRequestManagement(timeline.id);
  // Chỉ hỏi tên của những người có mặt trên trang, không cần quyền xem cả danh bạ
  const names = usePeopleNames([
    timeline.initiator,
    ...timeline.steps.map((step) => step.assignee),
    ...(management.data?.history.map((change) => change.actor) ?? []),
  ]);
  const { actor } = useActor();
  const { can } = usePermissions();
  const [completing, setCompleting] = useState<WorkItem | null>(null);
  const [assigning, setAssigning] = useState<WorkItem | null>(null);
  const claim = useClaimTask();

  const { processKey, startedAt, endedAt } = timeline;
  const initiator = personName(names, timeline.initiator);
  const content = requestContent(labels.startFields(processKey), timeline.variables);
  // Việc đang mở theo id: có nhóm xử lý và id để hoàn thành ngay từ trang này
  const openTasks = new Map(timeline.currentTasks.map((task) => [task.id, task]));
  const isMine = (task?: WorkItem) => task !== undefined && task.assignee === actor?.subject;
  // Cùng luật với Core: bước chưa ai giữ, mình thuộc nhóm xử lý và không phải người tạo yêu cầu
  const canClaim = (task?: WorkItem) =>
    task !== undefined &&
    actor !== null &&
    task.assignee === null &&
    can(PERMISSIONS.taskHandle) &&
    actor.subject !== timeline.initiator &&
    task.candidateGroups.some((group) => actor.roles.includes(groupRole(group)));

  const handleClaim = (task: WorkItem) => {
    claim.mutate(task, {
      onSuccess: () =>
        toast.success("Đã nhận việc", { description: "Bấm “Xử lý” khi đã có kết quả." }),
      onError: (error) => toastError(claimErrorMessage(error), error),
    });
  };

  return (
    <>
      {!compact && (
        <dl className="border-b border-crm-line px-4 py-3">
          <FieldRow label="Trạng thái">
            {timeline.cancelled ? (
              <Tag tone="gray">Đã hủy</Tag>
            ) : endedAt ? (
              <Tag tone="green">Đã kết thúc</Tag>
            ) : (
              <Tag tone="blue">Đang chạy</Tag>
            )}
          </FieldRow>
          <FieldRow label="Mã yêu cầu">
            <BusinessKeyLink processKey={processKey} businessKey={timeline.businessKey} />
          </FieldRow>
          <FieldRow label="Người tạo">{initiator}</FieldRow>
          <FieldRow label="Bắt đầu">{formatDateTime(startedAt)}</FieldRow>
          {endedAt && (
            <FieldRow label="Tổng thời gian">{formatDuration(startedAt, endedAt)}</FieldRow>
          )}
        </dl>
      )}

      {/* Quy trình gắn app (ticket, đặt phòng) có thể không có ô nào; nội dung nằm ở trang của app */}
      {!compact && content.length > 0 && (
        <section className="border-b border-crm-line px-4 py-4">
          <h2 className="mb-1 font-medium">Nội dung</h2>
          <dl>
            {content.map((item) => (
              <FieldRow key={item.key} label={item.label}>
                <p className="break-normal wrap-break-word whitespace-pre-wrap">{item.value}</p>
              </FieldRow>
            ))}
          </dl>
        </section>
      )}

      <section className={compact ? "px-4 pt-1 pb-3" : "px-4 py-4"}>
        {!compact && <h2 className="mb-3 font-medium">Các bước</h2>}
        <ol>
          <TimelineItem icon={IconFlag} marker="start" title="Tạo yêu cầu">
            <p className="text-crm-sm text-crm-text-3">
              {initiator} · {formatRelativeTime(startedAt)}
            </p>
          </TimelineItem>
          {[
            ...timeline.steps.map((step) => ({ kind: "step" as const, at: step.startedAt, step })),
            ...(management.data?.history ?? []).map((change) => ({
              kind: "change" as const,
              at: change.createdAt,
              change,
            })),
          ]
            .sort((a, b) => Date.parse(a.at) - Date.parse(b.at))
            .map((entry) => {
              if (entry.kind === "change") {
                const change = entry.change;
                return (
                  <TimelineItem
                    key={change.id}
                    marker="done"
                    icon={
                      change.action === "UPDATED"
                        ? IconEdit
                        : change.action === "CANCELLED"
                          ? IconX
                          : IconFlagCheck
                    }
                    title={
                      change.action === "UPDATED"
                        ? "Sửa yêu cầu"
                        : change.action === "CANCELLED"
                          ? "Hủy yêu cầu"
                          : "Đóng yêu cầu"
                    }
                  >
                    <p className="text-crm-sm text-crm-text-3">
                      {personName(names, change.actor)} · {formatDateTime(change.createdAt)}
                    </p>
                    <p className="text-crm-sm whitespace-pre-wrap wrap-break-word">
                      {change.reason}
                    </p>
                    {change.action === "UPDATED" && (
                      <details className="text-crm-sm">
                        <summary className="cursor-pointer text-crm-text-2">
                          Nội dung đã sửa
                        </summary>
                        {management.data?.fields
                          .filter(
                            (field) =>
                              change.beforeValues[field.key] !== change.afterValues[field.key],
                          )
                          .map((field) => (
                            <div key={field.key} className="mt-2 min-w-0">
                              <p className="font-medium">{field.label}</p>
                              <p className="text-crm-text-3 whitespace-pre-wrap wrap-break-word">
                                Trước: {change.beforeValues[field.key]}
                              </p>
                              <p className="whitespace-pre-wrap wrap-break-word">
                                Sau: {change.afterValues[field.key]}
                              </p>
                            </div>
                          ))}
                      </details>
                    )}
                  </TimelineItem>
                );
              }
              const step = entry.step;
              const openTask = openTasks.get(step.id);
              const showAllocation = !allocationHiddenSteps.includes(step.key);
              return (
                <TimelineStep
                  key={step.id}
                  step={step}
                  processKey={processKey}
                  labels={labels}
                  names={names}
                  openTask={openTask}
                  onClaim={showAllocation && canClaim(openTask) ? handleClaim : undefined}
                  claiming={claim.isPending && claim.variables?.id === step.id}
                  onComplete={isMine(openTask) ? setCompleting : undefined}
                  onAssign={
                    showAllocation && can(PERMISSIONS.taskAssign) ? setAssigning : undefined
                  }
                />
              );
            })}
          {endedAt && (
            <TimelineItem
              icon={IconFlagCheck}
              marker="end"
              title={timeline.cancelled ? "Đã hủy yêu cầu" : "Kết thúc"}
            >
              <p className="text-crm-sm text-crm-text-3">{formatRelativeTime(endedAt)}</p>
            </TimelineItem>
          )}
        </ol>
        {management.error && (
          <p role="alert" className="mt-3 text-crm-sm text-crm-text-3">
            Chưa tải được lịch sử sửa, hủy và đóng yêu cầu.
            <button
              type="button"
              className="ml-1 underline"
              onClick={() => void management.refetch()}
            >
              Thử lại
            </button>
          </p>
        )}
      </section>

      <CompleteTaskDialog task={completing} onClose={() => setCompleting(null)} />
      <AssignTaskDialog task={assigning} onClose={() => setAssigning(null)} />
    </>
  );
};
