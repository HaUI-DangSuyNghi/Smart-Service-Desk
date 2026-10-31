import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useCurrentWorkspace } from "../../../platform/human/workspace";
import { completeTask, type Priority } from "../../../platform/services";
import {
  correctClassification,
  createTicket,
  getEstimateDefault,
  getReviewSuggestion,
  getTicket,
  getIntelligenceStatus,
  retryTicketAssessment,
  listTickets,
  reviewTicket,
  type ClassificationCorrection,
  type NewTicket,
  type ReviewDecision,
  type TicketQuery,
  type TicketView,
} from "../services/ticketService";
import type { Category } from "./ticketRules";

export const TICKETS_PAGE_SIZE = 20;

// AI chạy nền sau khi tạo ticket: hỏi lại cho tới khi có kết quả
const ASSESSMENT_POLL_MS = 2000;

const ticketKeys = {
  all: (workspace: string | null) => ["servicedesk", "tickets", workspace] as const,
  list: (workspace: string | null, query: TicketQuery) =>
    [...ticketKeys.all(workspace), "list", query] as const,
  detail: (workspace: string, id: string) => [...ticketKeys.all(workspace), "detail", id] as const,
};

export type TicketFilter = Omit<TicketQuery, "page" | "size">;

/** Ticket của workspace đang chọn theo bộ lọc; mặc định mới nhất trước. */
export const useTicketList = (
  page: number,
  filter: TicketFilter = {},
  { size = TICKETS_PAGE_SIZE, enabled = true }: { size?: number; enabled?: boolean } = {},
) => {
  const { current } = useCurrentWorkspace();
  return useQuery({
    queryKey: ticketKeys.list(current, { ...filter, page, size }),
    // `enabled` bảo đảm current khác null khi queryFn chạy
    queryFn: ({ signal }) => listTickets(current!, { ...filter, page, size }, signal),
    enabled: current !== null && enabled,
    placeholderData: keepPreviousData,
  });
};

const isAssessing = (ticket?: TicketView) => ticket?.assessment?.status === "PENDING";

export const useIntelligenceStatus = (workspace: string) =>
  useQuery({
    queryKey: ["servicedesk", "intelligence", workspace],
    queryFn: () => getIntelligenceStatus(workspace),
    staleTime: 30_000,
  });

export const useRetryTicketAssessment = (workspace: string, id: string) => {
  const client = useQueryClient();
  return useMutation({
    mutationFn: () => retryTicketAssessment(workspace, id),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: ticketKeys.all(workspace) });
    },
  });
};

/** Một ticket; 404 khi không tồn tại hoặc người xem không được thấy. */
export const useTicket = (workspace: string, id: string) =>
  useQuery({
    queryKey: ticketKeys.detail(workspace, id),
    queryFn: ({ signal }) => getTicket(workspace, id, signal),
    refetchInterval: (query) => (isAssessing(query.state.data) ? ASSESSMENT_POLL_MS : false),
  });

/**
 * Đề xuất người xử lý theo loại / ưu tiên / ET đang chọn ở màn duyệt (R2);
 * chưa chọn đủ loại và ưu tiên thì chưa tải.
 */
export const useReviewSuggestion = (
  id: string,
  query: { category?: Category; priority?: Priority; estimateMinutes?: number },
) => {
  const { current } = useCurrentWorkspace();
  const { category, priority, estimateMinutes } = query;
  return useQuery({
    queryKey: [...ticketKeys.all(current), "suggestion", id, category, priority, estimateMinutes],
    queryFn: ({ signal }) =>
      getReviewSuggestion(
        current!,
        id,
        { category: category!, priority: priority!, estimateMinutes },
        signal,
      ),
    enabled: current !== null && category !== undefined && priority !== undefined,
    // Gõ ET thì giữ đề xuất cũ trong lúc tải lại, không nháy skeleton
    placeholderData: keepPreviousData,
  });
};

/** ET mặc định theo loại / ưu tiên đang chọn ở màn duyệt; chưa chọn đủ thì chưa tải. */
export const useEstimateDefault = (id: string, category?: Category, priority?: Priority) => {
  const { current } = useCurrentWorkspace();
  return useQuery({
    queryKey: [...ticketKeys.all(current), "estimate-default", id, category, priority],
    queryFn: ({ signal }) =>
      getEstimateDefault(current!, id, { category: category!, priority: priority! }, signal),
    enabled: current !== null && category !== undefined && priority !== undefined,
  });
};

/** Sau mỗi thao tác: làm mới ticket và khối P (hàng đợi, tiến độ, công việc). */
export const useRefreshTickets = () => {
  const { current } = useCurrentWorkspace();
  const queryClient = useQueryClient();
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ticketKeys.all(current) }),
      queryClient.invalidateQueries({ queryKey: ["process", current] }),
    ]);
};

const useTicketAction = <Input, Output>(
  action: (workspace: string, input: Input) => Promise<Output>,
) => {
  const { current } = useCurrentWorkspace();
  const refresh = useRefreshTickets();
  return useMutation({
    mutationFn: (input: Input) => {
      if (!current) throw new Error("Chưa chọn workspace");
      return action(current, input);
    },
    onSettled: refresh,
  });
};

export const useCreateTicket = () =>
  useTicketAction((workspace, input: NewTicket) => createTicket(workspace, input));

export const useReviewTicket = () =>
  useTicketAction((workspace, { id, decision }: { id: string; decision: ReviewDecision }) =>
    reviewTicket(workspace, id, decision),
  );

export const useCorrectClassification = () =>
  useTicketAction(
    (workspace, { id, correction }: { id: string; correction: ClassificationCorrection }) =>
      correctClassification(workspace, id, correction),
  );

/** Người gửi xác nhận kết quả (R11): accepted đóng ticket, rework mở lại cho kỹ thuật viên. */
export const useConfirmResolution = () =>
  useTicketAction(
    (workspace, input: { taskId: string; outcome: "accepted" | "rework"; comment?: string }) =>
      completeTask(workspace, input.taskId, {
        // Gửi bước đang thấy: backend trả 409 nếu bước đã đổi
        expectedTaskKey: "confirm",
        outcome: input.outcome,
        comment: input.comment,
      }),
  );
