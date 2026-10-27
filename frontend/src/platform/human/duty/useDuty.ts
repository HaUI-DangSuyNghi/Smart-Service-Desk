import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { listDuty, setDuty } from "@/platform/services";
import { useCurrentWorkspace } from "../workspace/useCurrentWorkspace";

const dutyKey = (workspace: string | null) => ["duty", workspace] as const;

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

/** Bật/tắt trực; subject "me" là chính mình (R3). */
export const useSetDuty = () => {
  const { current } = useCurrentWorkspace();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ subject, onDuty }: { subject: string; onDuty: boolean }) => {
      if (!current) throw new Error("Chưa chọn workspace");
      return setDuty(current, subject, { onDuty });
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: dutyKey(current) }),
  });
};
