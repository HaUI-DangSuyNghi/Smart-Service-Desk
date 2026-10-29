import { useDeferredValue, useState } from "react";
import { useNavigate } from "react-router";
import { IconSearch, IconTicket } from "@tabler/icons-react";
import { useDutyList } from "../../../platform/human/duty";
import {
  PERMISSIONS,
  personName,
  useActor,
  useCurrentWorkspace,
  useMe,
  usePeopleNames,
  usePermissions,
} from "../../../platform/human/workspace";
import { formatMinutes, PRIORITY_LABELS, useQueue, WorkActions } from "../../../platform/process";
import type { Priority } from "../../../platform/services";
import { Button } from "../../../platform/shared/ui/button";
import { DataTable, type DataTableColumn } from "../../../platform/shared/ui/DataTable";
import { EmptyState } from "../../../platform/shared/ui/EmptyState";
import { ErrorState } from "../../../platform/shared/ui/ErrorState";
import { Page, PageBody, PageHeader, PageToolbar } from "../../../platform/shared/ui/page";
import { Pagination } from "../../../platform/shared/ui/Pagination";
import { SelectField } from "../../../platform/shared/ui/SelectField";
import { TableSkeleton } from "../../../platform/shared/ui/Skeleton";
import { ViewTabs } from "../../../platform/shared/ui/ViewTabs";
import type { TicketState, TicketView } from "../services/ticketService";
import { boardAccess } from "../tickets/board/boardAccess";
import { TicketBoard } from "../tickets/board/TicketBoard";
import { useBoardView, type ListMode } from "../tickets/board/useBoardView";
import { CreateTicketDialog } from "../tickets/CreateTicketDialog";
import { ReviewDialog } from "../tickets/ReviewDialog";
import { createdAtColumn, titleColumn, workflowColumns } from "../tickets/ticketColumns";
import { TICKET_STATE_LABELS } from "../tickets/ticketLabels";
import type { Viewer } from "../tickets/ticketPermissions";
import { PRIORITIES } from "../tickets/ticketRules";
import {
  TICKETS_PAGE_SIZE,
  useRefreshTickets,
  useTicketList,
  type TicketFilter,
} from "../tickets/useTickets";

type View = "all" | "intake" | "review" | "overdue" | "mine";

const VIEW_LABELS: Record<View, string> = {
  all: "Tất cả",
  intake: "Đang tiếp nhận",
  review: "Chờ duyệt",
  mine: "Giao cho tôi",
  overdue: "Quá hạn",
};

