import {
  IconAbc,
  IconCalendar,
  IconCategory,
  IconClockHour4,
  IconFlag,
  IconProgress,
  IconUser,
} from "@tabler/icons-react";
import { personName } from "../../../platform/human/workspace";
import { PriorityTag, SlaIndicator } from "../../../platform/process";
import { formatDateTime, formatRelativeTime } from "../../../platform/shared/lib/format";
import type { DataTableColumn } from "../../../platform/shared/ui/DataTable";
import type { TicketView } from "../services/ticketService";
import { CATEGORY_LABELS } from "./ticketLabels";
import { aiSuggestion, isStarted } from "./ticketRules";
import { TicketStateTag } from "./TicketStateTag";

const NONE = <span className="text-crm-text-3">—</span>;

export const titleColumn: DataTableColumn<TicketView> = {
  key: "title",
  header: "Tiêu đề",
  icon: IconAbc,
  width: "220px",
  cell: (ticket) => (
    <span className="block max-w-55 truncate" title={ticket.title}>
      {ticket.title}
    </span>
  ),
};

export const createdAtColumn: DataTableColumn<TicketView> = {
  key: "createdAt",
  header: "Tạo lúc",
  icon: IconCalendar,
  width: "130px",
  cell: (ticket) => (
    <time dateTime={ticket.createdAt} title={formatDateTime(ticket.createdAt)}>
      {formatRelativeTime(ticket.createdAt)}
    </time>
  ),
};

export const creatorColumn = (names: ReadonlyMap<string, string>): DataTableColumn<TicketView> => ({
  key: "creator",
  header: "Người tạo",
  icon: IconUser,
  width: "170px",
  cell: (ticket) => personName(names, ticket.creator),
});

/** Cột nghiệp vụ (R4–R6); ticket quy trình cũ không có các trường này thì hiện "—". */
export const workflowColumns = (
  names: ReadonlyMap<string, string>,
): DataTableColumn<TicketView>[] => [
  {
    key: "state",
    header: "Trạng thái",
    icon: IconProgress,
    width: "130px",
    cell: (ticket) => (ticket.state ? <TicketStateTag state={ticket.state} /> : NONE),
  },
  {
    key: "priority",
    header: "Ưu tiên",
    icon: IconFlag,
    width: "90px",
    cell: (ticket) => {
      // Chưa duyệt thì hiện gợi ý AI cho điều phối viên sắp xếp
      const priority = ticket.classification?.priority ?? aiSuggestion(ticket.assessment)?.priority;
      return priority ? (
        <PriorityTag priority={priority} escalated={ticket.classification?.escalated} />
      ) : (
        NONE
      );
    },
  },
  {
    key: "category",
    header: "Loại",
    icon: IconCategory,
    width: "120px",
    cell: (ticket) => {
      const category = ticket.classification?.category ?? aiSuggestion(ticket.assessment)?.category;
      return category ? CATEGORY_LABELS[category] : NONE;
    },
  },
  {
    key: "assignee",
    header: "Người xử lý",
    icon: IconUser,
    width: "170px",
    cell: (ticket) => (ticket.assignee ? personName(names, ticket.assignee) : NONE),
  },
  {
    key: "sla",
    header: "Hạn",
    icon: IconClockHour4,
    width: "150px",
    cell: (ticket) =>
      ticket.sla ? <SlaIndicator sla={ticket.sla} started={isStarted(ticket)} /> : NONE,
  },
];
