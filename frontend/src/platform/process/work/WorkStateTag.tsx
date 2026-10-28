import type { PendingReason, WorkState } from "@/platform/services";
import { Tag } from "@/platform/shared/ui/Tag";
import { PENDING_REASON_LABELS, WORK_STATE_LABELS, WORK_STATE_TONES } from "./workLabels";

interface Props {
  state: WorkState;
  pendingReason?: PendingReason | null;
}

/** Chưa bắt đầu / Đang làm / Đang chờ (R6); đang chờ thì lý do nằm trong title. */
export const WorkStateTag = ({ state, pendingReason }: Props) => {
  return (
    <span title={pendingReason ? PENDING_REASON_LABELS[pendingReason] : undefined}>
      <Tag tone={WORK_STATE_TONES[state]}>{WORK_STATE_LABELS[state]}</Tag>
    </span>
  );
};
