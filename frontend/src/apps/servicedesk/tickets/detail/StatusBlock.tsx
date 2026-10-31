import { personName, usePeopleNames } from "../../../../platform/human/workspace";
import type { TicketView } from "../../services/ticketService";
import { TicketActions } from "../TicketActions";
import { ticketPermissions } from "../ticketPermissions";
import { TicketStateTag } from "../TicketStateTag";
import { useViewer } from "../useViewer";
import { RequestActions } from "../../../../platform/process";
import { useRequestManagement } from "../../../../platform/process/requests/useRequestManagement";
import type { RequestAction } from "../../../../platform/process/requests/RequestActions";

/**
 * Đầu cột phải trang chi tiết: trạng thái và các nút hành động. Ticket của người khác thì chỉ xem:
 * không có nút, có một dòng giải thích để người dùng không tưởng nút bị mất.
 */
export const StatusBlock = ({ ticket }: { ticket: TicketView }) => {
  const viewer = useViewer();
  const names = usePeopleNames([ticket.assignee]);
  const permissions = ticketPermissions(ticket, viewer);
  const management = useRequestManagement(ticket.process.processInstanceId, permissions.isCreator);
  const actions: RequestAction[] = [];
  if (permissions.isCreator && management.data) {
    if (management.data.editable) actions.push("edit");
    if (management.data.cancellable) actions.push("cancel");
    if (management.data.closable) actions.push("close");
  }
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
      </div>
      <TicketActions ticket={ticket} />
      {permissions.isCreator && management.error && (
        <p role="alert" className="border-b border-crm-line px-4 py-3 text-crm-sm text-crm-text-3">
          Chưa tải được quyền sửa, hủy hoặc đóng ticket.
          <button
            type="button"
            className="ml-1 underline"
            onClick={() => void management.refetch()}
          >
            Thử lại
          </button>
        </p>
      )}
      {actions.length > 0 && (
        <div className="border-b border-crm-line px-4 py-3">
          <RequestActions processId={ticket.process.processInstanceId} actions={actions} />
        </div>
      )}
      {showReadOnlyNote && ticket.assignee && (
        <p className="border-b border-crm-line px-4 py-3 text-crm-sm text-crm-text-3">
          Ticket này do {personName(names, ticket.assignee)} xử lý, bạn chỉ xem.
        </p>
      )}
    </div>
  );
};
