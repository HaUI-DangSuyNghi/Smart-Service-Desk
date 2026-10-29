import { apiClient, type Page } from "../../../platform/shared/api";
import {
  parseAssistance,
  parseSuggestion,
  type Assistance,
  type Suggestion,
} from "../../../platform/intelligence";
import type {
  AssignmentSuggestion,
  Estimate,
  Priority,
  Sla,
  WorkState,
} from "../../../platform/services";
import type { Category, EstimateTable, Impact, Urgency } from "../tickets/ticketRules";

/** Khớp TicketStatistics (servicedesk.yaml). Các trạng thái loại trừ nhau; overdue cắt ngang. */
export interface TicketStatistics {
  workspace: string;
  /** own: người yêu cầu chỉ thấy ticket của mình; workspace: kỹ thuật viên/điều phối viên thấy cả workspace. */
  visibility: "own" | "workspace";
  total: number;
  /** Chờ duyệt + đã giao + đang làm + đang chờ + mở lại. */
  awaitingHandling: number;
  awaitingConfirmation: number;
  completed: number;
  /** Ticket không xếp được vào trạng thái nào (dữ liệu bất thường); không coi là đã xong. */
  unclassified: number;
  awaitingReview: number;
  assigned: number;
  inProgress: number;
  pending: number;
  reopened: number;
  closed: number;
  /** Quá hạn bắt đầu hoặc quá hạn xử lý; không cộng vào total. */
  overdue: number;
  intake: number;
  following: number;
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
  /** service-desk (cũ, không duyệt), service-desk-reviewed, service-desk-intake-reviewed. */
  processKey: string;
  businessKey: string;
  workspace: string;
}

/** Phần gốc của ticket (Ticket trong servicedesk.yaml). */
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
 * Trạng thái R6: tiếp nhận, theo ticket gốc (trùng), chờ duyệt, đã giao, đang làm, đang chờ,
 * chờ xác nhận, mở lại (người dùng báo chưa xong), đã đóng, không xác định.
 */
export type TicketState =
  | "INTAKE"
  | "FOLLOWING"
  | "AWAITING_REVIEW"
  | "ASSIGNED"
  | "IN_PROGRESS"
  | "PENDING"
  | "AWAITING_CONFIRMATION"
  | "REOPENED"
  | "CLOSED"
  | "UNCLASSIFIED";

/** Phân loại đang hiệu lực (sau khi điều phối viên duyệt/sửa), không phải gợi ý AI. */
export interface Classification {
  category: Category;
  impact: Impact | null;
  urgency: Urgency | null;
  /** Ưu tiên hiệu lực; khác basePriority khi được nâng bậc do chờ lâu (R5). */
  priority: Priority;
  basePriority: Priority;
  escalated: boolean;
  /** Tổ theo Cấu hình hệ thống (loại lỗi → tổ); "manual" với loại Khác. */
  group: string;
  source: string;
  reviewedBy: string;
  reviewedAt: string;
}

export type AssessmentStatus = "PENDING" | "READY" | "DEGRADED";

/** Kết quả AI (luật service-desk@0.3.0); chỉ là gợi ý. */
export interface AssessmentResult {
  contractVersion: string;
  profileVersion: string;
  engine: string;
  rulesVersion: string | null;
  modelVersion: string | null;
  category: Category | null;
  priority: Priority | null;
  impact: Impact | null;
  urgency: Urgency | null;
  /** INSUFFICIENT_INFORMATION: OTHER/MEDIUM chỉ là mặc định, không phải gợi ý. */
  reasonCode:
    | "CLASSIFIED"
    | "ROOT_CAUSE_CONFLICT"
    | "INSUFFICIENT_INFORMATION"
    | "MODEL_CLASSIFIED"
    | "MODEL_UNAVAILABLE"
    | "HYBRID_CONFLICT"
    | "RULES_SUPPORTED"
    | "MODEL_ABSTAINED"
    | "MODEL_REVIEW_REQUIRED";
  evidence: { field: "title" | "description"; text: string }[];
  explanation: { classificationReason: string; priorityReason: string } | null;
  needsReview: boolean;
  nextQuestions: string[];
  cooperation?: {
    provider: string;
    modelCategory: Category | null;
    rulesCategory: Category | null;
    modelStatus: "AVAILABLE" | "UNAVAILABLE" | "ABSTAINED" | "BLOCKED";
    rulesStatus: "CLASSIFIED" | "UNAVAILABLE" | "INSUFFICIENT_INFORMATION";
    decision: string;
  } | null;
}

/**
 * Kết quả AI chạy nền lúc tạo ticket (StoredAssessment). PENDING thì hỏi lại định kỳ;
 * READY / DEGRADED là kết thúc. result chỉ có khi READY.
 */
export interface Assessment {
  status: AssessmentStatus;
  version: number;
  /** Có thể có cả khi PENDING (đang thử lại); USE_FINAL_CONTEXT_ASSESSMENT: xem kết quả ở luồng tiếp nhận. */
  errorCode: string | null;
  result: AssessmentResult | null;
}

/** Một lần con người sửa phân loại (R9). */
export interface Correction {
  operationId: string;
  field: "category" | "priority";
  previousValue: string;
  newValue: string;
  /** previousValue là gợi ý của AI. */
  fromAi: boolean;
  by: string;
  reason: string;
  at: string;
}

/**
 * Ticket kèm thông tin nghiệp vụ (TicketReviewView). Ticket quy trình cũ (service-desk)
 * chỉ có phần gốc nên các trường mới đều optional.
 */
