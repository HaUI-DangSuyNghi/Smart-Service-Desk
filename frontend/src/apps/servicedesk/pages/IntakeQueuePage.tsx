import { useState } from "react";
import { useNavigate } from "react-router";
import { IconClipboardList, IconInbox } from "@tabler/icons-react";
import { useCurrentWorkspace, usePeopleNames } from "../../../platform/human/workspace";
import { DataTable, type DataTableColumn } from "../../../platform/shared/ui/DataTable";
import { EmptyState } from "../../../platform/shared/ui/EmptyState";
import { ErrorState } from "../../../platform/shared/ui/ErrorState";
import { Page, PageBody, PageHeader, PageToolbar } from "../../../platform/shared/ui/page";
import { Pagination } from "../../../platform/shared/ui/Pagination";
import { TableSkeleton } from "../../../platform/shared/ui/Skeleton";
import type { TicketView } from "../services/ticketService";
import { CanonicalDialog } from "../tickets/CanonicalDialog";
import { IntakeRowMenu, IntakeStatusCell, type IntakeAction } from "../tickets/IntakeRow";
import { ReleaseIntakeDialog } from "../tickets/ReleaseIntakeDialog";
import { createdAtColumn, creatorColumn, titleColumn } from "../tickets/ticketColumns";
import { TICKETS_PAGE_SIZE, useTicketList, type TicketFilter } from "../tickets/useTickets";
import { useTicketListRealtime } from "../tickets/useTicketRealtime";

// Đang tiếp nhận + đang theo ticket gốc (tách ra để tiếp nhận độc lập)
const INTAKE_FILTER: TicketFilter = { state: ["INTAKE", "FOLLOWING"], sort: "priority" };

/** Ticket đang tiếp nhận: tiếp nhận, đánh giá lại, kiểm tra trùng / gộp / tách ngay trên dòng. */
const IntakeQueuePage = () => {
  const navigate = useNavigate();
  const { current } = useCurrentWorkspace();
  useTicketListRealtime(current);
  const [page, setPage] = useState(0);
  const [acting, setActing] = useState<{ ticket: TicketView; action: IntakeAction } | null>(null);
  const tickets = useTicketList(page, INTAKE_FILTER);
  const items = tickets.data?.items ?? [];
  const names = usePeopleNames(items.map((ticket) => ticket.creator));

  const columns: DataTableColumn<TicketView>[] = [
    titleColumn,
    {
      key: "intake",
      header: "Tiếp nhận",
      icon: IconInbox,
      width: "260px",
      cell: (ticket) => <IntakeStatusCell ticket={ticket} />,
    },
    creatorColumn(names),
    createdAtColumn,
    {
      key: "action",
      header: "",
      width: "48px",
      cell: (ticket) => (
        <IntakeRowMenu ticket={ticket} onAction={(row, action) => setActing({ ticket: row, action })} />
      ),
    },
  ];
  const target = (action: IntakeAction) =>
    acting?.action === action ? { workspace: acting.ticket.workspace, id: acting.ticket.id } : null;
  const close = () => setActing(null);

  return (
    <Page>
      <PageHeader title="Tiếp nhận" icon={IconClipboardList} />
      <PageToolbar
        start={
          tickets.data && (
            <span className="text-crm-text-2">{tickets.data.total} ticket đang tiếp nhận</span>
          )
        }
      />
      <PageBody>
        {current === null && (
          <EmptyState icon={IconClipboardList} title="Chưa thuộc workspace nào" />
        )}
        {current !== null && tickets.isPending && (
          <TableSkeleton columns={columns.length} label="Đang tải ticket đang tiếp nhận" />
        )}
        {tickets.error && <ErrorState error={tickets.error} />}
        {tickets.data && items.length === 0 && (
          <EmptyState icon={IconClipboardList} title="Không có ticket nào đang tiếp nhận" />
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
      {tickets.data && tickets.data.total > 0 && (
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
      <ReleaseIntakeDialog target={target("release")} onClose={close} />
      <CanonicalDialog
        target={target("canonical") ?? target("split")}
        following={acting?.action === "split"}
        onClose={close}
      />
    </Page>
  );
};

export default IntakeQueuePage;
