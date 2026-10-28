import type { TicketView } from "../services/ticketService";
import { awaitsReview, isHandling } from "./ticketRules";

/** Người đang xem ticket; `subject` null khi chưa biết vai trò (đang tải). */
export interface Viewer {
  subject: string | null;
  canHandle: boolean;
  canAssign: boolean;
  isPlatformAdmin: boolean;
}

export interface TicketPermissions {
  isHolder: boolean;
  isCreator: boolean;
  /** Người giữ việc Bắt đầu / Tạm dừng / Báo xong / Trả việc / Xin sửa ước tính (R6). */
  canAct: boolean;
  /** Điều phối viên duyệt và giao việc (R4). */
  canReview: boolean;
  /** Người giữ việc hoặc điều phối viên sửa phân loại (R9). */
  canCorrect: boolean;
  /** Người gửi xác nhận kết quả (R11). */
  canConfirm: boolean;
  /** Không làm được thao tác nào: chỉ xem. */
  readOnly: boolean;
}

/**
 * Quyền thao tác trên một ticket ở trang chi tiết: theo người giữ việc, không theo "có quyền handle".
 * Kỹ thuật viên A chỉ xem ticket của B; ticket của mình thì đổi trạng thái, hành động được.
 */
export const ticketPermissions = (ticket: TicketView, viewer: Viewer): TicketPermissions => {
  const isHolder = viewer.subject !== null && ticket.assignee === viewer.subject;
  const isCreator = viewer.subject !== null && ticket.creator === viewer.subject;
  const handling = isHandling(ticket);
  const hasTask = Boolean(ticket.taskId);

  const canAct = isHolder && handling && hasTask;
  // Backend cấm người tạo ticket tự duyệt hoặc tự sửa phân loại (cần người độc lập)
  const canReview = awaitsReview(ticket) && viewer.canAssign && hasTask && !isCreator;
  const canCorrect = handling && hasTask && (isHolder || viewer.canAssign) && !isCreator;
  const canConfirm = isCreator && ticket.state === "AWAITING_CONFIRMATION";

  return {
    isHolder,
    isCreator,
    canAct,
    canReview,
    canCorrect,
    canConfirm,
    readOnly: !(canAct || canReview || canCorrect || canConfirm),
  };
};