export interface TicketView extends Ticket {
  state?: TicketState;
  /** Việc đang mở (mọi bước); bước confirm thì người gửi dùng để xác nhận. */
  taskId?: string | null;
  /** Người giữ việc đang mở; null khi chưa ai nhận (kể cả "để chờ ca sau"). */
  assignee?: string | null;
  /** null khi chưa duyệt. */
  estimate?: Estimate | null;
  /** Chỉ có khi đang ở bước xử lý. */
  workState?: WorkState | null;
  workedMinutes?: number | null;
  remainingMinutes?: number | null;
  reopenCount?: number;
  /** null khi chưa duyệt. */
  classification?: Classification | null;
  /** null khi ticket không có kết quả AI chạy nền. */
  assessment?: Assessment | null;
  /** null khi chưa duyệt. */
  sla?: Sla | null;
  /** Không có với người yêu cầu và người tạo ticket. */
  corrections?: Correction[];
  // Chỉ có khi ticket đã tới bước xác nhận
  closedAt?: string | null;
  /** AUTO: tự đóng do người dùng không xác nhận (R11). */
  closedBy?: "REQUESTER" | "AUTO" | null;
  confirmDeadlineAt?: string;
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
  /** Bỏ trống: dùng bảng ET mặc định. */
  estimateMinutes?: number;
  /** null: để chờ ca sau (tổ không ai trực). */
  assignee: string | null;
  reason: string;
  // Ticket qua luồng tiếp nhận: gửi đúng revision/version đang xem từ GET /intake
  expectedContextRevision?: number;
  expectedIntakeVersion?: number;
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

export interface IntelligenceStatus {
  mode: "LOCAL_DEV" | "EXTERNAL";
  engine: "hybrid" | "rules" | "disabled";
  modelEnabled: boolean;
  assistanceMode: "api" | "grounded" | "disabled";
}

export const getIntelligenceStatus = async (workspace: string): Promise<IntelligenceStatus> => {
  const { data } = await apiClient.get<IntelligenceStatus>(
    `/apps/servicedesk/v1/workspaces/${encodeURIComponent(workspace)}/intelligence/status`,
  );
  return data;
};

export const retryTicketAssessment = async (workspace: string, id: string): Promise<Assessment> => {
  const { data } = await apiClient.post<Assessment>(
    `${ticketPath(workspace, id)}/assessment/retry`,
  );
  return data;
};

/** Danh sách ticket người xem được thấy (người yêu cầu: của mình; kỹ thuật viên/điều phối: cả workspace). */
export const listTickets = async (
  workspace: string,
  query: TicketQuery,
  signal?: AbortSignal,
): Promise<Page<TicketView>> => {
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
  const { data } = await apiClient.get<TicketView>(ticketPath(workspace, id), { signal });
  return data;
};

/** POST tạo ticket (R0). 409: đã gửi với cùng clientRequestId. */
export const createTicket = async (workspace: string, input: NewTicket): Promise<TicketView> => {
  const { data } = await apiClient.post<TicketView>(ticketsPath(workspace), input);
  return data;
};

/**
 * POST duyệt ticket (R4): chốt loại, ưu tiên, ET, người xử lý.
 * 409: người khác đã duyệt, người được giao đang nghỉ trực, hoặc tiếp nhận đã đổi.
 */
export const reviewTicket = async (
  workspace: string,
  id: string,
  decision: ReviewDecision,
): Promise<TicketView> => {
  const { data } = await apiClient.post<TicketView>(
    `${ticketPath(workspace, id)}/review`,
    decision,
  );
  return data;
};

/** GET đề xuất người xử lý theo loại / ưu tiên đang chọn ở màn duyệt (chưa có việc xử lý). */
export const getReviewSuggestion = async (
  workspace: string,
  id: string,
  query: { category: Category; priority: Priority; estimateMinutes?: number },
  signal?: AbortSignal,
): Promise<AssignmentSuggestion> => {
  const { data } = await apiClient.get<AssignmentSuggestion>(
    `${ticketPath(workspace, id)}/assignment-suggestion`,
    { params: query, signal },
  );
  return data;
};

/**
 * GET ET backend dùng nếu điều phối viên không tự nhập: trung vị việc tương tự (đủ mẫu) hoặc
 * bảng trong Cấu hình hệ thống. Màn duyệt điền sẵn số này.
 */
export const getEstimateDefault = async (
  workspace: string,
  id: string,
  query: { category: Category; priority: Priority },
  signal?: AbortSignal,
): Promise<Estimate> => {
  const { data } = await apiClient.get<Estimate>(`${ticketPath(workspace, id)}/estimate-default`, {
    params: query,
    signal,
  });
  return data;
};

/** PATCH sửa loại lỗi / ưu tiên kèm lý do (R9). */
export const correctClassification = async (
  workspace: string,
  id: string,
  correction: ClassificationCorrection,
): Promise<TicketView> => {
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

/** Hỏi trợ lý tri thức về ticket; 404 khi backend tắt tính năng. */
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
export const getServiceDeskSettings = async (
  signal?: AbortSignal,
): Promise<ServiceDeskSettings> => {
  const { data } = await apiClient.get<ServiceDeskSettings>("/apps/servicedesk/v1/settings", {
    signal,
  });
  return data;
};

/** PUT ghi đè cấu hình; 409 khi người khác vừa sửa. */
export const updateServiceDeskSettings = async (
  settings: ServiceDeskSettings,
): Promise<ServiceDeskSettings> => {
  // Bỏ trường chỉ đọc: backend từ chối body có trường lạ
  const body = { ...settings };
  delete body.updatedAt;
  delete body.updatedBy;
  const { data } = await apiClient.put<ServiceDeskSettings>("/apps/servicedesk/v1/settings", body);
  return data;
};
