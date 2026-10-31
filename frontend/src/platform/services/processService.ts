import { apiClient, type Page } from "@/platform/shared/api";
import { hpdiWorkspace } from "./paths";

// Kiểu khớp schema trong contracts/hpdi/openapi.yaml (WorkItem, ProcessDescriptor, ProcessTimeline...)

/**
 * ASSIGNED: việc đang giao cho tôi. CANDIDATE: việc của nhóm tôi chưa ai nhận (trừ yêu cầu tôi tạo).
 * ALL: mọi việc đang mở của workspace, để điều phối viên giao việc.
 */
export type InboxScope = "ASSIGNED" | "CANDIDATE" | "ALL";

/** mine: yêu cầu tôi tạo; all: mọi yêu cầu tôi được xem (điều phối viên). */
export type RequestScope = "mine" | "all";

/** Một việc (userTask) trong công việc. */
export interface WorkItem {
  id: string;
  processInstanceId: string;
  /** id của userTask trong BPMN, ví dụ "approve". */
  key: string;
  /** Tên trong BPMN (tiếng Anh); hiển thị thì ưu tiên nhãn trong ProcessDescriptor. */
  name: string;
  /** Dạng "lab-a:agent". */
  candidateGroups: string[];
  /** Subject người đang giữ việc; null khi chưa ai nhận. */
  assignee: string | null;
  createdAt: string;
  processKey: string;
  businessKey: string;
}

/** Cặp key gửi lên server và nhãn hiển thị. */
export interface ProcessOption {
  key: string;
  label: string;
}

/** Ô nhập khi tạo yêu cầu; `multiline`: ô nhiều dòng tối đa 2000 ký tự, ô thường 200. */
export interface ProcessStartField {
  key: string;
  label: string;
  multiline: boolean;
}

export interface ProcessTaskDescriptor {
  key: string;
  name: string;
  outcomes: ProcessOption[];
}

/** Quy trình kèm nhãn tiếng Việt, kết quả theo bước và quyền tạo từ form chung. */
export interface ProcessDescriptor {
  processKey: string;
  name: string;
  canStart: boolean;
  tasks: ProcessTaskDescriptor[];
  startFields: ProcessStartField[];
}

/** Một bước trong lịch sử; outcome/comment/endedAt là null khi chưa xong. */
export interface ProcessStep {
  id: string;
  key: string;
  name: string;
  assignee: string | null;
  outcome: string | null;
  comment: string | null;
  startedAt: string;
  claimedAt: string | null;
  endedAt: string | null;
}

export interface ProcessTimeline {
  id: string;
  processKey: string;
  businessKey: string;
  workspace: string;
  /** Subject người tạo yêu cầu. */
  initiator: string;
  startedAt: string;
  endedAt: string | null;
  currentTasks: WorkItem[];
  steps: ProcessStep[];
  /** Nội dung người tạo nhập lúc tạo yêu cầu, theo key của startFields. */
  variables: Record<string, string>;
}

export interface ProcessHandle {
  processInstanceId: string;
  processKey: string;
  businessKey: string;
  workspace: string;
}

/** Một yêu cầu tôi đã tạo. */
export interface ProcessSummary {
  id: string;
  processKey: string;
  businessKey: string;
  /** Subject người tạo. */
  initiator: string;
  startedAt: string;
  endedAt: string | null;
  /** Bước đang mở; rỗng khi đã kết thúc. */
  activeTaskKeys: string[];
}

export interface TaskCompletion {
  /** Bước người dùng đang thấy; backend trả 409 nếu bước đã đổi, tránh hoàn thành nhầm. */
  expectedTaskKey: string;
  outcome: string;
  comment?: string;
}

export interface PageQuery {
  page?: number;
  size?: number;
}

/** Số liệu một quy trình trên dashboard. */
export interface ProcessCount {
  processKey: string;
  /** Yêu cầu tạo trong khoảng thời gian. */
  started: number;
  /** Trong số đó, còn đang chạy. */
  running: number;
  /** Trong số đó, đã kết thúc bình thường. */
  completed: number;
  /** null khi chưa có yêu cầu nào xong. */
  averageCompletionSeconds: number | null;
  /** Việc đang mở chưa ai nhận (lúc gọi, không theo khoảng thời gian). */
  waitingClaim: number;
  /** Việc đang mở đã có người giữ. */
  inProgress: number;
}

