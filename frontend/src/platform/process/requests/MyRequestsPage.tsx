import { useState } from "react";
import { useNavigate } from "react-router";
import {
  IconCalendar,
  IconHash,
  IconList,
  IconProgress,
  IconRoute,
  IconSend,
  IconStatusChange,
} from "@tabler/icons-react";
import { useCurrentWorkspace } from "@/platform/human/workspace";
import type { ProcessSummary } from "@/platform/services";
import { formatDateTime, formatRelativeTime } from "@/platform/shared/lib/format";
import { DataTable, type DataTableColumn } from "@/platform/shared/ui/DataTable";
import { EmptyState } from "@/platform/shared/ui/EmptyState";
import { ErrorState } from "@/platform/shared/ui/ErrorState";
import { Page, PageBody, PageHeader, PageToolbar } from "@/platform/shared/ui/page";
import { Pagination } from "@/platform/shared/ui/Pagination";
import { TableSkeleton } from "@/platform/shared/ui/Skeleton";
import { Tag } from "@/platform/shared/ui/Tag";
import type { ProcessLabels } from "../catalog/processLabels";
import { useProcessLabels } from "../catalog/useProcessCatalog";
import { BusinessKeyLink } from "../common/BusinessKeyLink";
import { StartProcessDialog } from "../start/StartProcessDialog";
import { REQUESTS_PAGE_SIZE, useMyRequests } from "./useMyRequests";

const requestColumns = (labels: ProcessLabels): readonly DataTableColumn<ProcessSummary>[] => [
  {
    key: "process",
    header: "Quy trình",
    icon: IconRoute,
    width: "220px",
    cell: (request) => labels.processName(request.processKey),
  },
  {
    key: "step",
    header: "Bước hiện tại",
    icon: IconProgress,
    width: "220px",
    cell: (request) =>
      request.activeTaskKeys.length > 0 ? (
        request.activeTaskKeys.map((key) => labels.taskName(request.processKey, key)).join(", ")
      ) : (
        <span className="text-crm-text-3">—</span>
      ),
  },
  {
    key: "status",
    header: "Trạng thái",
    icon: IconStatusChange,
    width: "130px",
    cell: (request) =>
      request.endedAt ? <Tag tone="green">Đã kết thúc</Tag> : <Tag tone="blue">Đang chạy</Tag>,
  },
  {
    key: "businessKey",
    header: "Mã yêu cầu",
    icon: IconHash,
    width: "130px",
    cell: (request) => (
      <BusinessKeyLink processKey={request.processKey} businessKey={request.businessKey} />
    ),
  },
  {
    key: "startedAt",
    header: "Tạo lúc",
    icon: IconCalendar,
    width: "140px",
    cell: (request) => (
      <time dateTime={request.startedAt} title={formatDateTime(request.startedAt)}>
        {formatRelativeTime(request.startedAt)}
      </time>
    ),
  },
];

/** Yêu cầu tôi đã tạo, mọi quy trình; bấm một dòng để xem tiến độ. */
const MyRequestsPage = () => {
  const navigate = useNavigate();
  const { current } = useCurrentWorkspace();
  const labels = useProcessLabels();
  const [page, setPage] = useState(0);
  const requests = useMyRequests(page);
  const items = requests.data?.items ?? [];

  return (
    <Page>
      <PageHeader title="Yêu cầu" icon={IconSend} actions={<StartProcessDialog />} />
      <PageToolbar
        start={
          <span className="flex items-center gap-1 text-crm-text">
            <IconList size={16} stroke={2} className="text-crm-text-3" />
            Tất cả yêu cầu
            <span className="text-crm-text-3">· {requests.data?.total ?? 0}</span>
          </span>
        }
        end={
          requests.data && (
            <Pagination
              page={page}
              total={requests.data.total}
              pageSize={REQUESTS_PAGE_SIZE}
              onPageChange={setPage}
            />
          )
        }
      />
      <PageBody>
        {current === null && <EmptyState icon={IconSend} title="Chưa thuộc workspace nào" />}
        {current !== null && requests.isPending && (
          <TableSkeleton columns={5} label="Đang tải yêu cầu" />
        )}
        {requests.error && <ErrorState error={requests.error} />}
        {requests.data && items.length === 0 && (
          <EmptyState
            icon={IconSend}
            title="Bạn chưa tạo yêu cầu nào"
            description="Yêu cầu bạn tạo theo mọi quy trình sẽ hiện ở đây."
            action={<StartProcessDialog />}
          />
        )}
        {items.length > 0 && (
          <DataTable
            columns={requestColumns(labels)}
            rows={items}
            getRowKey={(request) => request.id}
            onRowClick={(request) => navigate(`/processes/${request.id}`)}
          />
        )}
      </PageBody>
    </Page>
  );
};

export default MyRequestsPage;
