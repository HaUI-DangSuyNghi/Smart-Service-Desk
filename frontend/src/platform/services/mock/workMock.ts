import type { Page } from "@/platform/shared/api";
import { getMe } from "../meService";
import type { ProcessStep, ProcessTimeline, TaskCompletion, WorkItem } from "../processService";
import type {
  Alert,
  AlertPage,
  AlertSeverity,
  AlertType,
  AssignmentCandidate,
  AssignmentSuggestion,
  DutyStatus,
  Estimate,
  EstimateRequest,
  EstimateRequestStatus,
  PauseInput,
  PendingReason,
  Priority,
  QueueItem,
  QueuePage,
  ReleaseInput,
  Sla,
  WorkSettings,
  WorkState,
} from "../workService";
import {
  DEFAULT_WORK_SETTINGS,
  isMockSubject,
  MOCK_AGENTS_BY_GROUP,
  MOCK_PEOPLE,
  PRIORITY_RANK,
} from "./mockDefaults";
import { mockDelay, mockError } from "./mockMode";

// Bộ máy quy trình giả của Tầng 2: hàng đợi, trạng thái việc, lịch sử, ca trực, cảnh báo.
// Không biết nghiệp vụ của app; app đăng ký luồng (registerFlow) và dữ liệu mẫu (registerSeed).

/** Một việc đang mở trong kho giả. */
export interface MockTask {
  id: string;
  key: string;
  name: string;
  candidateGroups: string[];
  assignee: string | null;
  createdAt: string;
  /** Việc vào hàng đợi người xử lý; bước duyệt, xác nhận thì không. */
  queued: boolean;
  /** Tổ xử lý, dùng đề xuất người. */
  group: string | null;
  workState: WorkState;
  pendingReason: PendingReason | null;
  pendingNote: string | null;
  pendingSince: string | null;
  inProgressSince: string | null;
  /** Phút đã làm của các lần trước, chưa tính lần đang làm. */
  workedBefore: number;
  firstStartedAt: string | null;
  priority: Priority;
  basePriority: Priority;
  escalated: boolean;
  estimate: Estimate;
  /** Mốc bắt đầu tính hạn; null là không có hạn. */
  slaFrom: string | null;
  reopenCount: number;
}

export interface MockProcess {
  workspace: string;
  processInstanceId: string;
  processKey: string;
  businessKey: string;
  label: string;
  initiator: string;
  startedAt: string;
  endedAt: string | null;
  steps: ProcessStep[];
  task: MockTask | null;
}

/** Bước app muốn mở tiếp; các trường không ghi lấy giá trị mặc định. */
export type NextTaskSpec = Partial<Omit<MockTask, "id">> &
  Pick<MockTask, "key" | "name" | "candidateGroups">;

/** Luồng của một processKey, do app đăng ký. */
export interface MockFlow {
  /** Hoàn thành bước qua API chung (Báo xong, xác nhận...): trả bước tiếp theo, null là kết thúc. */
  next: (process: MockProcess, task: MockTask, outcome: string) => NextTaskSpec | null;
  /** Trả việc: thường quay về hàng chờ duyệt. */
  release: (process: MockProcess, task: MockTask) => NextTaskSpec;
}

export interface MockUser {
  sub: string;
  name: string;
}

type Seed = (workspace: string, user: MockUser) => void;

const processes = new Map<string, MockProcess>();
const flows = new Map<string, MockFlow>();
const seeds: Seed[] = [];
const seeded = new Map<string, Promise<void>>();
const duty = new Map<string, Map<string, DutyStatus>>();
let estimateRequests: (EstimateRequest & { workspace: string })[] = [];
let alerts: (Alert & { workspace: string })[] = [];
let settings: WorkSettings = DEFAULT_WORK_SETTINGS;
let userPromise: Promise<MockUser> | null = null;

const MINUTE_MS = 60 * 1000;
const nowIso = () => new Date().toISOString();
const minutesBetween = (fromIso: string, to: number) =>
  Math.max(0, Math.round((to - new Date(fromIso).getTime()) / MINUTE_MS));
