import { useState, type MouseEvent } from "react";
import { useNavigate } from "react-router";
import { IconClipboardCheck } from "@tabler/icons-react";
import { useCurrentWorkspace, usePeopleNames } from "../../../platform/human/workspace";
import { Button } from "../../../platform/shared/ui/button";
import { DataTable, type DataTableColumn } from "../../../platform/shared/ui/DataTable";
import { EmptyState } from "../../../platform/shared/ui/EmptyState";
import { ErrorState } from "../../../platform/shared/ui/ErrorState";
import { Page, PageBody, PageHeader, PageToolbar } from "../../../platform/shared/ui/page";
import { Pagination } from "../../../platform/shared/ui/Pagination";
import { TableSkeleton } from "../../../platform/shared/ui/Skeleton";
import type { TicketView } from "../services/ticketService";
import { ReviewDialog } from "../tickets/ReviewDialog";
import {
  createdAtColumn,
  creatorColumn,
  titleColumn,
  workflowColumns,
} from "../tickets/ticketColumns";
import { TICKETS_PAGE_SIZE, useTicketList } from "../tickets/useTickets";

const REVIEW_FILTER = { state: ["AWAITING_REVIEW" as const], sort: "priority" as const };

/**
 * Hàng chờ duyệt (R4): ticket mới đã có gợi ý AI, khẩn cấp lên đầu.
 * Điều phối viên chốt loại, ưu tiên, ước tính và người xử lý ngay trên dòng.
 */
const ReviewQueuePage = () => {
  const navigate = useNavigate();
  const { current } = useCurrentWorkspace();
  const [page, setPage] = useState(0);
  const [reviewing, setReviewing] = useState<TicketView | null>(null);
  const tickets = useTicketList(page, REVIEW_FILTER);
  const items = tickets.data?.items ?? [];
  const names = usePeopleNames(items.map((ticket) => ticket.creator));

  // Ưu tiên, loại lấy từ gợi ý AI vì chưa duyệt; trạng thái, người xử lý, hạn chưa có nên bỏ
  const [, priorityColumn, categoryColumn] = workflowColumns(names);
  const columns: DataTableColumn<TicketView>[] = [
    titleColumn,
    priorityColumn,
    categoryColumn,
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
            setReviewing(ticket);
          }}
        >
          Duyệt
        </Button>
      ),
    },
  ];

  return (
    <Page>
      <PageHeader title="Chờ duyệt" icon={IconClipboardCheck} />
      <PageToolbar
        start={
          tickets.data && (
            <span className="text-crm-text-2">{tickets.data.total} ticket chờ duyệt</span>
          )
        }
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
        {current === null && (
          <EmptyState icon={IconClipboardCheck} title="Chưa thuộc workspace nào" />
        )}
        {current !== null && tickets.isPending && (
          <TableSkeleton columns={columns.length} label="Đang tải hàng chờ duyệt" />
        )}
        {tickets.error && <ErrorState error={tickets.error} />}
        {tickets.data && items.length === 0 && (
          <EmptyState
            icon={IconClipboardCheck}
            title="Không có ticket nào chờ duyệt"
            description="Ticket mới gửi sẽ hiện ở đây kèm gợi ý phân loại của AI."
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
      <ReviewDialog ticket={reviewing} onClose={() => setReviewing(null)} />
    </Page>
  );
};

export default ReviewQueuePage;
