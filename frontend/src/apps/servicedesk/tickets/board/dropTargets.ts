import type { TicketView } from "../../services/ticketService";
import { ticketPermissions, type Viewer } from "../ticketPermissions";
import { awaitsReview } from "../ticketRules";
import { boardAccess } from "./boardAccess";
import { BOARD_COLUMNS, columnOf } from "./boardColumns";

/** Việc xảy ra khi thả thẻ vào một cột; mỗi việc là hàm nghiệp vụ hoặc dialog đã có. */
export type DropAction =
  /** Dialog tiếp nhận: chuyển ticket đang tiếp nhận sang Chờ duyệt. */
  | "intake"
  /** Dialog duyệt và giao việc (R4). */
  | "review"
  /** Bắt đầu / Tiếp tục: chạy ngay, không hỏi thêm. */
  | "start"
  | "resume"
  /** Dialog lý do tạm dừng. */
  | "pause"
  /** Dialog báo xong. */
  | "complete"
  /** Dialog trả việc về điều phối viên. */
  | "release";

export type DropTargets = Readonly<Partial<Record<string, DropAction>>>;

const NONE: DropTargets = {};

/**
 * Cột nào nhận được thẻ này, theo quyền của người đang kéo (khóa cột → việc xảy ra):
 * - điều phối viên, siêu quản trị: Tiếp nhận → Chờ duyệt (tiếp nhận), Chờ duyệt → Đã giao
 *   (duyệt và giao việc); không nhảy cóc từ Tiếp nhận sang Đã giao;
 * - kỹ thuật viên giữ việc (quyền task:handle): Bắt đầu / Tiếp tục / Tạm dừng / Báo xong / Trả việc.
 * Siêu quản trị thuộc nhóm đầu: không kéo thẻ của kỹ thuật viên dù ticket đang gán cho họ.
 * Chỉ tác động vào trạng thái mình sửa được; còn lại không kéo được.
 */
export const dropTargets = (ticket: TicketView, viewer: Viewer): DropTargets => {
  // Tiếp nhận không cần việc đang mở (backend tự tìm bước tiếp nhận / duyệt của quy trình)
  if (ticket.state === "INTAKE") {
    return boardAccess(viewer).canEdit && ticket.creator !== viewer.subject
      ? { review: "intake" }
      : NONE;
  }
  if (!ticket.taskId) return NONE;

  if (awaitsReview(ticket)) {
    return boardAccess(viewer).canEdit && ticket.creator !== viewer.subject
      ? { assigned: "review" }
      : NONE;
  }

  // Bắt đầu, tạm dừng, báo xong, trả việc là việc của kỹ thuật viên giữ ticket, không phải của điều phối
  if (viewer.isPlatformAdmin || !viewer.canHandle || !ticketPermissions(ticket, viewer).canAct) {
    return NONE;
  }
  switch (ticket.state) {
    case "ASSIGNED":
    case "REOPENED":
      return { inProgress: "start", review: "release" };
    case "IN_PROGRESS":
      return { pending: "pause", confirm: "complete", review: "release" };
    case "PENDING":
      return { inProgress: "resume", review: "release" };
    default:
      return NONE;
  }
};

const labelOf = (columnKey: string) =>
  BOARD_COLUMNS.find((column) => column.key === columnKey)?.label ?? columnKey;

/** Câu toast ngắn khi thả vào cột không nhận: nói rõ cột nào thì thả được. */
export const dropRejection = (ticket: TicketView, viewer: Viewer): string => {
  const allowed = Object.keys(dropTargets(ticket, viewer)).map(labelOf);
  const source = columnOf(ticket.state)?.label ?? "Ticket";
  if (allowed.length === 0) return `${source}: bạn không có thao tác nào ở trạng thái này.`;
  return `Từ "${source}" chỉ chuyển được sang: ${allowed.join(", ")}.`;
};