const addMinutes = (iso: string, minutes: number) =>
  new Date(new Date(iso).getTime() + minutes * MINUTE_MS).toISOString();

// ---------- Người dùng, dữ liệu mẫu ----------

const currentUser = () => {
  userPromise ??= getMe().then((me) => ({ sub: me.sub, name: me.username }));
  return userPromise;
};

/** Tên hiển thị của người giả và người đang đăng nhập. */
const nameOf = (subject: string, user: MockUser) =>
  subject === user.sub ? user.name : (MOCK_PEOPLE[subject] ?? subject);

const ensureSeeded = (workspace: string) => {
  let promise = seeded.get(workspace);
  if (!promise) {
    promise = currentUser().then((user) => seeds.forEach((seed) => seed(workspace, user)));
    seeded.set(workspace, promise);
  }
  return promise;
};

/** Mọi hàm giả gọi trước: chờ giả, dựng dữ liệu mẫu lần đầu, trả người đang đăng nhập. */
const prepare = async (workspace: string) => {
  await mockDelay();
  await ensureSeeded(workspace);
  return currentUser();
};

// ---------- Tính toán theo luật R2, R4, R5 ----------

const workedMinutes = (task: MockTask, now: number) =>
  task.workedBefore + (task.inProgressSince ? minutesBetween(task.inProgressSince, now) : 0);

/** Phần tính vào tải (R2): chưa bắt đầu = ET; đang làm = phần còn lại; đang chờ = phần còn lại × hệ số. */
const remainingMinutes = (task: MockTask, now: number) => {
  const { minRemainingMinutes, pendingLoadFactor } = settings.assignment;
  if (task.workState === "NOT_STARTED" && task.workedBefore === 0) return task.estimate.minutes;
  const rest = Math.max(task.estimate.minutes - workedMinutes(task, now), minRemainingMinutes);
  return task.workState === "PENDING" ? Math.round(rest * pendingLoadFactor) : rest;
};

const slaOf = (task: MockTask, now: number): Sla | null => {
  if (!task.slaFrom) return null;
  const target = settings.sla.targets[task.priority];
  const startBy = addMinutes(task.slaFrom, target.startWithinMinutes);
  const resolveBy = addMinutes(task.slaFrom, target.resolveWithinMinutes);
  const left = new Date(resolveBy).getTime() - now;
  const resolveBreached = left < 0;
  return {
    startBy,
    resolveBy,
    startBreached: task.firstStartedAt === null && now > new Date(startBy).getTime(),
    resolveBreached,
    warning:
      !resolveBreached &&
      left < target.resolveWithinMinutes * MINUTE_MS * settings.alerts.warnRemainingRatio,
    // Chờ người dùng trả lời không tính vào hạn của kỹ thuật viên
    clockPaused: task.workState === "PENDING" && task.pendingReason === "WAITING_REQUESTER",
  };
};

const toWorkItem = (process: MockProcess, task: MockTask): WorkItem => ({
  id: task.id,
  processInstanceId: process.processInstanceId,
  key: task.key,
  name: task.name,
  candidateGroups: task.candidateGroups,
  assignee: task.assignee,
  createdAt: task.createdAt,
  processKey: process.processKey,
  businessKey: process.businessKey,
});

const toQueueItem = (process: MockProcess, task: MockTask, now: number): QueueItem => ({
  ...toWorkItem(process, task),
  label: process.label,
  workState: task.workState,
  pendingReason: task.pendingReason,
  pendingNote: task.pendingNote,
  pendingSince: task.pendingSince,
  priority: task.priority,
  basePriority: task.basePriority,
  escalated: task.escalated,
  estimate: task.estimate,
  workedMinutes: workedMinutes(task, now),
  remainingMinutes: remainingMinutes(task, now),
  sla: slaOf(task, now),
  queuePosition: null,
  expectedStartAt: null,
  suggestStartNow: false,
  reopenCount: task.reopenCount,
});

const openProcesses = (workspace: string) =>
  [...processes.values()].filter((process) => process.workspace === workspace && process.task);

