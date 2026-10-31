import { useState } from "react";
import { useNavigate } from "react-router";
import { IconDoor, IconRefresh } from "@tabler/icons-react";
import {
  PERMISSIONS,
  personName,
  useCurrentWorkspace,
  usePeopleNames,
  usePermissions,
} from "../../../platform/human/workspace";
import type { RequestScope } from "../../../platform/services";
import { formatDateTime, formatRelativeTime } from "../../../platform/shared/lib/format";
import { useTablePageSize } from "../../../platform/shared/lib/useTablePageSize";
import { Button } from "../../../platform/shared/ui/button";
import { DataTable, type DataTableColumn } from "../../../platform/shared/ui/DataTable";
import { EmptyState } from "../../../platform/shared/ui/EmptyState";
import { ErrorState } from "../../../platform/shared/ui/ErrorState";
import { Input } from "../../../platform/shared/ui/input";
import { Page, PageBody, PageHeader, PageToolbar } from "../../../platform/shared/ui/page";
import { Pagination } from "../../../platform/shared/ui/Pagination";
import { SelectField } from "../../../platform/shared/ui/SelectField";
import { TableSkeleton } from "../../../platform/shared/ui/Skeleton";
import { ViewTabs } from "../../../platform/shared/ui/ViewTabs";
import { CreateRoomRequestDialog } from "../requests/CreateRoomRequestDialog";
import { RoomOwnerActions } from "../requests/RoomOwnerActions";
import { RoomRequestStateTag } from "../requests/RoomRequestStateTag";
import { useRoomRequestList } from "../requests/useRoomRequests";
import type { RoomRequestRow } from "../services/roomRequestService";

const RoomRequestHomePage = () => {
  const navigate = useNavigate();
  const { current } = useCurrentWorkspace();
  const { can } = usePermissions();
  const [selectedScope, setScope] = useState<RequestScope>("mine");
  const scope = can(PERMISSIONS.requestViewAll) ? selectedScope : "mine";
  const [search, setSearch] = useState("");
  const [state, setState] = useState("");
  const [page, setPage] = useState(0);
  const { tableRef, pageSize } = useTablePageSize();
  const requests = useRoomRequestList(scope);
  const rows = requests.data ?? [];
  const names = usePeopleNames(rows.map((request) => request.initiator));
  const term = search.trim().toLocaleLowerCase("vi");
  const filtered = rows.filter(
    (request) =>
      (!term ||
        `${request.room} ${request.businessKey} ${personName(names, request.initiator)}`
          .toLocaleLowerCase("vi")
          .includes(term)) &&
      (!state ||
        (state === "cancelled"
          ? request.cancelled
          : state === "pending"
            ? !request.endedAt
            : Boolean(request.endedAt) && !request.cancelled)),
  );
  const currentPage = Math.min(page, Math.max(0, Math.ceil(filtered.length / pageSize) - 1));
  const columns: DataTableColumn<RoomRequestRow>[] = [
    {
      key: "room",
      header: "Phòng",
      width: "220px",
      cell: (request) => (
        <span className="block max-w-55 truncate font-medium" title={request.room}>
          {request.room}
        </span>
      ),
    },
    {
      key: "businessKey",
      header: "Mã yêu cầu",
      width: "130px",
      cell: (request) => (
        <span className="font-mono text-crm-accent" title={request.businessKey}>
          {request.businessKey.slice(0, 8)}
        </span>
      ),
    },
    {
      key: "state",
      header: "Trạng thái",
      width: "140px",
      cell: (request) => <RoomRequestStateTag request={request} />,
    },
    ...(scope === "all"
      ? [
          {
            key: "creator",
            header: "Người tạo",
            width: "180px",
            cell: (request: RoomRequestRow) => personName(names, request.initiator),
          },
        ]
      : []),
    {
      key: "createdAt",
      header: "Tạo lúc",
      width: "150px",
      cell: (request) => (
        <time dateTime={request.startedAt} title={formatDateTime(request.startedAt)}>
          {formatRelativeTime(request.startedAt)}
        </time>
      ),
    },
    {
      key: "action",
      header: "Thao tác",
      fit: true,
      cell: (request) =>
        request.endedAt ? null : (
          <RoomOwnerActions processId={request.id} creator={request.initiator} compact />
        ),
    },
  ];
  return (
    <Page>
      <PageHeader title="Đặt phòng" icon={IconDoor} />
      <PageToolbar
        start={
          <>
            <ViewTabs
              label="Phạm vi đặt phòng"
              value={scope}
              onChange={(value) => {
                setScope(value);
                setPage(0);
              }}
              tabs={[
                { key: "mine", label: "Của tôi" },
                ...(can(PERMISSIONS.requestViewAll)
                  ? [{ key: "all" as const, label: "Tất cả đặt phòng" }]
                  : []),
              ]}
            />
            <Input
              aria-label="Tìm đặt phòng"
              placeholder="Tìm phòng, mã yêu cầu…"
              value={search}
              onChange={(event) => {
                setSearch(event.target.value);
                setPage(0);
              }}
              className="h-7 w-52"
            />
          </>
        }
        end={
          <>
            <SelectField
              aria-label="Trạng thái đặt phòng"
              placeholder="Mọi trạng thái"
              value={state}
              onValueChange={(value) => {
                setState(value);
                setPage(0);
              }}
              options={[
                { value: "pending", label: "Chờ duyệt" },
                { value: "ended", label: "Đã kết thúc" },
                { value: "cancelled", label: "Đã hủy" },
              ]}
            />
            <Button
              variant="outline"
              size="icon-sm"
              aria-label="Làm mới đặt phòng"
              disabled={requests.isFetching}
              onClick={() => void requests.refetch()}
            >
              <IconRefresh size={16} />
            </Button>
            {current && can(PERMISSIONS.requestCreate) && <CreateRoomRequestDialog />}
          </>
        }
      />
      <PageBody>
        <div ref={tableRef} className="h-full min-h-0">
          {!current ? (
            <EmptyState icon={IconDoor} title="Chưa thuộc workspace nào" />
          ) : requests.isPending ? (
            <TableSkeleton columns={columns.length} label="Đang tải đặt phòng" />
          ) : requests.error ? (
            <ErrorState error={requests.error} />
          ) : filtered.length === 0 ? (
            <EmptyState
              icon={IconDoor}
              title="Không có yêu cầu đặt phòng"
              description="Yêu cầu đặt phòng phù hợp với bộ lọc sẽ hiển thị ở đây."
            />
          ) : (
            <DataTable
              columns={columns}
              rows={filtered.slice(currentPage * pageSize, (currentPage + 1) * pageSize)}
              getRowKey={(request) => request.id}
              onRowClick={(request) => navigate(`/roomrequest/requests/${request.businessKey}`)}
            />
          )}
        </div>
      </PageBody>
      {requests.isSuccess && filtered.length > 0 && (
        <div className="flex shrink-0 justify-center border-t border-crm-line px-3 py-1.5 text-crm-text-2">
          <Pagination
            editable
            page={currentPage}
            total={filtered.length}
            pageSize={pageSize}
            onPageChange={setPage}
          />
        </div>
      )}
    </Page>
  );
};

export default RoomRequestHomePage;
