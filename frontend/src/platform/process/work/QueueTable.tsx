import {
  IconCalendarTime,
  IconClockHour4,
  IconFlag,
  IconHash,
  IconHourglass,
  IconProgress,
  IconTypography,
} from "@tabler/icons-react";
import type { QueueItem } from "@/platform/services";
import { DataTable, type DataTableColumn } from "@/platform/shared/ui/DataTable";
import { EstimateText } from "./EstimateText";
import { PriorityTag } from "./PriorityTag";
import { SlaIndicator } from "./SlaIndicator";
import { WorkActions } from "./WorkActions";
import { formatMinutes, isEscalated } from "./workLabels";
import { WorkStateTag } from "./WorkStateTag";

interface Props {
  items: readonly QueueItem[];
  /** Số thứ tự của dòng đầu (trang sau thì cộng thêm). */
  firstPosition?: number;
  /** Hàng đợi của chính mình thì có cột thao tác. */
  showActions: boolean;
  onOpen: (item: QueueItem) => void;
}

const isStarted = (item: QueueItem) => item.workState !== "NOT_STARTED" || item.workedMinutes > 0;

/** Bảng hàng đợi đã sắp theo R4; thứ tự do backend quyết, bảng không tự sắp lại. */
export const QueueTable = ({ items, firstPosition = 1, showActions, onOpen }: Props) => {
  // Chưa có việc đang làm thì gợi ý làm ngay việc đầu hàng
  const suggestedId = items.some((item) => item.workState === "IN_PROGRESS")
    ? null
    : items.find((item) => item.workState === "NOT_STARTED")?.id;
  const columns: DataTableColumn<QueueItem>[] = [
    {
      key: "label",
      header: "Việc",
      icon: IconTypography,
      width: "320px",
      cell: (item) => (
        <span className="flex min-w-0 items-center gap-1.5">
          <span className="w-5 shrink-0 text-crm-text-3 tabular-nums">
            {firstPosition + items.indexOf(item)}
          </span>
          <span className="truncate">{item.label}</span>
          {item.reopenCount > 0 && (
            <span className="shrink-0 text-crm-sm text-crm-tone-red">
              mở lại {item.reopenCount}
            </span>
          )}
        </span>
      ),
    },
    {
      key: "priority",
      header: "Ưu tiên",
      icon: IconFlag,
      width: "130px",
      cell: (item) => <PriorityTag priority={item.priority} escalated={isEscalated(item)} />,
    },
    {
      key: "state",
      header: "Trạng thái",
      icon: IconProgress,
      width: "130px",
      cell: (item) => <WorkStateTag state={item.workState} pendingReason={item.pendingReason} />,
    },
    {
      key: "sla",
      header: "Hạn",
      icon: IconClockHour4,
      width: "180px",
      cell: (item) =>
        item.sla ? (
          <SlaIndicator sla={item.sla} started={isStarted(item)} />
        ) : (
          <span className="text-crm-text-3">Không có hạn</span>
        ),
    },
    {
      key: "estimate",
      header: "Ước tính",
      icon: IconHourglass,
      width: "220px",
      cell: (item) => <EstimateText estimate={item.estimate} />,
    },
    {
      key: "remaining",
      header: "Còn lại",
      icon: IconCalendarTime,
      width: "130px",
      // Phần tính vào tải: ước tính trừ thời gian đã làm (đang chờ thì tính một nửa)
      cell: (item) => formatMinutes(item.remainingMinutes),
    },
  ];
  if (showActions) {
    columns.push({
      key: "action",
      header: "Thao tác",
      icon: IconHash,
      width: "150px",
      cell: (item) => <WorkActions item={item} compact suggested={item.id === suggestedId} />,
    });
  }

  return (
    <DataTable columns={columns} rows={items} getRowKey={(item) => item.id} onRowClick={onOpen} />
  );
};
