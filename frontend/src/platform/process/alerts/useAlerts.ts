import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useCurrentWorkspace } from "@/platform/human/workspace";
import { listAlerts, markAlertRead } from "@/platform/services";
import { processKeys } from "../common/queryKeys";

// Chưa có đẩy thông báo thời gian thực nên hỏi định kỳ (plan R7, R8)
const POLL_INTERVAL_MS = 30 * 1000;

/** Cảnh báo của tôi, mới nhất trước; `enabled = false` khi không có quyền xử lý việc. */
export const useAlerts = (enabled = true) => {
  const { current } = useCurrentWorkspace();
  return useQuery({
    queryKey: processKeys.alerts(current),
    // `enabled` bảo đảm current khác null khi queryFn chạy
    queryFn: ({ signal }) => listAlerts(current!, {}, signal),
    enabled: current !== null && enabled,
    refetchInterval: POLL_INTERVAL_MS,
  });
};

export const useMarkAlertRead = () => {
  const { current } = useCurrentWorkspace();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (alertId: string) => {
      if (!current) throw new Error("Chưa chọn workspace");
      return markAlertRead(current, alertId);
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: processKeys.alerts(current) }),
  });
};
