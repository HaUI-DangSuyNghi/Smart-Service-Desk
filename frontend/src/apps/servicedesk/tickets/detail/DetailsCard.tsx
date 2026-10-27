import { personName, usePeopleNames } from "../../../../platform/human/workspace";
import {
  EstimateText,
  formatMinutes,
  PriorityTag,
  SlaIndicator,
} from "../../../../platform/process";
import { formatDateTime, formatRelativeTime } from "../../../../platform/shared/lib/format";
import { Collapsible } from "../../../../platform/shared/ui/Collapsible";
import { FieldRow } from "../../../../platform/shared/ui/FieldRow";
import type { TicketView } from "../../services/ticketService";
import { CATEGORY_LABELS, groupLabel } from "../ticketLabels";
import { isStarted } from "../ticketRules";

interface Props {
  ticket: TicketView;
  /** Người gửi chỉ thấy dự kiến bắt đầu và hạn; không thấy tổ, tải, ước tính. */
  isRequesterView: boolean;
}

/** Bảng "Chi tiết" bên phải trang ticket (kiểu Jira): người xử lý, ưu tiên, ước tính, hạn... */
export const DetailsCard = ({ ticket, isRequesterView }: Props) => {
  const names = usePeopleNames([ticket.assignee, ticket.creator]);
  const { state, classification, estimate, sla } = ticket;

  return (
    <Collapsible title="Chi tiết">
      <dl>
        {state && (
          <FieldRow stacked label="Người xử lý">
            {ticket.assignee ? (
              personName(names, ticket.assignee)
            ) : (
              <span className="text-crm-text-3">
                {state === "AWAITING_REVIEW" ? "Chờ điều phối viên giao việc" : "Chưa giao"}
              </span>
            )}
          </FieldRow>
        )}
        <FieldRow stacked label="Người gửi">
          {personName(names, ticket.creator)}
        </FieldRow>
        {classification && (
          <>
            <FieldRow stacked label="Ưu tiên">
              <PriorityTag
                priority={classification.priority}
                escalated={classification.escalated}
              />
            </FieldRow>
            <FieldRow stacked label="Loại sự cố">
              {CATEGORY_LABELS[classification.category]}
            </FieldRow>
            {!isRequesterView && (
              <FieldRow stacked label="Tổ">
                {groupLabel(classification.group)}
              </FieldRow>
            )}
          </>
        )}
        {!isRequesterView && estimate && (
          <FieldRow stacked label="Ước tính">
            <EstimateText estimate={estimate} />
          </FieldRow>
        )}
        {!isRequesterView && (ticket.workedMinutes ?? 0) > 0 && (
          <FieldRow stacked label="Đã làm">
            {formatMinutes(ticket.workedMinutes ?? 0)}
          </FieldRow>
        )}
        {sla && (
          <FieldRow stacked label="Hạn">
            <SlaIndicator sla={sla} started={isStarted(ticket)} />
          </FieldRow>
        )}
        {ticket.expectedStartAt && (
          <FieldRow stacked label="Dự kiến bắt đầu">
            <time dateTime={ticket.expectedStartAt} title={formatDateTime(ticket.expectedStartAt)}>
              {formatRelativeTime(ticket.expectedStartAt)}
            </time>
          </FieldRow>
        )}
        {(ticket.reopenCount ?? 0) > 0 && (
          <FieldRow stacked label="Mở lại">
            <span className="text-crm-tone-red">{ticket.reopenCount} lần</span>
          </FieldRow>
        )}
        <FieldRow stacked label="Tạo lúc">
          <time dateTime={ticket.createdAt} title={formatDateTime(ticket.createdAt)}>
            {formatRelativeTime(ticket.createdAt)}
          </time>
        </FieldRow>
        {ticket.closedAt && (
          <FieldRow stacked label="Đóng lúc">
            {formatDateTime(ticket.closedAt)}
            {ticket.closedBy === "AUTO" && (
              <span className="text-crm-text-3"> · tự đóng do không xác nhận</span>
            )}
          </FieldRow>
        )}
      </dl>
    </Collapsible>
  );
};
