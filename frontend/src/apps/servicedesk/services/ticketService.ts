import { apiClient } from "../../../platform/shared/api";

/** Khớp TicketStatistics trong contracts/openapi/servicedesk.yaml. */
export interface TicketStatistics {
  workspace: string;
  /** own: người yêu cầu chỉ thấy ticket của mình; workspace: kỹ thuật viên/điều phối viên thấy cả workspace. */
  visibility: "own" | "workspace";
  total: number;
  awaitingHandling: number;
  awaitingConfirmation: number;
  completed: number;
  /** Ticket không xếp được vào trạng thái nào (dữ liệu bất thường); không coi là đã xong. */
  unclassified: number;
}

/** GET thống kê ticket theo ngày tạo trong [from, to). */
export const getTicketStatistics = async (
  workspace: string,
  { from, to }: { from: string; to: string },
  signal?: AbortSignal,
): Promise<TicketStatistics> => {
  const { data } = await apiClient.get<TicketStatistics>(
    `/apps/servicedesk/v1/workspaces/${encodeURIComponent(workspace)}/tickets/statistics`,
    { params: { from, to }, signal },
  );
  return data;
};
