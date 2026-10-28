import { useState } from "react";
import { useNavigate } from "react-router";
import {
  IconClock,
  IconHourglass,
  IconMessage,
  IconProgress,
  IconTypography,
  IconUser,
} from "@tabler/icons-react";
import { personName, useCurrentWorkspace, usePeopleNames } from "@/platform/human/workspace";
import type { EstimateRequest, EstimateRequestStatus } from "@/platform/services";
import { formatDateTime, formatRelativeTime } from "@/platform/shared/lib/format";
import { Button } from "@/platform/shared/ui/button";
import { DataTable, type DataTableColumn } from "@/platform/shared/ui/DataTable";
import { EmptyState } from "@/platform/shared/ui/EmptyState";
import { ErrorState } from "@/platform/shared/ui/ErrorState";
import { Page, PageBody, PageHeader, PageToolbar } from "@/platform/shared/ui/page";
import { TableSkeleton } from "@/platform/shared/ui/Skeleton";
import { Tag, type TagTone } from "@/platform/shared/ui/Tag";
import { toast, toastError } from "@/platform/shared/ui/toast";
import { ViewTabs } from "@/platform/shared/ui/ViewTabs";
import { useDecideEstimate, useEstimateRequests } from "./useWork";
import { workErrorMessage } from "./workErrors";
import { formatMinutes } from "./workLabels";

const STATUS_LABELS: Record<EstimateRequestStatus, string> = {
  PENDING: "Chờ quyết",
  APPROVED: "Đã đồng ý",
  REJECTED: "Đã từ chối",
  // Bị thay do đổi phân loại hoặc đổi người giữ việc
  SUPERSEDED: "Hết hiệu lực",
};

const STATUS_TONES: Record<EstimateRequestStatus, TagTone> = {
  PENDING: "orange",
  APPROVED: "green",
  REJECTED: "gray",
  SUPERSEDED: "gray",
};

type View = "PENDING" | "ALL";

/** Yêu cầu sửa thời gian ước tính của kỹ thuật viên (R2); điều phối viên đồng ý hoặc từ chối. */
const EstimateRequestsPage = () => {
  const navigate = useNavigate();
  const { current } = useCurrentWorkspace();
  const [view, setView] = useState<View>("PENDING");
  const pending = useEstimateRequests("PENDING");
  const all = useEstimateRequests();
  const requests = view === "PENDING" ? pending : all;
  const items = requests.data?.items ?? [];
  const names = usePeopleNames(items.map((item) => item.requestedBy));
  const decide = useDecideEstimate();

  const handleDecide = (request: EstimateRequest, approved: boolean) => {
    decide.mutate(
      { requestId: request.id, approved },
      {
        onSuccess: () =>
          toast.success(approved ? "Đã đồng ý ước tính mới" : "Đã từ chối", {
            description: request.label,
          }),
        onError: (error) => toastError(workErrorMessage(error, "Không gửi được quyết định."), error),
      },
    );
  };

  const columns: DataTableColumn<EstimateRequest>[] = [
    {
      key: "label",
      header: "Việc",
      icon: IconTypography,
      width: "260px",
      cell: (request) => <span className="block truncate">{request.label}</span>,
    },
    {
      key: "requestedBy",
      header: "Người xin",
      icon: IconUser,
      width: "170px",
      cell: (request) => personName(names, request.requestedBy),
    },
    {
      key: "minutes",
      header: "Ước tính",
      icon: IconHourglass,
      width: "190px",
      cell: (request) => (
        <span className="whitespace-nowrap">
          {formatMinutes(request.currentMinutes)} → {formatMinutes(request.proposedMinutes)}
        </span>
      ),
    },
    {
      key: "reason",
      header: "Lý do",
      icon: IconMessage,
      width: "260px",
      cell: (request) => (
        <span className="block truncate" title={request.reason}>
          {request.reason}
        </span>
      ),
    },
    {
      key: "createdAt",
      header: "Gửi lúc",
      icon: IconClock,
      width: "120px",
      cell: (request) => (
        <time dateTime={request.createdAt} title={formatDateTime(request.createdAt)}>
          {formatRelativeTime(request.createdAt)}
        </time>
      ),
    },
    {
      key: "status",
      header: "Trạng thái",
      icon: IconProgress,
      width: "180px",
      cell: (request) =>
        request.status === "PENDING" ? (
          <span className="flex gap-1" onClick={(event) => event.stopPropagation()}>
            <Button
              size="xs"
              disabled={decide.isPending}
              onClick={() => handleDecide(request, true)}
            >
              Đồng ý
            </Button>
            <Button
              size="xs"
              variant="outline"
              disabled={decide.isPending}
              onClick={() => handleDecide(request, false)}
            >
              Từ chối
            </Button>
          </span>
        ) : (
          <Tag tone={STATUS_TONES[request.status]}>{STATUS_LABELS[request.status]}</Tag>
        ),
    },
  ];

  return (
    <Page>
      <PageHeader title="Yêu cầu sửa ước tính" icon={IconHourglass} />
      <PageToolbar
        start={
          <ViewTabs
            label="Lọc yêu cầu"
            tabs={[
              { key: "PENDING", label: "Chờ quyết", count: pending.data?.total },
              { key: "ALL", label: "Tất cả", count: all.data?.total },
            ]}
            value={view}
            onChange={setView}
          />
        }
      />
      <PageBody>
        {current === null && <EmptyState icon={IconHourglass} title="Chưa thuộc workspace nào" />}
        {current !== null && requests.isPending && (
          <TableSkeleton columns={6} label="Đang tải yêu cầu" />
        )}
        {requests.error && <ErrorState error={requests.error} />}
        {requests.data && items.length === 0 && (
          <EmptyState
            icon={IconHourglass}
            title="Không có yêu cầu nào"
            description="Kỹ thuật viên xin sửa ước tính thì yêu cầu hiện ở đây."
          />
        )}
        {items.length > 0 && (
          <DataTable
            columns={columns}
            rows={items}
            getRowKey={(request) => request.id}
            // Yêu cầu không mang processKey nên mở trang tiến độ chung
            onRowClick={(request) => navigate(`/processes/${request.processInstanceId}`)}
          />
        )}
      </PageBody>
    </Page>
  );
};

export default EstimateRequestsPage;
