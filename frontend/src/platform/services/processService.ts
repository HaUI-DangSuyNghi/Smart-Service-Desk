import { apiClient, type Page } from "@/platform/shared/api";
import { hpdiWorkspace } from "./paths";

// Kiểu khớp schema trong contracts/hpdi/openapi.yaml (WorkItem, ProcessDescriptor, ProcessTimeline...)

/** ASSIGNED: việc đang giao cho tôi. CANDIDATE: việc của nhóm tôi chưa ai nhận (trừ yêu cầu tôi tạo). */
export type InboxScope = "ASSIGNED" | "CANDIDATE";

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
  startFields: ProcessOption[];
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
  const { data } = await apiClient.get<ProcessDescriptor[]>(`${hpdiWorkspace(workspace)}/processes`, {
    signal,
  });
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

/** GET yêu cầu tôi đã tạo, mọi quy trình, mới nhất trước. */
export const listMyRequests = async (
  workspace: string,
  { page = 0, size = 20 }: PageQuery,
  signal?: AbortSignal,
): Promise<Page<ProcessSummary>> => {
  const { data } = await apiClient.get<Page<ProcessSummary>>(`${hpdiWorkspace(workspace)}/instances`, {
    params: { page, size },
    signal,
  });
  return data;
};
