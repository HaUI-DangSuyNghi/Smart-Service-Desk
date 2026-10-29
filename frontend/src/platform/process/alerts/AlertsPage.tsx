import { useNavigate } from "react-router";
import { IconAbc, IconAlertTriangle, IconBell, IconClock } from "@tabler/icons-react";
import { cn } from "cn";
import { useCurrentWorkspace } from "@/platform/human/workspace";
import type { Alert } from "@/platform/services";
import { formatDateTime, formatRelativeTime } from "@/platform/shared/lib/format";
import { DataTable, type DataTableColumn } from "@/platform/shared/ui/DataTable";
import { EmptyState } from "@/platform/shared/ui/EmptyState";
import { ErrorState } from "@/platform/shared/ui/ErrorState";
import { Page, PageBody, PageHeader } from "@/platform/shared/ui/page";
import { TableSkeleton } from "@/platform/shared/ui/Skeleton";
import { Tag } from "@/platform/shared/ui/Tag";
import { processLink } from "../common/processLinks";
import { alertLabel, alertSubject, SEVERITY_LABELS, SEVERITY_TONES } from "./alertLabels";
import { useAlerts, useMarkAlertRead } from "./useAlerts";

/** Cảnh báo (R7, R8, R11): quá hạn, sắp hết hạn, khẩn cấp, mở lại...; bấm để đánh dấu đã đọc và mở việc. */
const AlertsPage = () => {
  const navigate = useNavigate();
  const { current } = useCurrentWorkspace();
  const alerts = useAlerts();
  const markRead = useMarkAlertRead();
  const items = alerts.data?.items ?? [];

  const handleOpen = (alert: Alert) => {
    if (!alert.readAt) markRead.mutate(alert.id);
    navigate(
      processLink(alert.processKey, alert.businessKey) ?? `/processes/${alert.processInstanceId}`,
    );
  };

  const columns: DataTableColumn<Alert>[] = [
    {
      key: "type",
      header: "Cảnh báo",
      icon: IconAlertTriangle,
      width: "240px",
      cell: (alert) => (
        <span className={cn("block truncate", !alert.readAt && "font-medium")}>
          {alertLabel(alert.type)}
        </span>
      ),
    },
    {
      key: "severity",
      header: "Mức",
      width: "110px",
      cell: (alert) => (
        <Tag tone={SEVERITY_TONES[alert.severity]}>{SEVERITY_LABELS[alert.severity]}</Tag>
      ),
    },
    {
      key: "label",
      header: "Việc",
      icon: IconAbc,
      width: "300px",
      cell: (alert) => <span className="block truncate">{alertSubject(alert)}</span>,
    },
    {
      key: "createdAt",
      header: "Lúc",
      icon: IconClock,
      width: "140px",
      cell: (alert) => (
        <time dateTime={alert.createdAt} title={formatDateTime(alert.createdAt)}>
          {formatRelativeTime(alert.createdAt)}
        </time>
      ),
    },
  ];

  return (
    <Page>
      <PageHeader title="Cảnh báo" icon={IconBell} />
      <PageBody>
        {current === null && <EmptyState icon={IconBell} title="Chưa thuộc workspace nào" />}
        {current !== null && alerts.isPending && (
          <TableSkeleton columns={4} label="Đang tải cảnh báo" />
        )}
        {alerts.error && <ErrorState error={alerts.error} />}
        {alerts.data && items.length === 0 && (
          <EmptyState
            icon={IconBell}
            title="Không có cảnh báo nào"
            description="Việc quá hạn, sắp hết hạn hay khẩn cấp sẽ được báo ở đây."
          />
        )}
        {items.length > 0 && (
          <DataTable
            columns={columns}
            rows={items}
            getRowKey={(alert) => alert.id}
            onRowClick={handleOpen}
          />
        )}
      </PageBody>
    </Page>
  );
};

export default AlertsPage;
