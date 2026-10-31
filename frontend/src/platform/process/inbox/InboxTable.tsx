import type { MouseEvent } from "react";
import {
  IconCheckbox,
  IconClock,
  IconHash,
  IconRoute,
  IconUser,
  IconUsersGroup,
} from "@tabler/icons-react";
import {
  usePeopleNames,
  personName,
  usePermissions,
  PERMISSIONS,
} from "@/platform/human/workspace";
import type { InboxScope, WorkItem } from "@/platform/services";
import { formatDateTime, formatRelativeTime } from "@/platform/shared/lib/format";
import { Button } from "@/platform/shared/ui/button";
import { DataTable, type DataTableColumn } from "@/platform/shared/ui/DataTable";
import { useProcessLabels } from "../catalog/useProcessCatalog";
import { BusinessKeyLink } from "../common/BusinessKeyLink";
import { CandidateGroups } from "../common/CandidateGroups";
import { inboxAction } from "../common/inboxActions";

interface Props {
  scope: InboxScope;
  items: readonly WorkItem[];
  /** id việc đang gửi yêu cầu nhận, để khóa đúng nút đó. */
  claimingId?: string;
  onClaim: (task: WorkItem) => void;
  onComplete: (task: WorkItem) => void;
  onAssign: (task: WorkItem) => void;
  /** Mở trang tiến độ; việc chưa nhận thì backend chưa cho xem nên view "Có thể nhận" không mở. */
  onOpen: (task: WorkItem) => void;
}

const ACTION_BUTTON = "min-h-9 w-full md:min-h-7";

// Nút trong dòng: chặn cú bấm lan lên dòng (dòng cũng bấm được để mở trang tiến độ)
const stop = (action: () => void) => (event: MouseEvent) => {
  event.stopPropagation();
  action();
};

/** Bảng việc: cùng cột cho mọi quy trình, nút theo view (Nhận việc / Xử lý / Giao việc). */
export const InboxTable = ({
  scope,
  items,
  claimingId,
  onClaim,
  onComplete,
  onAssign,
  onOpen,
}: Props) => {
  const labels = useProcessLabels();
  const { can } = usePermissions();
  const names = usePeopleNames(items.map((task) => task.assignee));

  const genericActionCell = (task: WorkItem) => {
    if (scope === "CANDIDATE") {
      return (
        <Button
          size="sm"
          variant="info"
          className={ACTION_BUTTON}
          disabled={claimingId === task.id}
          onClick={() => onClaim(task)}
        >
          {claimingId === task.id ? "Đang nhận..." : "Nhận việc"}
        </Button>
      );
    }
    if (scope === "ALL") {
      const fallback = (
        <Button
          size="sm"
          variant="info"
          className={ACTION_BUTTON}
          onClick={stop(() => onAssign(task))}
        >
          {task.assignee ? "Giao lại" : "Giao việc"}
        </Button>
      );
      return fallback;
    }
    return (
      <Button
        size="sm"
        variant="info"
        className={ACTION_BUTTON}
        onClick={stop(() => onComplete(task))}
      >
        Xử lý
      </Button>
    );
  };
  const actionCell = (task: WorkItem) => {
    const fallback = genericActionCell(task);
    const Action = can(PERMISSIONS.taskAssign) ? inboxAction(task.processKey) : null;
    return Action ? <Action task={task} fallback={fallback} /> : fallback;
  };

  const columns: DataTableColumn<WorkItem>[] = [
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
      cell: (task) => (
        <BusinessKeyLink processKey={task.processKey} businessKey={task.businessKey} />
      ),
    },
    {
      key: "groups",
      header: "Nhóm xử lý",
      icon: IconUsersGroup,
      width: "160px",
      cell: (task) => <CandidateGroups groups={task.candidateGroups} />,
    },
  ];
  if (scope === "ALL") {
    columns.push({
      key: "assignee",
      header: "Người giữ",
      icon: IconUser,
      width: "180px",
      cell: (task) =>
        task.assignee ? (
          personName(names, task.assignee)
        ) : (
          <span className="text-crm-text-3">Chưa ai nhận</span>
        ),
    });
  }
  columns.push(
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
    // Vừa đủ cho chữ dài nhất ("Đang nhận...") ở cỡ nút xs
    { key: "action", header: "Thao tác", fit: true, cell: actionCell },
  );

  return (
    <DataTable
      columns={columns}
      rows={items}
      getRowKey={(task) => task.id}
      onRowClick={scope === "CANDIDATE" ? undefined : onOpen}
    />
  );
};
