import { useState, type MouseEvent } from "react";
import { useNavigate } from "react-router";
import { IconClipboardList } from "@tabler/icons-react";
import { useCurrentWorkspace, usePeopleNames } from "../../../platform/human/workspace";
import { Button } from "../../../platform/shared/ui/button";
import { DataTable, type DataTableColumn } from "../../../platform/shared/ui/DataTable";
import { EmptyState } from "../../../platform/shared/ui/EmptyState";
import { ErrorState } from "../../../platform/shared/ui/ErrorState";
import { Page, PageBody, PageHeader, PageToolbar } from "../../../platform/shared/ui/page";
import { Pagination } from "../../../platform/shared/ui/Pagination";
import { TableSkeleton } from "../../../platform/shared/ui/Skeleton";
import type { TicketView } from "../services/ticketService";
import { ReleaseIntakeDialog } from "../tickets/ReleaseIntakeDialog";
import { createdAtColumn, creatorColumn, titleColumn } from "../tickets/ticketColumns";
import { TICKETS_PAGE_SIZE, useTicketList, type TicketFilter } from "../tickets/useTickets";

const INTAKE_FILTER: TicketFilter = { state: ["INTAKE"], sort: "priority" };

/** Ticket đang tiếp nhận (AI hỏi bổ sung / đánh giá); điều phối viên chuyển sang Chờ duyệt ngay trên dòng. */
const IntakeQueuePage = () => {
  const navigate = useNavigate();
  const { current } = useCurrentWorkspace();
  const [page, setPage] = useState(0);
  const [releasing, setReleasing] = useState<TicketView | null>(null);
  const tickets = useTicketList(page, INTAKE_FILTER);
  const items = tickets.data?.items ?? [];
  const names = usePeopleNames(items.map((ticket) => ticket.creator));

  const columns: DataTableColumn<TicketView>[] = [
    titleColumn,
    creatorColumn(names),
    createdAtColumn,
    {
      key: "action",
      header: "Thao tác",
      width: "104px",
      cell: (ticket) => (
        <Button
          size="xs"
          className="w-full"
          onClick={(event: MouseEvent) => {
            // Không mở trang chi tiết khi bấm nút trong dòng
            event.stopPropagation();
            setReleasing(ticket);
          }}
        >
          Tiếp nhận
        </Button>
      ),
    },
  ];

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
      <ReleaseIntakeDialog
        target={releasing && { workspace: releasing.workspace, id: releasing.id }}
        onClose={() => setReleasing(null)}
      />
    </Page>
  );
};

export default IntakeQueuePage;
