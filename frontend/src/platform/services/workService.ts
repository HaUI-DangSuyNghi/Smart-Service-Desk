import { apiClient, type Page } from "@/platform/shared/api";
import { hpdiWorkspace } from "./paths";
import type { PageQuery, WorkItem } from "./processService";

// Kiểu khớp contracts/hpdi/openapi.yaml (QueueItem, AssignmentSuggestion, EstimateRequest, AgentDuty, WorkAlert...)

export type Priority = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";

/** NOT_STARTED: đã giao, chưa bắt đầu; IN_PROGRESS: đang làm; PENDING: đang chờ (đã làm dở). */
export type WorkState = "NOT_STARTED" | "IN_PROGRESS" | "PENDING";

export type PendingReason = "WAITING_REQUESTER" | "WAITING_PARTS" | "WAITING_EXTERNAL" | "OTHER";

/**
 * Nguồn của thời gian ước tính: bảng mặc định theo loại × ưu tiên, trung vị thời gian làm của việc
 * tương tự đã đóng, hoặc điều phối viên đặt/duyệt.
 */
export type EstimateBasis = "DEFAULT" | "SIMILAR" | "COORDINATOR";

/** Thời gian ước tính (ET): số phút làm, không tính thời gian chờ. */
export interface Estimate {
  minutes: number;
  basis: EstimateBasis;
  /** Số việc dùng tính trung vị; chỉ có khi basis là SIMILAR. */
  sampleSize?: number | null;
}

/** Hạn xử lý theo mức ưu tiên hiệu lực. */
export interface Sla {
  startBy: string;
  resolveBy: string;
  startBreached: boolean;
  resolveBreached: boolean;
  /** Còn dưới 20% thời gian. */
  warning: boolean;
  /** Đang chờ người dùng hoặc ngoài giờ làm việc. */
  clockPaused: boolean;
}

/** Việc kèm thông tin hàng đợi. */
export interface QueueItem extends WorkItem {
  /** Nhãn hiển thị do app cung cấp (ví dụ tiêu đề yêu cầu). */
  label: string;
  workState: WorkState;
  pendingReason: PendingReason | null;
  pendingNote: string | null;
  /** Ưu tiên hiệu lực; khác basePriority là đã được nâng bậc do chờ lâu. */
  priority: Priority;
  basePriority: Priority;
  estimate: Estimate;
  workedMinutes: number;
  /** Phần tính vào tải. */
  remainingMinutes: number;
  sla: Sla | null;
  reopenCount: number;
}

export interface QueuePage extends Page<QueueItem> {
  assignee: string;
  /** Tổng tải (phút) của người này. */
  loadMinutes: number;
}

export type ExcludedReason = "OFF_DUTY" | "IS_REQUESTER";

export interface AssignmentCandidate {
  subject: string;
  displayName: string;
  onDuty: boolean;
  loadMinutes: number;
  assignedToday: number;
  /** ET dài hơn số này thì điều phối viên tự quyết. */
  shiftRemainingMinutes: number | null;
  recommended: boolean;
  excludedReason: ExcludedReason | null;
}

export interface AssignmentSuggestion {
  taskId: string;
  /** Tổ hỗ trợ; "manual" là điều phối viên tự chọn (loại OTHER). */
  group: string;
  estimateMinutes: number;
  /** null khi tổ không ai trực hoặc loại OTHER. */
  recommended: string | null;
  /** Mã lý do, có thể chứa ESTIMATE_EXCEEDS_SHIFT. */
  reason: string;
  candidates: AssignmentCandidate[];
}

/** SUPERSEDED: bị thay do đổi phân loại hoặc đổi người giữ việc. */
export type EstimateRequestStatus = "PENDING" | "APPROVED" | "REJECTED" | "SUPERSEDED";

export interface EstimateRequest {
  id: string;
  taskId: string;
  processInstanceId: string;
  businessKey: string;
  label: string;
  requestedBy: string;
  currentMinutes: number;
  proposedMinutes: number;
  reason: string;
  status: EstimateRequestStatus;
  decidedBy: string | null;
  decidedAt: string | null;
  decisionNote: string | null;
  supersededReason?: "CLASSIFICATION_CHANGED" | "ASSIGNMENT_CHANGED" | null;
  createdAt: string;
}

/** Trạng thái trực: onDuty là công tắc tay; effectiveDuty tính cả lịch ca. */
export interface DutyStatus {
  subject: string;
  onDuty: boolean;
  effectiveDuty: boolean;
  note: string | null;
  updatedAt: string | null;
}

/** Một ca trực cụ thể (thời điểm tuyệt đối). */
export interface ScheduledShift {
  startsAt: string;
  endsAt: string;
  /** Mặc định Asia/Ho_Chi_Minh. */
  timezone?: string;
}

