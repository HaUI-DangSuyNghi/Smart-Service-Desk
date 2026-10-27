import { Collapsible } from "../../../../platform/shared/ui/Collapsible";
import type { TicketView } from "../../services/ticketService";
import { TicketProgress } from "../TicketProgress";

interface Props {
  ticket: TicketView;
  /** Người gửi hay xem tiến độ nên mở sẵn; người xử lý thu gọn, cần thì mở. */
  defaultOpen: boolean;
}

/** Khối "Tiến độ" ở cột phải: các bước của quy trình. Chỉ tải khi mở. */
export const ProgressCard = ({ ticket, defaultOpen }: Props) => (
  <Collapsible title="Tiến độ" defaultOpen={defaultOpen}>
    {/* Cột phải đã có lề; timeline tự lo lề của nó nên bù lại để không bị thụt đôi */}
    <div className="-mx-4">
      <TicketProgress processInstanceId={ticket.process.processInstanceId} compact />
    </div>
  </Collapsible>
);
