import { useState, type MouseEvent } from "react";
import { IconDots } from "@tabler/icons-react";
import { useActor } from "@/platform/human/workspace";
import type { QueueItem } from "@/platform/services";
import { Button } from "@/platform/shared/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/platform/shared/ui/dropdown-menu";
import { toast, toastError } from "@/platform/shared/ui/toast";
import { CompleteTaskDialog } from "../tasks/CompleteTaskDialog";
import { EstimateRequestDialog } from "./EstimateRequestDialog";
import { PauseDialog } from "./PauseDialog";
import { ReleaseDialog } from "./ReleaseDialog";
import { useResumeWork, useStartWork } from "./useWork";
import { workErrorMessage } from "./workErrors";

interface Props {
  item: QueueItem;
  /** Nút cỡ nhỏ cho dòng bảng; mặc định cỡ thường cho trang chi tiết. */
  compact?: boolean;
  /** Việc nên làm ngay (đầu hàng, chưa có việc đang làm): nút Bắt đầu nổi bật. */
  suggested?: boolean;
  /** Gọi sau mỗi thao tác để nơi nhúng (trang của app) tải lại dữ liệu của mình. */
  onChanged?: () => void;
}

type OpenDialog = "complete" | "pause" | "release" | "estimate" | null;

// Nút trong dòng bảng: chặn cú bấm lan lên dòng (dòng bấm được để mở chi tiết)
const stopPropagation = (event: MouseEvent) => event.stopPropagation();

/**
 * Thao tác R6 của người đang giữ việc: Bắt đầu / Báo xong / Tiếp tục là nút chính,
 * Tạm dừng, Xin sửa ước tính, Trả việc nằm trong menu. Người khác không thấy gì.
 */
export const WorkActions = ({ item, compact = false, suggested = false, onChanged }: Props) => {
  const { actor } = useActor();
  const start = useStartWork();
  const resume = useResumeWork();
  const [dialog, setDialog] = useState<OpenDialog>(null);

  if (!actor || item.assignee !== actor.subject) return null;

  const isBusy = start.isPending || resume.isPending;
  const size = compact ? "xs" : "sm";
  const closeDialog = () => {
    setDialog(null);
    onChanged?.();
  };

  const handleStart = () =>
    start.mutate(item.id, {
      onSuccess: () => {
        toast.success("Đã bắt đầu", { description: item.label });
        onChanged?.();
      },
      onError: (error) => toastError(workErrorMessage(error, "Không bắt đầu được."), error),
    });

  const handleResume = () =>
    resume.mutate(item.id, {
      onSuccess: () => {
        toast.success("Đã tiếp tục", { description: item.label });
        onChanged?.();
      },
      onError: (error) => toastError(workErrorMessage(error, "Không tiếp tục được."), error),
    });

  const primary = {
    NOT_STARTED: { label: "Bắt đầu", busyLabel: "Đang gửi...", onClick: handleStart },
    IN_PROGRESS: { label: "Báo xong", busyLabel: "Báo xong", onClick: () => setDialog("complete") },
    PENDING: { label: "Tiếp tục", busyLabel: "Đang gửi...", onClick: handleResume },
  }[item.workState];

  return (
    <div className="flex items-center gap-1" onClick={stopPropagation}>
      <Button
        size={size}
        variant={suggested && item.workState === "NOT_STARTED" ? "default" : "outline"}
        className={compact ? "flex-1" : undefined}
        disabled={isBusy}
        onClick={primary.onClick}
      >
        {isBusy ? primary.busyLabel : primary.label}
      </Button>

      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          {/* Vùng bấm ≥ 36px trên điện thoại (rules/07), thu về cỡ Twenty từ md */}
          <Button
            size={compact ? "icon-xs" : "icon-sm"}
            variant="ghost"
            aria-label="Thao tác khác"
            className={compact ? "size-9 md:size-6" : "size-9 md:size-7"}
          >
            <IconDots size={16} stroke={2} />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          {item.workState === "IN_PROGRESS" && (
            <DropdownMenuItem onSelect={() => setDialog("pause")}>Tạm dừng</DropdownMenuItem>
          )}
          <DropdownMenuItem onSelect={() => setDialog("estimate")}>
            Xin sửa ước tính
          </DropdownMenuItem>
          <DropdownMenuItem onSelect={() => setDialog("release")}>Trả việc</DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>

      <CompleteTaskDialog task={dialog === "complete" ? item : null} onClose={closeDialog} />
      <PauseDialog item={dialog === "pause" ? item : null} onClose={closeDialog} />
      <ReleaseDialog item={dialog === "release" ? item : null} onClose={closeDialog} />
      <EstimateRequestDialog item={dialog === "estimate" ? item : null} onClose={closeDialog} />
    </div>
  );
};
