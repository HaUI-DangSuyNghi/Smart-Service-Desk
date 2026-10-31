import { useState, type MouseEvent } from "react";
import { IconCheck, IconUser } from "@tabler/icons-react";
import { cn } from "cn";
import { avatarClassName, initialsOf } from "@/platform/shared/ui/Avatar";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/platform/shared/ui/dropdown-menu";
import { ErrorState } from "@/platform/shared/ui/ErrorState";
import { TextSkeleton } from "@/platform/shared/ui/Skeleton";
import { toast, toastError } from "@/platform/shared/ui/toast";
import { Tooltip } from "@/platform/shared/ui/Tooltip";
import { assignErrorMessage } from "../tasks/taskErrors";
import { useAssignTask } from "../tasks/useTaskActions";
import { CandidateLine } from "./CandidateLine";
import { useAssignmentSuggestion } from "./useWork";

const UNASSIGNED_CLASS =
  "inline-flex size-6 shrink-0 items-center justify-center rounded-full border border-dashed border-crm-line text-crm-text-3 outline-none focus-visible:ring-2 focus-visible:ring-crm-focus";

interface Props {
  taskId: string;
  /** subject đang giữ việc; null là chưa giao ai. */
  assignee: string | null;
  /** Tên hiển thị người đang giữ, để vẽ avatar. */
  name: string | null;
  onAssigned?: () => void;
}

/**
 * Avatar người xử lý bấm được: mở dropdown danh sách đề xuất (tải khi mở) và giao lại ngay khi chọn.
 * Người đang nghỉ / người tạo yêu cầu hiện kèm lý do nhưng không chọn được.
 */
export const AssigneeMenu = ({ taskId, assignee, name, onAssigned }: Props) => {
  const [open, setOpen] = useState(false);
  const suggestion = useAssignmentSuggestion(open ? taskId : null);
  const assign = useAssignTask();
  // Nằm trong thẻ bấm được để mở chi tiết: chặn cú bấm lan lên thẻ
  const stop = (event: MouseEvent) => event.stopPropagation();
  const label = name ? `${name} · Bấm để giao lại` : "Chưa giao · Bấm để giao việc";

  const handleSelect = (subject: string, displayName: string) => {
    if (subject === assignee) return;
    assign.mutate(
      { task: { id: taskId }, assignee: subject },
      {
        onSuccess: () => {
          toast.success("Đã giao lại", { description: displayName });
          onAssigned?.();
        },
        onError: (error) => toastError(assignErrorMessage(error), error),
      },
    );
  };

  return (
    <DropdownMenu open={open} onOpenChange={setOpen}>
      <Tooltip content={label}>
        <DropdownMenuTrigger
          aria-label={label}
          disabled={assign.isPending}
          onClick={stop}
          className={cn(
            name ? avatarClassName(name) : UNASSIGNED_CLASS,
            "cursor-pointer hover:ring-2 hover:ring-crm-line data-[state=open]:ring-2 data-[state=open]:ring-crm-line",
          )}
        >
          {name ? initialsOf(name) : <IconUser size={14} stroke={2} />}
        </DropdownMenuTrigger>
      </Tooltip>

      <DropdownMenuContent
        align="end"
        className="max-h-80 w-72 max-w-[calc(100vw-2rem)] overflow-y-auto"
        onClick={stop}
      >
        {suggestion.isPending && (
          <div className="p-2">
            <TextSkeleton lines={3} label="Đang tính đề xuất" />
          </div>
        )}
        {suggestion.error && (
          <div className="p-2">
            <ErrorState error={suggestion.error} fallback="Không tải được đề xuất người xử lý." />
          </div>
        )}
        {suggestion.data?.candidates.map((candidate) => (
          <DropdownMenuItem
            key={candidate.subject}
            disabled={Boolean(candidate.excludedReason)}
            onSelect={() => handleSelect(candidate.subject, candidate.displayName)}
          >
            <CandidateLine candidate={candidate} />
            {candidate.subject === assignee && <IconCheck size={14} stroke={2} />}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
};
