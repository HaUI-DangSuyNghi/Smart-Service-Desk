import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useCurrentWorkspace } from "@/platform/human/workspace";
import {
  decideEstimateRequest,
  getAssignmentSuggestion,
  getQueue,
  listEstimateRequests,
  pauseWork,
  releaseWork,
  requestEstimateChange,
  resumeWork,
  startWork,
  type EstimateRequestStatus,
  type PauseInput,
  type ReleaseInput,
} from "@/platform/services";
import { processKeys } from "../common/queryKeys";

// Hàng đợi đổi khi người khác giao việc mới, nên tự tải lại như trang Công việc
const REFRESH_INTERVAL_MS = 30 * 1000;

/** Hàng đợi đã sắp xếp; bỏ trống assignee là của chính mình. */
export const useQueue = (assignee?: string, enabled = true) => {
  const { current } = useCurrentWorkspace();
  return useQuery({
    queryKey: processKeys.queue(current, assignee),
    // `enabled` bảo đảm current khác null khi queryFn chạy
    queryFn: ({ signal }) => getQueue(current!, { assignee }, signal),
    enabled: current !== null && enabled,
    refetchInterval: REFRESH_INTERVAL_MS,
  });
};

/** Đề xuất người xử lý cho một việc; taskId null thì chưa tải. */
export const useAssignmentSuggestion = (taskId: string | null) => {
  const { current } = useCurrentWorkspace();
  return useQuery({
    queryKey: processKeys.suggestion(current, taskId ?? ""),
    queryFn: ({ signal }) => getAssignmentSuggestion(current!, taskId!, signal),
    enabled: current !== null && taskId !== null,
  });
};

export const useEstimateRequests = (status?: EstimateRequestStatus) => {
  const { current } = useCurrentWorkspace();
  return useQuery({
    queryKey: processKeys.estimateRequests(current, status),
    queryFn: ({ signal }) => listEstimateRequests(current!, { status }, signal),
    enabled: current !== null,
  });
};

/** Thao tác trên việc: xong (hay lỗi 409) đều làm mới cả khối P để hàng đợi, tiến độ khớp server. */
const useWorkAction = <Input, Output>(
  action: (workspace: string, input: Input) => Promise<Output>,
) => {
  const { current } = useCurrentWorkspace();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: Input) => {
      if (!current) throw new Error("Chưa chọn workspace");
      return action(current, input);
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: processKeys.all(current) }),
  });
};

export const useStartWork = () =>
  useWorkAction((workspace, taskId: string) => startWork(workspace, taskId));

export const useResumeWork = () =>
  useWorkAction((workspace, taskId: string) => resumeWork(workspace, taskId));

export const usePauseWork = () =>
  useWorkAction((workspace, { taskId, input }: { taskId: string; input: PauseInput }) =>
    pauseWork(workspace, taskId, input),
  );

export const useReleaseWork = () =>
  useWorkAction((workspace, { taskId, input }: { taskId: string; input: ReleaseInput }) =>
    releaseWork(workspace, taskId, input),
  );

export const useRequestEstimate = () =>
  useWorkAction(
    (workspace, input: { taskId: string; proposedMinutes: number; reason: string }) =>
      requestEstimateChange(workspace, input.taskId, {
        proposedMinutes: input.proposedMinutes,
        reason: input.reason,
      }),
  );

export const useDecideEstimate = () =>
  useWorkAction(
    (workspace, input: { requestId: string; approved: boolean; note?: string }) =>
      decideEstimateRequest(workspace, input.requestId, {
        approved: input.approved,
        note: input.note,
      }),
  );