/** Hàng đợi theo R4: đang làm đứng đầu, rồi ưu tiên → hạn gần → tạo trước. */
const queueOf = (workspace: string, subject: string, now: number): QueueItem[] => {
  const items = openProcesses(workspace)
    .filter(({ task }) => task?.queued && task.assignee === subject)
    .map((process) => toQueueItem(process, process.task as MockTask, now));

  items.sort((a, b) => {
    const inProgress = Number(b.workState === "IN_PROGRESS") - Number(a.workState === "IN_PROGRESS");
    if (inProgress !== 0) return inProgress;
    const priority = PRIORITY_RANK[b.priority] - PRIORITY_RANK[a.priority];
    if (priority !== 0) return priority;
    const deadline = (a.sla?.resolveBy ?? "").localeCompare(b.sla?.resolveBy ?? "");
    return deadline !== 0 ? deadline : a.createdAt.localeCompare(b.createdAt);
  });

  const hasInProgress = items.some((item) => item.workState === "IN_PROGRESS");
  let waitedMinutes = 0;
  return items.map((item, index) => {
    const expectedStartAt =
      item.workState === "NOT_STARTED" && item.workedMinutes === 0
        ? new Date(now + waitedMinutes * MINUTE_MS).toISOString()
        : null;
    waitedMinutes += item.remainingMinutes;
    return {
      ...item,
      queuePosition: index + 1,
      expectedStartAt,
      suggestStartNow: !hasInProgress && index === 0,
    };
  });
};

const loadOf = (workspace: string, subject: string, now: number) =>
  queueOf(workspace, subject, now).reduce((sum, item) => sum + item.remainingMinutes, 0);

// ---------- Kho ----------

const makeTask = (spec: NextTaskSpec): MockTask => {
  const priority = spec.priority ?? "MEDIUM";
  return {
    id: crypto.randomUUID(),
    createdAt: nowIso(),
    assignee: null,
    queued: false,
    group: null,
    workState: "NOT_STARTED",
    pendingReason: null,
    pendingNote: null,
    pendingSince: null,
    inProgressSince: null,
    workedBefore: 0,
    firstStartedAt: null,
    basePriority: priority,
    escalated: false,
    estimate: { minutes: 30, basis: "DEFAULT" },
    slaFrom: null,
    reopenCount: 0,
    ...spec,
    priority,
  };
};

const pushStep = (
  process: MockProcess,
  task: MockTask,
  outcome: string,
  comment: string | null,
) => {
  process.steps.push({
    id: task.id,
    key: task.key,
    name: task.name,
    assignee: task.assignee,
    outcome,
    comment,
    startedAt: task.createdAt,
    claimedAt: task.assignee ? task.createdAt : null,
    endedAt: nowIso(),
  });
};

const openNext = (process: MockProcess, spec: NextTaskSpec | null) => {
  if (spec === null) {
    process.task = null;
    process.endedAt = nowIso();
    return;
  }
  process.task = makeTask(spec);
};

/** Dừng đồng hồ của lần đang làm, cộng vào phút đã làm. */
const stopClock = (task: MockTask, now: number) => {
  task.workedBefore = workedMinutes(task, now);
  task.inProgressSince = null;
};

const findTask = (workspace: string, taskId: string) => {
  const process = openProcesses(workspace).find(({ task }) => task?.id === taskId);
  if (!process?.task) throw mockError(404, "Việc không còn tồn tại.");
  return { process, task: process.task };
};

const assertHolder = (task: MockTask, user: MockUser) => {
  if (task.assignee !== user.sub) throw mockError(403, "Bạn không giữ việc này.");
};

const inProgressCount = (workspace: string, subject: string) =>
  openProcesses(workspace).filter(
    ({ task }) => task?.assignee === subject && task.workState === "IN_PROGRESS",
  ).length;

const queueItemAfter = (workspace: string, process: MockProcess, now = Date.now()) => {
  const task = process.task as MockTask;
  const fromQueue = task.assignee
    ? queueOf(workspace, task.assignee, now).find((item) => item.id === task.id)
    : undefined;
  return fromQueue ?? toQueueItem(process, task, now);
};