/** Lịch ca của một người; bật lịch thì chỉ trực trong ca (và công tắc tay đang bật). */
export interface AgentSchedule {
  subject: string;
  /** Gửi lại version đang xem, người khác đã sửa trước thì 409. */
  version: number;
  enabled: boolean;
  shifts: ScheduledShift[];
}

export type AlertType =
  | "START_OVERDUE"
  | "SLA_WARNING"
  | "SLA_BREACHED"
  | "PENDING_TOO_LONG"
  | "CRITICAL_NEW"
  | "CLASSIFICATION_CHANGED"
  | "REOPENED"
  | "REOPEN_UNCLAIMED"
  | "ASSIGNEE_OFF_DUTY"
  | "NO_ONE_ON_DUTY"
  | "CRITICAL_UNREVIEWED";

export type AlertSeverity = "INFO" | "WARNING" | "CRITICAL";

export interface Alert {
  id: string;
  type: AlertType;
  severity: AlertSeverity;
  taskId: string | null;
  processInstanceId: string;
  processKey: string;
  businessKey: string;
  label: string;
  createdAt: string;
  readAt: string | null;
}

export interface AlertPage extends Page<Alert> {
  /** Tổng số chưa đọc, cho số đếm trên menu. */
  unread: number;
}

export interface AlertQuery extends PageQuery {
  since?: string;
  unreadOnly?: boolean;
}

export interface SlaTarget {
  startWithinMinutes: number;
  resolveWithinMinutes: number;
}

export interface Shift {
  name: string;
  /** "07:00" */
  start: string;
  end: string;
}

/** Cấu hình chung (mục 6 của plan nghiệp vụ), một bộ cho mọi workspace. */
export interface WorkSettings {
  /** Tăng mỗi lần lưu; gửi lại version đang xem, người khác đã sửa trước thì 409. */
  version: number;
  updatedAt?: string;
  updatedBy?: string;
  assignment: {
    pendingLoadFactor: number;
    tieThresholdMinutes: number;
    maxInProgress: number;
    minRemainingMinutes: number;
  };
  shifts: Shift[];
  sla: {
    targets: Record<Priority, SlaTarget>;
    escalateAfterRatio: number;
    maxEscalatedPriority: Priority;
    waitingExternalCounts: boolean;
  };
  critical: { autoQueueAfterMinutes: number };
  alerts: {
    warnRemainingRatio: number;
    pendingRemindDays: number;
    reopenUnclaimedMinutes: number;
  };
  closing: { autoCloseAfterDays: number };
  estimate: {
    minSamples: number;
    windowMonths: number;
    outlierMinMinutes: number;
    outlierMaxMinutes: number;
  };
}

export interface PauseInput {
  reason: PendingReason;
  /** Bắt buộc khi reason = OTHER. */
  note?: string;
}

export type ReleaseReason = "WRONG_GROUP" | "HANDOVER" | "OTHER";

export interface ReleaseInput {
  reason: ReleaseReason;
  note: string;
}

const taskPath = (workspace: string, taskId: string) =>
  `${hpdiWorkspace(workspace)}/tasks/${encodeURIComponent(taskId)}`;

/** GET hàng đợi đã sắp xếp; bỏ trống assignee là của chính mình, xem người khác cần quyền giao việc. */
export const getQueue = async (
  workspace: string,
  { assignee, page = 0, size = 50 }: PageQuery & { assignee?: string },
  signal?: AbortSignal,
): Promise<QueuePage> => {
  const { data } = await apiClient.get<QueuePage>(`${hpdiWorkspace(workspace)}/queue`, {
    params: { assignee, page, size },
    signal,
  });
  return data;
};

/** POST Bắt đầu. 409: đã đủ số việc đang làm, hoặc việc không ở trạng thái chưa bắt đầu. */
export const startWork = async (workspace: string, taskId: string): Promise<QueueItem> => {
  const { data } = await apiClient.post<QueueItem>(`${taskPath(workspace, taskId)}/start`);
  return data;
};

/** POST Tạm dừng kèm lý do. 409: việc không đang làm. */
export const pauseWork = async (
  workspace: string,
  taskId: string,
  input: PauseInput,
): Promise<QueueItem> => {
  const { data } = await apiClient.post<QueueItem>(`${taskPath(workspace, taskId)}/pause`, input);
  return data;
};

/** POST Tiếp tục. 409: đã đủ số việc đang làm. */
export const resumeWork = async (workspace: string, taskId: string): Promise<QueueItem> => {
  const { data } = await apiClient.post<QueueItem>(`${taskPath(workspace, taskId)}/resume`);
  return data;
};

/** POST Trả việc về điều phối viên (sai tổ, bàn giao hết ca...). */
export const releaseWork = async (
  workspace: string,
  taskId: string,
  input: ReleaseInput,
): Promise<void> => {
  await apiClient.post(`${taskPath(workspace, taskId)}/release`, input);
};

