import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useCurrentWorkspace } from "@/platform/human/workspace";
import { claimTask, completeTask, type WorkItem } from "@/platform/services";
import { processKeys } from "../common/queryKeys";

export interface CompleteTaskInput {
  task: WorkItem;
  outcome: string;
  comment: string;
}

// Thành công hay lỗi (409: người khác vừa nhận) đều tải lại để công việc, tiến độ khớp server
const useRefreshProcesses = () => {
  const { current } = useCurrentWorkspace();
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: processKeys.all(current) });
};

export const useClaimTask = () => {
  const { current } = useCurrentWorkspace();
  const refresh = useRefreshProcesses();
  return useMutation({
    mutationFn: (task: WorkItem) => {
      if (!current) throw new Error("Chưa chọn workspace");
      return claimTask(current, task.id);
    },
    onSettled: refresh,
  });
};

export const useCompleteTask = () => {
  const { current } = useCurrentWorkspace();
  const refresh = useRefreshProcesses();
  return useMutation({
    mutationFn: ({ task, outcome, comment }: CompleteTaskInput) => {
      if (!current) throw new Error("Chưa chọn workspace");
      return completeTask(current, task.id, {
        // Gửi bước đang thấy: backend trả 409 nếu bước đã đổi, tránh hoàn thành nhầm bước
        expectedTaskKey: task.key,
        outcome,
        comment: comment.trim() || undefined,
      });
    },
    onSettled: refresh,
  });
};
