import { useState } from "react";
import { useNavigate } from "react-router";
import { IconTicket } from "@tabler/icons-react";
import {
  PERMISSIONS,
  useCurrentWorkspace,
  usePeopleNames,
  usePermissions,
} from "../../../platform/human/workspace";
import { PRIORITY_LABELS } from "../../../platform/process";
import { isWorkflowAvailable, type Priority } from "../../../platform/services";
import { DataTable } from "../../../platform/shared/ui/DataTable";
import { EmptyState } from "../../../platform/shared/ui/EmptyState";
import { ErrorState } from "../../../platform/shared/ui/ErrorState";
import { Page, PageBody, PageHeader, PageToolbar } from "../../../platform/shared/ui/page";
import { Pagination } from "../../../platform/shared/ui/Pagination";
import { SelectField } from "../../../platform/shared/ui/SelectField";
import { TableSkeleton } from "../../../platform/shared/ui/Skeleton";
import { ViewTabs } from "../../../platform/shared/ui/ViewTabs";
import type { TicketState } from "../services/ticketService";
import { CreateTicketDialog } from "../tickets/CreateTicketDialog";
import {
  createdAtColumn,
  creatorColumn,
  titleColumn,
  workflowColumns,
} from "../tickets/ticketColumns";
import { TICKET_STATE_LABELS } from "../tickets/ticketLabels";
import { PRIORITIES } from "../tickets/ticketRules";
import { TICKETS_PAGE_SIZE, useTicketList, type TicketFilter } from "../tickets/useTickets";

type View = "all" | "mine" | "review" | "overdue";

const VIEW_LABELS: Record<View, string> = {
  all: "Tất cả",
  mine: "Giao cho tôi",
  review: "Chờ duyệt",
  overdue: "Quá hạn",
};

const VIEW_FILTERS: Record<View, TicketFilter> = {
  all: {},
  mine: { assignee: "me", sort: "priority" },
  review: { state: ["AWAITING_REVIEW"], sort: "priority" },
  overdue: { overdue: true, sort: "priority" },
};

const STATE_OPTIONS = (Object.keys(TICKET_STATE_LABELS) as TicketState[]).map((state) => ({
  value: state,
  label: TICKET_STATE_LABELS[state],
}));
const PRIORITY_OPTIONS = PRIORITIES.map((priority) => ({
  value: priority,
  label: PRIORITY_LABELS[priority],
}));

// Người yêu cầu chỉ thấy ticket của mình nên không cần tab; backend cũ chưa lọc được thì cũng không có tab
const viewsFor = (canAssign: boolean, canHandle: boolean): View[] => {
  if (!isWorkflowAvailable) return [];
  if (canAssign) return ["all", "review", "overdue", "mine"];
  if (canHandle) return ["all", "mine"];
  return [];
};

/** Danh sách ticket; tab theo vai trò (giao cho tôi, chờ duyệt, quá hạn), lọc theo trạng thái và ưu tiên. */
const TicketListPage = () => {
  const navigate = useNavigate();
  const { current } = useCurrentWorkspace();
  const { can } = usePermissions();
  const views = viewsFor(can(PERMISSIONS.taskAssign), can(PERMISSIONS.taskHandle));
  const [selectedView, setView] = useState<View>("all");
  const view = views.includes(selectedView) ? selectedView : "all";
  const [state, setState] = useState("");
  const [priority, setPriority] = useState("");
  const [page, setPage] = useState(0);

  const filter: TicketFilter = {
    ...VIEW_FILTERS[view],
    ...(state ? { state: [state as TicketState] } : {}),
    ...(priority ? { priority: [priority as Priority] } : {}),
  };
  const tickets = useTicketList(page, filter);
  const items = tickets.data?.items ?? [];
  const names = usePeopleNames(items.flatMap((ticket) => [ticket.creator, ticket.assignee]));
  const columns = isWorkflowAvailable
    ? [titleColumn, ...workflowColumns(names), createdAtColumn]
    : [titleColumn, creatorColumn(names), createdAtColumn];

  // Đổi tab hay bộ lọc thì về trang đầu
  const resetPage = <T,>(set: (value: T) => void) => (value: T) => {
    set(value);
    setPage(0);
  };

  return (
    <Page>
      <PageHeader
        title="Ticket"
        icon={IconTicket}
        actions={can(PERMISSIONS.requestCreate) && <CreateTicketDialog />}
      />
      <PageToolbar
        start={
          views.length > 1 && (
            <ViewTabs
              label="Xem ticket"
              tabs={views.map((key) => ({ key, label: VIEW_LABELS[key] }))}
              value={view}
              onChange={resetPage(setView)}
            />
          )
        }
        end={
          <>
            {isWorkflowAvailable && (
              <>
                <SelectField
                  aria-label="Lọc theo trạng thái"
                  placeholder="Mọi trạng thái"
                  options={STATE_OPTIONS}
                  value={state}
                  onValueChange={resetPage(setState)}
                />
                <SelectField
                  aria-label="Lọc theo ưu tiên"
                  placeholder="Mọi mức ưu tiên"
                  options={PRIORITY_OPTIONS}
                  value={priority}
                  onValueChange={resetPage(setPriority)}
                />
              </>
            )}
            {tickets.data && (
              <Pagination
                page={page}
                total={tickets.data.total}
                pageSize={TICKETS_PAGE_SIZE}
                onPageChange={setPage}
              />
            )}
          </>
        }
      />
      <PageBody>
        {current === null && <EmptyState icon={IconTicket} title="Chưa thuộc workspace nào" />}
        {current !== null && tickets.isPending && (
          <TableSkeleton columns={columns.length} label="Đang tải ticket" />
        )}
        {tickets.error && <ErrorState error={tickets.error} />}
        {tickets.data && items.length === 0 && (
          <EmptyState
            icon={IconTicket}
            title="Không có ticket nào"
            description="Ticket bạn được xem trong workspace, khớp bộ lọc, sẽ hiện ở đây."
            action={can(PERMISSIONS.requestCreate) && <CreateTicketDialog />}
          />
        )}
        {items.length > 0 && (
          <DataTable
            columns={columns}
            rows={items}
            getRowKey={(ticket) => ticket.id}
            onRowClick={(ticket) => navigate(`/servicedesk/tickets/${ticket.id}`)}
          />
        )}
      </PageBody>
    </Page>
  );
};

export default TicketListPage;
