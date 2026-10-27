import type { TicketView } from "../services/ticketService";
import { HANDLING_STATES } from "./ticketRules";

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
  const { state } = ticket;
  const isHolder = viewer.subject !== null && ticket.assignee === viewer.subject;
  const isCreator = viewer.subject !== null && ticket.creator === viewer.subject;
  const handling = state !== undefined && HANDLING_STATES.has(state);

  const canAct = isHolder && handling && Boolean(ticket.taskId);
  const canReview = state === "AWAITING_REVIEW" && viewer.canAssign && Boolean(ticket.taskId);
  const canCorrect = handling && Boolean(ticket.taskId) && (isHolder || viewer.canAssign);
  const canConfirm = isCreator && state === "AWAITING_CONFIRMATION";

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
