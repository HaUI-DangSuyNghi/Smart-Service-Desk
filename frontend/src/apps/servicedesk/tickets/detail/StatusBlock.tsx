import { personName, usePeopleNames } from "../../../../platform/human/workspace";
import { PENDING_REASON_LABELS } from "../../../../platform/process";
import type { TicketView } from "../../services/ticketService";
import { TicketActions } from "../TicketActions";
import { ticketPermissions } from "../ticketPermissions";
import { TicketStateTag } from "../TicketStateTag";
import { useViewer } from "../useViewer";

/**
 * Đầu cột phải trang chi tiết: trạng thái và các nút hành động. Ticket của người khác thì chỉ xem:
 * không có nút, có một dòng giải thích để người dùng không tưởng nút bị mất.
 */
export const StatusBlock = ({ ticket }: { ticket: TicketView }) => {
  const viewer = useViewer();
  const names = usePeopleNames([ticket.assignee]);
  const permissions = ticketPermissions(ticket, viewer);
  const showReadOnlyNote =
    permissions.readOnly &&
    ticket.assignee !== null &&
    ticket.assignee !== undefined &&
    !permissions.isCreator &&
    (viewer.canHandle || viewer.canAssign);

  if (!ticket.state) return null;
  return (
    <div>
      <div className="flex flex-wrap items-center gap-2 border-b border-crm-line px-4 py-3">
        <TicketStateTag state={ticket.state} />
        {ticket.pendingReason && (
          <span className="text-crm-sm text-crm-text-3">
            {PENDING_REASON_LABELS[ticket.pendingReason]}
          </span>
        )}
      </div>
      <TicketActions ticket={ticket} />
      {showReadOnlyNote && ticket.assignee && (
        <p className="border-b border-crm-line px-4 py-3 text-crm-sm text-crm-text-3">
          Ticket này do {personName(names, ticket.assignee)} xử lý, bạn chỉ xem.
        </p>
      )}
    </div>
  );
};
