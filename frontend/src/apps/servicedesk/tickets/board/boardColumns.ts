import type { TagTone } from "../../../../platform/shared/ui/Tag";
import type { TicketState } from "../../services/ticketService";
import { TICKET_STATE_TONES } from "../ticketLabels";

export interface BoardColumnDef {
  key: string;
  label: string;
  /** Trạng thái ticket nằm ở cột này. */
  states: readonly TicketState[];
  tone: TagTone;
  /** Cột dài mãi (Đã đóng): thu gọn, mở mới tải. */
  collapsedByDefault?: boolean;
}

/**
 * Thứ tự theo luồng R6. Không xác định (AI chưa phân loại) gộp vào cột Chờ duyệt,
 * Mở lại gộp vào cột Đã giao (bắt đầu lại cũng là thao tác Bắt đầu).
 */
export const BOARD_COLUMNS: readonly BoardColumnDef[] = [
  {
    key: "review",
    label: "Chờ duyệt",
    states: ["AWAITING_REVIEW", "UNCLASSIFIED"],
    tone: TICKET_STATE_TONES.AWAITING_REVIEW,
  },
  {
    key: "assigned",
    label: "Đã giao",
    states: ["ASSIGNED", "REOPENED"],
    tone: TICKET_STATE_TONES.ASSIGNED,
  },
  {
    key: "inProgress",
    label: "Đang làm",
    states: ["IN_PROGRESS"],
    tone: TICKET_STATE_TONES.IN_PROGRESS,
  },
  { key: "pending", label: "Đang chờ", states: ["PENDING"], tone: TICKET_STATE_TONES.PENDING },
  {
    key: "confirm",
    label: "Chờ xác nhận",
    states: ["AWAITING_CONFIRMATION"],
    tone: TICKET_STATE_TONES.AWAITING_CONFIRMATION,
  },
  {
    key: "closed",
    label: "Đã đóng",
    states: ["CLOSED"],
    tone: TICKET_STATE_TONES.CLOSED,
    collapsedByDefault: true,
  },
];

export const columnOf = (state: TicketState | undefined): BoardColumnDef | undefined =>
  BOARD_COLUMNS.find((column) => state !== undefined && column.states.includes(state));
