import { Collapsible } from "../../../platform/shared/ui/Collapsible";
import type { Ticket } from "../services/ticketService";

/** Nội dung người yêu cầu nhập; người tạo, thời điểm tạo đã có ở bảng Chi tiết. */
export const TicketSummary = ({ ticket }: { ticket: Ticket }) => {
  return (
    <Collapsible title="Mô tả">
      {/* Văn bản người dùng nhập: hiển thị dạng chữ thuần */}
      <p className="wrap-break-word whitespace-pre-wrap text-crm-text">{ticket.description}</p>
    </Collapsible>
  );
};
