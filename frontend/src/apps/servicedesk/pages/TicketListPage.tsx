import { useState } from "react";
import { Link, useNavigate } from "react-router";
import { IconCalendar, IconTicket, IconTypography, IconUser } from "@tabler/icons-react";
import {
  personName,
  useCurrentWorkspace,
  usePeopleNames,
  useActor,
} from "../../../platform/human/workspace";
import { formatDateTime, formatRelativeTime } from "../../../platform/shared/lib/format";
import { DataTable, type DataTableColumn } from "../../../platform/shared/ui/DataTable";
import { EmptyState } from "../../../platform/shared/ui/EmptyState";
import { ErrorState } from "../../../platform/shared/ui/ErrorState";
import { Page, PageBody, PageHeader, PageToolbar } from "../../../platform/shared/ui/page";
import { Pagination } from "../../../platform/shared/ui/Pagination";
import { TableSkeleton } from "../../../platform/shared/ui/Skeleton";
import type { Ticket } from "../services/ticketService";
import { TICKETS_PAGE_SIZE, useTicketList } from "../tickets/useTickets";
import { Button } from "../../../platform/shared/ui/button";

const ticketColumns = (names: ReadonlyMap<string, string>): DataTableColumn<Ticket>[] => [
  {
    key: "title",
    header: "Tiêu đề",
    icon: IconTypography,
    width: "360px",
    cell: (ticket) => <span className="block truncate">{ticket.title}</span>,
  },
  {
    key: "creator",
    header: "Người tạo",
    icon: IconUser,
    width: "180px",
    cell: (ticket) => personName(names, ticket.creator),
  },
  {
    key: "createdAt",
    header: "Tạo lúc",
    icon: IconCalendar,
    width: "140px",
    cell: (ticket) => (
      <time dateTime={ticket.createdAt} title={formatDateTime(ticket.createdAt)}>
        {formatRelativeTime(ticket.createdAt)}
      </time>
    ),
  },
];

/** Danh sách ticket của workspace đang chọn; bấm dòng để xem chi tiết và hỗ trợ của AI. */
const TicketListPage = () => {
  const navigate = useNavigate();
  const { current } = useCurrentWorkspace();
  const { roles } = useActor();
  const [page, setPage] = useState(0);
  const tickets = useTicketList(page);
  const items = tickets.data?.items ?? [];
  const names = usePeopleNames(items.map((ticket) => ticket.creator));

  return (
    <Page>
      <PageHeader
        title="Ticket"
        icon={IconTicket}
        actions={
          roles.includes("coordinator") && (
            <Button variant="outline" asChild>
              <Link to="/servicedesk/intake-pending">Ticket đang tiếp nhận</Link>
            </Button>
          )
        }
      />
      <PageToolbar
        end={
          tickets.data && (
            <Pagination
              page={page}
              total={tickets.data.total}
              pageSize={TICKETS_PAGE_SIZE}
              onPageChange={setPage}
            />
          )
        }
      />
      <PageBody>
        {current === null && <EmptyState icon={IconTicket} title="Chưa thuộc workspace nào" />}
        {current !== null && tickets.isPending && (
          <TableSkeleton columns={3} label="Đang tải ticket" />
        )}
        {tickets.error && <ErrorState error={tickets.error} />}
        {tickets.data && items.length === 0 && (
          <EmptyState
            icon={IconTicket}
            title="Chưa có ticket nào"
            description="Ticket bạn được xem trong workspace sẽ hiện ở đây."
          />
        )}
        {items.length > 0 && (
          <DataTable
            columns={ticketColumns(names)}
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