const VIEW_FILTERS: Record<View, TicketFilter> = {
  all: {},
  // AI đang hỏi bổ sung / đánh giá; xong mới sang Chờ duyệt
  intake: { state: ["INTAKE"], sort: "priority" },
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

// Người yêu cầu chỉ thấy ticket của mình nên không cần chọn nhóm
const viewsFor = (canAssign: boolean, canHandle: boolean): View[] => {
  if (canAssign) return ["all", "intake", "review", "overdue", "mine"];
  if (canHandle) return ["all", "mine"];
  return [];
};

/** Danh sách ticket; chọn nhóm xem theo vai trò (đang tiếp nhận, chờ duyệt, quá hạn, giao cho tôi), lọc theo trạng thái và ưu tiên. */
const TicketListPage = () => {
  const navigate = useNavigate();
  const refresh = useRefreshTickets();
  const { current } = useCurrentWorkspace();
  const { can } = usePermissions();
  const { actor } = useActor();
  const me = useMe();
  const viewer: Viewer = {
    subject: actor?.subject ?? null,
    canHandle: can(PERMISSIONS.taskHandle),
    canAssign: can(PERMISSIONS.taskAssign),
    isPlatformAdmin: me.data?.platformAdmin ?? false,
  };
  // Board chung cho điều phối viên, kỹ thuật viên, siêu quản trị; người gửi không có
  const canBoard = boardAccess(viewer).canView;
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
  const [reviewing, setReviewing] = useState<TicketView | null>(null);

  // "Giao cho tôi" thay cho trang Hàng đợi: điều phối viên chọn được người để xem việc và tải của họ
  const isMine = view === "mine";
  const [person, setPerson] = useState("");
  const duty = useDutyList(isMine && viewer.canAssign);
  const queue = useQueue(person || undefined, isMine);
  const isSelf = !person || person === viewer.subject;

  const filter: TicketFilter = {
    ...VIEW_FILTERS[view],
    ...(isMine && person ? { assignee: person } : {}),
    ...(query ? { q: query } : {}),
    ...(state ? { state: [state as TicketState] } : {}),
    ...(priority ? { priority: [priority as Priority] } : {}),
  };
  // Board tự tải từng cột; bảng mới cần danh sách này
  const tickets = useTicketList(page, filter, { enabled: !isBoard });
  const items = tickets.data?.items ?? [];
  const names = usePeopleNames([
    ...items.flatMap((ticket) => [ticket.creator, ticket.assignee]),
    ...(duty.data ?? []).map((status) => status.subject),
  ]);
  const columns: DataTableColumn<TicketView>[] = [
    titleColumn,
    ...workflowColumns(names),
    createdAtColumn,
  ];
  // Việc của chính mình: Bắt đầu / Tạm dừng / Tiếp tục / Báo xong ngay trên dòng
  if (isMine && isSelf && queue.data) {
    const work = new Map(queue.data.items.map((item) => [item.id, item]));
    // Chưa có việc đang làm thì gợi ý làm ngay việc đầu hàng
    const suggestedId = queue.data.items.some((item) => item.workState === "IN_PROGRESS")
      ? null
      : queue.data.items.find((item) => item.workState === "NOT_STARTED")?.id;
    columns.push({
      key: "action",
      header: "Thao tác",
      width: "150px",
      cell: (ticket) => {
        const item = ticket.taskId ? work.get(ticket.taskId) : undefined;
        return item ? (
          <WorkActions
            item={item}
            compact
            suggested={item.id === suggestedId}
            onChanged={() => void refresh()}
          />
        ) : null;
      },
    });
  }
  // Chờ duyệt: duyệt và giao ngay trên dòng, form điền sẵn theo gợi ý AI
  if (view === "review") {
    columns.push({
      key: "action",
      header: "Thao tác",
      width: "104px",
      cell: (ticket) => (
        <Button
          size="xs"
          className="w-full"
          onClick={(event) => {
            // Không mở trang chi tiết khi bấm nút trong dòng
            event.stopPropagation();
            setReviewing(ticket);
          }}
        >
          Duyệt
        </Button>
      ),
    });
  }
  const personOptions = (duty.data ?? []).map((status) => {
    const name = personName(names, status.subject);
    return { value: status.subject, label: status.effectiveDuty ? name : `${name} (nghỉ)` };
  });

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
        actions={can(PERMISSIONS.requestCreate) && <CreateTicketDialog />}
      />
      <PageToolbar
        start={
          <>
            {views.length > 1 && (
              <SelectField
                aria-label="Xem ticket"
                options={views.map((key) => ({ value: key, label: VIEW_LABELS[key] }))}
                value={view}
                onValueChange={resetPage((value: string) => setView(value as View))}
              />
            )}
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
            {isMine && viewer.canAssign && (
              <SelectField
                aria-label="Việc của"
                placeholder="Của tôi"
                options={personOptions}
                value={person}
                onValueChange={resetPage(setPerson)}
                className="w-full sm:w-48"
              />
            )}
            {/* Tổng tải: số phút việc còn lại (đang chờ tính một phần) */}
            {isMine && queue.data && (
              <span className="text-crm-text-2">
                {queue.data.total} việc · tải{" "}
                <span className="font-medium text-crm-text">
                  {formatMinutes(queue.data.loadMinutes)}
                </span>
              </span>
            )}
          </>
        }
        end={
          <>
            {canBoard && (
              <ViewTabs label="Chế độ xem" tabs={MODE_TABS} value={mode} onChange={setMode} />
            )}
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
      <ReviewDialog ticket={reviewing} onClose={() => setReviewing(null)} />
    </Page>
  );
};

export default TicketListPage;
