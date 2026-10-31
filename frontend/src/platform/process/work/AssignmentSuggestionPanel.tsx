import { IconCheck, IconChevronDown, IconSparkles } from "@tabler/icons-react";
import { cn } from "cn";
import type { AssignmentSuggestion } from "@/platform/services";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/platform/shared/ui/dropdown-menu";
import { ErrorState } from "@/platform/shared/ui/ErrorState";
import { TextSkeleton } from "@/platform/shared/ui/Skeleton";
import { CandidateLine } from "./CandidateLine";
import { WAIT_FOR_SHIFT } from "./workLabels";

const WAIT_LABEL = "Để chờ ca sau (chưa giao ai)";

interface Props {
  /**
   * Kết quả truy vấn đề xuất (useQuery); nơi gọi tự chọn nguồn: theo việc đã có
   * (useAssignmentSuggestion) hoặc theo loại / ưu tiên đang chọn ở màn duyệt của app.
   */
  suggestion: { data?: AssignmentSuggestion; isPending: boolean; error: Error | null };
  /** subject đã chọn; WAIT_FOR_SHIFT là để chờ ca sau; undefined là chưa chọn. */
  value: string | undefined;
  onChange: (value: string) => void;
  /** ET đang chọn trong form, để cảnh báo khi dài hơn thời gian ca còn lại. */
  estimateMinutes: number;
}

/**
 * Dropdown chọn người xử lý theo tải và ca trực (R2, R3): người được đề xuất đánh dấu,
 * người bị loại (nghỉ, là người tạo yêu cầu) vẫn hiện kèm lý do nhưng không chọn được.
 */
export const AssignmentSuggestionPanel = ({
  suggestion,
  value,
  onChange,
  estimateMinutes,
}: Props) => {
  if (suggestion.error) {
    return <ErrorState error={suggestion.error} fallback="Không tải được đề xuất người xử lý." />;
  }
  if (suggestion.isPending || !suggestion.data) {
    return <TextSkeleton lines={1} label="Đang tính đề xuất" />;
  }

  const { candidates } = suggestion.data;
  const selected = candidates.find((candidate) => candidate.subject === value);
  const label = value === WAIT_FOR_SHIFT ? WAIT_LABEL : selected?.displayName;

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        aria-label="Người xử lý"
        className={cn(
          "flex h-9 w-full min-w-0 items-center gap-2 rounded-crm-sm border border-crm-line bg-crm-surface px-2 text-left outline-none sm:h-8",
          "transition-colors duration-100 hover:bg-crm-hover focus-visible:border-crm-focus data-[state=open]:bg-crm-hover",
        )}
      >
        <span className={cn("flex-1 truncate", !label && "text-crm-text-3")}>
          {label ?? "Chọn người xử lý"}
        </span>
        {selected?.recommended && (
          <span className="inline-flex shrink-0 items-center gap-0.5 text-crm-sm text-crm-accent">
            <IconSparkles size={12} stroke={2} />
            đề xuất
          </span>
        )}
        <IconChevronDown size={14} stroke={2} className="shrink-0 text-crm-text-3" />
      </DropdownMenuTrigger>

      <DropdownMenuContent
        align="start"
        className="max-h-80 w-(--radix-dropdown-menu-trigger-width) overflow-y-auto"
      >
        {candidates.map((candidate) => (
          <DropdownMenuItem
            key={candidate.subject}
            disabled={Boolean(candidate.excludedReason)}
            onSelect={() => onChange(candidate.subject)}
          >
            <CandidateLine candidate={candidate} estimateMinutes={estimateMinutes} />
            {candidate.subject === value && <IconCheck size={14} stroke={2} />}
          </DropdownMenuItem>
        ))}
        {candidates.length > 0 && <DropdownMenuSeparator />}
        <DropdownMenuItem onSelect={() => onChange(WAIT_FOR_SHIFT)}>
          <span className="flex-1 text-crm-text-2">{WAIT_LABEL}</span>
          {value === WAIT_FOR_SHIFT && <IconCheck size={14} stroke={2} />}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
};
