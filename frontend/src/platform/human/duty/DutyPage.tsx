import { useState } from "react";
import { IconCalendarTime, IconCalendarUser, IconProgress, IconToggleRight, IconUser } from "@tabler/icons-react";
import type { AgentSchedule, DutyStatus } from "@/platform/services";
import { formatDateTime } from "@/platform/shared/lib/format";
import { Button } from "@/platform/shared/ui/button";
import { DataTable, type DataTableColumn } from "@/platform/shared/ui/DataTable";
import { EmptyState } from "@/platform/shared/ui/EmptyState";
import { ErrorState } from "@/platform/shared/ui/ErrorState";
import { Page, PageBody, PageHeader } from "@/platform/shared/ui/page";
import { TableSkeleton } from "@/platform/shared/ui/Skeleton";
import { Tag } from "@/platform/shared/ui/Tag";
import { personName } from "../workspace/people";
import { useCurrentWorkspace } from "../workspace/useCurrentWorkspace";
import { usePeopleNames } from "../workspace/usePeopleNames";
import { nextShift } from "./scheduleForm";
import { ScheduleDialog } from "./ScheduleDialog";
import { useDutyList, useSchedules } from "./useDuty";

interface Row {
  status: DutyStatus;
  /** Chưa từng lưu lịch thì là lịch rỗng (version 0). */
  schedule: AgentSchedule;
}

const emptySchedule = (subject: string): AgentSchedule => ({
  subject,
  version: 0,
  enabled: false,
  shifts: [],
});

const shiftText = (schedule: AgentSchedule) => {
  if (!schedule.enabled) return <span className="text-crm-text-3">Không dùng lịch</span>;
  const shift = nextShift(schedule);
  if (!shift) return <span className="text-crm-tone-red">Hết ca đã xếp</span>;
  const running = new Date(shift.startsAt) <= new Date();
  return (
    <span title={`${formatDateTime(shift.startsAt)} – ${formatDateTime(shift.endsAt)}`}>
      {running ? "Trong ca tới " : "Ca tới: "}
      {formatDateTime(running ? shift.endsAt : shift.startsAt)}
    </span>
  );
};

/**
 * Ca trực (R3): điều phối viên xem ai đang trực và xếp lịch ca cho từng người.
 * Công tắc trực do kỹ thuật viên tự bật/tắt; đang trực thực tế = công tắc bật và (không dùng lịch
 * hoặc đang trong ca). Chỉ người đang trực được đề xuất nhận việc mới.
 */
const DutyPage = () => {
  const { current } = useCurrentWorkspace();
  const duty = useDutyList();
  const schedules = useSchedules();
  const [editing, setEditing] = useState<AgentSchedule | null>(null);
  const names = usePeopleNames((duty.data ?? []).map((status) => status.subject));

  const bySubject = new Map((schedules.data ?? []).map((item) => [item.subject, item]));
  const rows: Row[] = (duty.data ?? []).map((status) => ({
    status,
    schedule: bySubject.get(status.subject) ?? emptySchedule(status.subject),
  }));

  const columns: DataTableColumn<Row>[] = [
    {
      key: "name",
      header: "Người xử lý",
      icon: IconUser,
      width: "220px",
      cell: ({ status }) => (
        <span className="block truncate">{personName(names, status.subject)}</span>
      ),
    },
    {
      key: "effective",
      header: "Trạng thái",
      icon: IconProgress,
      width: "120px",
      cell: ({ status }) =>
        status.effectiveDuty ? <Tag tone="green">Đang trực</Tag> : <Tag tone="gray">Nghỉ</Tag>,
    },
    {
      key: "manual",
      header: "Công tắc",
      icon: IconToggleRight,
      width: "110px",
      cell: ({ status }) => (
        <span className={status.onDuty ? undefined : "text-crm-text-3"}>
          {status.onDuty ? "Bật" : "Tắt"}
        </span>
      ),
    },
    {
      key: "shift",
      header: "Lịch ca",
      icon: IconCalendarTime,
      width: "240px",
      cell: ({ schedule }) => shiftText(schedule),
    },
    {
      key: "action",
      header: "Thao tác",
      width: "130px",
      cell: ({ schedule }) => (
        <Button
          size="xs"
          variant="outline"
          className="w-full"
          disabled={!schedules.data}
          onClick={(event) => {
            event.stopPropagation();
            setEditing(schedule);
          }}
        >
          Sửa lịch ca
        </Button>
      ),
    },
  ];

  const error = duty.error ?? schedules.error;

  return (
    <Page>
      <PageHeader title="Ca trực" icon={IconCalendarUser} />
      <PageBody>
        {current === null && <EmptyState icon={IconCalendarUser} title="Chưa thuộc workspace nào" />}
        {current !== null && duty.isPending && (
          <TableSkeleton columns={5} label="Đang tải ca trực" />
        )}
        {error && <ErrorState error={error} />}
        {duty.data && rows.length === 0 && (
          <EmptyState icon={IconCalendarUser} title="Chưa có người xử lý nào" />
        )}
        {rows.length > 0 && (
          <DataTable columns={columns} rows={rows} getRowKey={(row) => row.status.subject} />
        )}
      </PageBody>
      <ScheduleDialog
        schedule={editing}
        name={editing ? personName(names, editing.subject) : ""}
        onClose={() => setEditing(null)}
      />
    </Page>
  );
};

export default DutyPage;
