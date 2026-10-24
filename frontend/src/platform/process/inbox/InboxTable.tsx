import { IconCheckbox, IconClock, IconHash, IconRoute, IconUsersGroup } from "@tabler/icons-react";
import type { InboxScope, WorkItem } from "@/platform/services";
import { formatDateTime, formatRelativeTime } from "@/platform/shared/lib/format";
import { Button } from "@/platform/shared/ui/button";
import { DataTable, type DataTableColumn } from "@/platform/shared/ui/DataTable";
import { useProcessLabels } from "../catalog/useProcessCatalog";
import { BusinessKeyLink } from "../common/BusinessKeyLink";
import { CandidateGroups } from "../common/CandidateGroups";

interface Props {
  scope: InboxScope;
  items: readonly WorkItem[];
  /** id việc đang gửi yêu cầu nhận, để khóa đúng nút đó. */
  claimingId?: string;
  onClaim: (task: WorkItem) => void;
  onComplete: (task: WorkItem) => void;
  /** Mở trang tiến độ; chỉ dùng cho việc của tôi (việc chưa nhận thì backend chưa cho xem). */
  onOpen: (task: WorkItem) => void;
}

/** Bảng việc: cùng cột cho mọi quy trình, nút theo view (Nhận việc / Hoàn thành). */
export const InboxTable = ({ scope, items, claimingId, onClaim, onComplete, onOpen }: Props) => {
  const labels = useProcessLabels();

  const actionCell = (task: WorkItem) => {
    if (scope === "CANDIDATE") {
      return (
        <Button size="xs" disabled={claimingId === task.id} onClick={() => onClaim(task)}>
          {claimingId === task.id ? "Đang nhận..." : "Nhận việc"}
        </Button>
      );
    }
    return (
      <Button
        size="xs"
        onClick={(event) => {
          // Không để dòng bắt cú bấm này rồi mở trang tiến độ
          event.stopPropagation();
          onComplete(task);
        }}
      >
        Hoàn thành
      </Button>
    );
  };

  const columns: readonly DataTableColumn<WorkItem>[] = [
    {
      key: "task",
      header: "Việc",
      icon: IconCheckbox,
      width: "240px",
      cell: (task) => labels.taskName(task.processKey, task.key, task.name),
    },
    {
      key: "process",
      header: "Quy trình",
      icon: IconRoute,
      width: "200px",
      cell: (task) => labels.processName(task.processKey),
    },
    {
      key: "businessKey",
      header: "Mã yêu cầu",
      icon: IconHash,
      width: "130px",
      cell: (task) => <BusinessKeyLink processKey={task.processKey} businessKey={task.businessKey} />,
    },
    {
      key: "groups",
      header: "Nhóm xử lý",
      icon: IconUsersGroup,
      width: "160px",
      cell: (task) => <CandidateGroups groups={task.candidateGroups} />,
    },
    {
      key: "createdAt",
      header: "Tạo lúc",
      icon: IconClock,
      width: "140px",
      cell: (task) => (
        <time dateTime={task.createdAt} title={formatDateTime(task.createdAt)}>
          {formatRelativeTime(task.createdAt)}
        </time>
      ),
    },
    { key: "action", header: "Thao tác", width: "120px", cell: actionCell },
  ];

  return (
    <DataTable
      columns={columns}
      rows={items}
      getRowKey={(task) => task.id}
      onRowClick={scope === "ASSIGNED" ? onOpen : undefined}
    />
  );
};
