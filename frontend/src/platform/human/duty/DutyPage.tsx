import { IconCalendarUser, IconClock, IconProgress, IconUser } from "@tabler/icons-react";
import type { DutyStatus } from "@/platform/services";
import { formatDateTime, formatRelativeTime } from "@/platform/shared/lib/format";
import { Button } from "@/platform/shared/ui/button";
import { DataTable, type DataTableColumn } from "@/platform/shared/ui/DataTable";
import { EmptyState } from "@/platform/shared/ui/EmptyState";
import { ErrorState } from "@/platform/shared/ui/ErrorState";
import { Page, PageBody, PageHeader } from "@/platform/shared/ui/page";
import { TableSkeleton } from "@/platform/shared/ui/Skeleton";
import { Tag } from "@/platform/shared/ui/Tag";
import { toast, toastError } from "@/platform/shared/ui/toast";
import { useCurrentWorkspace } from "../workspace/useCurrentWorkspace";
import { useDutyList, useSetDuty } from "./useDuty";

/** Ca trực (R3): điều phối viên xem ai đang trực và bật/tắt hộ; chỉ người đang trực được giao việc mới. */
const DutyPage = () => {
  const { current } = useCurrentWorkspace();
  const duty = useDutyList();
  const setDuty = useSetDuty();
  const items = duty.data ?? [];

  const handleToggle = (status: DutyStatus) => {
    setDuty.mutate(
      { subject: status.subject, onDuty: !status.onDuty },
      {
        onSuccess: (next) =>
          toast.success(next.onDuty ? "Đã bật trực" : "Đã chuyển sang nghỉ", {
            description: next.displayName,
          }),
        onError: (error) => toastError("Không đổi được trạng thái trực.", error),
      },
    );
  };

  const columns: DataTableColumn<DutyStatus>[] = [
    {
      key: "name",
      header: "Người xử lý",
      icon: IconUser,
      width: "240px",
      cell: (status) => <span className="block truncate">{status.displayName}</span>,
    },
    {
      key: "state",
      header: "Trạng thái",
      icon: IconProgress,
      width: "120px",
      cell: (status) =>
        status.onDuty ? <Tag tone="green">Đang trực</Tag> : <Tag tone="gray">Nghỉ</Tag>,
    },
    {
      key: "changedAt",
      header: "Đổi lúc",
      icon: IconClock,
      width: "140px",
      cell: (status) => (
        <time dateTime={status.changedAt} title={formatDateTime(status.changedAt)}>
          {formatRelativeTime(status.changedAt)}
        </time>
      ),
    },
    {
      key: "action",
      header: "Thao tác",
      width: "120px",
      cell: (status) => (
        <Button
          size="xs"
          variant="outline"
          className="w-full"
          disabled={setDuty.isPending && setDuty.variables?.subject === status.subject}
          onClick={() => handleToggle(status)}
        >
          {status.onDuty ? "Cho nghỉ" : "Bật trực"}
        </Button>
      ),
    },
  ];

  return (
    <Page>
      <PageHeader title="Ca trực" icon={IconCalendarUser} />
      <PageBody>
        {current === null && <EmptyState icon={IconCalendarUser} title="Chưa thuộc workspace nào" />}
        {current !== null && duty.isPending && (
          <TableSkeleton columns={4} label="Đang tải ca trực" />
        )}
        {duty.error && <ErrorState error={duty.error} />}
        {duty.data && items.length === 0 && (
          <EmptyState icon={IconCalendarUser} title="Chưa có người xử lý nào" />
        )}
        {items.length > 0 && (
          <DataTable columns={columns} rows={items} getRowKey={(status) => status.subject} />
        )}
      </PageBody>
    </Page>
  );
};

export default DutyPage;