const pushAlert = (
  workspace: string,
  process: MockProcess,
  type: AlertType,
  severity: AlertSeverity,
) => {
  alerts.unshift({
    id: crypto.randomUUID(),
    workspace,
    type,
    severity,
    taskId: process.task?.id ?? null,
    processInstanceId: process.processInstanceId,
    processKey: process.processKey,
    businessKey: process.businessKey,
    label: process.label,
    createdAt: nowIso(),
    readAt: null,
  });
};

const dutyOf = (workspace: string, subject: string, user: MockUser): DutyStatus => {
  let byWorkspace = duty.get(workspace);
  if (!byWorkspace) {
    byWorkspace = new Map();
    duty.set(workspace, byWorkspace);
  }
  let status = byWorkspace.get(subject);
  if (!status) {
    status = {
      subject,
      displayName: nameOf(subject, user),
      // Một người giả nghỉ để thấy luật "chỉ giao cho người đang trực"
      onDuty: subject !== "mock-agent-cuong",
      source: "MANUAL",
      changedAt: nowIso(),
      changedBy: null,
      note: null,
    };
    byWorkspace.set(subject, status);
  }
  return status;
};

const page = <T>(items: T[]): Page<T> => ({ items, page: 0, size: 50, total: items.length });

// ---------- API giả (cùng chữ ký với workService) ----------

const getQueue = async (workspace: string, assignee?: string): Promise<QueuePage> => {
  const user = await prepare(workspace);
  const subject = assignee || user.sub;
  const now = Date.now();
  const items = queueOf(workspace, subject, now);
  return {
    ...page(items),
    assignee: subject,
    loadMinutes: items.reduce((sum, item) => sum + item.remainingMinutes, 0),
  };
};

const startWork = async (workspace: string, taskId: string) => {
  const user = await prepare(workspace);
  const { process, task } = findTask(workspace, taskId);
  assertHolder(task, user);
  if (task.workState !== "NOT_STARTED") throw mockError(409, "Việc không ở trạng thái chưa bắt đầu.");
  if (inProgressCount(workspace, user.sub) >= settings.assignment.maxInProgress) {
    throw mockError(409, "Bạn đang có việc Đang làm. Tạm dừng việc đó trước.");
  }
  task.workState = "IN_PROGRESS";
  task.inProgressSince = nowIso();
  task.firstStartedAt ??= task.inProgressSince;
  return queueItemAfter(workspace, process);
};

const pauseWork = async (workspace: string, taskId: string, input: PauseInput) => {
  const user = await prepare(workspace);
  const { process, task } = findTask(workspace, taskId);
  assertHolder(task, user);
  if (task.workState !== "IN_PROGRESS") throw mockError(409, "Việc không đang làm.");
  if (input.reason === "OTHER" && !input.note?.trim()) throw mockError(400, "Thiếu ghi chú.");
  stopClock(task, Date.now());
  task.workState = "PENDING";
  task.pendingReason = input.reason;
  task.pendingNote = input.note?.trim() || null;
  task.pendingSince = nowIso();
  return queueItemAfter(workspace, process);
};

const resumeWork = async (workspace: string, taskId: string) => {
  const user = await prepare(workspace);
  const { process, task } = findTask(workspace, taskId);
  assertHolder(task, user);
  if (task.workState !== "PENDING") throw mockError(409, "Việc không đang chờ.");
  if (inProgressCount(workspace, user.sub) >= settings.assignment.maxInProgress) {
    throw mockError(409, "Bạn đang có việc Đang làm. Tạm dừng việc đó trước.");
  }
  task.workState = "IN_PROGRESS";
  task.inProgressSince = nowIso();
  task.pendingReason = null;
  task.pendingNote = null;
  task.pendingSince = null;
  return queueItemAfter(workspace, process);
};

const releaseWork = async (workspace: string, taskId: string, input: ReleaseInput) => {
  const user = await prepare(workspace);
  const { process, task } = findTask(workspace, taskId);
  assertHolder(task, user);
  const flow = flows.get(process.processKey);
  if (!flow) throw mockError(409, "Quy trình không hỗ trợ trả việc.");
  stopClock(task, Date.now());
  pushStep(process, task, "released", input.note);
  openNext(process, { ...flow.release(process, task), workedBefore: task.workedBefore });
};