export interface ProcessStatistics {
  from: string;
  to: string;
  processes: ProcessCount[];
  /** Mỗi ngày trong khoảng (kể cả ngày 0), theo múi giờ đã gửi. */
  daily: { date: string; started: number }[];
}

export interface StatisticsQuery {
  from: string;
  to: string;
  /** Múi giờ IANA, ví dụ "Asia/Ho_Chi_Minh". */
  zone: string;
}

/** GET hpdi inbox. */
export const listInbox = async (
  workspace: string,
  scope: InboxScope,
  { page = 0, size = 20 }: PageQuery,
  signal?: AbortSignal,
): Promise<Page<WorkItem>> => {
  const { data } = await apiClient.get<Page<WorkItem>>(`${hpdiWorkspace(workspace)}/inbox`, {
    params: { scope, page, size },
    signal,
  });
  return data;
};

/** POST nhận việc. 409: đã có người nhận; 403: không thuộc nhóm hoặc là người tạo yêu cầu. */
export const claimTask = async (workspace: string, taskId: string): Promise<WorkItem> => {
  const { data } = await apiClient.post<WorkItem>(
    `${hpdiWorkspace(workspace)}/tasks/${encodeURIComponent(taskId)}/claim`,
  );
  return data;
};

/** POST giao (lại) việc. 400: người được giao không thuộc nhóm xử lý; 403: là người tạo yêu cầu. */
export const assignTask = async (
  workspace: string,
  taskId: string,
  assignee: string,
): Promise<WorkItem> => {
  const { data } = await apiClient.post<WorkItem>(
    `${hpdiWorkspace(workspace)}/tasks/${encodeURIComponent(taskId)}/assign`,
    { assignee },
  );
  return data;
};

/** GET số liệu dashboard (điều phối viên). */
export const getProcessStatistics = async (
  workspace: string,
  query: StatisticsQuery,
  signal?: AbortSignal,
): Promise<ProcessStatistics> => {
  const { data } = await apiClient.get<ProcessStatistics>(
    `${hpdiWorkspace(workspace)}/processes/statistics`,
    { params: query, signal },
  );
  return data;
};

/** POST hoàn thành bước. 403: không còn giữ việc; 409: bước đã đổi hoặc đã xong. */
export const completeTask = async (
  workspace: string,
  taskId: string,
  completion: TaskCompletion,
): Promise<void> => {
  await apiClient.post(
    `${hpdiWorkspace(workspace)}/tasks/${encodeURIComponent(taskId)}/complete`,
    completion,
  );
};

/** GET lịch sử một quy trình. 404 khi không tồn tại hoặc không được xem (agent chưa nhận việc). */
export const getTimeline = async (
  workspace: string,
  processInstanceId: string,
  signal?: AbortSignal,
): Promise<ProcessTimeline> => {
  const { data } = await apiClient.get<ProcessTimeline>(
    `${hpdiWorkspace(workspace)}/instances/${encodeURIComponent(processInstanceId)}/timeline`,
    { signal },
  );
  return data;
};

/** GET danh mục quy trình kèm nhãn và canStart của tôi. */
export const listProcesses = async (
  workspace: string,
  signal?: AbortSignal,
): Promise<ProcessDescriptor[]> => {
  const { data } = await apiClient.get<ProcessDescriptor[]>(
    `${hpdiWorkspace(workspace)}/processes`,
    {
      signal,
    },
  );
  return data;
};

/** POST tạo yêu cầu từ form chung; chỉ quy trình có canStart, businessKey do server sinh. */
export const startProcess = async (
  workspace: string,
  processKey: string,
  variables: Record<string, string>,
): Promise<ProcessHandle> => {
  const { data } = await apiClient.post<ProcessHandle>(
    `${hpdiWorkspace(workspace)}/processes/${encodeURIComponent(processKey)}/instances`,
    { variables },
  );
  return data;
};

/** GET danh sách yêu cầu, mới nhất trước: của tôi (mine) hoặc mọi yêu cầu tôi được xem (all). */
export const listRequests = async (
  workspace: string,
  scope: RequestScope,
  { page = 0, size = 20 }: PageQuery,
  signal?: AbortSignal,
): Promise<Page<ProcessSummary>> => {
  const { data } = await apiClient.get<Page<ProcessSummary>>(
    `${hpdiWorkspace(workspace)}/instances`,
    {
      params: { scope, page, size },
      signal,
    },
  );
  return data;
};
