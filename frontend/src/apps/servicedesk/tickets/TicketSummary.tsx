import type { Ticket } from "../services/ticketService";

/** Nội dung người yêu cầu nhập; người tạo, thời điểm tạo đã có ở phần tiến độ bên dưới. */
export const TicketSummary = ({ ticket }: { ticket: Ticket }) => {
  return (
    <section className="border-b border-crm-line px-4 py-4">
      <h2 className="mb-1 font-medium">Mô tả</h2>
      {/* Văn bản người dùng nhập: hiển thị dạng chữ thuần */}
      <p className="wrap-break-word whitespace-pre-wrap text-crm-text">{ticket.description}</p>
    </section>
  );
};
