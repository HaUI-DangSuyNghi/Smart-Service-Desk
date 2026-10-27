import { apiClient, type Page } from "../../../platform/shared/api";
import {
  parseAssistance,
  parseSuggestion,
  type Assistance,
  type Suggestion,
} from "../../../platform/intelligence";
import {
  isWorkflowMock,
  type EffortSize,
  type Estimate,
  type PendingReason,
  type Priority,
  type Sla,
} from "../../../platform/services";
import type { Category, EstimateTable, Impact, Urgency } from "../tickets/ticketRules";
import { ticketMock } from "./mock/ticketMock";

/** Khớp TicketStatistics (servicedesk.yaml) và TicketStatisticsV2 (servicedesk-target.yaml). */
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
  // Bản đích: backend chưa trả thì không có
  awaitingReview?: number;
  assigned?: number;
  inProgress?: number;
  pending?: number;
  reopened?: number;
  closed?: number;
  /** Quá hạn bắt đầu hoặc quá hạn xử lý; cắt ngang, không cộng vào total. */
  overdue?: number;
}

/** GET thống kê ticket theo ngày tạo trong [from, to). */
export const getTicketStatistics = async (
  workspace: string,
  { from, to }: { from: string; to: string },
  signal?: AbortSignal,
): Promise<TicketStatistics> => {
  if (isWorkflowMock) return ticketMock.getStatistics(workspace);
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

/**
 * Trạng thái theo R6: chờ duyệt, đã giao, đang làm, đang chờ, chờ xác nhận,
 * mở lại (người dùng báo chưa xong, chưa bắt đầu lại), đã đóng, không xác định.
 */
export type TicketState =
  | "AWAITING_REVIEW"
  | "ASSIGNED"
  | "IN_PROGRESS"
  | "PENDING"
  | "AWAITING_CONFIRMATION"
  | "REOPENED"
  | "CLOSED"
  | "UNCLASSIFIED";

/** Phân loại đang hiệu lực (sau khi người duyệt/sửa), không phải gợi ý AI. */
export interface Classification {
  category: Category;
  impact?: Impact | null;
  urgency?: Urgency | null;
  priority: Priority;
  basePriority: Priority;
  /** Được nâng bậc do chờ lâu (R5). */
  escalated: boolean;
  group: string;
  source: "AI" | "COORDINATOR" | "AGENT";
  reviewedBy?: string | null;
  reviewedAt?: string | null;
}

export type AssessmentStatus = "PENDING" | "READY" | "DEGRADED";

/** Kết quả AI đã lưu lúc tạo ticket; chỉ là gợi ý. `category = null` khi AI không chắc. */
export interface Assessment {
  status: AssessmentStatus;
  category?: Category | null;
  impact?: Impact | null;
  urgency?: Urgency | null;
  priority?: Priority | null;
  suggestedGroup?: string | null;
  reasonCode?:
    | "CLASSIFIED"
    | "ROOT_CAUSE_CONFLICT"
    | "INSUFFICIENT_INFORMATION"
    | "INTELLIGENCE_UNAVAILABLE"
    | null;
  needsReview: boolean;
  explanation?: { classificationReason: string; priorityReason: string } | null;
  evidence?: { field: "title" | "description"; text: string }[];
  nextQuestions: string[];
  effortSize?: EffortSize | null;
  profileVersion?: string | null;
  assessedAt?: string | null;
}

/** Một lần con người sửa giá trị (R9). */
export interface Correction {
  field: "category" | "priority" | "estimateMinutes" | "assignee";
  previousValue: string | null;
  newValue: string;
  /** previousValue là gợi ý của AI. */
  fromAi?: boolean;
  by: string;
  reason: string;
  at: string;
}

/** Ticket kèm thông tin nghiệp vụ mới; backend hiện tại chưa trả các trường này. */
export interface TicketView extends Ticket {
  state?: TicketState;
  /** null khi chưa duyệt. */
  classification?: Classification | null;
  assessment?: Assessment | null;
  assignee?: string | null;
  /** Việc đang mở, dùng cho các thao tác Bắt đầu/Tạm dừng/Tiếp tục/Trả việc. */
  taskId?: string | null;
  pendingReason?: PendingReason | null;
  estimate?: Estimate | null;
  workedMinutes?: number;
  sla?: Sla | null;
  /** Báo người dùng dự kiến bắt đầu (R4). */
  expectedStartAt?: string | null;
  reopenCount?: number;
  closedAt?: string | null;
  /** AUTO: tự đóng do người dùng không xác nhận (R11). */
  closedBy?: "REQUESTER" | "AUTO" | null;
  /** Chỉ có ở trang chi tiết, cho kỹ thuật viên/điều phối viên. */
  corrections?: Correction[];
}

export interface TicketQuery {
  page: number;
  size: number;
  q?: string;
  state?: TicketState[];
  priority?: Priority[];
  category?: Category;
  /** "me" là chính mình. */
  assignee?: string;
  overdue?: boolean;
  /** createdAt: mới nhất trước; priority: theo thứ tự hàng đợi (R4). */
  sort?: "createdAt" | "priority";
}

export interface NewTicket {
  /** Sinh một lần cho mỗi lần gửi; gửi lại cùng mã thì backend trả 409, không tạo trùng. */
  clientRequestId: string;
  title: string;
  description: string;
}

export interface ReviewDecision {
  category: Category;
  impact?: Impact;
  urgency?: Urgency;
  priority: Priority;
  estimateMinutes: number;
  /** null: để chờ ca sau (tổ không ai trực). */
  assignee: string | null;
  /** Bắt buộc khi khác gợi ý AI hoặc khác người được đề xuất. */
  reason?: string;
}

export interface ClassificationCorrection {
  category?: Category;
  priority?: Priority;
  reason: string;
}

export interface ServiceDeskSettings {
  version: number;
  updatedAt?: string;
  updatedBy?: string;
  /** R1: loại lỗi → tổ; OTHER = null là điều phối viên tự chọn. */
  categoryGroups: Record<Category, string | null>;
  /** R2: ET mặc định (phút). */
  estimateDefaults: EstimateTable;
}

const ticketsPath = (workspace: string) =>
  `/apps/servicedesk/v1/workspaces/${encodeURIComponent(workspace)}/tickets`;

const ticketPath = (workspace: string, id: string) =>
  `${ticketsPath(workspace)}/${encodeURIComponent(id)}`;

/** Danh sách ticket người xem được thấy (người yêu cầu: của mình; kỹ thuật viên/điều phối: cả workspace). */
export const listTickets = async (
  workspace: string,
  query: TicketQuery,
  signal?: AbortSignal,
): Promise<Page<TicketView>> => {
  if (isWorkflowMock) return ticketMock.listTickets(workspace, query);
  const { data } = await apiClient.get<Page<TicketView>>(ticketsPath(workspace), {
    params: query,
    // Mảng gửi dạng state=A&state=B như contract, không phải state[]=A
    paramsSerializer: { indexes: null },
    signal,
  });
  return data;
};

/** Một ticket; 404 khi không tồn tại hoặc người xem không được thấy. */
export const getTicket = async (
  workspace: string,
  id: string,
  signal?: AbortSignal,
): Promise<TicketView> => {
  if (isWorkflowMock && ticketMock.hasTicket(id)) return ticketMock.getTicket(workspace, id);
  const { data } = await apiClient.get<TicketView>(ticketPath(workspace, id), { signal });
  return data;
};

/** POST tạo ticket (R0). 409: đã gửi với cùng clientRequestId. */
export const createTicket = async (workspace: string, input: NewTicket): Promise<TicketView> => {
  if (isWorkflowMock) return ticketMock.createTicket(workspace, input);
  const { data } = await apiClient.post<TicketView>(ticketsPath(workspace), input);
  return data;
};

/** POST duyệt ticket (R4): chốt loại, ưu tiên, ET, người xử lý. 409: người khác đã duyệt. */
export const reviewTicket = async (
  workspace: string,
  id: string,
  decision: ReviewDecision,
): Promise<TicketView> => {
  if (isWorkflowMock) return ticketMock.reviewTicket(workspace, id, decision);
  const { data } = await apiClient.post<TicketView>(`${ticketPath(workspace, id)}/review`, decision);
  return data;
};

/** PATCH sửa loại lỗi / ưu tiên kèm lý do (R9). */
export const correctClassification = async (
  workspace: string,
  id: string,
  correction: ClassificationCorrection,
): Promise<TicketView> => {
  if (isWorkflowMock) return ticketMock.correctClassification(workspace, id, correction);
  const { data } = await apiClient.patch<TicketView>(
    `${ticketPath(workspace, id)}/classification`,
    correction,
  );
  return data;
};

/** Gợi ý phân loại của AI; backend tự lấy nội dung ticket nên không gửi body (có body là 400). */
export const triageTicket = async (workspace: string, id: string): Promise<Suggestion> => {
  const { data } = await apiClient.post<unknown>(`${ticketPath(workspace, id)}/triage`);
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
    `${ticketPath(workspace, id)}/assistance`,
    { question },
    // Backend chờ AI tối đa ~12 giây, cộng thời gian truy xuất
    { signal, timeout: 18000, headers: { "Cache-Control": "no-store" } },
  );
  return parseAssistance(data);
};

/** GET cấu hình riêng của Service Desk (quản trị nền tảng). */
export const getServiceDeskSettings = async (signal?: AbortSignal): Promise<ServiceDeskSettings> => {
  if (isWorkflowMock) return ticketMock.getSettings();
  const { data } = await apiClient.get<ServiceDeskSettings>("/apps/servicedesk/v1/settings", {
    signal,
  });
  return data;
};

/** PUT ghi đè cấu hình; 409 khi người khác vừa sửa. */
export const updateServiceDeskSettings = async (
  settings: ServiceDeskSettings,
): Promise<ServiceDeskSettings> => {
  if (isWorkflowMock) return ticketMock.updateSettings(settings);
  // Bỏ trường chỉ đọc: backend từ chối body có trường lạ
  const body = { ...settings };
  delete body.updatedAt;
  delete body.updatedBy;
  const { data } = await apiClient.put<ServiceDeskSettings>("/apps/servicedesk/v1/settings", body);
  return data;
};
