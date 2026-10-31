import { useState } from "react";
import { useNavigate } from "react-router";
import {
  IconCalendar,
  IconHash,
  IconProgress,
  IconRoute,
  IconSend,
  IconStatusChange,
  IconUser,
} from "@tabler/icons-react";
import {
  PERMISSIONS,
  useCurrentWorkspace,
  usePeopleNames,
  usePermissions,
  personName,
} from "@/platform/human/workspace";
import type { ProcessSummary, RequestScope } from "@/platform/services";
import { formatDateTime, formatRelativeTime } from "@/platform/shared/lib/format";
import { DataTable, type DataTableColumn } from "@/platform/shared/ui/DataTable";
import { EmptyState } from "@/platform/shared/ui/EmptyState";
import { ErrorState } from "@/platform/shared/ui/ErrorState";
import { Page, PageBody, PageHeader, PageToolbar } from "@/platform/shared/ui/page";
import { Pagination } from "@/platform/shared/ui/Pagination";
import { TableSkeleton } from "@/platform/shared/ui/Skeleton";
import { Tag } from "@/platform/shared/ui/Tag";
import { ViewTabs } from "@/platform/shared/ui/ViewTabs";
import type { ProcessLabels } from "../catalog/processLabels";
import { useProcessLabels } from "../catalog/useProcessCatalog";
import { BusinessKeyLink } from "../common/BusinessKeyLink";
import { processLink } from "../common/processLinks";
import { requestAction } from "../common/requestActions";
import { REQUESTS_PAGE_SIZE, useRequests } from "./useMyRequests";
import { RequestActions } from "./RequestActions";

const SCOPE_LABELS: Record<RequestScope, string> = { mine: "Của tôi", all: "Tất cả yêu cầu" };

const requestColumns = (
  labels: ProcessLabels,
  names: ReadonlyMap<string, string>,
  showInitiator: boolean,
  showActions: boolean,
): DataTableColumn<ProcessSummary>[] => {
  const columns: DataTableColumn<ProcessSummary>[] = [
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
        request.cancelled ? (
          <Tag tone="gray">Đã hủy</Tag>
        ) : request.endedAt ? (
          <Tag tone="green">Đã kết thúc</Tag>
        ) : (
          <Tag tone="blue">Đang chạy</Tag>
        ),
    },
  ];
  // Danh sách "Của tôi" toàn là mình tạo, không cần cột người tạo
  if (showInitiator) {
    columns.push({
      key: "initiator",
      header: "Người tạo",
      icon: IconUser,
      width: "180px",
      cell: (request) => personName(names, request.initiator),
    });
  }
  columns.push(
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
  );
  // Quản lý/lịch sử dùng chung luôn có; thao tác riêng của app chỉ cho danh sách của mình.
  columns.push({
    key: "action",
    header: "Thao tác",
    fit: true,
    cell: (request) => {
      const Action = showActions && !request.endedAt ? requestAction(request.processKey) : null;
      return (
        <div className="inline-flex w-max items-center gap-1">
          <RequestActions processId={request.id} compact />
          {Action ? (
            <Action
              processKey={request.processKey}
              businessKey={request.businessKey}
              activeTaskKeys={request.activeTaskKeys}
            />
          ) : null}
        </div>
      );
    },
  });
  return columns;
};

/** Yêu cầu: của tôi (mọi quy trình), và với điều phối viên là tất cả yêu cầu; bấm dòng để xem chi tiết (trang của app nếu có, không thì tiến độ chung). */
const MyRequestsPage = () => {
  const navigate = useNavigate();
  const { current } = useCurrentWorkspace();
  const { can } = usePermissions();
  const labels = useProcessLabels();
  const scopes: RequestScope[] = can(PERMISSIONS.requestViewAll) ? ["mine", "all"] : ["mine"];

  const [selectedScope, setScope] = useState<RequestScope>("mine");
  // Đổi sang workspace không có quyền xem tất cả thì quay về "Của tôi"
  const scope = scopes.includes(selectedScope) ? selectedScope : "mine";
  const [page, setPage] = useState(0);

  const pageOf = (item: RequestScope) => (scope === item ? page : 0);
  const views: Record<RequestScope, ReturnType<typeof useRequests>> = {
    mine: useRequests("mine", pageOf("mine")),
    all: useRequests("all", pageOf("all"), scopes.includes("all")),
  };
  const requests = views[scope];
  const items = requests.data?.items ?? [];
  const names = usePeopleNames(scope === "all" ? items.map((request) => request.initiator) : []);
  const hasActions = scope === "mine";

  const handleScopeChange = (next: RequestScope) => {
    setScope(next);
    setPage(0);
  };

  return (
    <Page>
      {/* Chỉ xem: tạo yêu cầu ở từng app (Service Desk, Đặt phòng); Tầng 2 không có nghiệp vụ riêng */}
      <PageHeader title="Yêu cầu" icon={IconSend} />
      <PageToolbar
        start={
          <ViewTabs
            label="Phạm vi yêu cầu"
            tabs={scopes.map((item) => ({
              key: item,
              label: SCOPE_LABELS[item],
              count: views[item].data?.total,
            }))}
            value={scope}
            onChange={handleScopeChange}
          />
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
            title={scope === "mine" ? "Bạn chưa tạo yêu cầu nào" : "Chưa có yêu cầu nào"}
            description={
              scope === "mine"
                ? "Yêu cầu bạn tạo ở các ứng dụng (ticket, đặt phòng...) sẽ hiện ở đây."
                : "Yêu cầu của mọi người trong workspace sẽ hiện ở đây."
            }
          />
        )}
        {items.length > 0 && (
          <DataTable
            columns={requestColumns(labels, names, scope === "all", hasActions)}
            rows={items}
            getRowKey={(request) => request.id}
            // App có trang chi tiết (nội dung + AI) thì mở trang đó; quy trình không có app mở tiến độ chung
            onRowClick={(request) =>
              navigate(
                processLink(request.processKey, request.businessKey) ?? `/processes/${request.id}`,
              )
            }
          />
        )}
      </PageBody>
    </Page>
  );
};

export default MyRequestsPage;
