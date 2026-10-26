import { apiClient, type Page } from "../../../platform/shared/api";
import {
  parseAssistance,
  parseSuggestion,
  type Assistance,
  type Suggestion,
} from "../../../platform/intelligence";

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

/** Quy trình gắn với ticket (khớp TicketProcessView). */
export interface TicketProcess {
  processInstanceId: string;
  processKey: string;
  businessKey: string;
  workspace: string;
}

/** Khớp Ticket trong contracts/openapi/servicedesk.yaml. */
export interface Ticket {
  id: string;
  workspace: string;
  /** Subject người tạo. */
  creator: string;
  clientRequestId: string;
  title: string;
  description: string;
  createdAt: string;
  process: TicketProcess;
}

const ticketsPath = (workspace: string) =>
  `/apps/servicedesk/v1/workspaces/${encodeURIComponent(workspace)}/tickets`;

/** Danh sách ticket người xem được thấy (người yêu cầu: của mình; kỹ thuật viên/điều phối: cả workspace). */
export const listTickets = async (
  workspace: string,
  { page, size }: { page: number; size: number },
  signal?: AbortSignal,
): Promise<Page<Ticket>> => {
  const { data } = await apiClient.get<Page<Ticket>>(ticketsPath(workspace), {
    params: { page, size },
    signal,
  });
  return data;
};

/** Một ticket; 404 khi không tồn tại hoặc người xem không được thấy. */
export const getTicket = async (
  workspace: string,
  id: string,
  signal?: AbortSignal,
): Promise<Ticket> => {
  const { data } = await apiClient.get<Ticket>(
    `${ticketsPath(workspace)}/${encodeURIComponent(id)}`,
    { signal },
  );
  return data;
};

/** Gợi ý phân loại của AI; backend tự lấy nội dung ticket nên không gửi body (có body là 400). */
export const triageTicket = async (workspace: string, id: string): Promise<Suggestion> => {
  const { data } = await apiClient.post<unknown>(
    `${ticketsPath(workspace)}/${encodeURIComponent(id)}/triage`,
  );
  return parseSuggestion(data);
};

/** Hỏi trợ lý tri thức về ticket; chỉ gửi câu hỏi, nguồn tri thức do backend chọn. */
export const askTicketAssistance = async (
  workspace: string,
  id: string,
  question: string,
  signal: AbortSignal,
): Promise<Assistance> => {
  const { data } = await apiClient.post<unknown>(
    `${ticketsPath(workspace)}/${encodeURIComponent(id)}/assistance`,
    { question },
    // Backend chờ AI tối đa ~12 giây, cộng thời gian truy xuất
    { signal, timeout: 18000, headers: { "Cache-Control": "no-store" } },
  );
  return parseAssistance(data);
};