/** GET đề xuất người xử lý cho việc đã duyệt (điều phối viên); không giao việc. */
export const getAssignmentSuggestion = async (
  workspace: string,
  taskId: string,
  signal?: AbortSignal,
): Promise<AssignmentSuggestion> => {
  const { data } = await apiClient.get<AssignmentSuggestion>(
    `${taskPath(workspace, taskId)}/assignment-suggestion`,
    { signal },
  );
  return data;
};

/** POST xin sửa thời gian ước tính. 409: đã có yêu cầu đang chờ cho việc này. */
export const requestEstimateChange = async (
  workspace: string,
  taskId: string,
  input: { proposedMinutes: number; reason: string },
): Promise<EstimateRequest> => {
  const { data } = await apiClient.post<EstimateRequest>(
    `${taskPath(workspace, taskId)}/estimate-requests`,
    input,
  );
  return data;
};

/** GET yêu cầu sửa ET, mới nhất trước; kỹ thuật viên chỉ thấy yêu cầu của mình. */
export const listEstimateRequests = async (
  workspace: string,
  { status, page = 0, size = 20 }: PageQuery & { status?: EstimateRequestStatus },
  signal?: AbortSignal,
): Promise<Page<EstimateRequest>> => {
  const { data } = await apiClient.get<Page<EstimateRequest>>(
    `${hpdiWorkspace(workspace)}/estimate-requests`,
    { params: { status, page, size }, signal },
  );
  return data;
};

/** POST đồng ý / từ chối yêu cầu sửa ET. 409: đã có người quyết. */
export const decideEstimateRequest = async (
  workspace: string,
  requestId: string,
  input: { approved: boolean; note?: string },
): Promise<EstimateRequest> => {
  const { data } = await apiClient.post<EstimateRequest>(
    `${hpdiWorkspace(workspace)}/estimate-requests/${encodeURIComponent(requestId)}/decision`,
    input,
  );
  return data;
};

/** GET trạng thái trực; điều phối viên thấy tất cả, kỹ thuật viên chỉ thấy mình. */
export const listDuty = async (workspace: string, signal?: AbortSignal): Promise<DutyStatus[]> => {
  const { data } = await apiClient.get<DutyStatus[]>(`${hpdiWorkspace(workspace)}/duty`, {
    signal,
  });
  return data;
};

/** PUT bật/tắt trực của chính mình (backend không cho bật/tắt hộ người khác). */
export const setMyDuty = async (
  workspace: string,
  input: { onDuty: boolean; note?: string },
): Promise<DutyStatus> => {
  const { data } = await apiClient.put<DutyStatus>(`${hpdiWorkspace(workspace)}/duty/me`, input);
  return data;
};

/** GET lịch ca; điều phối viên thấy tất cả kỹ thuật viên, kỹ thuật viên chỉ thấy mình. */
export const listSchedules = async (
  workspace: string,
  signal?: AbortSignal,
): Promise<AgentSchedule[]> => {
  const { data } = await apiClient.get<AgentSchedule[]>(`${hpdiWorkspace(workspace)}/schedules`, {
    signal,
  });
  return data;
};

/** PUT ghi đè lịch ca của một người (điều phối viên). 409: version cũ. */
export const updateSchedule = async (
  workspace: string,
  { subject, ...body }: AgentSchedule,
): Promise<AgentSchedule> => {
  const { data } = await apiClient.put<AgentSchedule>(
    `${hpdiWorkspace(workspace)}/schedules/${encodeURIComponent(subject)}`,
    body,
  );
  return data;
};

/** GET cảnh báo của tôi, mới nhất trước; `since` để chỉ lấy cảnh báo mới. */
export const listAlerts = async (
  workspace: string,
  { since, unreadOnly, page = 0, size = 20 }: AlertQuery,
  signal?: AbortSignal,
): Promise<AlertPage> => {
  const { data } = await apiClient.get<AlertPage>(`${hpdiWorkspace(workspace)}/alerts`, {
    params: { since, unreadOnly, page, size },
    signal,
  });
  return data;
};

/** POST đánh dấu đã đọc; gọi lặp vẫn thành công. */
export const markAlertRead = async (workspace: string, alertId: string): Promise<void> => {
  await apiClient.post(`${hpdiWorkspace(workspace)}/alerts/${encodeURIComponent(alertId)}/read`);
};

/** GET cấu hình chung (quản trị nền tảng). */
export const getWorkSettings = async (signal?: AbortSignal): Promise<WorkSettings> => {
  const { data } = await apiClient.get<WorkSettings>("/hpdi/v1/settings/work", { signal });
  return data;
};

/** PUT ghi đè cấu hình chung; 409 khi người khác vừa sửa (version cũ). */
export const updateWorkSettings = async (settings: WorkSettings): Promise<WorkSettings> => {
  // Bỏ trường chỉ đọc: backend từ chối body có trường lạ
  const body = { ...settings };
  delete body.updatedAt;
  delete body.updatedBy;
  const { data } = await apiClient.put<WorkSettings>("/hpdi/v1/settings/work", body);
  return data;
};