const getAssignmentSuggestion = async (
  workspace: string,
  taskId: string,
): Promise<AssignmentSuggestion> => {
  const user = await prepare(workspace);
  const { process, task } = findTask(workspace, taskId);
  const now = Date.now();
  const group = task.group ?? "";
  // Bước duyệt chưa chốt tổ thì xét mọi người xử lý; người đang đăng nhập cũng là ứng viên để tự thử
  const agents = MOCK_AGENTS_BY_GROUP[group] ?? Object.values(MOCK_AGENTS_BY_GROUP).flat();
  const subjects = [...new Set([...agents, user.sub])];
  const candidates: AssignmentCandidate[] = subjects.map((subject) => {
    const onDuty = dutyOf(workspace, subject, user).onDuty;
    const excludedReason =
      subject === process.initiator ? "IS_REQUESTER" : onDuty ? null : "OFF_DUTY";
    return {
      subject,
      displayName: nameOf(subject, user),
      onDuty,
      loadMinutes: loadOf(workspace, subject, now),
      assignedToday: queueOf(workspace, subject, now).length,
      shiftRemainingMinutes: 120,
      recommended: false,
      excludedReason,
    };
  });

  const { tieThresholdMinutes } = settings.assignment;
  const eligible = candidates
    .filter((candidate) => !candidate.excludedReason)
    .sort((a, b) =>
      Math.abs(a.loadMinutes - b.loadMinutes) < tieThresholdMinutes
        ? a.assignedToday - b.assignedToday
        : a.loadMinutes - b.loadMinutes,
    );
  const best = eligible[0];
  if (best) best.recommended = true;

  return {
    taskId,
    group,
    estimateMinutes: task.estimate.minutes,
    recommended: best?.subject ?? null,
    reason: best
      ? eligible.map((item) => `${item.displayName} còn ~${item.loadMinutes} phút việc`).join(", ")
      : "Tổ không có ai đang trực.",
    candidates,
  };
};

const requestEstimateChange = async (
  workspace: string,
  taskId: string,
  input: { proposedMinutes: number; reason: string },
) => {
  const user = await prepare(workspace);
  const { process, task } = findTask(workspace, taskId);
  assertHolder(task, user);
  if (estimateRequests.some((item) => item.taskId === taskId && item.status === "PENDING")) {
    throw mockError(409, "Việc này đã có yêu cầu sửa ước tính đang chờ.");
  }
  const request = {
    id: crypto.randomUUID(),
    workspace,
    taskId,
    processInstanceId: process.processInstanceId,
    businessKey: process.businessKey,
    label: process.label,
    requestedBy: user.sub,
    currentMinutes: task.estimate.minutes,
    proposedMinutes: input.proposedMinutes,
    reason: input.reason,
    status: "PENDING" as const,
    decidedBy: null,
    decidedAt: null,
    decisionNote: null,
    createdAt: nowIso(),
  };
  estimateRequests.unshift(request);
  pushAlert(workspace, process, "ESTIMATE_REQUESTED", "INFO");
  return request;
};

const listEstimateRequests = async (workspace: string, status?: EstimateRequestStatus) => {
  await prepare(workspace);
  return page(
    estimateRequests.filter(
      (item) => item.workspace === workspace && (!status || item.status === status),
    ),
  );
};

const decideEstimateRequest = async (
  workspace: string,
  requestId: string,
  input: { approved: boolean; note?: string },
) => {
  const user = await prepare(workspace);
  const request = estimateRequests.find((item) => item.id === requestId);
  if (!request || request.workspace !== workspace) throw mockError(404, "Không tìm thấy yêu cầu.");
  if (request.status !== "PENDING") throw mockError(409, "Yêu cầu đã được quyết.");
  request.status = input.approved ? "APPROVED" : "REJECTED";
  request.decidedBy = user.sub;
  request.decidedAt = nowIso();
  request.decisionNote = input.note?.trim() || null;
  const task = processes.get(request.processInstanceId)?.task;
  if (input.approved && task?.id === request.taskId) {
    task.estimate = { minutes: request.proposedMinutes, basis: "COORDINATOR" };
  }
  return request;
};

