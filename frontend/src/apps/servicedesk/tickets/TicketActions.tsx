import { useState } from "react";
import { cn } from "cn";
import { useActor } from "../../../platform/human/workspace";
import { useQueue, useTimeline, WorkActions } from "../../../platform/process";
import { Button } from "../../../platform/shared/ui/button";
import type { TicketView } from "../services/ticketService";
import { ConfirmResolution } from "./ConfirmResolution";
import { CorrectClassificationDialog } from "./CorrectClassificationDialog";
import { ReleaseIntakeDialog } from "./ReleaseIntakeDialog";
import { ReviewDialog } from "./ReviewDialog";
import { ticketPermissions } from "./ticketPermissions";
import { useRefreshTickets } from "./useTickets";
import { useViewer } from "./useViewer";

/**
 * Thao tác theo vai trò và trạng thái: điều phối viên tiếp nhận rồi duyệt (R4), người giữ việc
 * Bắt đầu/Tạm dừng/Báo xong (R6), sửa phân loại (R9), người gửi xác nhận (R11).
 * Nhận việc / giao lại vẫn ở phần tiến độ bên dưới.
 */
export const TicketActions = ({ ticket }: { ticket: TicketView }) => {
  const { actor } = useActor();
  const viewer = useViewer();
  const refresh = useRefreshTickets();
  const [dialog, setDialog] = useState<"release" | "review" | "correct" | null>(null);
  const { isHolder, isCreator, canRelease, canReview, canCorrect } = ticketPermissions(
    ticket,
    viewer,
  );
  // Thao tác R6 lấy từ hàng đợi của chính mình (Tầng 2 giữ trạng thái việc)
  const queue = useQueue(undefined, isHolder);
  const queueItem = queue.data?.items.find((item) => item.id === ticket.taskId);
  // Bước xác nhận lấy từ tiến độ (dùng chung cache với phần tiến độ) để chạy được cả với backend
  // hiện tại, khi ticket chưa có trạng thái R6
  const timeline = useTimeline(ticket.process.processInstanceId);
  const confirmTask = timeline.data?.currentTasks.find(
    (task) => task.key === "confirm" && isCreator && task.assignee === actor?.subject,
  );

  if (confirmTask) return <ConfirmResolution taskId={confirmTask.id} />;

  const closeDialog = () => {
    setDialog(null);
    void refresh();
  };

  // Đang tiếp nhận: chưa có việc duyệt, chỉ có nút Tiếp nhận (xong thì sang Duyệt)
  if (canRelease) {
    return (
      <section className="flex flex-wrap items-center gap-2 border-b border-crm-line px-4 py-3">
        <Button size="sm" variant="warning" onClick={() => setDialog("release")}>
          Tiếp nhận
        </Button>
        <ReleaseIntakeDialog
          target={dialog === "release" ? { workspace: ticket.workspace, id: ticket.id } : null}
          onClose={closeDialog}
        />
      </section>
    );
  }

  const { state, taskId } = ticket;
  if (!state || !taskId) return null;

  if (!canReview && !canCorrect && !(isHolder && queueItem)) return null;

  return (
    // Hàng 1: thao tác việc (nút chính trái, ⋯ phải); hàng 2: duyệt / sửa phân loại
    <section className="flex flex-col gap-3 border-b border-crm-line px-4 py-3">
      {isHolder && queueItem && (
        <WorkActions item={queueItem} spread onChanged={() => void refresh()} />
      )}
      {(canReview || canCorrect) && (
        // Có hàng 1 thì kẻ một đường ngăn cách phía trên
        <div
          className={cn(
            "flex flex-wrap items-center gap-2",
            // -mx-4 + px-4: đường kẻ tràn hết bề ngang cột bên, nút vẫn thẳng hàng
            isHolder && queueItem && "-mx-4 border-t border-crm-line px-4 pt-3",
          )}
        >
          {canReview && (
            <Button size="sm" variant="success" onClick={() => setDialog("review")}>
              Duyệt và giao việc
            </Button>
          )}
          {canCorrect && (
            <Button size="sm" variant="info" onClick={() => setDialog("correct")}>
              Sửa phân loại
            </Button>
          )}
        </div>
      )}

      <ReviewDialog ticket={dialog === "review" ? ticket : null} onClose={closeDialog} />
      <CorrectClassificationDialog
        ticket={dialog === "correct" ? ticket : null}
        onClose={closeDialog}
      />
    </section>
  );
};
