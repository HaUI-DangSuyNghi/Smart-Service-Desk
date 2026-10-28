import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { listDuty, listSchedules, setMyDuty, updateSchedule } from "@/platform/services";
import type { AgentSchedule } from "@/platform/services";
import { useCurrentWorkspace } from "../workspace/useCurrentWorkspace";

const dutyKey = (workspace: string | null) => ["duty", workspace] as const;
const scheduleKey = (workspace: string | null) => ["duty", workspace, "schedules"] as const;

/** Trạng thái trực trong workspace; kỹ thuật viên chỉ nhận về của mình. */
export const useDutyList = (enabled = true) => {
  const { current } = useCurrentWorkspace();
  return useQuery({
    queryKey: dutyKey(current),
    // `enabled` bảo đảm current khác null khi queryFn chạy
    queryFn: ({ signal }) => listDuty(current!, signal),
    enabled: current !== null && enabled,
  });
};

/** Bật/tắt trực của chính mình (R3); không bật/tắt hộ người khác được. */
export const useSetMyDuty = () => {
  const { current } = useCurrentWorkspace();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (onDuty: boolean) => {
      if (!current) throw new Error("Chưa chọn workspace");
      return setMyDuty(current, { onDuty });
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: dutyKey(current) }),
  });
};

/** Lịch ca của mọi kỹ thuật viên (điều phối viên). */
export const useSchedules = (enabled = true) => {
  const { current } = useCurrentWorkspace();
  return useQuery({
    queryKey: scheduleKey(current),
    queryFn: ({ signal }) => listSchedules(current!, signal),
    enabled: current !== null && enabled,
  });
};

/** Ghi lịch ca của một người; trạng thái trực thực tế đổi theo nên tải lại cả hai. */
export const useUpdateSchedule = () => {
  const { current } = useCurrentWorkspace();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (schedule: AgentSchedule) => {
      if (!current) throw new Error("Chưa chọn workspace");
      return updateSchedule(current, schedule);
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: dutyKey(current) }),
  });
};