const listDuty = async (workspace: string) => {
  const user = await prepare(workspace);
  const subjects = [...new Set([...Object.values(MOCK_AGENTS_BY_GROUP).flat(), user.sub])];
  return subjects.map((subject) => dutyOf(workspace, subject, user));
};

const setDuty = async (
  workspace: string,
  subject: string,
  input: { onDuty: boolean; note?: string },
) => {
  const user = await prepare(workspace);
  const status = dutyOf(workspace, subject === "me" ? user.sub : subject, user);
  status.onDuty = input.onDuty;
  status.note = input.note?.trim() || null;
  status.changedAt = nowIso();
  status.changedBy = user.sub;
  // R3: tắt trực thì báo điều phối viên về các việc người này đang giữ
  if (!input.onDuty) {
    openProcesses(workspace)
      .filter(({ task }) => task?.queued && task.assignee === status.subject)
      .forEach((process) => pushAlert(workspace, process, "ASSIGNEE_OFF_DUTY", "WARNING"));
  }
  return status;
};

const listAlerts = async (
  workspace: string,
  { since, unreadOnly }: { since?: string; unreadOnly?: boolean },
): Promise<AlertPage> => {
  await prepare(workspace);
  const inWorkspace = alerts.filter((alert) => alert.workspace === workspace);
  const items = inWorkspace.filter(
    (alert) => (!since || alert.createdAt > since) && (!unreadOnly || !alert.readAt),
  );
  return { ...page(items), unread: inWorkspace.filter((alert) => !alert.readAt).length };
};

const markAlertRead = async (workspace: string, alertId: string) => {
  await prepare(workspace);
  const alert = alerts.find((item) => item.id === alertId && item.workspace === workspace);
  if (!alert) throw mockError(404, "Không tìm thấy cảnh báo.");
  alert.readAt ??= nowIso();
};

const getWorkSettings = async () => {
  await mockDelay();
  return settings;
};

const updateWorkSettings = async (next: WorkSettings) => {
  await mockDelay();
  const user = await currentUser();
  if (next.version !== settings.version) throw mockError(409, "Người khác vừa sửa cấu hình.");
  settings = { ...next, version: next.version + 1, updatedAt: nowIso(), updatedBy: user.sub };
  return settings;
};

// ---------- Thay cho API chung của khối P khi việc nằm trong kho giả ----------

const hasTask = (taskId: string) =>
  [...processes.values()].some((process) => process.task?.id === taskId);

const hasProcess = (processInstanceId: string) => processes.has(processInstanceId);

const claimTask = async (workspace: string, taskId: string) => {
  const user = await prepare(workspace);
  const { process, task } = findTask(workspace, taskId);
  if (task.assignee) throw mockError(409, "Việc này vừa có người khác nhận.");
  if (process.initiator === user.sub) throw mockError(403, "Không nhận việc của yêu cầu mình tạo.");
  task.assignee = user.sub;
  return toWorkItem(process, task);
};

const assignTask = async (workspace: string, taskId: string, assignee: string) => {
  await prepare(workspace);
  const { process, task } = findTask(workspace, taskId);
  if (assignee === process.initiator) throw mockError(403, "Không giao cho người tạo yêu cầu.");
  task.assignee = assignee;
  return toWorkItem(process, task);
};

const completeTask = async (workspace: string, taskId: string, completion: TaskCompletion) => {
  const user = await prepare(workspace);
  const { process, task } = findTask(workspace, taskId);
  if (task.key !== completion.expectedTaskKey) throw mockError(409, "Bước đã thay đổi.");
  assertHolder(task, user);
  const flow = flows.get(process.processKey);
  if (!flow) throw mockError(409, "Quy trình không có luồng giả.");
  stopClock(task, Date.now());
  pushStep(process, task, completion.outcome, completion.comment ?? null);
  openNext(process, flow.next(process, task, completion.outcome));
};

