import type { TicketView } from "../../services/ticketService";
import { awaitsReview, isHandling } from "../ticketRules";
import type { Viewer } from "../ticketPermissions";

export interface BoardAccess {
  /** Thấy Board và các thẻ (người gửi không có Board). */
  canView: boolean;
  /** Bấm thẻ mở trang chi tiết. */
  canOpen: boolean;
  /** Có menu sửa trên thẻ: điều phối viên và siêu quản trị. */
  canEdit: boolean;
}

export const boardAccess = (viewer: Viewer): BoardAccess => {
  const canView = viewer.canHandle || viewer.canAssign || viewer.isPlatformAdmin;
  return { canView, canOpen: canView, canEdit: viewer.canAssign || viewer.isPlatformAdmin };
};

export type BoardAction = "intake" | "review" | "reassign" | "correct";

/**
 * Mục sửa trên menu của thẻ. Không có "Nhận việc": việc do điều phối viên giao theo tải,
 * để kỹ thuật viên không nhặt việc dễ từ Board.
 */
export const boardActions = (ticket: TicketView, viewer: Viewer): BoardAction[] => {
  if (!boardAccess(viewer).canEdit || !ticket.state) return [];
  // Người tạo ticket không tự tiếp nhận, tự duyệt, tự sửa phân loại (backend trả 403)
  const independent = ticket.creator !== viewer.subject;
  if (ticket.state === "INTAKE") return independent ? ["intake"] : [];
  if (!ticket.taskId) return [];
  if (awaitsReview(ticket)) return independent ? ["review"] : [];
  if (isHandling(ticket)) return independent ? ["reassign", "correct"] : ["reassign"];
  return [];
};
