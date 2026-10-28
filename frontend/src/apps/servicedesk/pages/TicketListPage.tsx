import { useDeferredValue, useState } from "react";
import { Link, useNavigate } from "react-router";
import { IconSearch, IconTicket } from "@tabler/icons-react";
import {
  PERMISSIONS,
  useActor,
  useCurrentWorkspace,
  useMe,
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
import { Button } from "../../../platform/shared/ui/button";
import type { TicketState } from "../services/ticketService";
import { boardAccess } from "../tickets/board/boardAccess";
import { TicketBoard } from "../tickets/board/TicketBoard";
import { useBoardView, type ListMode } from "../tickets/board/useBoardView";
import { CreateTicketDialog } from "../tickets/CreateTicketDialog";
import {
  createdAtColumn,
  creatorColumn,
  titleColumn,
  workflowColumns,
} from "../tickets/ticketColumns";
import { TICKET_STATE_LABELS } from "../tickets/ticketLabels";
import type { Viewer } from "../tickets/ticketPermissions";
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

const MODE_TABS: readonly { key: ListMode; label: string }[] = [
  { key: "table", label: "Bảng" },
  { key: "board", label: "Board" },
];

const STATE_OPTIONS = (Object.keys(TICKET_STATE_LABELS) as TicketState[]).map((state) => ({
  value: state,
  label: TICKET_STATE_LABELS[state],
}));
const PRIORITY_OPTIONS = PRIORITIES.map((priority) => ({
  value: priority,
  label: PRIORITY_LABELS[priority],
}));

// Người yêu cầu chỉ thấy ticket của mình nên không cần chọn nhóm; backend cũ chưa lọc được thì cũng không có
const viewsFor = (canAssign: boolean, canHandle: boolean): View[] => {
  if (!isWorkflowAvailable) return [];
  if (canAssign) return ["all", "review", "overdue", "mine"];
  if (canHandle) return ["all", "mine"];
  return [];
};

/** Danh sách ticket; chọn nhóm xem theo vai trò (giao cho tôi, chờ duyệt, quá hạn), lọc theo trạng thái và ưu tiên. */
const TicketListPage = () => {
  const navigate = useNavigate();
  const { current } = useCurrentWorkspace();
  const { can } = usePermissions();
  const { actor, roles } = useActor();
  const me = useMe();
  const viewer: Viewer = {
    subject: actor?.subject ?? null,
    canHandle: can(PERMISSIONS.taskHandle),
    canAssign: can(PERMISSIONS.taskAssign),
    isPlatformAdmin: me.data?.platformAdmin ?? false,
  };
  // Board chung cho điều phối viên, kỹ thuật viên, siêu quản trị; người gửi không có
  const canBoard = isWorkflowAvailable && boardAccess(viewer).canView;
  const [mode, setMode] = useBoardView(canBoard);
  const isBoard = mode === "board";
  const views = viewsFor(viewer.canAssign, viewer.canHandle);
  const [selectedView, setView] = useState<View>("all");
  const view = views.includes(selectedView) ? selectedView : "all";
  const [state, setState] = useState("");
  const [priority, setPriority] = useState("");
  const [search, setSearch] = useState("");
  const query = useDeferredValue(search.trim());
  const [page, setPage] = useState(0);

  const filter: TicketFilter = {
    ...VIEW_FILTERS[view],
    ...(query ? { q: query } : {}),
    ...(state ? { state: [state as TicketState] } : {}),
    ...(priority ? { priority: [priority as Priority] } : {}),
  };
  // Board tự tải từng cột; bảng mới cần danh sách này
  const tickets = useTicketList(page, filter, { enabled: !isBoard });
  const items = tickets.data?.items ?? [];
  const names = usePeopleNames(items.flatMap((ticket) => [ticket.creator, ticket.assignee]));
  const columns = isWorkflowAvailable
    ? [titleColumn, ...workflowColumns(names), createdAtColumn]
    : [titleColumn, creatorColumn(names), createdAtColumn];

  // Đổi nhóm xem hay bộ lọc thì về trang đầu
  const resetPage =
    <T,>(set: (value: T) => void) =>
    (value: T) => {
      set(value);
      setPage(0);
    };

  return (
    <Page>
      <PageHeader
        title="Ticket"
        icon={IconTicket}
        actions={
          <>
            {roles.includes("coordinator") && (
              <Button variant="outline" asChild>
                <Link to="/servicedesk/intake-pending">Ticket đang tiếp nhận</Link>
              </Button>
            )}
            {can(PERMISSIONS.requestCreate) && <CreateTicketDialog />}
          </>
        }
      />
      <PageToolbar
        start={
          views.length > 1 && (
            <SelectField
              aria-label="Xem ticket"
              options={views.map((key) => ({ value: key, label: VIEW_LABELS[key] }))}
              value={view}
              onValueChange={resetPage((value: string) => setView(value as View))}
            />
          )
        }
        end={
          <>
            {/* Điện thoại: ô tìm kiếm giãn hết hàng; màn rộng: cố định 224px */}
            <label className="flex h-8 min-w-0 flex-1 items-center gap-1 rounded-crm-sm border border-crm-line px-2 focus-within:border-crm-focus sm:h-7 sm:w-56 sm:flex-none">
              <IconSearch size={14} stroke={2} className="shrink-0 text-crm-text-3" />
              <input
                value={search}
                onChange={(event) => resetPage(setSearch)(event.target.value)}
                placeholder="Tìm theo tiêu đề, mô tả"
                aria-label="Tìm ticket"
                maxLength={200}
                className="min-w-0 flex-1 bg-transparent outline-none placeholder:text-crm-text-3"
              />
            </label>
            {canBoard && (
              <ViewTabs label="Chế độ xem" tabs={MODE_TABS} value={mode} onChange={setMode} />
            )}
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
          </>
        }
      />
      <PageBody>
        {current === null && <EmptyState icon={IconTicket} title="Chưa thuộc workspace nào" />}
        {current !== null && isBoard && <TicketBoard filter={filter} viewer={viewer} />}
        {current !== null && !isBoard && tickets.isPending && (
          <TableSkeleton columns={columns.length} label="Đang tải ticket" />
        )}
        {!isBoard && tickets.error && <ErrorState error={tickets.error} />}
        {!isBoard && tickets.data && items.length === 0 && (
          <EmptyState
            icon={IconTicket}
            title="Không có ticket nào"
            description="Ticket bạn được xem trong workspace, khớp bộ lọc, sẽ hiện ở đây."
            action={can(PERMISSIONS.requestCreate) && <CreateTicketDialog />}
          />
        )}
        {!isBoard && items.length > 0 && (
          <DataTable
            columns={columns}
            rows={items}
            getRowKey={(ticket) => ticket.id}
            onRowClick={(ticket) => navigate(`/servicedesk/tickets/${ticket.id}`)}
          />
        )}
      </PageBody>
      {!isBoard && tickets.data && tickets.data.total > 0 && (
        <div className="flex shrink-0 justify-center border-t border-crm-line px-3 py-1.5 text-crm-text-2">
          <Pagination
            editable
            page={page}
            total={tickets.data.total}
            pageSize={TICKETS_PAGE_SIZE}
            onPageChange={setPage}
          />
        </div>
      )}
    </Page>
  );
};

export default TicketListPage;
