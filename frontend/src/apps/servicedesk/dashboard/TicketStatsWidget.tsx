import { useQuery } from "@tanstack/react-query";
import {
  IconAlertTriangle,
  IconCircleCheck,
  IconMessageCheck,
  IconTicket,
  IconTool,
} from "@tabler/icons-react";
import { StatTile, type DashboardWidgetProps } from "../../../platform/data/dashboard";
import { useCurrentWorkspace } from "../../../platform/human/workspace";
import { ErrorState } from "../../../platform/shared/ui/ErrorState";
import { TextSkeleton } from "../../../platform/shared/ui/Skeleton";
import { getTicketStatistics } from "../services/ticketService";

/** Thống kê ticket Service Desk theo khoảng thời gian dashboard đang chọn (ngày tạo ticket). */
export const TicketStatsWidget = ({ from, to }: DashboardWidgetProps) => {
  const { current } = useCurrentWorkspace();
  const statistics = useQuery({
    queryKey: ["servicedesk", "statistics", current, from, to],
    // `enabled` bảo đảm current khác null khi queryFn chạy
    queryFn: ({ signal }) => getTicketStatistics(current!, { from, to }, signal),
    enabled: current !== null,
  });

  if (statistics.isPending) return <TextSkeleton lines={2} label="Đang tải thống kê ticket" />;
  if (statistics.error)
    return <ErrorState error={statistics.error} fallback="Không tải được thống kê ticket." />;
  const data = statistics.data;

  return (
    <div className="flex flex-col gap-2">
      <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
        <StatTile
          label="Ticket mới"
          icon={IconTicket}
          value={String(data.total)}
          hint="tạo trong khoảng đã chọn"
        />
        <StatTile
          label="Chờ xử lý"
          icon={IconTool}
          value={String(data.awaitingHandling)}
          hint="kỹ thuật viên chưa xong"
        />
        <StatTile
          label="Chờ xác nhận"
          icon={IconMessageCheck}
          value={String(data.awaitingConfirmation)}
          hint="chờ người yêu cầu xác nhận"
        />
        <StatTile
          label="Đã xong"
          icon={IconCircleCheck}
          value={String(data.completed)}
          hint="người yêu cầu đã chấp nhận"
        />
      </div>
      {/* Ticket không xếp được trạng thái là dấu hiệu dữ liệu bất thường, không gộp vào "Đã xong" */}
      {data.unclassified > 0 && (
        <p className="flex items-center gap-1.5 text-crm-sm text-crm-tone-orange">
          <IconAlertTriangle size={14} stroke={2} />
          {data.unclassified} ticket không xác định được trạng thái, cần kiểm tra dữ liệu.
        </p>
      )}
    </div>
  );
};
