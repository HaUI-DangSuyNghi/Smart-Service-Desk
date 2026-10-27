import { IconSparkles } from "@tabler/icons-react";
import type { AssignmentCandidate, ExcludedReason } from "@/platform/services";
import { ChoiceCard } from "@/platform/shared/ui/ChoiceCard";
import { ErrorState } from "@/platform/shared/ui/ErrorState";
import { TextSkeleton } from "@/platform/shared/ui/Skeleton";
import { useAssignmentSuggestion } from "./useWork";
import { formatMinutes, WAIT_FOR_SHIFT } from "./workLabels";

const EXCLUDED_LABELS: Record<ExcludedReason, string> = {
  OFF_DUTY: "đang nghỉ",
  IS_REQUESTER: "người tạo yêu cầu",
};

interface Props {
  taskId: string;
  /** subject đã chọn; WAIT_FOR_SHIFT là để chờ ca sau; undefined là chưa chọn. */
  value: string | undefined;
  onChange: (value: string) => void;
  /** ET đang chọn trong form, để cảnh báo khi dài hơn thời gian ca còn lại. */
  estimateMinutes: number;
  name: string;
}

const CandidateLine = ({
  candidate,
  estimateMinutes,
}: {
  candidate: AssignmentCandidate;
  estimateMinutes: number;
}) => {
  const overShift =
    candidate.shiftRemainingMinutes != null && estimateMinutes > candidate.shiftRemainingMinutes;
  return (
    <span className="flex flex-col gap-0.5">
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
        {overShift && (
          <span className="text-crm-tone-orange"> · ước tính dài hơn ca còn lại</span>
        )}
      </span>
    </span>
  );
};

/**
 * Đề xuất người xử lý theo tải và ca trực (R2, R3): người được đề xuất đánh dấu,
 * người bị loại (nghỉ, là người tạo yêu cầu) vẫn hiện kèm lý do nhưng không chọn được.
 */
export const AssignmentSuggestionPanel = ({
  taskId,
  value,
  onChange,
  estimateMinutes,
  name,
}: Props) => {
  const suggestion = useAssignmentSuggestion(taskId);

  if (suggestion.isPending) return <TextSkeleton lines={3} label="Đang tính đề xuất" />;
  if (suggestion.error) {
    return <ErrorState error={suggestion.error} fallback="Không tải được đề xuất người xử lý." />;
  }

  const { candidates, reason } = suggestion.data;
  return (
    <div className="flex flex-col gap-1.5">
      <p className="text-crm-sm text-crm-text-3">{reason}</p>
      {candidates.map((candidate) => (
        <ChoiceCard
          key={candidate.subject}
          name={name}
          value={candidate.subject}
          checked={value === candidate.subject}
          disabled={Boolean(candidate.excludedReason)}
          onChange={() => onChange(candidate.subject)}
        >
          <CandidateLine candidate={candidate} estimateMinutes={estimateMinutes} />
        </ChoiceCard>
      ))}
      <ChoiceCard
        name={name}
        value={WAIT_FOR_SHIFT}
        checked={value === WAIT_FOR_SHIFT}
        onChange={() => onChange(WAIT_FOR_SHIFT)}
      >
        <span className="text-crm-text-2">Để chờ ca sau (chưa giao ai)</span>
      </ChoiceCard>
    </div>
  );
};