const getTimeline = async (workspace: string, processInstanceId: string) => {
  await prepare(workspace);
  const process = processes.get(processInstanceId);
  if (!process || process.workspace !== workspace) throw mockError(404, "Không tìm thấy.");
  const { task } = process;
  const openStep: ProcessStep[] = task
    ? [
        {
          id: task.id,
          key: task.key,
          name: task.name,
          assignee: task.assignee,
          outcome: null,
          comment: null,
          startedAt: task.createdAt,
          claimedAt: task.firstStartedAt,
          endedAt: null,
        },
      ]
    : [];
  const timeline: ProcessTimeline = {
    id: process.processInstanceId,
    processKey: process.processKey,
    businessKey: process.businessKey,
    workspace,
    initiator: process.initiator,
    startedAt: process.startedAt,
    endedAt: process.endedAt,
    currentTasks: task ? [toWorkItem(process, task)] : [],
    steps: [...process.steps, ...openStep],
    variables: {},
  };
  return timeline;
};

/** Tên của người giả; người thật để backend tra. */
const peopleNames = (ids: readonly string[]) =>
  Object.fromEntries(
    ids.filter(isMockSubject).map((id) => [id, MOCK_PEOPLE[id] ?? id] as const),
  );

// ---------- Cho kho giả của app ----------

const createProcess = (input: {
  workspace: string;
  processKey: string;
  businessKey: string;
  label: string;
  initiator: string;
  startedAt?: string;
  steps?: ProcessStep[];
  task: NextTaskSpec | null;
}): MockProcess => {
  const process: MockProcess = {
    workspace: input.workspace,
    processInstanceId: crypto.randomUUID(),
    processKey: input.processKey,
    businessKey: input.businessKey,
    label: input.label,
    initiator: input.initiator,
    startedAt: input.startedAt ?? nowIso(),
    endedAt: null,
    steps: input.steps ?? [],
    task: null,
  };
  openNext(process, input.task);
  processes.set(process.processInstanceId, process);
  return process;
};

/** App tự chuyển bước (ví dụ duyệt) với kết quả và người thao tác của mình. */
const advance = (
  process: MockProcess,
  step: { outcome: string; comment: string | null; actor: string },
  next: NextTaskSpec | null,
) => {
  if (process.task) {
    const task = process.task;
    stopClock(task, Date.now());
    pushStep(process, { ...task, assignee: step.actor }, step.outcome, step.comment);
  }
  openNext(process, next);
};

/** Bản ghi đầy đủ để app ghép vào view của mình; null khi quy trình đã xong. */
const queueItemOf = (process: MockProcess) =>
  process.task ? queueItemAfter(process.workspace, process) : null;

/** Xóa toàn bộ kho (test). `user` để không phải gọi /me. */
const resetWorkMock = (user?: MockUser) => {
  processes.clear();
  seeded.clear();
  duty.clear();
  estimateRequests = [];
  alerts = [];
  settings = DEFAULT_WORK_SETTINGS;
  userPromise = user ? Promise.resolve(user) : null;
};

export const workMock = {
  // Cùng chữ ký với workService
  getQueue,
  startWork,
  pauseWork,
  resumeWork,
  releaseWork,
  getAssignmentSuggestion,
  requestEstimateChange,
  listEstimateRequests,
  decideEstimateRequest,
  listDuty,
  setDuty,
  listAlerts,
  markAlertRead,
  getWorkSettings,
  updateWorkSettings,
  // Thay cho API chung của khối P
  hasTask,
  hasProcess,
  claimTask,
  assignTask,
  completeTask,
  getTimeline,
  peopleNames,
  // Cho kho giả của app
  registerFlow: (processKey: string, flow: MockFlow) => flows.set(processKey, flow),
  registerSeed: (seed: Seed) => seeds.push(seed),
  prepare,
  currentUser,
  createProcess,
  findProcess: (processInstanceId: string) => processes.get(processInstanceId),
  listProcesses: (workspace: string, processKey: string) =>
    [...processes.values()].filter(
      (process) => process.workspace === workspace && process.processKey === processKey,
    ),
  advance,
  queueItemOf,
  pushAlert,
  nameOf,
  settings: () => settings,
  resetWorkMock,
};
