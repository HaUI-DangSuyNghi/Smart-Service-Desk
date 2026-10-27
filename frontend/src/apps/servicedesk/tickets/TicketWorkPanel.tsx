import { personName, usePeopleNames } from "../../../platform/human/workspace";
import {
  EstimateText,
  formatMinutes,
  PENDING_REASON_LABELS,
  PriorityTag,
  SlaIndicator,
} from "../../../platform/process";
import { formatDateTime, formatRelativeTime } from "../../../platform/shared/lib/format";
import { FieldRow } from "../../../platform/shared/ui/FieldRow";
import type { TicketView } from "../services/ticketService";
import { CATEGORY_LABELS, groupLabel } from "./ticketLabels";
import { TicketStateTag } from "./TicketStateTag";

interface Props {
  ticket: TicketView;
  /** Người gửi chỉ thấy trạng thái, dự kiến bắt đầu và hạn; không thấy tải, ước tính. */
  isRequesterView: boolean;
}

const STARTED_STATES = new Set(["IN_PROGRESS", "PENDING"]);

/** Thông tin xử lý ticket (R4–R6, R11); backend cũ chưa trả trạng thái thì không hiện. */
export const TicketWorkPanel = ({ ticket, isRequesterView }: Props) => {
  const names = usePeopleNames([ticket.assignee]);
  const { state, classification, estimate, sla } = ticket;
  if (!state) return null;

  const started = STARTED_STATES.has(state) || (ticket.workedMinutes ?? 0) > 0;
  return (
    <section className="border-b border-crm-line px-4 py-3">
      <h2 className="mb-1 font-medium">Xử lý</h2>
      <dl>
        <FieldRow label="Trạng thái">
          <span className="flex flex-wrap items-center gap-2">
            <TicketStateTag state={state} />
            {ticket.pendingReason && (
              <span className="text-crm-sm text-crm-text-3">
                {PENDING_REASON_LABELS[ticket.pendingReason]}
              </span>
            )}
          </span>
        </FieldRow>
        {classification && (
          <>
            <FieldRow label="Loại sự cố">{CATEGORY_LABELS[classification.category]}</FieldRow>
            <FieldRow label="Ưu tiên">
              <PriorityTag
                priority={classification.priority}
                escalated={classification.escalated}
              />
            </FieldRow>
            {!isRequesterView && <FieldRow label="Tổ">{groupLabel(classification.group)}</FieldRow>}
          </>
        )}
        {ticket.assignee && (
          <FieldRow label="Người xử lý">{personName(names, ticket.assignee)}</FieldRow>
        )}
        {state === "AWAITING_REVIEW" && (
          <FieldRow label="Người xử lý">
            <span className="text-crm-text-3">Chờ điều phối viên giao việc</span>
          </FieldRow>
        )}
        {sla && (
          <FieldRow label="Hạn">
            <SlaIndicator sla={sla} started={started} />
          </FieldRow>
        )}
        {ticket.expectedStartAt && (
          <FieldRow label="Dự kiến bắt đầu">
            <time dateTime={ticket.expectedStartAt} title={formatDateTime(ticket.expectedStartAt)}>
              {formatRelativeTime(ticket.expectedStartAt)}
            </time>
          </FieldRow>
        )}
        {!isRequesterView && estimate && (
          <FieldRow label="Ước tính">
            <EstimateText estimate={estimate} />
          </FieldRow>
        )}
        {!isRequesterView && (ticket.workedMinutes ?? 0) > 0 && (
          <FieldRow label="Đã làm">{formatMinutes(ticket.workedMinutes ?? 0)}</FieldRow>
        )}
        {(ticket.reopenCount ?? 0) > 0 && (
          <FieldRow label="Mở lại">
            <span className="text-crm-tone-red">{ticket.reopenCount} lần</span>
          </FieldRow>
        )}
        {ticket.closedAt && (
          <FieldRow label="Đóng lúc">
            {formatDateTime(ticket.closedAt)}
            {ticket.closedBy === "AUTO" && (
              <span className="text-crm-text-3"> · tự đóng do không xác nhận</span>
            )}
          </FieldRow>
        )}
      </dl>
    </section>
  );
};
