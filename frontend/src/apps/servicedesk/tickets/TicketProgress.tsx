import { ProcessTimeline, useTimeline } from "../../../platform/process";
import { ErrorState } from "../../../platform/shared/ui/ErrorState";
import { FieldsSkeleton } from "../../../platform/shared/ui/Skeleton";

/** Tiến độ xử lý ticket, dùng lại timeline chung của khối P (nhận/xử lý việc ngay tại đây). */
export const TicketProgress = ({
  processInstanceId,
  compact,
}: {
  processInstanceId: string;
  compact?: boolean;
}) => {
  const timeline = useTimeline(processInstanceId);

  if (timeline.isPending) return <FieldsSkeleton rows={5} label="Đang tải tiến độ" />;
  if (timeline.error)
    return <ErrorState error={timeline.error} fallback="Không tải được tiến độ ticket." />;
  return <ProcessTimeline timeline={timeline.data} compact={compact} />;
};
