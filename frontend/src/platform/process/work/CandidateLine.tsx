import { IconSparkles } from "@tabler/icons-react";
import type { AssignmentCandidate, ExcludedReason } from "@/platform/services";
import { formatMinutes } from "./workLabels";

const EXCLUDED_LABELS: Record<ExcludedReason, string> = {
  OFF_DUTY: "đang nghỉ",
  IS_REQUESTER: "người tạo yêu cầu",
};

interface Props {
  candidate: AssignmentCandidate;
  /** ET đang chọn, để cảnh báo khi dài hơn thời gian ca còn lại; không có thì không cảnh báo. */
  estimateMinutes?: number;
}

/** Một người trong danh sách đề xuất: tên, nhãn đề xuất / lý do bị loại, tải và số việc hôm nay. */
export const CandidateLine = ({ candidate, estimateMinutes }: Props) => {
  const overShift =
    estimateMinutes !== undefined &&
    candidate.shiftRemainingMinutes != null &&
    estimateMinutes > candidate.shiftRemainingMinutes;
  return (
    <span className="flex min-w-0 flex-1 flex-col gap-0.5">
      <span className="flex flex-wrap items-center gap-x-2">
        <span className="font-medium">{candidate.displayName}</span>
        {candidate.recommended && (
          <span className="inline-flex items-center gap-0.5 text-crm-sm text-crm-accent">
            <IconSparkles size={12} stroke={2} />
            đề xuất
          </span>
        )}
        {candidate.excludedReason && (
          <span className="text-crm-sm text-crm-text-3">
            {EXCLUDED_LABELS[candidate.excludedReason]}
          </span>
        )}
      </span>
      <span className="text-crm-sm text-crm-text-3">
        Tải {formatMinutes(candidate.loadMinutes)} · hôm nay {candidate.assignedToday} việc
        {overShift && <span className="text-crm-tone-orange"> · ước tính dài hơn ca còn lại</span>}
      </span>
    </span>
  );
};
