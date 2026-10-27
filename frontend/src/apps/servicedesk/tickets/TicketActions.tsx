import { useState } from "react";
import { useActor } from "../../../platform/human/workspace";
import { useQueue, useTimeline, WorkActions } from "../../../platform/process";
import { isWorkflowAvailable } from "../../../platform/services";
import { Button } from "../../../platform/shared/ui/button";
import type { TicketView } from "../services/ticketService";
import { ConfirmResolution } from "./ConfirmResolution";
import { CorrectClassificationDialog } from "./CorrectClassificationDialog";
import { ReviewDialog } from "./ReviewDialog";
import { ticketPermissions } from "./ticketPermissions";
import { useRefreshTickets } from "./useTickets";
import { useViewer } from "./useViewer";

/**
 * Thao tác theo vai trò và trạng thái: điều phối viên duyệt (R4), người giữ việc
 * Bắt đầu/Tạm dừng/Báo xong (R6), sửa phân loại (R9), người gửi xác nhận (R11).
 * Nhận việc / giao lại vẫn ở phần tiến độ bên dưới.
 */
export const TicketActions = ({ ticket }: { ticket: TicketView }) => {
  const { actor } = useActor();
  const viewer = useViewer();
  const refresh = useRefreshTickets();
  const [dialog, setDialog] = useState<"review" | "correct" | null>(null);
  const { isHolder, isCreator, canReview, canCorrect } = ticketPermissions(ticket, viewer);
  // Thao tác R6 lấy từ hàng đợi của chính mình (Tầng 2 giữ trạng thái việc)
  const queue = useQueue(undefined, isWorkflowAvailable && isHolder);
  const queueItem = queue.data?.items.find((item) => item.id === ticket.taskId);
  // Bước xác nhận lấy từ tiến độ (dùng chung cache với phần tiến độ) để chạy được cả với backend
  // hiện tại, khi ticket chưa có trạng thái R6
  const timeline = useTimeline(ticket.process.processInstanceId);
  const confirmTask = timeline.data?.currentTasks.find(
    (task) => task.key === "confirm" && isCreator && task.assignee === actor?.subject,
  );

  if (confirmTask) return <ConfirmResolution taskId={confirmTask.id} />;

  const { state, taskId } = ticket;
  if (!state || !taskId) return null;

  if (!canReview && !canCorrect && !(isHolder && queueItem)) return null;

  const closeDialog = () => {
    setDialog(null);
    void refresh();
  };

  return (
    <section className="flex flex-wrap items-center gap-2 border-b border-crm-line px-4 py-3">
      {canReview && (
        <Button size="sm" onClick={() => setDialog("review")}>
          Duyệt và giao việc
        </Button>
      )}
      {isHolder && queueItem && <WorkActions item={queueItem} onChanged={() => void refresh()} />}
      {canCorrect && (
        <Button size="sm" variant="outline" onClick={() => setDialog("correct")}>
          Sửa phân loại
        </Button>
      )}

      <ReviewDialog ticket={dialog === "review" ? ticket : null} onClose={closeDialog} />
      <CorrectClassificationDialog
        ticket={dialog === "correct" ? ticket : null}
        onClose={closeDialog}
      />
    </section>
  );
};
